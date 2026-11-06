#!/system/bin/sh
# 只撤销 systemless 挂载。账号、短信、推送和卡片数据均不在卸载时清理。
# 智能卡跨 sharedUID 的回滚必须使用迁移时生成的备份单独处理。
MODDIR=${0%/*}
sh "$MODDIR/invalidate-cache.sh" --force
exit 0
