"""Exercise ROM input integrity and archive path boundaries without real APKs."""
from argparse import Namespace
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

import rom_inputs


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.payload = b"original signed ROM bytes"
        self.name = "cn/app/Example/Example.apk"

    def archive(self, members, files=None):
        archive = self.root / "inputs.tar.gz"
        with tarfile.open(archive, "w:gz") as out:
            for name, kind in members:
                info = tarfile.TarInfo(name)
                info.type = kind
                if kind == tarfile.REGTYPE:
                    info.size = len(self.payload)
                    out.addfile(info, io.BytesIO(self.payload))
                else:
                    info.linkname = "../../outside"
                    out.addfile(info)
        record = {"sha256": hashlib.sha256(self.payload).hexdigest(), "size": len(self.payload)}
        lock = {"schema": 1, "sha256": rom_inputs.sha(archive), "files": files or {self.name: record}}
        lock_path = self.root / "lock.json"
        lock_path.write_text(json.dumps(lock))
        return Namespace(lock=lock_path, archive=archive, output=self.root / "rom")

    def test_valid_input_and_existing_output(self):
        args = self.archive([(self.name, tarfile.REGTYPE)])
        with contextlib.redirect_stdout(io.StringIO()):
            rom_inputs.extract(args)
        self.assertEqual((args.output / self.name).read_bytes(), self.payload)
        with self.assertRaisesRegex(RuntimeError, "already exist"):
            rom_inputs.extract(args)

    def test_archive_checksum_mismatch_does_not_write(self):
        args = self.archive([(self.name, tarfile.REGTYPE)])
        args.archive.write_bytes(args.archive.read_bytes() + b"corruption")
        with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
            rom_inputs.extract(args)
        self.assertFalse(args.output.exists())

    def test_rejects_traversal_links_and_unexpected_files(self):
        for name, kind in [("../outside", tarfile.REGTYPE), (self.name, tarfile.SYMTYPE),
                           ("global/extra.apk", tarfile.REGTYPE)]:
            with self.subTest(name=name, kind=kind):
                args = self.archive([(name, kind)])
                with self.assertRaisesRegex(RuntimeError, "Unexpected ROM archive member"):
                    rom_inputs.extract(args)
                self.assertFalse(args.output.exists())
                self.assertFalse((self.root / "outside").exists())

    def test_missing_and_duplicate_files(self):
        args = self.archive([])
        with self.assertRaisesRegex(RuntimeError, "missing locked files"):
            rom_inputs.extract(args)
        args = self.archive([(self.name, tarfile.REGTYPE), (self.name, tarfile.REGTYPE)])
        with self.assertRaisesRegex(RuntimeError, "Unexpected ROM archive member"):
            rom_inputs.extract(args)

    def test_individual_file_checksum_mismatch(self):
        args = self.archive([(self.name, tarfile.REGTYPE)],
                            {self.name: {"sha256": "0" * 64, "size": len(self.payload)}})
        with self.assertRaisesRegex(RuntimeError, "file checksum mismatch"):
            rom_inputs.extract(args)


if __name__ == "__main__":
    unittest.main()
