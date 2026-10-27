#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$ROOT/app/src/main/cpp" "$ROOT/app/src/main/java"
echo "Jarvis ColorOS project root: $ROOT"
if command -v adb >/dev/null; then adb shell getprop ro.product.model || true; fi
if [ -x "$ROOT/gradlew" ]; then "$ROOT/gradlew" :app:assembleDebug; else echo "Gradle wrapper not present; CI uses Gradle 8.10.2."; fi
