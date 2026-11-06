#!/usr/bin/env python3
"""Pack the exact stock ROM inputs, or verify and extract a locked resource asset."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile

from build import ROOT, SPECS

LOCK = ROOT / "songyuan/rom-inputs.lock.json"
ASSET = "songyuan-rom-inputs.tar.gz"
LIBS = ("libxl_thunder_sdk.so", "libxl_stat.so", "libdownload.so", "libvip_channel.so")


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def pack(args):
    source = args.source.resolve()
    paths = set()
    for relative, _, _ in SPECS:
        apks = list((source / "cn" / relative).glob("*.apk"))
        if not apks:
            raise RuntimeError(f"Missing ROM APKs: {relative}")
        paths.update(apks)
    for relative in ("etc/permissions", "system/etc/permissions", "system_ext/etc/permissions"):
        paths.update((source / "cn" / relative).glob("*.xml"))
    paths.update(source / "cn/system/lib64" / name for name in LIBS)
    paths.add(source / "global/data-app/MIUIDeskClockGlobal/MIUIDeskClockGlobal.apk")
    files = {}
    for path in sorted(paths):
        if path.is_symlink() or not path.is_file():
            raise RuntimeError(f"Not a regular ROM input: {path}")
        files[path.relative_to(source).as_posix()] = {"sha256": sha(path), "size": path.stat().st_size}
    args.archive.parent.mkdir(parents=True, exist_ok=True)
    with args.archive.open("wb") as raw:
        with gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0, compresslevel=6) as compressed:
            with tarfile.open(fileobj=compressed, mode="w|", format=tarfile.PAX_FORMAT) as archive:
                for name, record in sorted(files.items()):
                    info = tarfile.TarInfo(name)
                    info.size, info.mode, info.mtime = record["size"], 0o644, 0
                    with (source / name).open("rb") as stream:
                        archive.addfile(info, stream)
    lock = {"schema": 1, "repository": "Android-Modules/HyperOS-CN-Bridge",
            "release": "rom-songyuan-OS3.0.306.0", "asset": ASSET,
            "source": "OS3.0.306.0.WGNCNXM", "target": "OS3.0.306.0.WGNMIXM",
            "sha256": sha(args.archive), "files": files}
    args.lock.write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n")
    print(f"Packed {len(files)} stock ROM files, {args.archive.stat().st_size / 1048576:.1f} MiB; SHA256 {lock['sha256']}")


def extract(args):
    lock = json.loads(args.lock.read_text())
    if lock["schema"] != 1 or sha(args.archive) != lock["sha256"]:
        raise RuntimeError("ROM input archive checksum mismatch")
    if args.output.exists():
        raise RuntimeError("Output must not already exist; use a fresh directory")
    expected, seen = lock["files"], set()
    # Never let a tar member choose paths, permissions, links, or ownership.
    with tarfile.open(args.archive, "r:gz") as archive:
        for member in archive:
            name = member.name
            path = PurePosixPath(name)
            if (not member.isfile() or name not in expected or name in seen
                    or path.is_absolute() or ".." in path.parts
                    or path.parts[0] not in {"cn", "global"}
                    or member.size != expected[name]["size"]):
                raise RuntimeError(f"Unexpected ROM archive member: {name}")
            destination = args.output / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as src, destination.open("wb") as dst:
                shutil.copyfileobj(src, dst)
            if sha(destination) != expected[name]["sha256"]:
                raise RuntimeError(f"ROM input file checksum mismatch: {name}")
            seen.add(name)
    if seen != set(expected):
        raise RuntimeError("ROM archive is missing locked files")
    print(f"Verified archive and {len(seen)} stock ROM input files")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("pack", "extract"))
    parser.add_argument("--source", type=Path, default=ROOT / "rom")
    parser.add_argument("--archive", type=Path, default=ROOT / "dist" / ASSET)
    parser.add_argument("--lock", type=Path, default=LOCK)
    parser.add_argument("--output", type=Path, default=ROOT / "rom")
    args = parser.parse_args()
    (pack if args.command == "pack" else extract)(args)


if __name__ == "__main__":
    main()
