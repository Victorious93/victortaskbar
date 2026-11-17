#!/usr/bin/env bash
# Package the OPTIONAL VictorTaskbar root module (Magisk / KernelSU / APatch).
#
#   scripts/build-module.sh [path/to/app-debug.apk]
#
# Without an argument it runs ./gradlew assembleDebug and uses
# app/build/outputs/apk/debug/app-debug.apk. Output:
#   build/module/victortaskbar-root-module-<version>.zip
# version and versionCode come from app/build.gradle.kts, so the module matches the APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APK="${1:-}"
if [ -z "$APK" ]; then
  ./gradlew assembleDebug --console=plain
  APK="app/build/outputs/apk/debug/app-debug.apk"
fi
[ -f "$APK" ] || { echo "error: APK not found: $APK" >&2; exit 1; }
APK="$(cd "$(dirname "$APK")" && pwd)/$(basename "$APK")"

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n1)"
VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -n1)"
if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "error: could not read versionName/versionCode from app/build.gradle.kts" >&2
  exit 1
fi

OUT_DIR="$ROOT/build/module"
STAGE="$OUT_DIR/stage"
ZIP="$OUT_DIR/victortaskbar-root-module-$VERSION_NAME.zip"
rm -rf "$STAGE" "$ZIP"
mkdir -p "$STAGE"

cp -R module/. "$STAGE/"
rm -f "$STAGE/README.md" "$STAGE/system/priv-app/VictorTaskbar/README.txt"
cp "$APK" "$STAGE/system/priv-app/VictorTaskbar/VictorTaskbar.apk"
sed -i.bak -e "s/^version=.*/version=v$VERSION_NAME/" -e "s/^versionCode=.*/versionCode=$VERSION_CODE/" "$STAGE/module.prop"
rm -f "$STAGE/module.prop.bak"
chmod 0755 "$STAGE/META-INF/com/google/android/update-binary"

(
  cd "$STAGE"
  if command -v zip >/dev/null 2>&1; then
    zip -qr9 -X "$ZIP" META-INF system module.prop customize.sh
  else
    python3 -m zipfile -c "$ZIP" META-INF system module.prop customize.sh
  fi
)
echo "Built $ZIP"
