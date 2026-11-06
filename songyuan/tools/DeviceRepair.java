import android.accounts.Account;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** One-time migrations for the exact Global/CN 3.0.306.0 pair; no account identifiers logged. */
public final class DeviceRepair {
    private static final String AUTHORITY = "com.miui.gallery.cloud.provider";
    private static final String[][] COLUMNS = {
        {"if_range_id", "TEXT"}, {"file_create_time", "INTEGER NOT NULL DEFAULT 0"},
        {"downloading_current_speed", "INTEGER NOT NULL DEFAULT 0"},
        {"download_surplus_time", "INTEGER NOT NULL DEFAULT 0"},
        {"xl_accelerate_speed", "INTEGER NOT NULL DEFAULT 0"},
        {"downloaded_time", "INTEGER NOT NULL DEFAULT 0"},
        {"xl_vip_status", "INTEGER NOT NULL DEFAULT 0"}, {"xl_vip_cdn_url", "TEXT"},
        {"xl_task_open_mark", "INTEGER NOT NULL DEFAULT 0"}, {"download_task_thumbnail", "TEXT"},
        {"apk_package_name", "TEXT"}, {"torrent_file_infos_hash", "TEXT"},
        {"torrent_file_count", "INTEGER NOT NULL DEFAULT 0"},
        {"download_type", "INTEGER NOT NULL DEFAULT -1"}, {"download_file_hash", "TEXT"},
        {"download_extra", "TEXT"}, {"download_apk_install_way", "INTEGER NOT NULL DEFAULT -1"},
        {"download_speedup_time", "TEXT"}, {"download_speedup_status", "INTEGER NOT NULL DEFAULT -1"},
        {"download_speedup_mode", "INTEGER NOT NULL DEFAULT -1"}, {"download_extra2", "TEXT"},
        {"kcg_task_open_mark", "INTEGER NOT NULL DEFAULT 0"}, {"percent", "INTEGER NOT NULL DEFAULT 0"},
        {"file_size", "INTEGER NOT NULL DEFAULT -1"}
    };

    static void downloads(boolean cn) {
        downloads(new File("/data/user/0/com.android.providers.downloads/databases/downloads.db"),cn);
    }
    static void downloads(File file, boolean cn) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, 0)) {
            if (db.getVersion() != 123 && db.getVersion() != 125)
                throw new IllegalStateException("Unexpected download DB version: " + db.getVersion());
            Set<String> names = new HashSet<>();
            try (Cursor c = db.rawQuery("PRAGMA table_info(downloads)", null)) {
                while (c.moveToNext()) names.add(c.getString(1));
            }
            int added = 0;
            db.beginTransaction();
            try {
                for (String[] column : COLUMNS) if (cn && !names.contains(column[0])) {
                    db.execSQL("ALTER TABLE downloads ADD COLUMN " + column[0] + " " + column[1]);
                    added++;
                }
                if (cn) db.execSQL("CREATE TABLE IF NOT EXISTS bt_detail(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "download_id INTEGER NOT NULL,current_size INTEGER,torrent_file_size INTEGER,"
                        + "torrent_sub_path TEXT NOT NULL,torrent_file_index INTEGER,p2s_speed INTEGER,"
                        + "download_speed INTEGER,status INTEGER,reason TEXT,torrent_data TEXT,"
                        + "torrent_file_name TEXT,torrent_file_infos_hash TEXT NOT NULL)");
                try (Cursor c = db.rawQuery("PRAGMA integrity_check", null)) {
                    if (!c.moveToFirst() || !"ok".equals(c.getString(0)))
                        throw new IllegalStateException("Download DB integrity check failed");
                }
                // Global expects 123, CN expects 125. Neither provider's automatic
                // downgrade/upgrade path preserves this already merged schema.
                // Keep every row and extra column; change the header before boot scan.
                db.setVersion(cn ? 125 : 123);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            System.out.println("download_schema_ok version=" + db.getVersion() + " added=" + added);
        }
    }

    private static void gallery(boolean repair) throws Exception {
        Object binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "account");
        Object manager = Class.forName("android.accounts.IAccountManager$Stub")
                .getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
        Account[] accounts = (Account[]) manager.getClass()
                .getMethod("getAccountsAsUser", String.class, int.class, String.class)
                .invoke(manager, "com.xiaomi", 0, "android");
        if (accounts.length == 0) throw new IllegalStateException("No Xiaomi account; retry after sign-in");
        for (Account account : accounts) {
            if (repair) {
                ContentResolver.setIsSyncable(account, AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
            }
            int syncable = ContentResolver.getIsSyncable(account, AUTHORITY);
            boolean enabled = ContentResolver.getSyncAutomatically(account, AUTHORITY);
            System.out.println("gallery_syncable=" + syncable + " enabled=" + enabled);
            if (repair && (syncable != 1 || !enabled))
                throw new IllegalStateException("Gallery sync migration did not apply");
            if (repair) {
                Bundle extras = new Bundle();
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                ContentResolver.requestSync(account, AUTHORITY, extras);
            }
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("Expected downloads, gallery or status");
            if (args[0].equals("downloads")) downloads(true);
            else if (args[0].equals("gallery")) gallery(true);
            else if (args[0].equals("status")) gallery(false);
            else throw new IllegalArgumentException("Unknown mode");
            System.exit(0);
        } catch (Throwable error) {
            System.err.println(error.getClass().getSimpleName() + ": " + error.getMessage());
            System.exit(1);
        }
    }
}
