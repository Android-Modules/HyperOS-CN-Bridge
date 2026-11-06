#!/system/bin/sh
MODDIR=${0%/*}
COUNT=0
while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$COUNT" -lt 180 ]; do
  sleep 2
  COUNT=$((COUNT + 1))
done
# Global init sets GB before module post-fs-data. Keep the overlay as a reboot
# fallback and apply CN after framework startup as well.
if [ -x /data/adb/ksu/bin/resetprop ]; then
  REGION=$(sed -n 's/^ro.miui.region=//p' "$MODDIR/system.prop")
  /data/adb/ksu/bin/resetprop -n ro.miui.region "$REGION"
fi
COUNT=0
while ! /system/bin/cmd package path android >/dev/null 2>&1 && [ "$COUNT" -lt 30 ]; do
  sleep 2
  COUNT=$((COUNT + 1))
done
mkdir -p /data/adb/songyuan_cn
chmod 700 /data/adb/songyuan_cn
# Finish after credential storage is unlocked; do not silently skip data restore.
COUNT=0
while [ "$(cmd activity get-started-user-state 0 2>/dev/null)" != RUNNING_UNLOCKED ] && [ "$COUNT" -lt 1800 ]; do
  sleep 2
  COUNT=$((COUNT + 1))
done
RESULT=$(sh "$MODDIR/config.sh" late 2>&1)
printf '%s\n' "$RESULT" > /data/adb/songyuan_cn/config-boot.txt
chmod 600 /data/adb/songyuan_cn/config-boot.txt
sh "$MODDIR/repair-data.sh"
REPORT=/data/adb/songyuan_cn/boot-status.txt
{
  date
  echo "build=$(getprop ro.build.version.incremental)"
  echo "mod_device=$(getprop ro.product.mod_device)"
  echo "region=$(getprop ro.miui.region)"
  echo "home=$(getprop ro.miui.product.home)"
  echo "theme.store.support=$(getprop ro.miui.support.system.app.uninstall.v2)"
  echo "se.type=$(getprop ro.vendor.se.type)"
  while IFS= read -r PACKAGE; do
    echo "[$PACKAGE]"
    # Keep private report/list file descriptors out of the Binder call.
    PATHS=$(pm path "$PACKAGE" </dev/null 2>&1)
    printf '%s\n' "$PATHS"
  done < "$MODDIR/packages.list"
} > "$REPORT" 2>&1
chmod 600 "$REPORT"
log -t songyuan_cn "组件路径检查已完成：$REPORT"
