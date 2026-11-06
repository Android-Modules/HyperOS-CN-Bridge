#!/usr/bin/env python3
"""Inspect or explicitly migrate this phone's TSM settings to android.uid.nfc.

The default is read-only. --apply requires an already staged complete module.
Run only after reviewing the report and obtaining permission for the UID change.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.miui.tsmclient"


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--adb", default=str(Path.home() / "Library/Android/sdk/platform-tools/adb"))
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    def adb(*cmd):
        result = subprocess.run([args.adb, *cmd], capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stdout.decode(errors="replace") + result.stderr.decode(errors="replace"))
        return result.stdout.decode()

    def root(script):
        return adb("shell", "su -c " + shlex.quote(script))

    if root("getprop ro.build.version.incremental").strip() != "OS3.0.306.0.WGNMIXM":
        raise RuntimeError("Unexpected ROM")
    text = root("abx2xml /data/system/packages.xml -")
    tree = ET.fromstring(text)
    package = tree.find(f"package[@name='{PACKAGE}']")
    group = tree.find("shared-user[@name='android.uid.nfc']")
    nfc = tree.find("package[@name='com.android.nfc']")
    if package is None or group is None or nfc is None:
        raise RuntimeError("TSM or NFC package settings missing")
    if group.get("userId") != "1027" or nfc.get("sharedUserId") != "1027":
        raise RuntimeError("Unexpected NFC identity")
    if package.get("sharedUserId") == "1027":
        print("TSM already uses shared NFC UID 1027")
        return
    old_uid = package.get("userId", "")
    if not old_uid.isdecimal() or not 10000 <= int(old_uid) < 20000:
        raise RuntimeError("Unexpected TSM UID; refusing migration")
    # Only allow a signer already trusted by the NFC shared user.
    tsm_certs = {n.get("index") for n in package.findall("sigs/cert")}
    nfc_certs = {n.get("index") for n in group.findall("sigs/cert")}
    if not tsm_certs or tsm_certs != nfc_certs:
        raise RuntimeError("TSM/NFC signer indices differ; review certificates first")
    users = root("ls /data/user").split()
    if users != ["0"]:
        raise RuntimeError("This migration requires a separate review for multiple users")
    data_paths = [f"/data/user/0/{PACKAGE}", f"/data/user_de/0/{PACKAGE}"]
    contexts = {}
    for path in data_paths:
        values = root(f"stat -c '%u:%g' {shlex.quote(path)}; ls -Zd {shlex.quote(path)}").splitlines()
        if values[0] != f"{old_uid}:{old_uid}":
            raise RuntimeError(f"Unexpected ownership at {path}")
        contexts[path] = values[1].split()[0]
    report = {"package": PACKAGE, "old_uid": int(old_uid), "new_shared_uid": 1027, "data_contexts": contexts,
              "data_deleted": False, "warning": "Android Keystore keys are UID-bound; the CN app may require reauthentication or card re-provisioning."}
    print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)
    if not args.apply:
        return
    if root("test -f /data/adb/modules_update/songyuan_cn_complete/product/app/MITSMClient/MITSMClient.apk && echo ready").strip() != "ready":
        raise RuntimeError("Complete module is not staged; refusing to change the UID")
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    dest = f"/data/adb/songyuan_cn/tsm-migration-{stamp}"
    local = ROOT / "diagnostics/private" / f"tsm-migration-{stamp}"
    local.mkdir(parents=True, mode=0o700)
    (local / "migration.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    stopped = False
    rollback_ready = False
    try:
        root("stop")
        stopped = True
        root("for i in $(seq 1 30); do pidof system_server >/dev/null || break; sleep 1; done; ! pidof system_server >/dev/null")
        # Take a consistent snapshot after stopping framework writers.
        root(f"set -eu; mkdir -p {dest}; chmod 700 {dest}; cp -p /data/system/packages.xml {dest}/packages.xml; "
             f"cp -p /data/system/packages.xml.reservecopy {dest}/packages.xml.reservecopy; "
             f"if [ -f /data/system/packages.xml.fallback ]; then cp -p /data/system/packages.xml.fallback {dest}/packages.xml.fallback; fi; "
             f"cd / && tar -cpf {dest}/tsm-data.tar data/user/0/{PACKAGE} data/user_de/0/{PACKAGE}; chmod 600 {dest}/tsm-data.tar")
        current = root("abx2xml /data/system/packages.xml -")
        snapshot = local / "packages-before.xml"
        snapshot.write_text(current)
        snapshot.chmod(0o600)
        data_backup = local / "tsm-data.tar"
        with data_backup.open("wb") as output:
            subprocess.run([args.adb, "exec-out", "su -c " + shlex.quote(f"cat {dest}/tsm-data.tar")], stdout=output, check=True)
        data_backup.chmod(0o600)
        if hashlib.sha256(data_backup.read_bytes()).hexdigest() != root(f"sha256sum {dest}/tsm-data.tar").split()[0]:
            raise RuntimeError("TSM backup transfer checksum mismatch")
        pattern = re.compile(r'(<package\s+[^>]*\bname="com\.miui\.tsmclient"[^>]*)(>)')
        matches = list(pattern.finditer(current))
        if len(matches) != 1:
            raise RuntimeError("TSM settings entry is not unique")
        tag = matches[0].group(1)
        changed, count = re.subn(r'\buserId="' + old_uid + r'"', 'sharedUserId="1027"', tag)
        if count != 1:
            raise RuntimeError("TSM settings changed since preflight")
        updated = current[:matches[0].start()] + changed + ">" + current[matches[0].end():]
        parsed = ET.fromstring(updated)
        target = parsed.find(f"package[@name='{PACKAGE}']")
        if target.get("sharedUserId") != "1027" or target.get("userId") is not None:
            raise RuntimeError("Invalid updated settings")
        patch = local / "packages-updated.xml"
        patch.write_text(updated)
        patch.chmod(0o600)
        adb("push", str(patch), "/data/local/tmp/songyuan-packages-updated.xml")
        root(f"xml2abx /data/local/tmp/songyuan-packages-updated.xml {dest}/packages-updated.abx")
        roundtrip = ET.fromstring(root(f"abx2xml {dest}/packages-updated.abx -"))
        if ET.tostring(parsed) != ET.tostring(roundtrip):
            raise RuntimeError("ABX conversion changed the document")
        rollback = "#!/system/bin/sh\nset -eu\n"
        for suffix in ["", ".reservecopy", ".fallback"]:
            rollback += f'if [ -f "{dest}/packages.xml{suffix}" ]; then cp -p "{dest}/packages.xml{suffix}" "/data/system/packages.xml{suffix}.songyuan-new"; chown 1000:1000 "/data/system/packages.xml{suffix}.songyuan-new"; chmod 660 "/data/system/packages.xml{suffix}.songyuan-new"; chcon u:object_r:system_data_file:s0 "/data/system/packages.xml{suffix}.songyuan-new"; mv -f "/data/system/packages.xml{suffix}.songyuan-new" "/data/system/packages.xml{suffix}"; fi\n'
        rollback += f'cd /\ntar -xpf "{dest}/tsm-data.tar"\n'
        for path, context in contexts.items():
            rollback += f'chcon -R {shlex.quote(context)} {shlex.quote(path)}\n'
        rollback += "sync\n"
        recover = local / "rollback-before-reboot.sh"
        recover.write_text(rollback)
        adb("push", str(recover), "/data/local/tmp/songyuan-rollback-before-reboot.sh")
        root(f"cp /data/local/tmp/songyuan-rollback-before-reboot.sh {dest}/rollback-before-reboot.sh; chmod 600 {dest}/rollback-before-reboot.sh")
        rollback_ready = True
        commands = ["set -eu"]
        for suffix in ["", ".reservecopy", ".fallback"]:
            target = f"/data/system/packages.xml{suffix}"
            commands.append(f'cp "{dest}/packages-updated.abx" "{target}.songyuan-new"')
            commands.append(f'chown 1000:1000 "{target}.songyuan-new"; chmod 660 "{target}.songyuan-new"; chcon u:object_r:system_data_file:s0 "{target}.songyuan-new"')
            commands.append(f'mv -f "{target}.songyuan-new" "{target}"')
        for path in data_paths:
            commands.append(f'chown -R 1027:1027 "{path}"; chcon -R u:object_r:nfc_data_file:s0 "{path}"')
        commands += [f'echo complete > "{dest}/status"',
                     f'if [ -f /data/adb/modules_update/songyuan_cn_complete/disable ]; then mv /data/adb/modules_update/songyuan_cn_complete/disable "{dest}/staged-disable.before-migration"; fi',
                     f'if [ -f /data/adb/songyuan_cn/allow-tsm-migration ]; then mv /data/adb/songyuan_cn/allow-tsm-migration "{dest}/authorization.used"; fi', 'sync']
        root("\n".join(commands))
        stopped = False  # A successful change must boot directly into the staged CN package.
        print(f"Migration saved at {dest}; rebooting", flush=True)
        adb("reboot")
    except BaseException:
        if stopped:
            if rollback_ready:
                root(f"sh {dest}/rollback-before-reboot.sh")
            root("touch /data/adb/modules_update/songyuan_cn_complete/disable")
            root("start")
        raise


if __name__ == "__main__":
    main()
