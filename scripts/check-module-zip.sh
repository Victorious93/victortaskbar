#!/usr/bin/env bash
# Validate the layout of a built VictorTaskbar root module zip.
#   scripts/check-module-zip.sh build/module/victortaskbar-root-module-<version>.zip
set -euo pipefail

ZIP="${1:?usage: check-module-zip.sh <module.zip>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }

echo "== unzip -l $ZIP"
unzip -l "$ZIP"
# File entries only (directory entries end in /).
LIST="$(unzip -Z1 "$ZIP" | grep -v '/$')"

for f in \
  module.prop \
  customize.sh \
  META-INF/com/google/android/update-binary \
  META-INF/com/google/android/updater-script \
  system/priv-app/VictorTaskbar/VictorTaskbar.apk \
  system/etc/permissions/privapp-permissions-com.victorious.taskbar.xml
do
  grep -qxF "$f" <<<"$LIST" || fail "missing $f"
done

if grep -vE '^(META-INF/com/google/android/(update-binary|updater-script)|system/priv-app/VictorTaskbar/VictorTaskbar\.apk|system/etc/permissions/privapp-permissions-com\.victorious\.taskbar\.xml|module\.prop|customize\.sh)$' <<<"$LIST"; then
  fail "unexpected entries above"
fi

PROP="$(unzip -p "$ZIP" module.prop)"
for k in id name version versionCode author description; do
  grep -qE "^$k=.+" <<<"$PROP" || fail "module.prop: $k missing or empty"
done
grep -qx 'id=victortaskbar_priv' <<<"$PROP" || fail "module.prop: id must be victortaskbar_priv"
grep -qE '^versionCode=[0-9]+$' <<<"$PROP" || fail "module.prop: versionCode must be an integer"

[ "$(unzip -p "$ZIP" META-INF/com/google/android/updater-script | tr -d '\r\n')" = "#MAGISK" ] \
  || fail "updater-script must be #MAGISK"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -p "$ZIP" system/priv-app/VictorTaskbar/VictorTaskbar.apk > "$TMP/app.apk"
unzip -Z1 "$TMP/app.apk" | grep -qx AndroidManifest.xml || fail "APK has no AndroidManifest.xml"

# The allowlist must be well-formed, for our package, list only permissions the app requests,
# and cover every privileged permission it requests (missing ones can bootloop on enforce).
unzip -p "$ZIP" system/etc/permissions/privapp-permissions-com.victorious.taskbar.xml > "$TMP/perms.xml"
python3 - "$TMP/perms.xml" "$ROOT/app/src/main/AndroidManifest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
A = "{http://schemas.android.com/apk/res/android}name"
blocks = ET.parse(sys.argv[1]).getroot().findall("privapp-permissions")
assert len(blocks) == 1 and blocks[0].get("package") == "com.victorious.taskbar", "bad privapp-permissions block"
granted = {p.get("name") for p in blocks[0].findall("permission")}
requested = {u.get(A) for u in ET.parse(sys.argv[2]).getroot().findall("uses-permission")}
extra = granted - requested
assert not extra, f"allowlisted but not requested by the app: {sorted(extra)}"
privileged = {"android.permission.PACKAGE_USAGE_STATS", "android.permission.REAL_GET_TASKS"}
missing = (requested & privileged) - granted
assert not missing, f"privileged permissions requested but not allowlisted: {sorted(missing)}"
print("privapp allowlist OK:", sorted(granted))
PY
echo "OK: $ZIP"
