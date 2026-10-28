#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.jarvis.breeno.assistant"
OUT="${2:-runtime-report}"
mkdir -p "$OUT"

command -v adb >/dev/null || { echo 'adb not found'; exit 2; }
[[ -f "$APK" ]] || { echo "APK not found: $APK"; exit 2; }

adb wait-for-device
adb install -r -d "$APK"
adb shell am force-stop "$PKG"
adb logcat -c
adb shell monkey -p "$PKG" 1 >/dev/null
sleep 5

adb shell dumpsys activity activities > "$OUT/activities.txt"
adb shell dumpsys package "$PKG" > "$OUT/package.txt"
adb logcat -d -v threadtime > "$OUT/logcat.txt"
adb exec-out screencap -p > "$OUT/screenshot.png"

printf '\n=== LAUNCHER CHECK ===\n'
grep -E 'com\.jarvis\.breeno\.assistant.*MainActivity' "$OUT/activities.txt" || true
printf '\n=== VERSION CHECK ===\n'
grep -E 'versionName|versionCode' "$OUT/package.txt" | head -10 || true
printf '\n=== VOICE/ACCESSIBILITY REGISTRATION ===\n'
grep -E 'JarvisVoiceInteractionService|JarvisAccessibilityService' "$OUT/package.txt" || true
printf '\n=== CRASH/ERROR CHECK ===\n'
grep -E 'FATAL EXCEPTION|AndroidRuntime|UnsatisfiedLinkError|EGL|GLSL|Shader|SIGSEGV|SIGABRT' "$OUT/logcat.txt" || true
printf '\nReport saved to %s\n' "$OUT"
