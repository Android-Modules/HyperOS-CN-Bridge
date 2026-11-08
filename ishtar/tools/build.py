#!/usr/bin/env python3
"""Build the strictly separate ishtar Elite native-ROM supplement profile."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "songyuan/tools"))
from build import SPECS, run, sha
from catalog import FEATURES, OWNERS

PACKAGES = {
    "com.miui.nextpay", "com.unionpay.tsmservice.mi", "com.xiaomi.payment",
    "com.miui.voiceassist", "com.miui.voicetrigger", "com.xiaomi.aiasst.service",
    "com.miui.contentextension", "com.miui.yellowpage", "com.miui.hybrid",
}
BUILD = "OS3.0.308.0.WMACNXM"
PLATFORM = "c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025"
CERTIFICATES = {
    "com.unionpay.tsmservice.mi": "5f6b3d22859624a8de36f95d03ae2ff7581a1daabb230c62aed9470a54414845",
    "com.miui.yellowpage": "e4431422e25ae61fa3655e9f908a9b40da2acd67fab99b737027c21fd22b7d40",
}


def inputs(source):
    paths = set()
    for relative, package, _ in SPECS:
        if package in PACKAGES:
            apks = list((source / relative).glob("*.apk"))
            if len(apks) != 1:
                raise RuntimeError("Expected one original APK: " + relative)
            paths.update(apks)
    # Pin permissions too; never reuse songyuan grants or native libraries.
    paths.update((source / "etc/permissions").glob("*.xml"))
    return {p.relative_to(source).as_posix(): sha(p) for p in sorted(paths)}


def build(source, sdk):
    lock = json.loads((ROOT / "ishtar/rom-inputs.lock.json").read_text())
    if inputs(source) != lock["files"]:
        raise RuntimeError("ishtar ROM input hashes do not match the locked originals")
    tools = sdk / "build-tools/36.0.0"
    with tempfile.TemporaryDirectory(prefix="ishtar-build-") as tmp:
        temp = Path(tmp)
        stage = temp / "module"
        shutil.copytree(ROOT / "ishtar/module", stage)
        shutil.copytree(ROOT / "META-INF", stage / "META-INF")
        classes, dex = temp / "classes", temp / "dex"
        classes.mkdir(); dex.mkdir()
        java = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
        javac = java / "javac" if java else shutil.which("javac")
        jvm = java / "java" if java else shutil.which("java")
        android = sdk / "platforms/android-36/android.jar"
        run([javac, "--release", "8", "-cp", android, "-d", classes,
             ROOT / "songyuan/tools/ComponentController.java", ROOT / "songyuan/tools/DeviceRepair.java"])
        run([jvm, "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "36",
             "--lib", android, "--output", dex, *sorted(classes.glob("*.class"))])
        with zipfile.ZipFile(stage / "repair.jar", "w", zipfile.ZIP_DEFLATED) as z:
            info = zipfile.ZipInfo("classes.dex", (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, (dex / "classes.dex").read_bytes())
        catalog = dict(schema=1, profile="ishtar-elite308-addons", version="3.2.0-ishtar-preview",
                       target=BUILD, features=[], units=[], nativeFeatures=[], requiredLibraries=[])
        manifest = dict(source=BUILD, device="ishtar", mode="native-addons", apks=[])
        libraries = set()
        for relative, package, _ in SPECS:
            if package not in PACKAGES:
                continue
            apk = next((source / relative).glob("*.apk"))
            cert = run([tools / "apksigner", "verify", "--min-sdk-version", "36", "--print-certs", apk])
            if re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", cert) != [CERTIFICATES.get(package, PLATFORM)]:
                raise RuntimeError("Unexpected original signer: " + package)
            badge = run([tools / "aapt2", "dump", "badging", apk])
            if not badge.startswith("package: name='" + package + "' "):
                raise RuntimeError("Unexpected package: " + package)
            libraries.update(re.findall(r"^uses-library:'([^']+)'", badge, re.M))
            version = re.search(r"versionName='([^']+)'", badge)[1]
            target = stage / "system/product" / relative
            target.mkdir(parents=True)
            shutil.copyfile(apk, target / apk.name)
            (target / ".replace").touch()
            xml = run([tools / "aapt2", "dump", "xmltree", apk, "--file", "AndroidManifest.xml"])
            if "extractNativeLibs(0x010104ea)=false" not in xml:
                with zipfile.ZipFile(apk) as z:
                    for name in z.namelist():
                        if re.fullmatch(r"lib/arm64-v8a/[^/]+\.so", name):
                            lib = target / "lib/arm64" / Path(name).name
                            lib.parent.mkdir(parents=True, exist_ok=True)
                            lib.write_bytes(z.read(name))
            files = list(target.rglob("*"))
            grants = ET.Element("permissions")
            for path in sorted((source / "etc/permissions").glob("*.xml")):
                for node in ET.parse(path).getroot():
                    if node.tag == "privapp-permissions" and node.get("package") == package:
                        grants.append(node)
            if len(grants):
                permission = stage / f"system/product/etc/permissions/ishtar-{package}.xml"
                permission.parent.mkdir(parents=True, exist_ok=True)
                ET.indent(grants)
                permission.write_bytes(ET.tostring(grants, encoding="utf-8", xml_declaration=True))
                files.append(permission)
            catalog["units"].append(dict(package=package, owners=OWNERS[package], globalPresent=False,
                version=version, certificate=CERTIFICATES.get(package, PLATFORM),
                files=[dict(path=p.relative_to(stage).as_posix(), sha256=sha(p)) for p in sorted(files) if p.is_file()]))
            manifest["apks"].append(dict(package=package, version=version, source=relative + "/" + apk.name,
                file=(target / apk.name).relative_to(stage).as_posix(), sha256=sha(apk), certificate=CERTIFICATES.get(package, PLATFORM)))
        catalog["requiredLibraries"] = sorted(libraries)
        owners = {o for u in catalog["units"] for o in u["owners"]}
        catalog["features"] = [dict(id="cn_region", name="CN 地区与国内功能判断", category="地区配置",
            description="恢复 CN / cn，并移除 mod_device 的 _global 标记；不修改设备型号", update="paired")]
        for i, name, category, description, update in FEATURES:
            if i in owners:
                catalog["features"].append(dict(id=i, name=name, category=category, description=description, update=update))
            else:
                catalog["nativeFeatures"].append(dict(id=i, name=name, category=category,
                    packages=[p for p, owners in OWNERS.items() if owners[0] == i and p not in {"com.miui.themestore", "com.hyper.gallery.plugin"}],
                    description="由现有 ROM 提供，保留原包与数据；国内功能受 CN 配置和 ROM 实现影响"))
        (stage / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
        (stage / "sources.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        shutil.copyfile(stage / "catalog.json", stage / "webroot/catalog.json")
        for script in stage.rglob("*.sh"):
            run(["sh", "-n", script])
        output = ROOT / "dist/ishtar-cn-addons-OS3.0.308.0.zip"
        output.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for p in sorted(stage.rglob("*")):
                if not p.is_file(): continue
                info = zipfile.ZipInfo(p.relative_to(stage).as_posix(), (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (0o100755 if p.suffix == ".sh" else 0o100644) << 16
                z.writestr(info, p.read_bytes())
        with zipfile.ZipFile(output) as z:
            if z.testzip(): raise RuntimeError("ZIP CRC mismatch")
            for unit in catalog["units"]:
                for item in unit["files"]:
                    if hashlib.sha256(z.read(item["path"])).hexdigest() != item["sha256"]:
                        raise RuntimeError("ZIP file changed: " + item["path"])
        output.with_suffix(".zip.sha256").write_text(sha(output) + "  " + output.name + "\n")
        print(f"{output}: {output.stat().st_size / 1048576:.1f} MiB; {len(catalog['units'])} original APKs")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, default=ROOT / "rom/ishtar/cn")
    ap.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk")))
    args = ap.parse_args()
    build(args.source, args.sdk)
