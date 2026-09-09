#!/usr/bin/env bash
set -euo pipefail

PKG="site.garsyanimultiusaha.gawone"
APK="GAWONE_Customer_v1.0.3_RC4.apk"
REPORT_DIR="emulator-reports"
mkdir -p "$REPORT_DIR"

check_process() {
  local label="$1"
  adb shell pidof "$PKG" | tr -d '\r' | tee "$REPORT_DIR/${label}-pid.txt"
  test -s "$REPORT_DIR/${label}-pid.txt"
}

check_no_crash() {
  local label="$1"
  adb logcat -d -v threadtime > "$REPORT_DIR/${label}-logcat.txt"
  if grep -Fq "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt"; then
    echo "GAWONE Customer crashed during $label" >&2
    grep -n -A30 -B10 -F "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt" >&2 || true
    exit 1
  fi
}

wait_ui_text() {
  local needle="$1"
  local label="$2"
  for i in $(seq 1 15); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    adb pull /sdcard/window.xml "$REPORT_DIR/${label}-ui.xml" >/dev/null 2>&1 || true
    if test -f "$REPORT_DIR/${label}-ui.xml" && grep -Fq "$needle" "$REPORT_DIR/${label}-ui.xml"; then
      return 0
    fi
    sleep 1
  done
  echo "UI text not found: $needle ($label)" >&2
  test -f "$REPORT_DIR/${label}-ui.xml" && cat "$REPORT_DIR/${label}-ui.xml" >&2 || true
  return 1
}

echo "=== INSTALL ==="
test -f "$APK"
adb install -r "$APK" | tee "$REPORT_DIR/01-install.txt"
adb shell pm path "$PKG" | tr -d '\r' | tee "$REPORT_DIR/02-package-path.txt"
grep -q '^package:' "$REPORT_DIR/02-package-path.txt"

echo "=== COLD START + LIVE RUNTIME ==="
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/03-cold-start.txt"
check_process "04-cold-start"
wait_ui_text "Masuk ke GAWONE" "05-cold-start"
check_no_crash "06-cold-start"

echo "=== DEEP LINK WHILE AUTH GATED ==="
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" -a android.intent.action.VIEW -d "gawone://support" | tee "$REPORT_DIR/07-deeplink-support.txt"
sleep 2
check_process "08-deeplink"
wait_ui_text "Masuk ke GAWONE" "09-deeplink"
check_no_crash "10-deeplink"

echo "=== OFFLINE DEGRADED START FROM CACHED RUNTIME ==="
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null || true
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/11-offline-start.txt"
check_process "12-offline"
wait_ui_text "Masuk ke GAWONE" "13-offline"
if grep -Fq "Backend belum dapat diverifikasi" "$REPORT_DIR/13-offline-ui.xml"; then
  echo "Offline degraded runtime cache did not recover app" >&2
  exit 1
fi
check_no_crash "14-offline"

echo "=== RECONNECT + PROCESS RESTART ==="
adb shell settings put global airplane_mode_on 0
adb shell svc wifi enable || true
adb shell svc data enable || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null || true
sleep 4
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/15-reconnect-restart.txt"
check_process "16-reconnect"
wait_ui_text "Masuk ke GAWONE" "17-reconnect"
check_no_crash "18-reconnect"

adb shell dumpsys package "$PKG" > "$REPORT_DIR/19-package-dumpsys.txt"
adb shell getprop ro.build.version.release > "$REPORT_DIR/20-android-version.txt"
adb shell getprop ro.build.version.sdk > "$REPORT_DIR/21-api-level.txt"

echo "GAWONE Customer RC4 emulator production-prep gate: PASS" | tee "$REPORT_DIR/RESULT.txt"
