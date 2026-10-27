#!/usr/bin/env bash
set -euo pipefail

APK="${1:?APK path required}"
AAPT2="${ANDROID_HOME}/build-tools/35.0.0/aapt2"
UNZIP="$(command -v unzip)"

[[ -x "$AAPT2" ]] || { echo "aapt2 not found: $AAPT2"; exit 1; }
[[ -f "$APK" ]] || { echo "APK not found: $APK"; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Current AAPT2 requires --file for xmltree; Android's AAPT2 documentation and
# upstream DumpXmlTreeCommand both define --file as a required option.
"$AAPT2" dump xmltree "$APK" --file AndroidManifest.xml > "$TMP/manifest.txt"
cat "$TMP/manifest.txt"

echo '--- REQUIRED MANIFEST CONTRACTS ---'
grep -q 'android.permission.BIND_VOICE_INTERACTION' "$TMP/manifest.txt"
grep -q 'android.permission.BIND_ACCESSIBILITY_SERVICE' "$TMP/manifest.txt"
grep -q 'android.service.voice.VoiceInteractionService' "$TMP/manifest.txt"
grep -q 'android.accessibilityservice.AccessibilityService' "$TMP/manifest.txt"

echo '--- NATIVE ABI CHECK ---'
# Sherpa-ONNX v2.0.1 packages libonnxruntime.so with its Android runtime.
# libonnxruntime4j_jni.so is not required by the current Sherpa Android stack.
for abi in arm64-v8a armeabi-v7a; do
  for lib in libjarvis_native.so libsherpa-onnx-jni.so libonnxruntime.so; do
    "$UNZIP" -l "$APK" "lib/$abi/$lib" | grep -q "$lib" || { echo "Missing $abi/$lib"; exit 1; }
  done
done

echo '--- SECRET CHECK ---'
if "$UNZIP" -l "$APK" | grep -Ei '(^|/)(\.env|.*(keystore|secret|credentials|service-account).*)$'; then
  echo 'Potential secret-like file found in APK'
  exit 1
fi

echo 'APK inspection: PASS'
