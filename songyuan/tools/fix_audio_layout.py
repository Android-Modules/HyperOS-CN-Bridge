#!/usr/bin/env python3
"""Repackage the backed-up sound module with one canonical product payload."""
import hashlib
from pathlib import Path
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
source = ROOT / "diagnostics/baseline/audio-module.tar"
output = ROOT / "dist/hyperos-gushi-audio-layout-fixed.zip"
with tarfile.open(source) as archive:
    files = {m.name.removeprefix("./"): archive.extractfile(m).read()
             for m in archive.getmembers() if m.isfile()}
payload = {k.removeprefix("product/"): v for k, v in files.items() if k.startswith("product/media/")}
if len(payload) != 262:
    raise RuntimeError(f"Unexpected sound payload: {len(payload)} files")
for name, data in payload.items():
    if files.get("system/" + name) != data:
        raise RuntimeError(f"Conflicting duplicate: {name}")
output.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("module.prop", files["module.prop"])
    z.writestr("customize.sh", 'SKIPUNZIP=0\nset_perm_recursive "$MODPATH" 0 0 0755 0644\nui_print "- 已规范化 product 声音目录，保留全部 262 个原始音频文件"\n')
    for name, data in sorted(payload.items()):
        z.writestr("system/product/" + name, data)
with zipfile.ZipFile(output) as z:
    if z.testzip() is not None:
        raise RuntimeError("ZIP CRC error")
checksum = hashlib.sha256(output.read_bytes()).hexdigest()
output.with_suffix(".zip.sha256").write_text(f"{checksum}  {output.name}\n")
print(f"{output}: {len(payload)} unchanged sound files, SHA256 {checksum}")
