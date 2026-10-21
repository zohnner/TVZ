#!/usr/bin/env bash
# Build the release APK, install it on the TV, and compile it ahead of time.
#
# Usage: scripts/deploy.sh [adb-serial]      (default: $TV_SERIAL, else the only connected device)
#
# The AOT compile matters: a fresh install runs interpreted and is 3-5x slower on this TV until
# Android gets around to compiling it in the background.
set -euo pipefail

cd "$(dirname "$0")/.."

# Git Bash on Windows: use Android Studio's JDK and the SDK's adb if they aren't already set up,
# and don't let MSYS rewrite device paths.
export MSYS_NO_PATHCONV=1
if [[ -z "${JAVA_HOME:-}" && -d "/c/Program Files/Android/Android Studio/jbr" ]]; then
  export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
fi
ADB="${ADB:-$(command -v adb || echo "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe")}"
SERIAL="${1:-${TV_SERIAL:-}}"
if [[ -z "$SERIAL" ]]; then
  # The TV's wireless-debugging serial changes between sessions; take the single connected device.
  mapfile -t DEVICES < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ ${#DEVICES[@]} -ne 1 ]]; then
    echo "Expected exactly one connected device, found ${#DEVICES[@]}. Pass a serial." >&2
    exit 1
  fi
  SERIAL="${DEVICES[0]}"
fi
GRADLEW=./gradlew
[[ "${OS:-}" == "Windows_NT" ]] && GRADLEW=./gradlew.bat

"$GRADLEW" assembleRelease --console=plain -q
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/release/app-release.apk
"$ADB" -s "$SERIAL" shell cmd package compile -m speed -f cc.zohn.tvlauncher
"$ADB" -s "$SERIAL" shell am start -n cc.zohn.tvlauncher/.MainActivity >/dev/null
echo "Deployed to $SERIAL"
