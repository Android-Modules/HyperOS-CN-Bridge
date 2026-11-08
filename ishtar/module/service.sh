#!/system/bin/sh
MODDIR=${0%/*}
COUNT=0
while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$COUNT" -lt 180 ]; do
  sleep 2
  COUNT=$((COUNT + 1))
done
COUNT=0
while [ "$(cmd activity get-started-user-state 0 2>/dev/null)" != RUNNING_UNLOCKED ] && [ "$COUNT" -lt 1800 ]; do
  sleep 2
  COUNT=$((COUNT + 1))
done
RESULT=$(sh "$MODDIR/config.sh" late 2>&1)
printf '%s\n' "$RESULT" > /data/adb/ishtar_cn/config-boot.txt
chmod 600 /data/adb/ishtar_cn/config-boot.txt
