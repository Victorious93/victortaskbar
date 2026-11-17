# shellcheck shell=sh
# VictorTaskbar optional root module: installs com.victorious.taskbar as a privileged
# system app, systemlessly. Sourced by the Magisk, KernelSU or APatch installer.
# Installer variables: MODPATH, API, ARCH, IS64BIT, BOOTMODE,
#   MAGISK_VER(_CODE), KSU, KSU_VER(_CODE), APATCH, APATCH_VER(_CODE).
# Installer functions: ui_print, abort, set_perm, set_perm_recursive.
# shellcheck disable=SC2154

PKG=com.victorious.taskbar
MIN_API=24
APK="$MODPATH/system/priv-app/VictorTaskbar/VictorTaskbar.apk"
PERMS_XML="$MODPATH/system/etc/permissions/privapp-permissions-$PKG.xml"

ui_print "- VictorTaskbar privileged module (optional)"

# Which root manager runs us (documented env vars of each manager).
if [ "$APATCH" = "true" ]; then
  ui_print "- Manager: APatch ${APATCH_VER:-?} (${APATCH_VER_CODE:-?})"
elif [ "$KSU" = "true" ]; then
  ui_print "- Manager: KernelSU ${KSU_VER:-?} (${KSU_VER_CODE:-?})"
elif [ -n "$MAGISK_VER_CODE" ]; then
  ui_print "- Manager: Magisk ${MAGISK_VER:-?} ($MAGISK_VER_CODE)"
else
  ui_print "- Manager: unknown (expects Magisk-style installer functions)"
fi

# API level: the app's minSdk is 24 (Android 7.0).
[ -n "$API" ] || API="$(getprop ro.build.version.sdk)"
if [ -z "$API" ] || [ "$API" -lt "$MIN_API" ]; then
  abort "! Android API ${API:-unknown} is below $MIN_API (Android 7.0). Not installing."
fi
ui_print "- Android API $API"

# Architecture: the APK only ships native libraries (from AndroidX) for arm64-v8a and
# armeabi-v7a (abiFilters in app/build.gradle.kts), so it cannot run on x86/x86_64.
case "$ARCH" in
  arm64|arm) ui_print "- Architecture: $ARCH" ;;
  *) abort "! Architecture ${ARCH:-unknown} is not supported (APK is arm64-v8a / armeabi-v7a only)." ;;
esac

# The APK is added to the zip at build time by scripts/build-module.sh.
[ -f "$APK" ] || abort "! VictorTaskbar.apk is missing from the zip. Build it with scripts/build-module.sh."
[ -f "$PERMS_XML" ] || abort "! Privileged permissions XML is missing from the zip."
rm -f "$MODPATH/system/priv-app/VictorTaskbar/README.txt"

# A user install (/data/app) signed with the SAME key becomes an update of the system copy.
if [ "$BOOTMODE" = "true" ] && command -v pm >/dev/null 2>&1; then
  if pm path "$PKG" >/dev/null 2>&1; then
    ui_print "- $PKG is already installed; it must use the same debug signing key."
    ui_print "  If the taskbar misbehaves after reboot, uninstall the app and reboot."
  fi
fi

# Ownership, modes and SELinux labels for the system files.
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/priv-app" 0 0 0755 0644 u:object_r:system_file:s0
set_perm_recursive "$MODPATH/system/etc/permissions" 0 0 0755 0644 u:object_r:system_file:s0

ui_print "- Done. Reboot to apply. Remove the module in your manager to undo."
ui_print "  BACK UP FIRST. If the device does not boot, see module/README.md."
