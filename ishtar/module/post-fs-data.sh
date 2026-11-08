#!/system/bin/sh
MODDIR=${0%/*}
STATE=/data/adb/ishtar_cn
RESULT=$(sh "$MODDIR/config.sh" early 2>&1)
if [ "$?" != 0 ]; then
  printf '%s\n' "$RESULT" > "$STATE/config-early.txt"
  chmod 600 "$STATE/config-early.txt"
  log -t ishtar_cn "配置未完成，沿用上一套组件；请查看 WebUI"
fi
if [ -x /data/adb/ksu/bin/resetprop ]; then
  while IFS='=' read -r PROP VALUE; do
    [ -n "$PROP" ] && /data/adb/ksu/bin/resetprop -n "$PROP" "$VALUE"
  done < "$MODDIR/system.prop"
fi
if [ -f /data/miui/cust_variant ] && [ -f "$MODDIR/cust_variant" ]; then
  chcon u:object_r:system_data_file:s0 "$MODDIR/cust_variant"
  mount -o bind "$MODDIR/cust_variant" /data/miui/cust_variant || log -t ishtar_cn "cust_variant bind failed"
fi
