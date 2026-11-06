#!/usr/bin/env python3
"""Build a device-specific module exclusively from original, signed CN ROM APKs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from catalog import ALIASES, GLOBAL_PACKAGES, OWNERS, feature_catalog

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "songyuan/module"
SPECS = [
    ("app/MITSMClient", "com.miui.tsmclient", "wallet"),
    ("data-app/MIpay", "com.mipay.wallet", "wallet"),
    ("app/MINextpay", "com.miui.nextpay", "wallet"),
    ("app/UPTsmService", "com.unionpay.tsmservice.mi", "wallet"),
    ("app/PaymentService", "com.xiaomi.payment", "wallet"),
    ("app/VoiceAssistAndroidT", "com.miui.voiceassist", "basic"),
    ("app/AiasstVision", "com.xiaomi.aiasst.vision", "basic"),
    ("app/MIUIAiasstService", "com.xiaomi.aiasst.service", "basic"),
    ("priv-app/MIUIPersonalAssistantPhoneOS3", "com.miui.personalassistant", "basic"),
    ("priv-app/MiuiMms", "com.android.mms", "basic"),
    ("priv-app/MIUIContentExtension", "com.miui.contentextension", "basic"),
    ("priv-app/MIUIYellowPage", "com.miui.yellowpage", "basic"),
    ("app/HybridPlatform", "com.miui.hybrid", "basic"),
    ("app/MIUISuperMarket", "com.xiaomi.market", "basic"),
    ("app/MIUICloudService", "com.miui.cloudservice", "cloud"),
    ("app/MIUIMiCloudSync", "com.miui.micloudsync", "cloud"),
    ("priv-app/MIUICloudBackup", "com.miui.cloudbackup", "cloud"),
    ("app/MIUIXiaomiAccount", "com.xiaomi.account", "cloud"),
    ("app/ContentCatcherOS3_1", "com.miui.contentcatcher", "basic"),
    ("app/VoiceTrigger", "com.miui.voicetrigger", "basic"),
    ("app/XiaomiSimActivateServiceCn", "com.xiaomi.simactivate.service", "cloud"),
    ("app/split-XiaomiServiceFrameworkCN", "com.xiaomi.xmsf", "cloud"),
    ("data-app/MIUICalendar", "com.android.calendar", "basic"),
    ("data-app/MIUIDeskClock", "com.android.deskclock", "basic"),
    ("app/MIUIThemeManager", "com.android.thememanager", "basic"),
    ("data-app/MIUIThemeStore", "com.miui.themestore", "basic"),
    ("data-app/MIUIGallery", "com.miui.gallery", "basic"),
    ("data-app/HyperGalleryPluginCn", "com.hyper.gallery.plugin", "basic"),
    ("system_ext/priv-app/AuthManager", "com.lbe.security.miui", "dependency"),
    ("system/priv-app/DownloadProvider", "com.android.providers.downloads", "dependency"),
    ("priv-app/MiuiHome", "com.miui.home", "dependency"),
]
GLOBAL_DIRS = {
    "basic": ["app/AiAsstVision", "priv-app/MiuiMmsGlobal", "priv-app/MIUIYellowPageGlobal", "app/MIUIMiPicks", "app/Calendar", "app/MIUIThemeManagerGlobal", "priv-app/MIUIGalleryGlobal", "data-app/MIUIDeskClockGlobal", "priv-app/MiLauncherGlobal"],
    "wallet": ["app/MITSMClientGlobal"],
    "cloud": ["app/MIUIXiaomiAccountGlobal", "priv-app/MIUICloudBackupGlobal", "app/XiaomiServiceFrameworkGlobal", "app/XiaomiSimActivateServiceGlobal"],
}


def target_path(relative):
    if relative == "priv-app/MiuiHome":
        # MIUI skips /product/priv-app/MiuiHome on a POCO when the Global
        # launcher directory exists, even if that directory is empty.
        return "system/product/priv-app/MiLauncherGlobal"
    if relative == "system_ext/priv-app/AuthManager":
        # MIUI mounts another system_ext/priv-app overlay after KernelSU. Use a
        # signed PackageManager update for this one package instead of a lost bind.
        return "payload/AuthManager"
    if relative.startswith("system/"):
        return relative
    if relative.startswith("system_ext/"):
        return "system/" + relative
    # New data-app directories are not reliably scanned on an initialized Global
    # installation. Publish these original APKs as boot-scanned system apps.
    if relative == "data-app/MIUIGallery":
        relative = "priv-app/MIUIGallery"
    elif relative.startswith("data-app/"):
        relative = "app/" + relative.removeprefix("data-app/")
    return "system/product/" + relative


def run(args):
    return subprocess.run([str(x) for x in args], check=True, capture_output=True, text=True).stdout


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for data in iter(lambda: f.read(1024 * 1024), b""):
            h.update(data)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, default=ROOT / "rom/cn")
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
               or Path.home() / "Library/Android/sdk")
    ap.add_argument("--build-tools", type=Path, default=sdk / "build-tools/36.0.0")
    ap.add_argument("--without-wallet", action="store_true")
    args = ap.parse_args()
    mode = "basic-cloud" if args.without_wallet else "complete"
    selected = [x for x in SPECS if not (args.without_wallet and x[2] == "wallet")]
    packages = {x[1] for x in selected}
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    manifest = {"source": "OS3.0.306.0.WGNCNXM", "target": "OS3.0.306.0.WGNMIXM", "mode": mode, "apks": []}
    with tempfile.TemporaryDirectory(prefix="songyuan-build-") as tmp:
        stage = Path(tmp) / "module"
        shutil.copytree(TEMPLATE, stage)
        shutil.copytree(ROOT / "META-INF", stage / "META-INF")
        android_jar = args.build_tools.parents[1] / "platforms/android-36/android.jar"
        def java_tool(name):
            path = (Path(os.environ["JAVA_HOME"]) / "bin" / name
                    if os.environ.get("JAVA_HOME") else shutil.which(name))
            if not path or not Path(path).is_file():
                raise RuntimeError(f"Missing {name}; install JDK 17+ and set JAVA_HOME")
            return path
        classes = Path(tmp) / "repair-classes"
        classes.mkdir()
        run([java_tool("javac"), "--release", "8", "-cp", android_jar,
             "-d", classes, ROOT / "songyuan/tools/DeviceRepair.java",
             ROOT / "songyuan/tools/ComponentController.java"])
        dex = Path(tmp) / "repair-dex"
        dex.mkdir()
        run([java_tool("java"), "-cp", args.build_tools / "lib/d8.jar",
             "com.android.tools.r8.D8", "--min-api", "36", "--lib", android_jar,
             "--output", dex, *sorted(classes.glob("*.class"))])
        with zipfile.ZipFile(stage / "repair.jar", "w", zipfile.ZIP_DEFLATED) as z:
            info = zipfile.ZipInfo("classes.dex", date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, (dex / "classes.dex").read_bytes())
        replace = set()
        for relative, package, group in selected:
            source = args.source / relative
            apks = sorted(source.glob("*.apk"))
            if not apks:
                raise RuntimeError(f"Missing ROM APK: {source}")
            target_relative = target_path(relative)
            target = stage / target_relative
            target.mkdir(parents=True)
            if target_relative.startswith("system/"):
                replace.add(target_relative)
            for apk in apks:
                cert = run([args.build_tools / "apksigner", "verify", "--min-sdk-version", "36", "--print-certs", apk])
                badge = run([args.build_tools / "aapt2", "dump", "badging", apk])
                if not badge.startswith(f"package: name='{package}' "):
                    raise RuntimeError(f"Unexpected package: {apk}")
                cert_hash = re.search(r"certificate SHA-256 digest: ([0-9a-f]{64})", cert).group(1)
                expected_cert = {
                    "com.unionpay.tsmservice.mi": "5f6b3d22859624a8de36f95d03ae2ff7581a1daabb230c62aed9470a54414845",
                    "com.android.mms": "d45f076fe23a1a5b7f486e3ff41547a2023dbfe1fe73353b1e48ebdfed72cc6f",
                    "com.miui.yellowpage": "e4431422e25ae61fa3655e9f908a9b40da2acd67fab99b737027c21fd22b7d40",
                    "com.android.calendar": "d45f076fe23a1a5b7f486e3ff41547a2023dbfe1fe73353b1e48ebdfed72cc6f",
                    "com.hyper.gallery.plugin": "d45f076fe23a1a5b7f486e3ff41547a2023dbfe1fe73353b1e48ebdfed72cc6f",
                    "com.android.providers.downloads": "5e1299153a37cd822e51fc39e7dc8571ad844199a4b5a481ddbcc1a74c05e657",
                }.get(package, "c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025")
                if cert_hash != expected_cert:
                    raise RuntimeError(f"Unexpected signing certificate: {apk}")
                shutil.copyfile(apk, target / apk.name)
                xml = run([args.build_tools / "aapt2", "dump", "xmltree", apk, "--file", "AndroidManifest.xml"])
                # System APKs with extractNativeLibs=true require adjacent extracted libraries.
                if "extractNativeLibs(0x010104ea)=false" not in xml:
                    with zipfile.ZipFile(apk) as z:
                        for name in z.namelist():
                            if re.fullmatch(r"lib/arm64-v8a/[^/]+\.so", name):
                                lib = target / "lib/arm64" / Path(name).name
                                lib.parent.mkdir(parents=True, exist_ok=True)
                                lib.write_bytes(z.read(name))
                manifest["apks"].append({"package": package, "source_file": f"product/{relative}/{apk.name}" if not relative.startswith(("system/", "system_ext/")) else f"{relative}/{apk.name}", "file": f"{target_relative}/{apk.name}", "version": badge.splitlines()[0], "sha256": sha(apk), "certificate": cert_hash})
        for group, dirs in GLOBAL_DIRS.items():
            if group == "wallet" and args.without_wallet:
                continue
            replace.update("system/product/" + x for x in dirs)
        manifest["native_libraries"] = []
        for name in ["libxl_thunder_sdk.so", "libxl_stat.so", "libdownload.so", "libvip_channel.so"]:
            source = args.source / "system/lib64" / name
            target = stage / "system/lib64" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            manifest["native_libraries"].append({"file": f"system/lib64/{name}", "sha256": sha(source)})
        for relative in replace:
            folder = stage / relative
            folder.mkdir(parents=True, exist_ok=True)
            (folder / ".replace").touch()
        # Separate permissions by package so an off component leaves no CN grants.
        permission_files = {p: [] for p in packages}
        for source_dir, dest_dir in [("etc/permissions", "system/product/etc/permissions"),
                                      ("system_ext/etc/permissions", "system/system_ext/etc/permissions"),
                                      ("system/etc/permissions", "system/etc/permissions")]:
            nodes = {p: ET.Element("permissions") for p in packages}
            for path in sorted((args.source / source_dir).glob("*.xml")):
                for node in ET.parse(path).getroot():
                    if node.tag == "privapp-permissions" and node.get("package") in packages:
                        nodes[node.get("package")].append(node)
            for package, permissions in nodes.items():
                if not len(permissions):
                    continue
                relative = f"{dest_dir}/songyuan-{package}.xml"
                dest = stage / relative
                dest.parent.mkdir(parents=True, exist_ok=True)
                ET.indent(permissions)
                dest.write_bytes(ET.tostring(permissions, encoding="utf-8", xml_declaration=True))
                permission_files[package].append(relative)
        props = ["ro.product.mod_device=songyuan", "ro.miui.region=CN", "ro.vendor.audio.aiasst.support=true",
                 "ro.miui.support.system.app.uninstall.v2=true", "ro.miui.product.home=com.miui.home"]
        if not args.without_wallet:
            props.append("ro.vendor.se.type=eSE,HCE,UICC")
        (stage / "system.prop").write_text("\n".join(props) + "\n")
        (stage / "build-mode.txt").write_text(mode + "\n")
        (stage / "packages.list").write_text("\n".join(sorted(packages)) + "\n")
        (stage / "replace.list").write_text("\n".join(sorted(replace)) + "\n")
        (stage / "sources.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        catalog = {"schema": 1, "version": "3.1.0", "features": feature_catalog(selected), "units": []}
        # Global clock is a preload under /product/data-app, not a boot-scanned
        # system app. Keep its signed original as an explicit off-state fallback.
        clock_source = ROOT / "rom/global/data-app/MIUIDeskClockGlobal/MIUIDeskClockGlobal.apk"
        badge = run([args.build_tools / "aapt2", "dump", "badging", clock_source])
        cert = run([args.build_tools / "apksigner", "verify", "--min-sdk-version", "36", "--print-certs", clock_source])
        if not badge.startswith("package: name='com.android.deskclock' ") or "c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025" not in cert:
            raise RuntimeError("Unexpected Global clock identity")
        clock_target = stage / "payload/GlobalDeskClock"
        clock_target.mkdir(parents=True)
        shutil.copyfile(clock_source, clock_target / clock_source.name)
        clock_xml = run([args.build_tools / "aapt2", "dump", "xmltree", clock_source, "--file", "AndroidManifest.xml"])
        if "extractNativeLibs(0x010104ea)=false" not in clock_xml:
            with zipfile.ZipFile(clock_source) as z:
                for name in z.namelist():
                    if re.fullmatch(r"lib/arm64-v8a/[^/]+\.so", name):
                        lib = clock_target / "lib/arm64" / Path(name).name
                        lib.parent.mkdir(parents=True, exist_ok=True)
                        lib.write_bytes(z.read(name))
        catalog["clockFallback"] = [dict(path=p.relative_to(stage).as_posix(), sha256=sha(p))
                                    for p in sorted(clock_target.rglob("*")) if p.is_file()]
        manifest["fallback_apks"] = [dict(package="com.android.deskclock", source="Global ROM OS3.0.306.0.WGNMIXM",
            file="payload/GlobalDeskClock/" + clock_source.name, sha256=sha(clock_source), version=badge.splitlines()[0])]
        (stage / "sources.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        for relative, package, _ in selected:
            paths = {p.relative_to(stage).as_posix() for p in (stage / target_path(relative)).rglob("*") if p.is_file()}
            for alias in ALIASES.get(package, []):
                paths.add(f"system/product/{alias}/.replace")
            paths.update(permission_files[package])
            if package == "com.android.providers.downloads":
                paths.update(x["file"] for x in manifest["native_libraries"])
            unit_apks = [x for x in manifest["apks"] if x["package"] == package]
            catalog["units"].append(dict(package=package, owners=OWNERS[package],
                globalPresent=package in GLOBAL_PACKAGES,
                version=re.search(r"versionName='([^']*)'", unit_apks[0]["version"]).group(1),
                certificate=unit_apks[0]["certificate"],
                files=[dict(path=p, sha256=sha(stage / p)) for p in sorted(paths)]))
        (stage / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
        shutil.copyfile(stage / "catalog.json", stage / "webroot/catalog.json")
        for path in stage.rglob("*.sh"):
            run(["sh", "-n", path])
        output = dist / f"songyuan-cn-{mode}-OS3.0.306.0.zip"
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for path in sorted(stage.rglob("*")):
                if path.is_file():
                    name = path.relative_to(stage).as_posix()
                    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = (0o100755 if path.suffix == ".sh" else 0o100644) << 16
                    z.writestr(info, path.read_bytes())
            # macOS commonly collapses AiasstVision / AiAsstVision on disk.
            # Android paths are case sensitive; publish both replacement markers.
            written = set(z.namelist())
            for relative in sorted(replace):
                name = relative + "/.replace"
                if name not in written:
                    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    z.writestr(info, b"")
        with zipfile.ZipFile(output) as z:
            if z.testzip() is not None:
                raise RuntimeError("ZIP CRC failed")
            for item in manifest["apks"]:
                if hashlib.sha256(z.read(item["file"])).hexdigest() != item["sha256"]:
                    raise RuntimeError("APK changed during packaging")
            for unit in catalog["units"]:
                for file in unit["files"]:
                    if hashlib.sha256(z.read(file["path"])).hexdigest() != file["sha256"]:
                        raise RuntimeError(f"Component file changed: {file['path']}")
            for file in catalog["clockFallback"]:
                if hashlib.sha256(z.read(file["path"])).hexdigest() != file["sha256"]:
                    raise RuntimeError("Global clock fallback changed")
        checksum = sha(output)
        (dist / (output.name + ".sha256")).write_text(f"{checksum}  {output.name}\n")
        (dist / f"sources-{mode}.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        print(f"{output}: {output.stat().st_size / 1048576:.1f} MiB, {len(packages)} packages, {len(manifest['apks'])} APKs, SHA256 {checksum}")


if __name__ == "__main__":
    main()
