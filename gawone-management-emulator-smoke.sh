#!/usr/bin/env bash
set -euo pipefail

PKG="site.garsyanimultiusaha.gawone.management"
APK="GAWONE_Management_v0.2.1_PILOT_M1_1.apk"
REPORT_DIR="emulator-reports"
mkdir -p "$REPORT_DIR"

adb_retry() {
  local n=0
  until "$@"; do
    n=$((n+1))
    if [ "$n" -ge 5 ]; then return 1; fi
    adb wait-for-device || true
    sleep 2
  done
}

check_alive() {
  local label="$1"
  adb_retry adb shell pidof "$PKG" | tr -d '\r' | tee "$REPORT_DIR/${label}-pid.txt"
  test -s "$REPORT_DIR/${label}-pid.txt"
}

check_no_crash() {
  local label="$1"
  adb logcat -d -v threadtime > "$REPORT_DIR/${label}-logcat.txt" || true
  if grep -Fq "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt"; then
    echo "GAWONE Management crashed during $label" >&2
    grep -n -A35 -B10 -F "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt" >&2 || true
    exit 1
  fi
}

echo "=== INSTALL ==="
test -f "$APK"
adb wait-for-device
adb_retry adb install -r "$APK" | tee "$REPORT_DIR/01-install.txt"
adb_retry adb shell pm path "$PKG" | tr -d '\r' | tee "$REPORT_DIR/02-package-path.txt"
grep -q '^package:' "$REPORT_DIR/02-package-path.txt"

echo "=== COLD START ==="
adb shell am force-stop "$PKG" || true
adb logcat -c || true
adb_retry adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/03-cold-start.txt"
sleep 5
check_alive "04-cold-start"
check_no_crash "05-cold-start"

echo "=== OFFLINE RESTART ==="
adb shell settings put global airplane_mode_on 1 || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null || true
adb shell am force-stop "$PKG" || true
adb logcat -c || true
adb_retry adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/06-offline-start.txt"
sleep 5
check_alive "07-offline"
check_no_crash "08-offline"

adb shell settings put global airplane_mode_on 0 || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null || true
adb shell getprop ro.build.version.release > "$REPORT_DIR/09-android-version.txt"
adb shell getprop ro.build.version.sdk > "$REPORT_DIR/10-api-level.txt"
echo "GAWONE Management Pilot M1.1 emulator smoke gate: PASS" | tee "$REPORT_DIR/RESULT.txt"
