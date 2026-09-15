#!/usr/bin/env bash
set -euo pipefail

PKG="site.garsyanimultiusaha.gawone.management.debug"
APK="GAWONE-Management-v1.0.6-M2.23-BUSINESS-LABOUR-debug.apk"
REPORT_DIR="management-emulator-reports"
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
    echo "GAWONE Management crashed during $label" >&2
    grep -n -A30 -B10 -F "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt" >&2 || true
    exit 1
  fi
}

wait_ui_text() {
  local needle="$1"
  local label="$2"
  for i in $(seq 1 20); do
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

echo "=== CLEAN INSTALL ==="
test -f "$APK"
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$APK" | tee "$REPORT_DIR/01-install.txt"
adb shell pm path "$PKG" | tr -d '\r' | tee "$REPORT_DIR/02-package-path.txt"
grep -q '^package:' "$REPORT_DIR/02-package-path.txt"

echo "=== COLD START ==="
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$PKG/.ManagementGateActivity" | tee "$REPORT_DIR/03-cold-start.txt"
check_process "04-cold-start"
wait_ui_text "Command Center GAWONE" "05-cold-start"
wait_ui_text "Masuk Management" "06-login-gate"
check_no_crash "07-cold-start"

echo "=== OFFLINE LOGIN-GATE RESILIENCE ==="
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null || true
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$PKG/.ManagementGateActivity" | tee "$REPORT_DIR/08-offline-start.txt"
check_process "09-offline"
wait_ui_text "Command Center GAWONE" "10-offline-ui"
check_no_crash "11-offline"

echo "=== RECONNECT + RESTART ==="
adb shell settings put global airplane_mode_on 0
adb shell svc wifi enable || true
adb shell svc data enable || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null || true
sleep 4
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$PKG/.ManagementGateActivity" | tee "$REPORT_DIR/12-reconnect-start.txt"
check_process "13-reconnect"
wait_ui_text "Command Center GAWONE" "14-reconnect-ui"
check_no_crash "15-reconnect"

adb shell dumpsys package "$PKG" > "$REPORT_DIR/16-package-dumpsys.txt"
adb shell getprop ro.build.version.release > "$REPORT_DIR/17-android-version.txt"
adb shell getprop ro.build.version.sdk > "$REPORT_DIR/18-api-level.txt"

echo "GAWONE Management M2.23 emulator gate: PASS" | tee "$REPORT_DIR/RESULT.txt"
