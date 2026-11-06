#!/system/bin/sh
MODDIR=${0%/*}
STATE=/data/adb/songyuan_cn
mkdir -p "$STATE"
chmod 700 "$STATE"
REPORT="$STATE/data-repair.txt"
: > "$REPORT"
chmod 600 "$REPORT"
# Binder must not inherit a private report FD from the su domain.
run() {
  OUTPUT=$("$@" </dev/null 2>&1)
  RESULT=$?
  printf '%s\n' "$OUTPUT" >> "$REPORT"
  return "$RESULT"
}
date >> "$REPORT"

# Find Device's installed official CN update already supplies the domestic UI.
# Global's denied UID AppOps survive replacing the account/cloud applications.
# Grant once per enable, then respect later permission choices by the user.
if grep -q '^ro.miui.product.home=com.miui.home$' "$MODDIR/system.prop" && [ ! -f "$STATE/finddevice-location-v1" ]; then
  if run pm path com.xiaomi.finddevice &&
     run pm grant com.xiaomi.finddevice android.permission.ACCESS_COARSE_LOCATION &&
     run pm grant com.xiaomi.finddevice android.permission.ACCESS_FINE_LOCATION &&
     run pm grant com.xiaomi.finddevice android.permission.ACCESS_BACKGROUND_LOCATION &&
     run cmd appops set --uid com.xiaomi.finddevice COARSE_LOCATION allow &&
     run cmd appops set --uid com.xiaomi.finddevice FINE_LOCATION allow; then
    touch "$STATE/finddevice-location-v1"
  fi
fi

if [ -f "$MODDIR/system/priv-app/DownloadProvider/DownloadProvider.apk" ] && [ ! -f "$STATE/downloads-schema-v1" ]; then
  DB=/data/user/0/com.android.providers.downloads/databases/downloads.db
  if [ -f "$DB" ]; then
    run am force-stop com.android.providers.downloads
    run am force-stop com.android.providers.downloads.ui
    BACKUP="$STATE/downloads-auto-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$BACKUP"
    chmod 700 "$BACKUP"
    if cp -a "$DB"* "$BACKUP/"; then
      run env CLASSPATH="$MODDIR/repair.jar" app_process /system/bin DeviceRepair downloads && touch "$STATE/downloads-schema-v1"
      OWNER=$(stat -c '%u:%g' "$DB")
      chown "$OWNER" "$DB"* 2>/dev/null
      restorecon -F "$DB"* 2>/dev/null
    fi
    run cmd package unstop --user 0 com.android.providers.downloads
    run cmd package unstop --user 0 com.android.providers.downloads.ui
  fi
fi

# CN cloud backup categorizes backups by launcher namespace. Seed the matching
# CN launcher from the archive saved before PackageManager removes POCO's data.
if [ -f "$MODDIR/system/product/priv-app/MiLauncherGlobal/MiuiHome.apk" ] && [ ! -f "$STATE/launcher-migration-v2" ] && run pm path com.miui.home; then
  OLD=com.mi.android.globallauncher
  NEW=com.miui.home
  ARCHIVE="$STATE/launcher-baseline-data.tar"
  if [ -f "$ARCHIVE" ]; then
    SOURCE="$STATE/launcher-source"
    mkdir -p "$SOURCE"
    chmod 700 "$SOURCE"
    tar -xpf "$ARCHIVE" -C "$SOURCE" || exit 1
    run am force-stop "$NEW"
    for BASE in /data/user/0 /data/user_de/0; do
      [ -d "$SOURCE$BASE/$OLD" ] || continue
      [ -d "$BASE/$NEW" ] || continue
      OWNER=$(stat -c '%u:%g' "$BASE/$NEW") || exit 1
      cp -a "$SOURCE$BASE/$OLD/." "$BASE/$NEW/" || exit 1
      PREF="$BASE/$NEW/shared_prefs/${OLD}_preferences.xml"
      [ ! -f "$PREF" ] || cp -p "$PREF" "$BASE/$NEW/shared_prefs/${NEW}_preferences.xml"
      chown -R "$OWNER" "$BASE/$NEW"
      restorecon -RF "$BASE/$NEW"
    done
  fi
  run cmd package set-home-activity --user 0 com.miui.home/com.miui.home.launcher.Launcher && touch "$STATE/launcher-migration-v2"
fi

if [ -f "$MODDIR/system/product/priv-app/MIUIGallery/MIUIGallery.apk" ] && [ ! -f "$STATE/gallery-sync-v1" ]; then
  run pm grant com.miui.gallery android.permission.GET_ACCOUNTS || exit 1
  run cmd appops set com.miui.gallery GET_ACCOUNTS allow || exit 1
  # ContentService's account-access check rejects UID 0. Use the framework UID.
  cp "$MODDIR/repair.jar" /data/local/tmp/songyuan-repair.jar
  chmod 444 /data/local/tmp/songyuan-repair.jar
  run su 1000 -c 'CLASSPATH=/data/local/tmp/songyuan-repair.jar app_process /system/bin DeviceRepair gallery' && touch "$STATE/gallery-sync-v1"
fi
# Same-package Global/CN transitions can allocate a new app UID. Android repairs
# the external data root, but existing cache descendants may retain the old UID.
# Check on every unlocked boot (also in Global/off mode); only stale owners change.
GALLERY_STORAGE_RESULT=0
if run pm path --user 0 com.miui.gallery; then
  run env CLASSPATH="$MODDIR/repair.jar" app_process /system/bin DeviceRepair gallery-storage || GALLERY_STORAGE_RESULT=1
fi
chmod 600 "$REPORT" "$STATE/"*-v1 2>/dev/null
exit "$GALLERY_STORAGE_RESULT"
