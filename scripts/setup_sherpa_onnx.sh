#!/usr/bin/env bash
set -euo pipefail

# Fetch a pinned, reproducible Sherpa-ONNX Android runtime and its Kotlin API.
# Pinned to v1.13.8 so it matches Gradle's AAR (v1.13.8) and retains built-in
# espeak-ng / piper-phonemize for offline Russian Piper TTS voices.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SHERPA_VERSION="v1.13.8"
WORK="$ROOT/.sherpa-onnx"
API_DST="$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx"
JNI_DST="$ROOT/app/src/main/jniLibs"

rm -rf "$WORK" "$API_DST" "$JNI_DST"
mkdir -p "$WORK" "$API_DST" "$JNI_DST"

git clone --depth 1 --branch "$SHERPA_VERSION" https://github.com/k2-fsa/sherpa-onnx.git "$WORK/src"
cp "$WORK/src"/sherpa-onnx/kotlin-api/*.kt "$API_DST/"

curl -fL --retry 4 --retry-delay 2 \
  -o "$WORK/android.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/${SHERPA_VERSION}/sherpa-onnx-${SHERPA_VERSION}-android.tar.bz2"

tar -xjf "$WORK/android.tar.bz2" -C "$WORK"

# The release archive contains prebuilt Android libraries for the common ABIs.
find "$WORK" -type f \( \
  -name 'libsherpa-onnx-jni.so' -o \
  -name 'libonnxruntime.so' -o \
  -name 'libsherpa-onnx-c-api.so' -o \
  -name 'libsherpa-onnx-cxx-api.so' \
\) -print0 | while IFS= read -r -d '' lib; do
  abi="$(basename "$(dirname "$lib")")"
  case "$abi" in
    arm64-v8a|armeabi-v7a|x86_64|x86)
      mkdir -p "$JNI_DST/$abi"
      cp "$lib" "$JNI_DST/$abi/"
      ;;
  esac
done

for abi in arm64-v8a armeabi-v7a; do
  test -s "$JNI_DST/$abi/libsherpa-onnx-jni.so"
  test -s "$JNI_DST/$abi/libonnxruntime.so"
done

echo "Sherpa-ONNX $SHERPA_VERSION Android runtime and Kotlin API installed."
