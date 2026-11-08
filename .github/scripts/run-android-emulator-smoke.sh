#!/usr/bin/env bash
set -uo pipefail
mkdir -p emulator-diagnostics

capture_diagnostics() {
  local suffix="${1:-failure}"
  adb devices -l > "emulator-diagnostics/devices-${suffix}.txt" 2>&1 || true
  timeout 12s adb shell getprop > "emulator-diagnostics/getprop-${suffix}.txt" 2>&1 || true
  timeout 12s adb shell service check package > "emulator-diagnostics/package-service-${suffix}.txt" 2>&1 || true
  timeout 12s adb shell service check activity > "emulator-diagnostics/activity-service-${suffix}.txt" 2>&1 || true
  timeout 12s adb shell dumpsys package > "emulator-diagnostics/package-dump-${suffix}.txt" 2>&1 || true
  timeout 12s adb shell dumpsys activity > "emulator-diagnostics/activity-dump-${suffix}.txt" 2>&1 || true
  timeout 20s adb logcat -d -v threadtime > "emulator-diagnostics/logcat-${suffix}.txt" 2>&1 || true
}

echo "Waiting for Android boot and live framework services without restarting ADB."
if ! timeout 60s adb wait-for-device; then
  echo "ERROR: ADB did not connect to the emulator within 60 seconds."
  capture_diagnostics "adb-connect"
  exit 1
fi

ready=0
for attempt in $(seq 1 180); do
  device_state="$(adb get-state 2>/dev/null || true)"
  boot_completed="$(timeout 5s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_service="$(timeout 5s adb shell service check package 2>/dev/null || true)"
  activity_service="$(timeout 5s adb shell service check activity 2>/dev/null || true)"
  package_list="$(timeout 8s adb shell cmd package list packages 2>/dev/null || true)"
  package_count="$(printf '%s\n' "$package_list" | grep -c '^package:' || true)"

  if [[ "$device_state" == "device" && "$boot_completed" == "1" ]] \
    && grep -q "found" <<< "$package_service" \
    && grep -q "found" <<< "$activity_service" \
    && [[ "$package_count" -ge 5 ]]; then
    ready=1
    echo "Android boot and framework services are ready (attempt $attempt; packages=$package_count)."
    break
  fi
  sleep 2
done

if [[ "$ready" -ne 1 ]]; then
  echo "ERROR: Android PackageManager/ActivityManager did not remain available within 360 seconds."
  capture_diagnostics "framework-readiness"
  exit 1
fi

adb logcat -c >/dev/null 2>&1 || true
set +e
gradle --no-daemon --max-workers=2 :app:connectedDebugAndroidTest
test_status=$?
set -e

if [[ -d app/build/reports/androidTests/connected ]]; then
  cp -R app/build/reports/androidTests/connected emulator-diagnostics/test-reports
fi
if [[ -d app/build/outputs/androidTest-results/connected ]]; then
  cp -R app/build/outputs/androidTest-results/connected emulator-diagnostics/test-results
fi
if [[ -d app/build/outputs/androidTest-results ]]; then
  cp -R app/build/outputs/androidTest-results emulator-diagnostics/all-test-results
fi

# UIAutomator failure screenshots and accessibility hierarchy are saved by tests
# under Download; pull them into the artifact on both pass and fail.
mkdir -p emulator-diagnostics/ui-captures
timeout 20s adb pull /sdcard/Download/jarvis-test emulator-diagnostics/ui-captures >/dev/null 2>&1 || true

if [[ "$test_status" -eq 0 ]]; then
  python3 - <<'PY' || test_status=$?
import glob
import sys
import xml.etree.ElementTree as ET

paths = glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True)
if not paths:
    print("ERROR: no Android instrumentation XML reports were produced")
    sys.exit(1)
total = 0
for path in paths:
    root = ET.parse(path).getroot()
    total += int(root.attrib.get("tests", "0"))
print(f"Android instrumentation executed tests: {total}")
if total < 1:
    print("ERROR: instrumentation reported zero executed tests")
    sys.exit(1)
PY
fi

if [[ "$test_status" -ne 0 ]]; then
  capture_diagnostics "instrumentation-failure"
fi
exit "$test_status"
