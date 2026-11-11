# Jarvis local inference decision record — 2026-11-11

Status: **architecture selected; implementation not yet approved by evidence**
Branch: `integration/jarvis-assistant-ci-smoke-repair-2026-11-10`
PR: #133 (draft; do not merge)

## Decision

Use **llama.cpp as the first local text-inference backend**, integrated through a narrow Kotlin/JNI adapter. Do not replace the current `LocalLlmEngine` echo stub with a pretend response. The adapter must report an explicit unavailable/not-installed state until a verified model is present and the native runtime is loaded successfully.

Why this backend:
- Upstream documents Android NDK cross-compilation and provides an Android Studio sample at `examples/llama.android`.
- The sample's documented flow reads GGUF metadata from a content URI or app-private file, obtains an `InferenceEngine` via `AiChat`, loads the private model path, and streams generated tokens as Kotlin Flow.
- The documented portable Android build uses `arm64-v8a`, `GGML_NATIVE=OFF`, `GGML_OPENMP=OFF`, `GGML_LLAMAFILE=OFF`, and `LLAMA_OPENSSL=OFF`. The repository currently targets Android API 29+ and NDK 26.3.11579264; compatibility must be established by a pinned build, not inferred from the docs.
- CPU-only arm64 is the baseline. Optional Adreno/OpenCL/Hexagon acceleration is a later, separately qualified optimization, never a requirement for correctness.

## Candidate first model pack (not yet a production default)

Candidate: `Qwen/Qwen2.5-0.5B-Instruct-GGUF`, quantization `qwen2.5-0.5b-instruct-q4_k_m.gguf` (approximately 491 MB in the repository listing).

- The upstream Hugging Face model card reports Apache-2.0.
- The model card lists multiple quantizations; do not use the full-precision 1.27 GB file as the default phone download.
- This is a small compatibility/profiling candidate only. Russian response quality, instruction following, tool-call reliability, latency, peak RAM, sustained thermals and battery cost must be measured on the target OPPO Find X6 Pro before product selection.
- Never pin a mutable `main` URL. Before implementation, resolve the model repository to an immutable commit/revision, record exact file name, byte length and SHA-256 from the trusted artifact source, and verify the model's chat template/tokenizer metadata.
- Keep a second, stronger candidate out of the APK and out of the initial download until the first model passes the device budget. Model choice must be benchmark-driven.

## Integration contract

1. Keep model weights out of the APK and repository. Store downloaded files under app-private storage; expose model management, storage size, delete/uninstall and retry controls.
2. Require explicit user consent before any model download. Download must be resumable and verify final length and SHA-256 before the loader sees the file. A failed/incomplete download is never loadable.
3. Implement a small interface boundary: load verified model, stream tokens, cancel generation, unload, expose state/diagnostics. Native errors and out-of-memory failures must become explicit recoverable states.
4. Do not load multiple heavyweight inference engines simultaneously. ASR/TTS and LLM must share a resource coordinator; policy may suspend/unload optional inference under memory/thermal pressure.
5. Wire `JarvisFoldedRuntimePolicy` to live Android resource signals before loading or generating. Unknown/unsupported thermal data is conservative, not interpreted as cool.
6. Route ordinary conversational turns through `JarvisCognitiveKernel` and the local provider. Deterministic intents remain first for calls, navigation and system actions. Cloud remains disabled unless the user explicitly enables it for that turn.
7. Stream generated text to the UI and TTS with cancellation and audio-focus handling. Do not speak partial output that was cancelled or rejected by safety/tool validation.
8. No arbitrary splitting of GGUF tensors or renaming shards. If a future model is sharded, follow the model publisher's exact shard/index convention and verify every shard plus the index.

## Build and supply-chain requirements

- Pin llama.cpp to a reviewed immutable upstream commit; do not build from floating `master`.
- Vendor only the minimum source/submodule or reproducibly fetch the pinned commit in CI. Preserve upstream license and notices.
- Build first for `arm64-v8a`; retain other APK ABIs only if a matching runtime is built and tested for each one. Never package an ABI without its native inference library.
- Build with the project's existing Android NDK and CMake versions first. If upstream requires a different toolchain, make a deliberate project-wide compatibility change with CI evidence rather than silently drifting versions.
- Add CI checks for native library ABI presence, APK size (weights absent), JNI load, model metadata validation and deterministic fake-backend tests.
- Pin dependency hashes/commit and model revision/checksum in a checked-in manifest. No API keys or cloud secrets in source or APK.

## Required acceptance tests before claiming offline conversation works

- Unit: model manifest validation; checksum mismatch rejection; partial download resume; HTTP range fallback; cancellation; unload idempotency; no inference before verified load; runtime-policy denial; thermal HOT/CRITICAL unload; one-heavy-engine invariant.
- Instrumented: JNI library loads on arm64 emulator; model can be loaded from app-private path; short Russian prompt produces non-empty generated tokens; cancellation stops generation; unload releases native resources.
- Physical OPPO Find X6 Pro: first download consent; offline airplane-mode conversation after download; Russian quality test set; time-to-first-token and tokens/sec; peak memory; sustained thermal behavior; screen-off/background behavior; audio-focus interruption; battery drain.
- Exact-head CI must pass after every integrated slice. Physical device qualification is a separate release gate and cannot be replaced by emulator success.

## Evidence and source references

- llama.cpp Android documentation: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- llama.cpp Android build documentation: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- llama.cpp Android CI workflow: https://github.com/ggml-org/llama.cpp/blob/master/.github/workflows/build-android.yml
- Qwen candidate model card/files/license: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF

Research snapshot checked 2026-11-11. Upstream `master` documentation and model `main` listing are discovery evidence only; implementation must pin immutable revisions.

## Explicit non-goals for this slice

- No cloud fallback by default.
- No undocumented OPPO AndesGPT endpoint or account bypass.
- No model weights in APK.
- No claim of self-training model weights. Learning remains explicit user memory, skills, and verified knowledge packs.
- No merge or public release based on this ADR alone.
