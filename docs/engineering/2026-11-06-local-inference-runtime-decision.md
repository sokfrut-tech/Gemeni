# Phase B runtime evaluation — decision record (2026-11-06)

Status: evidence gathered; no runtime vendored and no neural inference claim.

## Executive decision

**Primary candidate for a minimal Jarvis runtime spike: upstream llama.cpp C/C++ API built with Android NDK, pinned to an exact upstream commit.** Do not copy the current Arm AI Chat Kotlin library unchanged. Keep MLC LLM as a later hardware-accelerated experiment, not the first integration. Do not choose ONNX Runtime GenAI until an Android-specific generative API and model compatibility are demonstrated; ordinary ONNX Runtime Android package availability alone is not proof of an LLM generation stack.

This is a candidate decision, not an approval to ship. It must pass a reproducible build, license review, model license review and OPPO device benchmarks.

## Verified upstream evidence

### llama.cpp

- Upstream repository: https://github.com/ggml-org/llama.cpp
- Exact inspected upstream commit: `954de248ac6907dd05dc19f0f511ab18f2807a54` (2026-11-06).
- Upstream root license at that commit: MIT. Preserve the copyright and license notice when incorporating code.
- Official Android guide: https://github.com/ggml-org/llama.cpp/blob/954de248ac6907dd05dc19f0f511ab18f2807a54/docs/android.md
- The guide documents Android NDK CMake cross-compilation and a portable arm64-v8a configuration. Its example sets `ANDROID_PLATFORM=android-28`, disables `GGML_NATIVE`, `GGML_OPENMP`, `GGML_LLAMAFILE` and OpenSSL. The guide recommends a reasonable context length because memory use can spike.
- The same upstream tree contains `examples/llama.android`, but the current Arm AI Chat wrapper is not a ready drop-in for this app: its library Gradle file declares `minSdk = 33`, `compileSdk = 36`, NDK 29, and only arm64-v8a/x86_64. Its API exposes model loading, system prompt, streaming token Flow, cancellation state and cleanup. Use this as an API/lifecycle reference only, not as an unreviewed dependency.
- Consequence: prefer a small app-owned JNI adapter over importing the Arm sample library. Pin upstream source exactly; do not follow a moving master branch during builds.

### MLC LLM

- Official Android deployment guide: https://llm.mlc.ai/docs/deploy/android.html
- The guide describes a source build requiring Rust, Android NDK (example NDK 27), TVM/MLC packaging and per-model compiled libraries. Its Android sample targets actual physical phones; docs explicitly say the app cannot meaningfully run on an emulator because mobile GPU acceleration is required.
- This is promising for a later Adreno GPU profile but adds a model compilation/package pipeline and should not be the first baseline runtime.

### ONNX Runtime

- Official installation matrix: https://onnxruntime.ai/docs/install/
- Android package availability is confirmed, but that fact alone does not establish an Android generative LLM API, supported decoder models, KV-cache behavior or token streaming. Evaluate only with a concrete Android GenAI sample and compatible model, not by adding the base ONNX Runtime dependency.

## Compatibility warning

The current Gemeni app declares `minSdk = 29`. The official Arm llama.android sample library currently declares `minSdk = 33`, so importing that library would raise the effective platform floor and conflict with the project's Android 7/API24+ goal. The generic llama.cpp Android NDK guide's example uses API 28, not API 24. Therefore **API24 support is not yet proven for the neural runtime**. Keep the existing API-floor compatibility work separate and test the minimum API actually required by the chosen native build and Kotlin/Android APIs. Do not claim Android 7 support until a minSdk24 build and runtime test pass.

## Proposed spike boundary

1. Pin llama.cpp commit `954de248ac6907dd05dc19f0f511ab18f2807a54`.
2. Build arm64-v8a first, CPU-only and portable; no forced CPU ISA flags.
3. Keep native runtime isolated behind `LocalInferenceEngine`; Kotlin provider never receives Android action objects or tool callbacks.
4. Start with a small GGUF instruction model whose model card explicitly permits the intended use and distribution. Model weights are not bundled in APK.
5. Add cancellation, bounded token generation, explicit model unload, memory-pressure handling and a deterministic fallback.
6. Add native unit/smoke tests and an airplane-mode test. A successful Gradle build is not inference proof.
7. Benchmark on the OPPO Find X6 Pro before enabling by default: RAM, cold load, time-to-first-token, tokens/sec, thermal throttling, battery and Russian instruction/tool-selection accuracy.

