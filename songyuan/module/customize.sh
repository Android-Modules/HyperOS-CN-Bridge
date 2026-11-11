SKIPUNZIP=0

ui_print "- HyperOS CN Bridge · dalong"
ui_print "- Songyuan 官方国际版 → 同版本国行组件"
[ "$(getprop ro.product.device)" = songyuan ] || abort "! 仅支持 songyuan"
[ "$(getprop ro.build.version.incremental)" = OS3.0.306.0.WGNMIXM ] || abort "! 仅支持 OS3.0.306.0.WGNMIXM"
[ "$(getprop ro.build.version.sdk)" = 36 ] || abort "! 仅支持 Android 16"
[ -e /system_ext/priv-app/RtMiCloudSDK/RtMiCloudSDK.apk ] || abort "! 缺少系统 micloud-sdk"
if [ "${KSU:-false}" = true ] || [ -n "${KSU_VER_CODE:-}" ]; then
  [ -L /data/adb/metamodule ] || abort "! KernelSU 需要先启用挂载元模块"
fi
for OLD in HyperOS3EULocalization Fix_MiPay xiaomi_eu_toolkit_zygisk songyuan_cn_wallet songyuan_cn_appstore songyuan_cn_yellowpage; do
  if [ -d "/data/adb/modules/$OLD" ] && [ ! -e "/data/adb/modules/$OLD/disable" ] && [ ! -e "/data/adb/modules/$OLD/remove" ]; then
    abort "! 请先停用冲突模块 $OLD"
  fi
done

AUTH_APK="$MODPATH/payload/AuthManager/AuthManager.apk"
AUTH_BACKUP=/data/adb/songyuan_cn/managed-updates
mkdir -p "$AUTH_BACKUP"
chmod 700 /data/adb/songyuan_cn "$AUTH_BACKUP"
# PackageManager removes a disappeared system package's data at the next scan.
# Save POCO's layout before hiding its original APK.
LAUNCHER_BACKUP=/data/adb/songyuan_cn/launcher-baseline-data.tar
if [ ! -f "$LAUNCHER_BACKUP" ] && [ -d /data/user_de/0/com.mi.android.globallauncher ]; then
  am force-stop com.mi.android.globallauncher
  tar -cpf "$LAUNCHER_BACKUP" /data/user/0/com.mi.android.globallauncher /data/user_de/0/com.mi.android.globallauncher || abort "! 原桌面备份失败"
  chmod 600 "$LAUNCHER_BACKUP"
fi
if [ ! -f "$AUTH_BACKUP/AuthManager-global.apk" ]; then
  cp -p /system_ext/priv-app/AuthManager/AuthManager.apk "$AUTH_BACKUP/AuthManager-global.apk" || abort "! 权限管理器备份失败"
  chmod 600 "$AUTH_BACKUP/AuthManager-global.apk"
fi
if [ ! -f /data/adb/songyuan_cn/desired.json ] || grep -q '"enabled"[[:space:]]*:[[:space:]]*true' /data/adb/songyuan_cn/desired.json; then
  AUTH_SIZE=$(stat -c '%s' "$AUTH_APK") || abort "! 权限管理器 APK 不存在"
  ui_print "- 安装同签名国行权限管理器更新（保留数据）"
  pm install -r --user 0 -S "$AUTH_SIZE" < "$AUTH_APK" || abort "! 权限管理器更新失败，请在已开机的系统内安装"
fi

REPLACE=""
# Every .replace is already owned by the generated component tree. Letting the
# installer recreate the full legacy list would mask Global apps on an off boot.
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/config.sh" 0 0 0755
ui_print "- 缓存离线安装源，准备可切换组件与数据备份"
[ -f "$ZIPFILE" ] || abort "! 无法读取安装源"
cp "$ZIPFILE" /data/adb/songyuan_cn/source.zip.new || abort "! 安装源缓存失败"
chmod 600 /data/adb/songyuan_cn/source.zip.new
mv /data/adb/songyuan_cn/source.zip.new /data/adb/songyuan_cn/source.zip
CONFIG_OUTPUT=$(sh "$MODPATH/config.sh" install 2>&1)
CONFIG_RESULT=$?
ui_print "$CONFIG_OUTPUT"
[ "$CONFIG_RESULT" = 0 ] || abort "! 配置准备失败，请先解锁手机并检查上方原因"
ui_print "- 安装同签名 AI 搜索适配 APK（保留原有配套配置）"
COMPANION_APK="$MODPATH/payload/cn-bridge-companion.apk"
COMPANION_SIZE=$(stat -c '%s' "$COMPANION_APK") || abort "! AI 搜索适配 APK 不存在"
pm install -r --user 0 -S "$COMPANION_SIZE" < "$COMPANION_APK" || abort "! AI 搜索适配 APK 安装失败"
ui_print "- 同版本原签名 APK、ARM64 库和按包权限已安装"
ui_print "- 当前模式：$(cat "$MODPATH/build-mode.txt")"
ui_print "- 通过 /product 实际目录替换屏蔽 Global 组件"
ui_print "- 首次启动会备份并迁移下载器与桌面数据，修复相册账号及同步权限"
ui_print "- 国行桌面用于兼容国内云备份，原 POCO 数据已归档备份；重启生效"
ui_print "- KernelSU 模块的 WebUI / 操作按钮可管理组件；默认全部启用"
ui_print "- AI 搜索另需在 LSPosed API 102 中启用「国内化按应用适配」与推荐作用域"
