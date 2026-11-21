#!/usr/bin/env bash
set -euo pipefail

# Fetch a pinned, reproducible llama.cpp source tree for Android NDK JNI builds.
# Model weights are intentionally NOT bundled into the APK; LocalLlmModelManager
# downloads and SHA-256 verifies GGUF packs into app-private storage.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLAMA_COMMIT="2d8a32502909ca065c41c4cf5f2fb1c427c047fc" # v1.0.0
DST="$ROOT/.llama.cpp"

if [[ -d "$DST/.git" ]] && [[ "$(git -C "$DST" rev-parse HEAD 2>/dev/null || true)" == "$LLAMA_COMMIT" ]]; then
  echo "llama.cpp already pinned at $LLAMA_COMMIT"
else
  rm -rf "$DST"
  mkdir -p "$DST"
  git -C "$DST" init -q
  git -C "$DST" remote add origin https://github.com/ggml-org/llama.cpp.git
  git -C "$DST" fetch --depth 1 origin "$LLAMA_COMMIT"
  git -C "$DST" checkout --force FETCH_HEAD
fi

test -f "$DST/CMakeLists.txt"
test -f "$DST/include/llama.h"
echo "llama.cpp pinned source ready at $LLAMA_COMMIT"
