#!/usr/bin/env bash
set -euo pipefail

PKG="site.garsyanimultiusaha.gawone"
LAUNCHER_PKG="com.google.android.apps.nexuslauncher"
APK="GAWONE_Customer_v1.0.18_P3_PAYMENT_FOUNDATION_RC19.apk"
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
  if grep -Fq "Process: $PKG" "$REPORT_DIR/${label}-logcat.txt" || grep -Fq "ANR in $PKG" "$REPORT_DIR/${label}-logcat.txt"; then
    echo "GAWONE Customer crashed or ANR during $label" >&2
    grep -n -A35 -B12 -E "Process: $PKG|ANR in $PKG" "$REPORT_DIR/${label}-logcat.txt" >&2 || true
    exit 1
  fi
}

clear_pixel_launcher_anr_if_present() {
  local label="$1"
  local host="$REPORT_DIR/${label}-system-ui.xml"
  adb shell uiautomator dump /sdcard/system-window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/system-window.xml "$host" >/dev/null 2>&1 || true
  if test -f "$host" && grep -Fq "Pixel Launcher isn't responding" "$host"; then
    echo "Detected Pixel Launcher system ANR; dismissing infrastructure dialog ($label)" | tee -a "$REPORT_DIR/system-launcher-recovery.txt"
    # Pixel_4 emulator bounds for Android's 'Close app' button; this is ONLY reached
    # after exact detection of the Pixel Launcher ANR title above.
    adb shell input tap 540 1142 || true
    sleep 1
    adb shell am force-stop "$LAUNCHER_PKG" || true
    sleep 1
    adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1 || true
    sleep 2
  fi
}

wait_ui_any() {
  local label="$1"
  shift
  for i in $(seq 1 30); do
    clear_pixel_launcher_anr_if_present "$label-$i"
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    adb pull /sdcard/window.xml "$REPORT_DIR/${label}-ui.xml" >/dev/null 2>&1 || true
    if test -f "$REPORT_DIR/${label}-ui.xml"; then
      if grep -Fq "GAWONE Customer isn't responding" "$REPORT_DIR/${label}-ui.xml" || grep -Fq "GAWONE isn't responding" "$REPORT_DIR/${label}-ui.xml"; then
        echo "GAWONE Customer ANR dialog detected ($label)" >&2
        cat "$REPORT_DIR/${label}-ui.xml" >&2
        return 1
      fi
      for needle in "$@"; do
        if grep -Fq "$needle" "$REPORT_DIR/${label}-ui.xml"; then
          echo "UI state accepted: $needle ($label)" | tee "$REPORT_DIR/${label}-state.txt"
          return 0
        fi
      done
    fi
    sleep 1
  done
  echo "Expected safe UI state not found ($label)" >&2
  test -f "$REPORT_DIR/${label}-ui.xml" && cat "$REPORT_DIR/${label}-ui.xml" >&2 || true
  return 1
}

start_app() {
  local report="$1"
  adb shell am force-stop "$PKG"
  # Pixel Launcher is not under test. Keeping it stopped avoids a known API-35
  # hosted-emulator system ANR from obscuring the GAWONE activity.
  adb shell am force-stop "$LAUNCHER_PKG" || true
  adb logcat -c
  adb shell am start -W -n "$PKG/.MainActivity" | tee "$REPORT_DIR/$report"
}

echo "=== INSTALL ==="
test -f "$APK"
adb install -r "$APK" | tee "$REPORT_DIR/01-install.txt"
adb shell pm path "$PKG" | tr -d '\r' | tee "$REPORT_DIR/02-package-path.txt"
grep -q '^package:' "$REPORT_DIR/02-package-path.txt"

echo "=== COLD START + SAFE RUNTIME GATE ==="
start_app "03-cold-start.txt"
check_process "04-cold-start"
wait_ui_any "05-cold-start" "Masuk ke GAWONE" "Backend belum dapat diverifikasi"
check_process "05b-cold-start-after-ui"
check_no_crash "06-cold-start"

echo "=== DEEP LINK WHILE AUTH/RUNTIME GATED ==="
adb logcat -c
adb shell am force-stop "$LAUNCHER_PKG" || true
adb shell am start -W -n "$PKG/.MainActivity" -a android.intent.action.VIEW -d "gawone://support" | tee "$REPORT_DIR/07-deeplink-support.txt"
sleep 2
check_process "08-deeplink"
wait_ui_any "09-deeplink" "Masuk ke GAWONE" "Backend belum dapat diverifikasi"
check_process "09b-deeplink-after-ui"
check_no_crash "10-deeplink"

echo "=== OFFLINE SAFE-DEGRADED START ==="
adb shell settings put global airplane_mode_on 1
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null || true
start_app "11-offline-start.txt"
check_process "12-offline"
wait_ui_any "13-offline" "Masuk ke GAWONE" "Backend belum dapat diverifikasi"
check_process "13b-offline-after-ui"
check_no_crash "14-offline"

echo "=== RECONNECT + PROCESS RESTART ==="
adb shell settings put global airplane_mode_on 0
adb shell svc wifi enable || true
adb shell svc data enable || true
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null || true
sleep 4
start_app "15-reconnect-restart.txt"
check_process "16-reconnect"
wait_ui_any "17-reconnect" "Masuk ke GAWONE" "Backend belum dapat diverifikasi"
check_process "17b-reconnect-after-ui"
check_no_crash "18-reconnect"

adb shell dumpsys package "$PKG" > "$REPORT_DIR/19-package-dumpsys.txt"
adb shell getprop ro.build.version.release > "$REPORT_DIR/20-android-version.txt"
adb shell getprop ro.build.version.sdk > "$REPORT_DIR/21-api-level.txt"

echo "GAWONE Customer RC19 P3 payment foundation emulator gate: PASS" | tee "$REPORT_DIR/RESULT.txt"
