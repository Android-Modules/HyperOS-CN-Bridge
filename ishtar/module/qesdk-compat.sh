#!/system/bin/sh
# QESDK verifies exact bytes, including comments and trailing blank lines.
# Restore only the two known Elite v4.05 text edits; do not alter sandbox rules,
# binaries, persist properties, or the read-only vendor partition.
MODDIR=${0%/*}
STATE=/data/adb/ishtar_cn/qesdk-compat
RC=/vendor/etc/init/qesdk-manager.rc
POLICY=/vendor/etc/seccomp_policy/qesdk.policy
RC_GOOD=72f06fd61edadc11f90099bbca060373d9f463c584640223bf9f1e836c7bccf5
RC_OLD=3fd7dff8942965bac3fbbc71686b7469915b05014711c4b756f963b9b8631d6e
POLICY_GOOD=458589447d055323eeead9e7d440be4c51063ce952e2b942746e7c6d1fa148ea
POLICY_OLD=98b479672e6c8b48f1ad09ddc5d1b0904854e33316e3eb3887a990d000bbe3cd
BUSYBOX=/data/adb/ksu/bin/busybox
fail() { echo "QESDK compatibility: $*"; exit 1; }
digest() { sha256sum "$1" | cut -d ' ' -f1; }
[ "$(getprop ro.product.device)" = ishtar ] &&
[ "$(getprop ro.build.version.incremental)" = OS3.0.308.0.WMACNXM ] &&
[ "$(getprop ro.build.elite.romvariant)" = EliteRomLite ] &&
[ "$(getprop ro.build.elite.versioncode)" = v4.05 ] || fail "unsupported ROM"
[ "$(digest /vendor/bin/modemManager)" = d9806701021e1531a42e45342a9e993df3f97c285c5fe937726fe90474c0c7da ] || fail "unknown modemManager"
[ -x "$BUSYBOX" ] || fail "KernelSU busybox missing"
# Validate both inputs and targets before any mount or backup mutation.
[ "$(digest "$MODDIR/compat/qesdk-manager.rc")" = "$RC_GOOD" ] || fail "invalid RC payload"
[ "$(digest "$MODDIR/compat/qesdk.policy")" = "$POLICY_GOOD" ] || fail "invalid policy payload"
RC_CURRENT=$(digest "$RC")
POLICY_CURRENT=$(digest "$POLICY")
case "$RC_CURRENT" in "$RC_GOOD"|"$RC_OLD") ;; *) fail "unknown RC; left untouched";; esac
case "$POLICY_CURRENT" in "$POLICY_GOOD"|"$POLICY_OLD") ;; *) fail "unknown policy; left untouched";; esac
[ "$1" != verify ] || { echo "QESDK compatibility inputs verified"; exit 0; }
mkdir -p "$STATE" || fail "backup directory"
chmod 700 "$STATE" || fail "backup permissions"
MOUNTED=
trap 'for target in $MOUNTED; do umount "$target"; done' 0
restore_file() {
    target=$1
    expected=$2
    [ "$(digest "$target")" != "$expected" ] || return 0
    name=${target##*/}
    source="$MODDIR/compat/$name"
    if [ ! -f "$STATE/$name.original" ]; then
        cp "$target" "$STATE/$name.original" || fail "backup $name"
        chmod 600 "$STATE/$name.original" || fail "backup mode $name"
    fi
    chmod 644 "$source" && chcon u:object_r:vendor_configs_file:s0 "$source" || fail "payload label $name"
    mount -o bind "$source" "$target" || fail "bind $name"
    MOUNTED="$target $MOUNTED"
    "$BUSYBOX" mount -o remount,bind,ro "$source" "$target" || fail "read-only bind $name"
    [ "$(digest "$target")" = "$expected" ] || fail "mounted hash $name"
}
restore_file "$RC" "$RC_GOOD"
restore_file "$POLICY" "$POLICY_GOOD"
trap - 0
echo "QESDK official RC and policy verified; sandbox rules unchanged"
