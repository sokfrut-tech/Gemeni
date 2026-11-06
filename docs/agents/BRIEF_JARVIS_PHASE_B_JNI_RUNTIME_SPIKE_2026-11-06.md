# Jarvis Phase B — JNI inference runtime spike implementation plan

Date: 2026-11-06
Repository: sokfrut-tech/Gemeni
Branch: feat/jarvis-phase-b-native-runtime-spike-2026-11-06
Status: implementation work branch; this document is not runtime implementation evidence.

## Why this is the next step

The safe Android action gateway and strict typed proposal validation now exist, but `LocalLlmEngine` is deliberately a placeholder and explicitly reports that no neural model is connected. Do not disguise deterministic parsing as reasoning. This phase adds a real, cancellable, local inference boundary without giving model code direct Android privileges.

## Verified source baseline

- Android module currently uses NDK 26.3.11579264, CMake 3.31.6, C++17 and ABI filters including arm64-v8a.
- Existing native library target is `jarvis_native`, built from `security_bridge.cpp` and `alphavideo_gl_engine.cpp`.
- App minSdk is currently 29. Do not claim API 24 compatibility for the native LLM path; upstream Android guide evidence only establishes an API 28 example.
- Existing `LocalLlmEngine.complete(prompt)` is a placeholder returning an honest unavailable message.
- `ToolProposalCodec` accepts only a closed JSON schema: open_app, web_search, navigate, open_dialer, tell_time. Dialer proposals require confirmation and route only through the existing gateway.

## Implementation scope

### A. Native runtime adapter

1. Pin llama.cpp to upstream commit `954de248ac6907dd05dc19f0f511ab18f2807a54`; never build from floating master.
2. Add a reproducible CMake/NDK integration for arm64-v8a first. Keep portable CPU defaults; do not enable host-native ISA flags or assume GPU acceleration.
3. Keep third-party source under a clearly attributed vendor directory with the exact MIT license and notices preserved.
4. Add a narrow JNI bridge for create/load, prompt submission, incremental token retrieval, cancellation, unload and error reporting.
5. Do not expose Android Context, filesystem handles, arbitrary paths, shell, network, intents or tool-execution callbacks to native inference.
6. Ensure JNI references and native handles are released on every failure, cancellation and lifecycle exit.

### B. Kotlin contract and lifecycle

Create a provider-neutral `LocalInferenceEngine` contract with:
- model metadata and readiness state;
- explicit load/unload;
- streaming generation;
- cancellation;
- bounded max tokens and context;
- deterministic typed errors (model missing, invalid model, unsupported ABI, insufficient memory, cancelled, native failure);
- memory-pressure response and safe unload.

The implementation must be injected behind the existing provider boundary. Keep `LocalOnlyJarvisModelProvider` deterministic fallback when no model is installed or inference is unavailable. Do not let inference failure block ASR or basic offline commands.

### C. Model handling

- First smoke model: official `ggml-org/Qwen3.5-0.8B-GGUF`, Q4_0, 563 MB, revision `4c11520cef475dd40ed72ec4a1e68b26721bd769`, SHA-256 `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`.
- Do not bundle weights in APK.
- No download begins automatically. A later model-manager UI must disclose host, file size, license, hash verification and storage before explicit user approval.
- Do not use a floating URL or accept a file until SHA-256 matches.
- Keep the 2B Q4_K_M candidate out of this initial smoke-test slice until its exact conversion revision and full-file hash are pinned.

### D. Safety and prompt-to-action boundary

Flow remains:
ASR -> context builder -> local model -> structured proposal -> strict schema validator -> risk policy -> Android gateway -> verified result.

- Treat all model output as untrusted.
- Prefer constrained decoding/grammar if supported by the pinned runtime; still validate after decoding.
- Unknown tools, unknown fields, malformed JSON, oversized values and ambiguous arguments are rejected.
- SAFE/CONFIRM/BLOCK policy remains centralized; model cannot downgrade risk.
- The model must not claim an action succeeded until the gateway returns a result and its verification rule passes.
- No arbitrary code execution, self-modification, direct ACTION_CALL, SMS sending or unrestricted Accessibility.

### E. Tests required in this phase

1. JNI library loads on arm64-v8a and reports a stable runtime version/commit.
2. Model load rejects wrong SHA, missing file and unsupported architecture with typed errors.
3. Prompt produces a streamed response from the pinned smoke model in airplane mode.
4. Cancellation stops generation and frees native resources.
5. Repeated load/unload does not leak native handles.
6. Invalid/adversarial tool proposals are rejected by the existing codec.
7. Deterministic commands still work with no model installed.
8. Gradle unit tests, Kotlin compilation, APK assembly, CodeQL and secret scanning pass.
9. Physical OPPO Find X6 Pro benchmark records RAM, load time, first-token latency, tokens/sec, thermal behavior, battery and Russian prompt/tool-selection quality before any default-model decision.

## Non-goals

- No model weights in the APK.
- No cloud inference or paid API.
- No default background download.
- No claim of API24 support.
- No claim that emulator inference proves OPPO GPU/thermal performance.
- No merge or release until all required CI gates pass and physical-device acceptance is recorded.

## Definition of done

A user can explicitly install the pinned model, run a general Russian prompt fully offline, see streaming output, cancel it, and remove/unload it. If the model is absent or fails, Jarvis truthfully falls back to supported deterministic offline commands. Model-generated actions remain gated by the existing strict validator and Android tool gateway.

## Handoff rule

Update this document with exact commit SHAs, CI run URLs, model hashes and measured results. Never mark a gate complete based on a planned test or a successful APK build alone.
