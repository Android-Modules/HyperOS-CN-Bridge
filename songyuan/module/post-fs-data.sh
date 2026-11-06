#!/system/bin/sh
MODDIR=${0%/*}
RESULT=$(sh "$MODDIR/config.sh" early 2>&1)
if [ "$?" != 0 ]; then
  log -t songyuan_cn "组件配置未完成，请在 WebUI 查看状态"
  printf '%s\n' "$RESULT" > /data/adb/songyuan_cn/config-early.txt
  chmod 600 /data/adb/songyuan_cn/config-early.txt
  case "$RESULT" in
    *'"ok":false'*) ;;
    *) echo '{"ok":false,"phase":"early","message":"启动配置程序未完成，请检查 config-early.txt"}' > /data/adb/songyuan_cn/last-result.json ;;
  esac
fi
sh "$MODDIR/invalidate-cache.sh"
# Set the property again after Global init's early region selection.
if [ -x /data/adb/ksu/bin/resetprop ]; then
  while IFS='=' read -r PROP VALUE; do
    [ -n "$PROP" ] && /data/adb/ksu/bin/resetprop -n "$PROP" "$VALUE"
  done < "$MODDIR/system.prop"
fi
# MIUI init re-reads this file on some builds. A bind mount keeps the original
# user setting intact for disable/uninstall rollback.
if [ -f /data/miui/cust_variant ]; then
  chcon u:object_r:system_data_file:s0 "$MODDIR/cust_variant"
  mount -o bind "$MODDIR/cust_variant" /data/miui/cust_variant || log -t songyuan_cn "cust_variant bind failed"
fi
