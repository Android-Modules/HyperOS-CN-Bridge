#!/usr/bin/env python3
"""Preserve Global download records while adding the CN 125 schema extensions.

Legacy helper for an already-updated Global database at version 125 only.
The stock Global provider needs version 123; this helper does not handle that
downgrade. Normal switching uses the module's transactional controller instead.
Column definitions come from the original CN DownloadProvider.DatabaseHelper.
"""
import argparse
import hashlib
import json
import os
import shlex
import shutil
import sqlite3
import subprocess
import tempfile
from datetime import datetime
from pathlib import Path

ADB = os.environ.get("ADB") or shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")
DB = "/data/user/0/com.android.providers.downloads/databases/downloads.db"
COLUMNS = {
    "if_range_id": "TEXT",
    "file_create_time": "INTEGER NOT NULL DEFAULT 0",
    "downloading_current_speed": "INTEGER NOT NULL DEFAULT 0",
    "download_surplus_time": "INTEGER NOT NULL DEFAULT 0",
    "xl_accelerate_speed": "INTEGER NOT NULL DEFAULT 0",
    "downloaded_time": "INTEGER NOT NULL DEFAULT 0",
    "xl_vip_status": "INTEGER NOT NULL DEFAULT 0",
    "xl_vip_cdn_url": "TEXT",
    "xl_task_open_mark": "INTEGER NOT NULL DEFAULT 0",
    "download_task_thumbnail": "TEXT",
    "apk_package_name": "TEXT",
    "torrent_file_infos_hash": "TEXT",
    "torrent_file_count": "INTEGER NOT NULL DEFAULT 0",
    "download_type": "INTEGER NOT NULL DEFAULT -1",
    "download_file_hash": "TEXT",
    "download_extra": "TEXT",
    "download_apk_install_way": "INTEGER NOT NULL DEFAULT -1",
    "download_speedup_time": "TEXT",
    "download_speedup_status": "INTEGER NOT NULL DEFAULT -1",
    "download_speedup_mode": "INTEGER NOT NULL DEFAULT -1",
    "download_extra2": "TEXT",
    "kcg_task_open_mark": "INTEGER NOT NULL DEFAULT 0",
    "percent": "INTEGER NOT NULL DEFAULT 0",
    "file_size": "INTEGER NOT NULL DEFAULT -1",
}
BT_SQL = """CREATE TABLE IF NOT EXISTS bt_detail(
_id INTEGER PRIMARY KEY AUTOINCREMENT,download_id INTEGER NOT NULL,
current_size INTEGER,torrent_file_size INTEGER,torrent_sub_path TEXT NOT NULL,
torrent_file_index INTEGER,p2s_speed INTEGER,download_speed INTEGER,status INTEGER,
reason TEXT,torrent_data TEXT,torrent_file_name TEXT,torrent_file_infos_hash TEXT NOT NULL)"""


def migrate(path):
    with sqlite3.connect(path) as db:
        if db.execute("PRAGMA user_version").fetchone()[0] != 125:
            raise RuntimeError("Expected original Global/CN database version 125")
        names = [row[1] for row in db.execute("PRAGMA table_info(downloads)")]
        before = db.execute("SELECT * FROM downloads ORDER BY _id").fetchall()
        missing = [name for name in COLUMNS if name not in names]
        db.execute("BEGIN IMMEDIATE")
        for name in missing:
            db.execute('ALTER TABLE downloads ADD COLUMN "' + name + '" ' + COLUMNS[name])
        db.execute(BT_SQL)
        projection = ",".join('"' + name + '"' for name in names)
        assert before == db.execute("SELECT " + projection + " FROM downloads ORDER BY _id").fetchall()
        assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
        db.commit()
        return {"added_columns": missing, "preserved_records": len(before)}


def root(script, binary=False):
    return subprocess.run([ADB, "exec-out", "su -c " + shlex.quote(script)],
                          check=True, capture_output=True, text=not binary).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--offline", type=Path, help="Migrate a local copy only")
    args = parser.parse_args()
    if args.offline:
        print(json.dumps(migrate(args.offline)))
        return
    if "OS3.0.306.0.WGNMIXM" not in root("getprop ro.build.version.incremental"):
        raise RuntimeError("Unsupported firmware")
    if not args.apply:
        print(root("ls -lZ " + DB))
        print("Use --apply to back up and migrate the download database")
        return
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = "/data/adb/songyuan_cn/downloads-schema-" + stamp
    root("am force-stop com.android.providers.downloads; am force-stop com.android.providers.downloads.ui; "
         "mkdir -p " + backup + "; chmod 700 " + backup + "; cp -a " + DB + "* " + backup + "/")
    try:
        if root("pidof android.process.media || true").strip():
            raise RuntimeError("Download process restarted; refusing an offline database edit")
        original = root("cat " + DB, binary=True)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "downloads.db"
            path.write_bytes(original)
            path.chmod(0o600)
            # The stopped provider uses a rollback journal; reject a live WAL.
            if root("test ! -s " + DB + "-wal && echo clean").strip() != "clean":
                raise RuntimeError("Unexpected WAL; database requires a consistent snapshot")
            result = migrate(path)
            subprocess.run([ADB, "push", str(path), "/data/local/tmp/songyuan-downloads.db"], check=True, capture_output=True)
            current = root("sha256sum " + DB).split()[0]
            if current != hashlib.sha256(original).hexdigest() or root("pidof android.process.media || true").strip():
                raise RuntimeError("Download database changed; original data left intact")
            root("cp -a " + DB + " " + DB + ".songyuan-new; "
                 "cat /data/local/tmp/songyuan-downloads.db > " + DB + ".songyuan-new; "
                 "mv -f " + DB + ".songyuan-new " + DB + "; "
                 "if [ -f " + DB + "-journal ]; then mv " + DB + "-journal " + backup + "/journal-after-stop; fi")
    finally:
        root("cmd package unstop --user 0 com.android.providers.downloads; "
             "cmd package unstop --user 0 com.android.providers.downloads.ui; "
             "rm -f /data/local/tmp/songyuan-downloads.db")
    result["backup"] = backup
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
