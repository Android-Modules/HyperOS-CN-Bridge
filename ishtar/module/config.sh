#!/system/bin/sh
MODDIR=$(cd "$(dirname "$0")" && pwd) || exit 1
case "$1" in status|save|install|early|late|retry|ready) ;; *) exit 1 ;; esac
export CLASSPATH="$MODDIR/repair.jar"
exec app_process /system/bin IshtarController "$MODDIR" "$@"
