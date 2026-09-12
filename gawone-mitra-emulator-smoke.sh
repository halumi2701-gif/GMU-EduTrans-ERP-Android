#!/usr/bin/env bash
set -euo pipefail

PKG="site.garsyanimultiusaha.gawone.mitra"
APK="GAWONE_Mitra_v1.0.2_STAGE4J_PILOT_RC3.apk"
REPORT_DIR="emulator-reports"
mkdir -p "$REPORT_DIR"

wait_adb() {
  adb wait-for-device >/dev/null 2>&1 || true
  sleep 1
}

check_process() {
  local label="$1" i
  for i in 1 2 3; do
    wait_adb
    if adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' > "$REPORT_DIR/${label}-pid.txt" && test -s "$REPORT_DIR/${label}-pid.txt"; then
      cat "$REPORT_DIR/${label}-pid.txt"
      return 0
    fi
    sleep 2
  done
  echo "Process $PKG tidak aktif pada gate $label" >&2
  return 1
}

capture_activities() {
  local i
  for i in 1 2 3; do
    wait_adb
    if adb shell dumpsys activity activities > "$REPORT_DIR/05-activities.txt" 2>"$REPORT_DIR/05-activities-stderr.txt"; then
      grep -Fq "$PKG/.MainActivity" "$REPORT_DIR/05-activities.txt" && return 0
    fi
    sleep 2
  done
  echo "Tidak dapat memverifikasi MainActivity melalui dumpsys setelah retry" >&2
  return 1
}

check_no_crash() {
  local label="$1" i
  for i in 1 2 3; do
    wait_adb
    if adb logcat -d -v threadtime > "$REPORT_DIR/${label}-logcat.txt" 2>"$REPORT_DIR/${label}-logcat-stderr.txt"; then
      if grep -Fq "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt"; then
        echo "GAWONE Mitra crashed during $label" >&2
        grep -n -A30 -B10 -F "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt" >&2 || true
        return 1
      fi
      return 0
    fi
    sleep 2
  done
  echo "Logcat tidak dapat dibaca setelah retry pada gate $label" >&2
  return 1
}

echo "=== INSTALL ==="
test -f "$APK"
wait_adb
adb install -r "$APK" | tee "$REPORT_DIR/01-install.txt"
adb shell pm path "$PKG" | tr -d '\r' | tee "$REPORT_DIR/02-package-path.txt"
grep -q '^package:' "$REPORT_DIR/02-package-path.txt"

echo "=== COLD START ==="
adb shell am force-stop "$PKG"
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/03-cold-start.txt"
sleep 5
check_process "04-cold-start"
capture_activities
check_no_crash "06-cold-start"

echo "=== DEEP LINK ==="
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" -a android.intent.action.VIEW -d "gawone://mitra/offer/00000000-0000-0000-0000-000000000000" | tee "$REPORT_DIR/07-deeplink.txt"
sleep 3
check_process "08-deeplink"
check_no_crash "09-deeplink"

echo "=== OFFLINE START ==="
adb shell settings put global airplane_mode_on 1 || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null || true
adb shell am force-stop "$PKG"
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/10-offline-start.txt"
sleep 5
check_process "11-offline"
check_no_crash "12-offline"

echo "=== RECONNECT + RESTART ==="
adb shell settings put global airplane_mode_on 0 || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null || true
sleep 3
adb shell am force-stop "$PKG"
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/13-reconnect-restart.txt"
sleep 5
check_process "14-reconnect"
check_no_crash "15-reconnect"

wait_adb
adb shell dumpsys package "$PKG" > "$REPORT_DIR/16-package-dumpsys.txt" || true
adb shell getprop ro.build.version.release > "$REPORT_DIR/17-android-version.txt" || true
adb shell getprop ro.build.version.sdk > "$REPORT_DIR/18-api-level.txt" || true
echo "GAWONE Mitra Pilot RC3 emulator smoke gate: PASS" | tee "$REPORT_DIR/RESULT.txt"
