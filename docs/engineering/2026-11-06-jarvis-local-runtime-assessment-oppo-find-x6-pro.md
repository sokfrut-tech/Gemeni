# Jarvis local runtime assessment — OPPO Find X6 Pro

Date: 2026-11-06
Status: Evidence-based candidate assessment; no on-device benchmark has been run yet.
Target: OPPO Find X6 Pro (Snapdragon 8 Gen 2 / SM8550 expected; confirm from the actual device before selecting binaries).

## Executive decision

Do not force all capabilities into one Qwen model or one runtime. Use a modular runtime router:

1. **Reasoning/coding/chat:** Qwen3.5 small GGUF checkpoint through llama.cpp as the portable baseline; evaluate Qualcomm GenieX/QAIRT as an optional Snapdragon acceleration backend.
2. **Vision-language:** a separate Qwen3-VL-compatible model/runtime path; test image input independently from text-only inference.
3. **Image generation:** a dedicated diffusion/DiT engine. Snapdragon 8 Gen 2 has a plausible local SD 1.5 NPU path; do not assume SDXL/Flux/Qwen Image high-end pipelines are supported on this generation.
4. **Python:** Chaquopy is a credible embedded Python candidate for the Android app, with a separate sandboxed execution process and strict file/network restrictions.
5. **APK builds:** keep as a separate feasibility gate. Python-in-app does not provide Android SDK/Gradle. Verify whether a supported on-device toolchain can run reliably; do not silently upload projects to a build server.

## Evidence and candidate matrix

| Capability | Candidate | Evidence | Decision / caveat |
|---|---|---|---|
| Local LLM | llama.cpp Android JNI / GGUF | Official Android guide documents Android app binding, model loading from app-private storage, Kotlin Flow token output and NDK cross-build for arm64. | Baseline path; benchmark CPU first, then optional acceleration. |
| Qualcomm acceleration | GenieX / QAIRT | Qualcomm docs list Snapdragon 8 Gen 2 (SM8550), Hexagon V73 and Android toolchain. GenieX supports CPU/GPU/NPU and GGUF/QAIRT paths. | Prototype adapter; SDK terms, API availability and actual Find X6 Pro performance must be tested. |
| Qwen model | Qwen3.5 0.8B / 2B | Qwen official repository lists these sizes, Apache-2.0 weights and llama.cpp GGUF support; Qualcomm GenieX docs list Qwen3.5-0.8B and 2B as supported models. | Start 0.8B for runtime smoke test; compare 2B quality/latency/RAM. Pin exact repo revision and SHA-256 before download. |
| Vision-language | Qwen3-VL-2B-Instruct | Qualcomm GenieX Android documentation lists Qwen3-VL-2B/4B GGUF as VLM candidates. | Separate image-understanding evaluation; do not equate VLM with image generation. |
| Python runtime | Chaquopy 17.2 | Official docs: Python 3.10–3.15, Android Gradle Plugin 7.3–9.5, minSdk 24, arm64-v8a support; Python stdlib included except documented modules. | Strong candidate. Check current project Gradle/AGP compatibility, package wheels, APK size and sandbox design before integration. |
| Local image generation | Stable Diffusion 1.5 with Qualcomm QNN/NPU, or Local Dream-compatible engine | Local Dream documentation reports SD1.5 NPU support on Snapdragon 8 Gen 2 and CPU/GPU fallback. Qualcomm AI Hub lists 8 Gen 2 among supported chipsets for its mobile SD2.1 model, but its page currently marks that model unsupported on mobile, so do not rely on that route. | First benchmark SD1.5 512x512, memory, latency, thermal load and output quality. Treat community engine/license and Qualcomm runtime terms as separate review gates. |
| Advanced image/video | SDXL / FLUX / Qwen Image / video DiT | The researched Android NPU node-graph project lists SDXL and newer DiT/video paths for Snapdragon 8 Gen 3 or 8 Elite, not 8 Gen 2. | Not a baseline promise for this phone. Re-evaluate only if a compatible CPU/GPU/NPU backend is demonstrated. |
| Android APK build | Android SDK + Gradle on device | No verified end-to-end on-device build result yet. | Separate spike required; measure storage, RAM, execution restrictions, build time and thermal behavior. Fallback must remain explicit user-approved build on a trusted machine, not hidden cloud upload. |

