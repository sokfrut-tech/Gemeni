#!/usr/bin/env bash
set -euo pipefail

# Build pinned stable-diffusion.cpp JNI library (libjarvis_sd.so) for arm64-v8a and x86_64
# into app/src/main/jniLibs/<abi>/libjarvis_sd.so with 16 KiB ELF alignment.
# Built in a separate CMake project to avoid ggml target collisions with llama.cpp.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SD_COMMIT="dee497f4df67786a24a30b46176ebc188f247192"
SRC_DIR="$ROOT/.stable-diffusion.cpp"
JNI_SRC="$ROOT/app/src/main/sd_cpp"
JNILIBS="$ROOT/app/src/main/jniLibs"

NDK_DIR="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_HOME:-}/ndk/26.3.11579264}}"
if [[ ! -d "$NDK_DIR" ]]; then
  echo "Android NDK not found at $NDK_DIR" >&2
  exit 1
fi

CMAKE_BIN="$(command -v cmake || true)"
if [[ -z "$CMAKE_BIN" ]] && [[ -x "${ANDROID_HOME:-}/cmake/3.31.6/bin/cmake" ]]; then
  CMAKE_BIN="${ANDROID_HOME}/cmake/3.31.6/bin/cmake"
fi
if [[ -z "$CMAKE_BIN" ]]; then
  echo "cmake not found" >&2
  exit 1
fi

if [[ -f "$JNILIBS/arm64-v8a/libjarvis_sd.so" && -f "$JNILIBS/x86_64/libjarvis_sd.so" ]]; then
  echo "libjarvis_sd.so already present for arm64-v8a and x86_64 (cached)"
  exit 0
fi

if [[ ! -d "$SRC_DIR/.git" ]] || [[ "$(git -C "$SRC_DIR" rev-parse HEAD 2>/dev/null || true)" != "$SD_COMMIT" ]]; then
  rm -rf "$SRC_DIR"
  mkdir -p "$SRC_DIR"
  git -C "$SRC_DIR" init -q
  git -C "$SRC_DIR" remote add origin https://github.com/leejet/stable-diffusion.cpp.git
  git -C "$SRC_DIR" fetch --depth 1 origin "$SD_COMMIT"
  git -C "$SRC_DIR" checkout --force FETCH_HEAD
  git -C "$SRC_DIR" submodule update --init --depth 1 ggml
fi

JOBS="$(nproc 2>/dev/null || echo 4)"

for ABI in arm64-v8a x86_64; do
  BUILD_DIR="$ROOT/.sd_build_$ABI"
  rm -rf "$BUILD_DIR"
  "$CMAKE_BIN" -S "$JNI_SRC" -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release
  "$CMAKE_BIN" --build "$BUILD_DIR" --target jarvis_sd -j "$JOBS"
  mkdir -p "$JNILIBS/$ABI"
  cp -f "$BUILD_DIR/libjarvis_sd.so" "$JNILIBS/$ABI/libjarvis_sd.so"
  rm -rf "$BUILD_DIR"
done

ls -lh "$JNILIBS/arm64-v8a/libjarvis_sd.so" "$JNILIBS/x86_64/libjarvis_sd.so"
