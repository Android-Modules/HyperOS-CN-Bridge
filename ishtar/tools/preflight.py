#!/usr/bin/env python3
"""Read-only inventory for the ishtar EliteRomLite target; never changes the phone."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "songyuan/tools"))
from catalog import FEATURES, OWNERS

TARGET_BUILD = "OS3.0.308.0.WMACNXM"
PROPERTIES = (
    "ro.product.device", "ro.build.version.incremental", "ro.build.version.sdk",
    "ro.build.display.id", "ro.build.elite.romvariant", "ro.build.elite.versioncode",
    "ro.miui.region", "ro.miui.cust_variant", "ro.product.mod_device",
    "ro.miui.product.home", "ro.miui.support.system.app.uninstall.v2",
)


def run(*args):
    return subprocess.run(args, text=True, capture_output=True, check=True).stdout.strip()


def inventory(adb, serial, output, sdk, pull_apks=False):
    def root(command):
        return run(str(adb), "-s", serial, "shell", "su -c " + shlex.quote(command))

    if root("id -u") != "0":
        raise RuntimeError("Shell Root is required")
    # getprop may return empty strings; query separately to preserve them.
    props = {p: root("getprop " + p) for p in PROPERTIES}
    if (props["ro.product.device"] != "ishtar"
            or props["ro.build.version.incremental"] != TARGET_BUILD
            or props["ro.build.version.sdk"] != "36"
            or props["ro.build.elite.romvariant"] != "EliteRomLite"
            or props["ro.build.elite.versioncode"] != "v4.05"):
        raise RuntimeError("This preflight targets ishtar / EliteRomLite / " + TARGET_BUILD)
    output.mkdir(parents=True, exist_ok=True)
    users = sorted(map(int, root("ls /data/user_de").split()))
    # Read PackageManager's cached identity as well as APK signatures. A modified
    # ROM can ship stripped APK signatures while retaining the original identity.
    tree = ET.fromstring(root("abx2xml /data/system/packages.xml -"))
    certificates = {c.get("index"): c.get("key") for c in tree.iter("cert") if c.get("key")}
    identities = {}
    for package in tree.findall("package"):
        name = package.get("name")
        if name in OWNERS or name in {"android", "com.xiaomi.finddevice"}:
            identities[name] = {
                "uid": int(package.get("userId") or package.get("sharedUserId")),
                "signers": [hashlib.sha256(bytes.fromhex(certificates[c.get("index")])).hexdigest()
                            for c in package.findall("./sigs/cert") if c.get("index") in certificates],
            }
    packages = {}
    for pkg in sorted(set(OWNERS) | {"com.xiaomi.finddevice"}):
        paths = [s.removeprefix("package:") for s in root("pm path --user 0 " + pkg + " || true").splitlines()
                 if s.startswith("package:")]
        item = {"installed": bool(paths), "paths": paths}
        if paths:
            dump = root("dumpsys package " + pkg)
            for key in ("versionName", "versionCode"):
                match = re.search(r"\b" + key + r"=([^\s]+)", dump)
                item[key] = match[1] if match else None
            uid = re.search(r"\b(?:userId|appId)=(\d+)", dump)
            shared = re.search(r"sharedUser=SharedUserSetting\{[^ ]+ ([^}/]+)/([0-9]+)\}", dump)
            item["sharedUser"] = shared[1] if shared else None
            item["uid"] = identities.get(pkg, {}).get("uid", int(shared[2]) if shared else int(uid[1]) if uid else None)
            item["registeredSigners"] = identities.get(pkg, {}).get("signers", [])
            item["users"] = sorted(map(int, re.findall(r"User (\d+):[^\n]*installed=true", dump)))
            item["apks"] = []
            for n, path in enumerate(paths):
                digest = root("sha256sum " + shlex.quote(path)).split()[0]
                apk = {"name": Path(path).name, "sha256": digest}
                if pull_apks:
                    dest = output / "apks" / pkg / (str(n) + "-" + Path(path).name)
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    if not dest.is_file() or hashlib.sha256(dest.read_bytes()).hexdigest() != digest:
                        run(str(adb), "-s", serial, "pull", path, str(dest))
                    if hashlib.sha256(dest.read_bytes()).hexdigest() != digest:
                        raise RuntimeError("APK changed during capture: " + pkg)
                    verified = subprocess.run([str(sdk / "build-tools/36.0.0/apksigner"), "verify",
                                               "--min-sdk-version", "36", "--print-certs", str(dest)],
                                              text=True, capture_output=True)
                    apk["signatureValid"] = verified.returncode == 0
                    apk["certificates"] = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})",
                                                      verified.stdout) if verified.returncode == 0 else []
                    badge = run(str(sdk / "build-tools/36.0.0/aapt2"), "dump", "badging", str(dest))
                    if not badge.startswith("package: name='" + pkg + "' "):
                        raise RuntimeError("Unexpected package: " + pkg)
                    xml = run(str(sdk / "build-tools/36.0.0/aapt2"), "dump", "xmltree", str(dest),
                              "--file", "AndroidManifest.xml")
                    shared_manifest = re.search(r'sharedUserId[^=]*="([^"]+)"', xml)
                    apk["manifestSharedUser"] = shared_manifest[1] if shared_manifest else None
                item["apks"].append(apk)
        packages[pkg] = item
    features = [{"id": f[0], "name": f[1],
                 "packages": [p for p, owners in OWNERS.items() if f[0] in owners]}
                for f in FEATURES]
    result = {"schema": 1, "properties": props, "users": users, "platformIdentity": identities.get("android"),
              "packages": packages, "features": features}
    dest = output / "baseline.json"
    dest.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f"Captured {sum(p['installed'] for p in packages.values())}/{len(packages)} packages; users={users}")
    print("Missing: " + ", ".join(p for p, item in packages.items() if not item["installed"]))
    print(dest)
    return result


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", required=True, help="Exact ADB serial; no implicit device selection")
    ap.add_argument("--adb", type=Path, default=Path.home() / "Library/Android/sdk/platform-tools/adb")
    ap.add_argument("--sdk", type=Path, default=Path.home() / "Library/Android/sdk")
    ap.add_argument("--output", type=Path, default=ROOT / "diagnostics/ishtar-adaptation/baseline")
    ap.add_argument("--pull-apks", action="store_true", help="Copy system APKs and verify signatures locally")
    args = ap.parse_args()
    inventory(args.adb, args.serial, args.output, args.sdk, args.pull_apks)


if __name__ == "__main__":
    main()
