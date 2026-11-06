#!/system/bin/sh
MODDIR=${0%/*}
STATE=/data/adb/songyuan_cn
CACHE_BACKUP="$STATE/package-cache-last-boot"
mkdir -p "$CACHE_BACKUP"
chmod 700 "$STATE" "$CACHE_BACKUP"
# Magic Mount can preserve directory mtimes. Reparse affected APKs every boot,
# including after a disable/re-enable cycle. Keep one bounded backup per cache.
{
  cat "$MODDIR/replace.list"
  echo MiuiHomeLauncherResOverlay.apk
  echo MiuiPocoLauncherResOverlay.apk
} | while IFS= read -r REL; do
  NAME=${REL##*/}
  for CACHE_DIR in /data/system/package_cache/*; do
    [ -d "$CACHE_DIR" ] || continue
    for CACHE in "$CACHE_DIR/$NAME-"*; do
      [ -f "$CACHE" ] || continue
      mkdir -p "$CACHE_BACKUP/${CACHE_DIR##*/}"
      mv "$CACHE" "$CACHE_BACKUP/${CACHE_DIR##*/}/"
    done
  done
done
