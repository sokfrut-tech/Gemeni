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

The model weights are not included in the APK or this repository. This branch does not download model files. A future model installer must require a clear user approval, pin the source revision, verify the full SHA-256, support cancellation and removal, and work without transmitting prompts, voice, files or memory.

## Important boundaries

- The native library is fetched during the developer/CI CMake build at an immutable commit. The installed app does not fetch source code or contact llama.cpp.
- The runtime adapter is not yet connected to `LocalOnlyJarvisModelProvider`, the microphone controller, model-install UI or safe Android action pipeline. The existing deterministic provider remains the active assistant path.
- The current JNI generation baseline uses greedy sampling and CPU inference, with context capped at 8192 (default 4096), output capped at 512 (default 256), and prompt text capped at 12,000 characters.
- JNI load/generate operations are synchronous and must be invoked from a worker thread. Cancellation is callable from another thread; unload waits for active generation to leave the native lock.
- This code has not yet been proven by an Android NDK build, emulator inference, airplane-mode inference, or a physical OPPO run. Do not claim neural inference is available until those gates pass.
- API 24 support is not established. The app currently declares minSdk 29; the upstream Android guide baseline was API 28.

## Required verification before integration

1. Exact-head Gradle/JVM tests, Kotlin compile, Android APK build, CodeQL and secret scan.
2. Confirm CMake FetchContent configures and links all enabled Android ABIs; if older secondary ABIs fail, scope the first inference build to arm64-v8a without removing emulator test coverage.
3. Add a small test GGUF artifact in CI only if licensing and artifact-size policy permit; otherwise document the manual, consent-based device installation step.
4. Verify model SHA rejection, successful load, prompt template, Russian output, token callback, cancellation during generation, unload and reload.
5. Repeat generation in airplane mode after the model is installed.
6. Benchmark cold-load time, time-to-first-token, tokens/sec, peak RAM, thermal behavior and battery on OPPO Find X6 Pro.
7. Only then wire provider selection into Jarvis. All model text must still pass strict typed proposal validation and the existing risk policy before Android tools can execute.

## License

llama.cpp is MIT-licensed at the pinned commit. The full upstream license text is reproduced in `THIRD_PARTY_NOTICES.md`. The model's Apache-2.0 model-card statement and attribution must be preserved separately when the model is offered for installation.