## License gate

llama.cpp code is MIT at the inspected commit. This does **not** grant a license to redistribute arbitrary GGUF model weights. Every candidate model must have its own model card, exact license, attribution and any use restrictions recorded. No model is selected by this decision record.

## Sources

- llama.cpp Android guide: https://github.com/ggml-org/llama.cpp/blob/954de248ac6907dd05dc19f0f511ab18f2807a54/docs/android.md
- llama.cpp Android Kotlin API: https://github.com/ggml-org/llama.cpp/tree/954de248ac6907dd05dc19f0f511ab18f2807a54/examples/llama.android/lib/src/main/java/com/arm/aichat
- llama.cpp license: https://github.com/ggml-org/llama.cpp/blob/954de248ac6907dd05dc19f0f511ab18f2807a54/LICENSE
- MLC Android guide: https://llm.mlc.ai/docs/deploy/android.html
- ONNX Runtime install matrix: https://onnxruntime.ai/docs/install/


## Model shortlist update — Qwen3.5 (2026-11-06)

Fresh model-card and GGUF checks found a better current candidate family than the earlier Qwen3-1.7B baseline.

### Candidate A — small pipeline smoke-test
- Upstream weights: https://huggingface.co/Qwen/Qwen3.5-0.8B
- Official GGUF conversion: https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF
- Candidate file: `Qwen3.5-0.8B-Q4_0.gguf`, 563 MB.
- Exact GGUF revision: `4c11520cef475dd40ed72ec4a1e68b26721bd769`.
- SHA-256 reported by Hugging Face Xet pointer: `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`.
- Use only as a small runtime bring-up / Russian prompt smoke-test model; 0.8B is not assumed sufficient for high-quality agent planning.
- The upstream Qwen3.5-0.8B model card marks the model Apache-2.0. Preserve the model and GGUF attribution/licensing notices.

### Candidate B — primary quality/size experiment
- Upstream model: https://huggingface.co/Qwen/Qwen3.5-2B
- Model card marks Apache-2.0; it is a post-trained instruction model, not the Base checkpoint.
- Candidate GGUF conversion: https://huggingface.co/bartowski/Qwen_Qwen3.5-2B-GGUF
- Candidate quantization: `Qwen3.5-2B-Q4_K_M.gguf`, reported around 1.40 GB. The quantizer describes this as its recommended default size/quality balance.
- This is a third-party conversion, not a Qwen or ggml-org official conversion. Before enabling downloads, pin the exact repository revision and verify the full-file SHA-256; do not use a floating `main` URL.
- Candidate for OPPO Find X6 Pro benchmark, not yet approved as default.

### Runtime compatibility cross-check
- The current llama.cpp source tree identifies `qwen35` / `qwen35moe` architectures and the upstream README demonstrates the `ggml-org/Qwen3.5-0.8B-GGUF` model family. Still verify the exact pinned llama.cpp commit against the selected GGUF metadata during the spike.
- Begin with context 4096 (then 8192 only if memory allows), low batch, bounded output tokens and explicit cancellation. Never assume the model's advertised maximum context is safe on a phone.
- Model download stays outside APK, resumable where feasible, SHA-256 verified, user-cancellable and removable.
- Russian quality, tool-schema compliance, latency, RAM, thermals and battery are not yet measured. No quality or speed claims are made.

### Selection gate
1. First load official 0.8B Q4_0 with the pinned runtime to prove JNI, tokenizer/chat template, streaming, cancellation and airplane-mode behavior.
2. Evaluate the 2B Q4_K_M candidate after exact conversion revision + SHA pin.
3. Run the fixed Russian prompt/tool-call benchmark on the physical OPPO Find X6 Pro.
4. Keep deterministic parser fallback available even after model integration.
