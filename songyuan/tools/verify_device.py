#!/usr/bin/env python3
"""Compare installed APK bytes and native libraries with a build's source list."""
import argparse
import json
from pathlib import Path
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ap = argparse.ArgumentParser(description=__doc__)
ap.add_argument("--mode", default="complete", choices=["complete", "basic-cloud"])
ap.add_argument("--adb", default=str(Path.home() / "Library/Android/sdk/platform-tools/adb"))
args = ap.parse_args()


def root(command):
    return subprocess.run([args.adb, "shell", "su -c " + shlex.quote(command)],
                          check=True, capture_output=True, text=True).stdout


sources = json.loads((ROOT / f"dist/sources-{args.mode}.json").read_text())
packages = sorted({item["package"] for item in sources["apks"]})
paths = {}
for package in packages:
    paths[package] = [x.removeprefix("package:") for x in root("pm path " + package).splitlines() if x.startswith("package:")]
checks = []
for item in sources["apks"]:
    if item["file"].startswith("payload/"):
        candidates = paths[item["package"]]
    else:
        expected_path = "/" + item["file"]
        expected_path = expected_path.replace("/system/product/", "/product/").replace("/system/system_ext/", "/system_ext/")
        candidates = ([expected_path] if expected_path in paths[item["package"]]
                      else [p for p in paths[item["package"]] if p.startswith("/data/app/")])
    matched = False
    actual_hash = None
    for path in candidates:
        output = root("sha256sum " + shlex.quote(path)).split()
        actual_hash = output[0] if output else None
        if actual_hash == item["sha256"]:
            matched = True
            break
    checks.append({"package": item["package"], "file": item["file"], "match": matched, "installed_paths": paths[item["package"]], "sha256": actual_hash})
for item in sources.get("native_libraries", []):
    output = root("sha256sum " + shlex.quote("/" + item["file"])).split()
    checks.append({"file": item["file"], "match": bool(output) and output[0] == item["sha256"]})
report = {"build": root("getprop ro.build.version.incremental").strip(), "region": root("getprop ro.miui.region").strip(),
          "mod_device": root("getprop ro.product.mod_device").strip(), "checks": checks,
          "all_match": all(item["match"] for item in checks)}
dest = ROOT / f"diagnostics/verification-{args.mode}.json"
dest.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
print(f"{sum(item['match'] for item in checks)}/{len(checks)} byte-for-byte checks passed; region={report['region']}; {dest}")
for item in checks:
    if not item["match"]:
        print("MISMATCH", item)
raise SystemExit(0 if report["all_match"] else 1)
