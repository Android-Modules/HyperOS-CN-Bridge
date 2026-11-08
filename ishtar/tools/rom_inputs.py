#!/usr/bin/env python3
"""Publish/retrieve only the checksum-locked ishtar ROM inputs used by the build."""
import argparse
import gzip
import json
from pathlib import Path
import sys
import tarfile
import tempfile
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "songyuan/tools"))
import rom_inputs as shared

LOCK = ROOT / "ishtar/rom-inputs.lock.json"


def pack(args):
    lock = json.loads(LOCK.read_text())
    sizes = {}
    for name, digest in lock["files"].items():
        path = args.source / name
        if path.is_symlink() or not path.is_file() or shared.sha(path) != digest:
            raise RuntimeError("Original ROM input mismatch: " + name)
        sizes[name] = path.stat().st_size
    args.archive.parent.mkdir(parents=True, exist_ok=True)
    with args.archive.open("wb") as raw:
        with gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0, compresslevel=6) as compressed:
            with tarfile.open(fileobj=compressed, mode="w|", format=tarfile.PAX_FORMAT) as archive:
                for name in sorted(sizes):
                    info = tarfile.TarInfo("cn/" + name)
                    info.size, info.mode, info.mtime = sizes[name], 0o644, 0
                    with (args.source / name).open("rb") as stream:
                        archive.addfile(info, stream)
    lock["archive"] = dict(repository="Android-Modules/HyperOS-CN-Bridge",
                           release="rom-ishtar-OS3.0.308.0", asset=args.archive.name,
                           sha256=shared.sha(args.archive), sizes=sizes)
    LOCK.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n")
    print(f"Packed {len(sizes)} locked files: {args.archive}")


def extract(args):
    lock = json.loads(LOCK.read_text())
    archive = lock["archive"]
    if set(archive["sizes"]) != set(lock["files"]):
        raise RuntimeError("Archive inventory differs from original ROM lock")
    # Reuse the tested extractor: reject traversal, links, duplicate/unlisted files,
    # wrong sizes, missing entries and both archive/file hash mismatches.
    common = dict(schema=1, sha256=archive["sha256"], files={
        "cn/" + name: dict(sha256=digest, size=archive["sizes"][name])
        for name, digest in lock["files"].items()})
    with tempfile.TemporaryDirectory(prefix="ishtar-input-lock-") as temp:
        translated = Path(temp) / "lock.json"
        translated.write_text(json.dumps(common))
        shared.extract(SimpleNamespace(lock=translated, archive=args.archive, output=args.output))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("pack", "extract"))
    parser.add_argument("--source", type=Path, default=ROOT / "rom/ishtar/cn")
    parser.add_argument("--archive", type=Path, default=ROOT / "dist/ishtar-rom-inputs.tar.gz")
    parser.add_argument("--output", type=Path, default=ROOT / "rom/ishtar")
    args = parser.parse_args()
    (pack if args.command == "pack" else extract)(args)
