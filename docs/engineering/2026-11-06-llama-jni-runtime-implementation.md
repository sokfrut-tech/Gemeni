# Jarvis Phase B — JNI inference runtime implementation slice

Date: 2026-11-06

## What this branch adds

- A pinned build-time FetchContent dependency on upstream `ggml-org/llama.cpp` commit `954de248ac6907dd05dc19f0f511ab18f2807a54`.
- CPU-only portable baseline for the first Android bring-up; GPU offload is deliberately disabled until measured on the OPPO Find X6 Pro.
- JNI model load, GGUF chat-template formatting, tokenization, bounded greedy generation, cancellation, explicit unload and runtime version reporting.
- Kotlin adapter that rejects malformed expected digests, unreadable/empty files and SHA-256 mismatches before entering native code.
- A streaming callback that buffers incomplete UTF-8 token fragments before delivering valid text to Kotlin.
- Unit tests for SHA-256 verification and mobile runtime limits.

## Model integrity

The first smoke candidate is the official ggml-org conversion `Qwen3.5-0.8B-Q4_0.gguf` with the recorded SHA-256:

`57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`

The model weights are not included in the APK or this repository. Installation is explicit-consent only, uses the pinned model revision and SHA-256, downloads to app-private storage, supports cancellation/removal and does not upload prompts, voice, files or memory.

## Current PR #92 integration state (supersedes earlier integration notes below)

- `JarvisRuntimeController` exposes the Qwen installer and can switch from the deterministic local parser to the llama.cpp-backed provider after a verified model load.
- `QwenModelPanel` provides explicit consent, download progress/cancellation, verify-and-activate, and removal controls.
- Generated model text is validated through `ToolProposalCodec.validate`; invalid output becomes `NoAction`. It is not allowed to execute Android actions directly.
- The model installer and runtime have unit tests, but a passing CI build, successful real model download, on-device inference, and physical OPPO validation remain separate acceptance gates.

## Important boundaries

- The native library is fetched during the developer/CI CMake build at an immutable commit. The installed app does not fetch source code or contact llama.cpp.
- The runtime adapter uses bounded context/output limits; the baseline JNI generation path uses greedy sampling and CPU inference.
- JNI load/generate operations are synchronous and must be invoked from a worker thread. Cancellation is callable from another thread; unload waits for active generation to leave the native lock.
- Do not claim neural inference is available until the Android NDK build, model installation, emulator/device inference and offline inference gates pass.
- API 24 support is not established. The app currently declares minSdk 29.

## Required verification before declaring this phase complete

1. Exact-head Gradle/JVM tests, Kotlin compile, Android APK build, CodeQL and secret scan.
2. Confirm CMake FetchContent configures and links the enabled ABIs: arm64-v8a for OPPO Find X6 Pro and x86_64 for Android emulator coverage.
3. Verify model SHA rejection, successful load, prompt template, Russian output, token callback, cancellation during generation, unload and reload.
4. Repeat generation in airplane mode after the model is installed.
5. Benchmark cold-load time, time-to-first-token, tokens/sec, peak RAM, thermal behavior and battery on OPPO Find X6 Pro.
6. Only then consider this phase complete. All model text must still pass strict typed proposal validation and the existing risk policy before Android tools can execute.

## CI retry record — 2026-11-07

- Workflow run `42293532481` on PR #92 reached successful secret scanning, Android SDK/component setup, CodeQL initialization and Kotlin compilation.
- The job then remained reported as `in_progress` at `Build full debug APK`; the run's `updated_at` timestamp stopped advancing and GitHub's job-log endpoint returned BlobNotFound.
- No APK artifact was present at the last inspection. Therefore APK build, SHA verification, emulator smoke test and CodeQL analysis are not considered passed.
- To avoid reporting a false success, a follow-up PR commit is used to trigger a fresh pull-request workflow run. Workflow concurrency is configured to cancel the previous run for the same branch when a new run starts.
- Keep PR #92 open and draft until a fresh run completes all required gates. Do not merge or publish an APK on the basis of Kotlin compilation alone.

## License

llama.cpp is MIT-licensed at the pinned commit. The full upstream license text is reproduced in `THIRD_PARTY_NOTICES.md`. The model's Apache-2.0 model-card statement and attribution must be preserved separately when the model is offered for installation.