## Implementation plan derived from the assessment

### R1 — Device identity and resource probe
- Read actual Build.HARDWARE, Build.SOC_MODEL where available, ABI, API level, RAM class, free storage, thermal status and GPU/driver identifiers.
- Display values in a local diagnostic page; do not upload them.
- Confirm exact chipset and ColorOS version on the target OPPO before selecting Qualcomm-specific libraries.

### R2 — Qwen inference adapter
- Define a provider-neutral LocalModelRuntime contract: model metadata, load/unload, generate stream, cancel, health, resource estimate and runtime diagnostics.
- Implement llama.cpp arm64 baseline with a small pinned GGUF model.
- Add GenieX as an optional backend behind the same contract only after SDK packaging, licensing and physical-device tests.
- Model catalog stores source URL, license, exact revision, file size and SHA-256. Never trust model-provided URLs or hashes.

### R3 — Python workspace
- Prototype Chaquopy 17.2 in an isolated feature branch/module.
- First offline acceptance script: create files in an allowlisted project directory, execute a pure-Python calculation, run unit tests, capture stdout/stderr and exit code.
- Add timeout, cancellation, bounded output, memory guard where possible, process isolation, read/write path allowlist and network-deny default.
- Do not expose Android Context, arbitrary Intents, shell/root, credentials or app-private memory to Python jobs.
- Native pip wheels require explicit arm64 compatibility checks; a package being available on desktop PyPI is not evidence it works on Android.

### R4 — Image pipeline
- Build a model/runtime compatibility table before downloading any model.
- Start with a real local SD1.5 512x512 inference spike; save the output image and verify no outbound traffic.
- Benchmark cold start, warm generation, peak memory, thermal throttling, battery use and failure recovery.
- Add img2img/inpainting only after text-to-image works reliably.
- Respect model license and upstream project license separately. Do not adopt a non-commercial engine into any future commercial distribution without a separate license decision.

### R5 — APK build feasibility
- Test whether the target device can host a pinned JDK, Android SDK, Gradle and required build tools in a managed environment.
- Prove a minimal Compose APK can be built, hashed, installed after confirmation and smoke-tested.
- If not feasible inside the app sandbox, present a transparent alternative. Source code must not leave the device unless the user explicitly chooses and approves that exact route.

## Security and privacy gates

- Local mode has no analytics, crash upload, prompt sync, source upload or remote inference.
- A Python script, model file, image prompt or generated code cannot grant itself network access.
- Each worker receives only the files it needs and returns bounded structured results.
- Build and model download endpoints are pinned/verified; no arbitrary download URL from model output.
- Model and runtime updates cannot change privacy policy or approval requirements.
- Record command, artifact hashes, test results and runtime versions locally; export only on explicit user action.

## Sources reviewed

- llama.cpp Android guide: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- Qwen3.5 official repository: https://github.com/go-ai/Qwen3.5
- Qualcomm GenieX product/runtime: https://aihub.qualcomm.com/geniex
- Qualcomm Android GenieX guide: https://qualcomm.github.io/qidk/blogs/geniex_android.html
- Qualcomm QAIRT supported-device table: https://docs.qualcomm.com/nav/home/QNN_general_overview.html?product=924033590759186372
- Chaquopy Android setup: https://www.chaquo.com/chaquopy/doc/current/android.html
- Chaquopy version summary: https://www.chaquo.com/chaquopy/doc/current/versions.html
- Local Dream Android image-generation project: https://github.com/zuoweitan/localAI
- Qualcomm Stable Diffusion 2.1 mobile model page: https://aihub.qualcomm.com/mobile/models/stable_diffusion_v2_1

## Completion status

This is a candidate selection and spike plan only. No local Qwen, Python runtime, diffusion engine or on-device Gradle build is claimed integrated or benchmarked on the user's OPPO. The first engineering proof should be the smallest Qwen runtime smoke test and isolated Python script, while the image-generation spike runs independently.
