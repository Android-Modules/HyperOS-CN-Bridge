SKIPUNZIP=0
ui_print "- HyperOS CN Bridge · ishtar Elite 补全 · dalong"
[ "$(getprop ro.product.device)" = ishtar ] || abort "! 仅支持 ishtar"
[ "$(getprop ro.build.version.incremental)" = OS3.0.308.0.WMACNXM ] || abort "! 仅支持 OS3.0.308.0.WMACNXM"
[ "$(getprop ro.build.version.sdk)" = 36 ] || abort "! 需要 Android 16"
[ "$(getprop ro.build.elite.romvariant)" = EliteRomLite ] || abort "! 仅支持 EliteRomLite"
[ "$(getprop ro.build.elite.versioncode)" = v4.05 ] || abort "! 仅支持 EliteRomLite v4.05"
[ -L /data/adb/metamodule ] || abort "! 需要已启用的 KernelSU 挂载元模块"
[ ! -d /data/adb/modules/songyuan_cn_complete ] || abort "! 检测到 songyuan 模块，请勿混用"
STATE=/data/adb/ishtar_cn
mkdir -p "$STATE"
chmod 700 "$STATE"
[ -f "$ZIPFILE" ] || abort "! 安装源不存在"
cp "$ZIPFILE" "$STATE/source.zip.new" || abort "! 无法缓存离线安装源"
chmod 600 "$STATE/source.zip.new"
mv "$STATE/source.zip.new" "$STATE/source.zip"
set_perm_recursive "$MODPATH" 0 0 0755 0644
for SCRIPT in config.sh post-fs-data.sh service.sh action.sh; do
  set_perm "$MODPATH/$SCRIPT" 0 0 0755
done
REPLACE=""
RESULT=$(sh "$MODPATH/config.sh" install 2>&1)
CODE=$?
ui_print "$RESULT"
[ "$CODE" = 0 ] || abort "! 补全准备失败，未切换系统文件"
sh "$MODPATH/mount-compat.sh" || abort "! 提示音模块的挂载目录兼容处理失败"
ui_print "- 保留现有相册、账号、桌面、NFC、下载器及其数据"
ui_print "- 缺失组件默认开启，重启并解锁后生效"
ui_print "- 多用户环境支持首次补装；后续开关切换需单用户"
ui_print "- WebUI 明确区分补装组件与 ROM 原有应用"
