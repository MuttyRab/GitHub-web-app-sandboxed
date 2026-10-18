#!/usr/bin/env bash
# Test the exact signed APK emitted by build-apk, not another local rebuild.
set -euo pipefail
ARTIFACT_DIR="$1"
EVIDENCE="$2"
APK="$(find "$ARTIFACT_DIR" -name app-release.apk -type f -print -quit)"
test -n "$APK"
mkdir -p "$EVIDENCE"
sha256sum "$APK" | tee "$EVIDENCE/installed-release-sha256.txt"
if adb shell pm list packages | grep -q '^package:app.githubwebview.locked$'; then
  adb uninstall app.githubwebview.locked
fi
adb install --no-streaming "$APK" | tee "$EVIDENCE/release-install.txt"
grep -q 'Success' "$EVIDENCE/release-install.txt"
adb logcat -c
adb shell am start -W -n app.githubwebview.locked/app.githubwebview.locked.MainActivity | tee "$EVIDENCE/release-launch.txt"
grep -q 'Status: ok' "$EVIDENCE/release-launch.txt"
sleep 18
adb shell pidof app.githubwebview.locked > "$EVIDENCE/release-pid.txt"
test -s "$EVIDENCE/release-pid.txt"
adb logcat -d -s GitHubWebViewLocked:V > "$EVIDENCE/release-logcat.txt"
grep -q 'egress guard ready' "$EVIDENCE/release-logcat.txt"
grep -q 'egress tunnel github.com' "$EVIDENCE/release-logcat.txt"
adb exec-out screencap -p > "$EVIDENCE/release-screenshot.png"
adb shell dumpsys package app.githubwebview.locked > "$EVIDENCE/release-package.txt"
echo "::notice title=Signed release install verification::PASS installed and launched the exact build-apk artifact; egress guard ready and github.com tunnel observed; $(sha256sum "$APK")"
