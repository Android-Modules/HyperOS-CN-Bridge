#!/system/bin/sh
# Normalize one known legacy audio module layout. A symlink inside system/ can
# claim the entire product node in mmrs 4.0.11, hiding every later module there.
# Keep all audio files and the module itself; save the original link for rollback.
BASE=/data/adb/modules/hyperos_gushi_audio
LINK="$BASE/system/product"
STATE=/data/adb/ishtar_cn/mount-compat
[ -L "$LINK" ] || exit 0
[ "$(readlink "$LINK")" = ../product ] || { echo "未知的提示音 product 软链接，拒绝修改"; exit 1; }
[ -d "$BASE/product" ] && [ ! -L "$BASE/product" ] || exit 1
[ -d "$BASE/product/media" ] || exit 1
mkdir -p "$STATE" || exit 1
chmod 700 "$STATE"
if [ ! -f "$STATE/audio-original-link" ]; then
  printf '%s\n' '../product' > "$STATE/audio-original-link" || exit 1
  chmod 600 "$STATE/audio-original-link"
fi
[ ! -e "$STATE/audio-link-in-progress" ] && [ ! -L "$STATE/audio-link-in-progress" ] || exit 1
mv "$LINK" "$STATE/audio-link-in-progress" || exit 1
if ! mv "$BASE/product" "$LINK"; then
  mv "$STATE/audio-link-in-progress" "$LINK"
  exit 1
fi
if ! ln -s system/product "$BASE/product"; then
  mv "$LINK" "$BASE/product"
  mv "$STATE/audio-link-in-progress" "$LINK"
  exit 1
fi
# The saved link is deliberately kept as a link; no audio payload is deleted.
mv "$STATE/audio-link-in-progress" "$STATE/audio-original-link.symlink"
echo "已保留提示音模块并规范 product 目录，重启后重新挂载"
