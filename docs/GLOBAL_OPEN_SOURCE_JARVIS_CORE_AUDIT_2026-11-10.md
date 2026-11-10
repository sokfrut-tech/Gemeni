# Global Open-Source Audit for Jarvis Core
Date: 2026-11-10
Scope: public projects and primary project documentation discovered across international open-source ecosystems. This is a curated engineering audit, not an exhaustive crawl of every repository on every forge.

## Executive decision

Do not transplant a complete assistant app. Jarvis should selectively adopt proven components behind stable Kotlin interfaces, preserve its own orchestration and safety shell, and benchmark every inference engine on OPPO Find X6 Pro before selecting a default.

Recommended stack:
- Android app and UX: existing native Kotlin/Compose.
- Speech: Sherpa-ONNX ASR/VAD/KWS + Russian model benchmark; Piper or verified local TTS.
- LLM runtime shortlist: llama.cpp as baseline; LiteRT-LM and MLC-LLM as measured alternatives.
- Reasoning model: benchmark mobile-first sub-billion candidates, Qwen family and other multilingual models; never choose by parameter count alone.
- Memory: SQLite + FTS5 as baseline, compact hybrid lexical/vector retrieval; evaluate embedded graph memory only if measured useful.
- Actions: typed Kotlin tool registry and confirmation policy; no broad accessibility automation by default.
- Learning: user-approved memory, retrieval, skill packs; adapter training/distillation off-device.

## Repositories and what is reusable

### Android assistant implementations
1. OfflineMate — https://github.com/KingPegasus/OfflineMate (MIT per project page)
   - Tiered Lite/Standard/Full model strategy, explicit network policy, local RAG, voice and permissioned Android tools.
   - Reuse ideas: device capability scan before model download; tiers; local-first defaults; tool path with schema validation.
   - Do not copy React Native app shell into native Compose.

2. Vesta — https://github.com/RisorseArtificiali/vesta
   - Android offline assistant using GGUF through llama.rn; model manager, resumable downloads, RAG, history and system actions.
   - Reuse ideas: model catalog with RAM-aware filtering, resumable verified downloads, confirmation before destructive actions, real-hardware test cases.
   - Verify exact license and native dependency compatibility before code reuse; app is React Native.

3. PocketOrca-LLM — https://github.com/PocketOrca/PocketOrca-LLM
   - Qualcomm-oriented local inference project; advertises CPU/Adreno/Hexagon backends, expert streaming and speculative decoding.
   - Reuse ideas: investigate Snapdragon NPU backend selection and expert streaming as research spikes.
   - Do not assume claims generalize to our app or device; it is a local server app and asks users to keep device cool. Benchmark before any dependency.

4. Andee — https://github.com/kuafuai/andee
   - Android device-agent/provider abstraction and screen interaction.
   - Reuse ideas: provider interface and narrow tool contract only.
   - Avoid copying broad screen-reading/accessibility automation; it expands privacy and reliability risk.

5. OffHand — https://github.com/MRDarshan7/OffHand
   - Offline Android assistant with local Qwen, constrained JSON tools and host-side validation.
   - Reuse ideas: typed tool proposals, independent validation, confirmation, bounded execution loop.
   - Check current license and supported model/runtime revisions before porting code.

6. Pocketminder — https://github.com/ushanboe/pocketminder
   - Gemma on-device through LiteRT-LM, device capability scan, resumable downloads, streaming/cancel, RAG and model health.
   - Reuse ideas: capability preflight, cancellable token stream, health dashboard, lifecycle tests.
   - Its model storage/RAM requirements demonstrate why model size must be opt-in and preflighted.

7. NIDO — https://github.com/Nido007-cmyk/Nido
   - Offline Android assistant with encrypted memory and local models.
   - Reuse ideas: encrypted personal memory and local-first policy.
   - Alpha status and security caveats mean design reference only until audited.

8. Jenny Android AI Agent — https://github.com/flagdizero/jenny-android-ai-agent
   - Persistent memory, scheduled tasks and optional local/cloud models.
   - Reuse ideas: durable task state and background-work lifecycle.
   - Do not import self-writing apps or autonomous execution without sandbox and approval gates.

### Inference runtimes and model optimization
9. llama.cpp — https://github.com/ggml-org/llama.cpp
   - Mature GGUF runtime, Android integration, CPU/GPU backends, quantization and speculative decoding.
   - Role: baseline local LLM runtime; use only one active generation context; evaluate KV-cache quantization and draft model only if net memory/latency win.

10. MLC-LLM — https://github.com/mlc-ai/mlc-llm
   - ML compiler and deployment engine; Android OpenCL backends for Adreno and Mali.
   - Role: benchmark alternative to llama.cpp, especially GPU path. Requires model compilation and platform-specific validation; do not include both runtimes in APK by default.

11. LiteRT / LiteRT-LM — https://github.com/google-ai-edge/LiteRT
   - Google edge inference stack; Android CPU/GPU/NPU paths, conversational LLM streaming, dynamic KV cache and tool calling in LiteRT-LM.
   - Role: benchmark against llama.cpp and MLC on OPPO. Potentially strongest Android integration if Qualcomm delegate is supported on target device and model format is available.
   - Pin exact release; do not use a moving latest dependency in release builds.

12. bitnet.cpp — https://github.com/microsoft/BitNet
   - 1.58-bit ternary-model inference research/runtime, optimized ARM CPU kernels; published speed/energy claims are benchmark-specific.
   - Role: experimental research lane only. Verify model availability, Russian quality, Android build and thermal results. Not a drop-in compression pass for arbitrary Qwen weights.

13. MobileLLM — https://github.com/facebookresearch/MobileLLM
   - Mobile-first sub-billion architectures; deep/thin layers, embedding sharing and grouped-query attention. Training recipes/models.
   - Role: candidate model family and design ideas for a future Jarvis distilled model; check Russian data and license before use.

14. Awesome Mobile LLMs — https://github.com/stevelaskaridis/awesome-mobile-llm
   - Curated mobile model/runtime/benchmark research list.
   - Role: maintain as a recurring discovery source; validate each paper/repo directly.

15. Speculative decoding docs — https://github.com/ggml-org/llama.cpp/blob/master/docs/speculative.md
   - Small draft model proposes tokens, target model verifies them.
   - Role: only if target LLM already fits. A second model can increase RAM and heat; test total energy per accepted token, not tokens/sec alone.

### Memory and retrieval
16. memlocal — https://memlocal.dev/
   - Embedded on-device memory concept with semantic, full-text, graph retrieval and reranking; published small native core claim.
   - Role: architecture inspiration; inspect source, license, Android SDK readiness and benchmarks before adopting. Do not add an external Rust service if SQLite FTS5 meets latency and quality targets.

17. Vesta / OfflineMate document RAG patterns
   - Reuse local document ingestion, chunk provenance, lexical retrieval and bounded context injection.
   - Prefer hybrid BM25 + optional compact embeddings; embeddings should be computed in small background batches only while thermally safe.

## Optimal Jarvis core: proposed composition

### Always-resident microkernel
- Kotlin state machine, command grammar for common intents, permission manager, tool registry, memory metadata, thermal governor and download manager.
- Target: no LLM resident; very low steady-state memory and no background generation.

### On-demand voice chain
- Wake word / VAD first.
- ASR activated only after wake/tap; stream partial transcript and confidence.
- Release audio/ASR native resources before LLM if memory pressure is high.
- TTS starts after answer composition; support barge-in cancellation.

### Cascaded cognition
1. Deterministic parser for known device commands.
2. Tiny intent classifier / grammar for ambiguous routing.
3. Compact local LLM for dialogue and bounded planning.
4. Specialist model only for a named task.
5. Paired PC or authorized cloud only with explicit per-turn permission.
No default cloud. No hidden OPPO endpoints.

### Model pack system
- Capability preflight: ABI, Android API, runtime delegate, RAM headroom, thermal state, storage and battery.
- Download by immutable revision; resume safely; verify SHA-256; stage atomically; roll back.
- Store quantization/runtime/model/tokenizer as a compatible pack; never manually slice tensor bytes.
- Only show model tiers that pass device preflight. Let users delete downloaded weights independently from app code.

### Learning without phone-side training
- Personal corrections -> user-approved memory entries.
- Documents -> local retrieval index.
- Repeated tasks -> tested deterministic skill proposals; user approves activation.
- Training/distillation/LoRA -> optional workstation pipeline; ship only validated small adapters.
- Never mutate base weights silently during conversation.

## Runtime decision matrix to measure on Find X6 Pro

| Runtime | CPU | Adreno GPU | Qualcomm NPU | Build/size complexity | Initial role |
|---|---|---|---|---|---|
| llama.cpp | Yes | Vulkan/OpenCL varies by build | Not assumed | Medium | Baseline |
| MLC-LLM | Yes | OpenCL path | Not assumed | High (compile artifacts) | GPU challenger |
| LiteRT-LM | Yes | GPU delegate | Device/delegate dependent | Medium/high | Android-native challenger |
| bitnet.cpp | ARM CPU focus | experimental/limited | not ready by default | High | Research only |

Do not assume Find X6 Pro exposes a supported public NPU delegate for every runtime. Query device/runtime support and benchmark.

## Benchmark protocol and gates

Use identical prompt set, model revision, quantization, context, output and temperature. Record:
- cold load time, first token, tokens/sec;
- peak PSS + native heap + KV cache;
- energy per 100 generated tokens and per voice turn;
- thermal headroom/status over 5/10/20-minute runs;
- Russian task success and structured-tool accuracy;
- offline operation and cancellation latency;
- competing navigation/music/camera load.
Run at least three repeats, report median and p95. A runtime wins only if it meets quality and safety gates while improving energy/thermal/memory—not just raw speed.

## What should be implemented first

P0: connect Android PowerManager sampling to the already-added folded runtime policy; expose model readiness and actual failure state.
P1: add model capability preflight and verified resumable model packs.
P2: integrate exactly one baseline runtime (llama.cpp) behind JarvisReasoningProvider.
P3: create benchmark flavor to compare LiteRT-LM and MLC without shipping all runtimes.
P4: benchmark Russian model candidates and select smallest model meeting task success.
P5: add local hybrid memory (SQLite FTS5 first; vectors only if measured improvement).
P6: implement skill packs and approved learning pipeline.

## Licensing and supply-chain requirements

Before copying any code, model or dataset:
- inspect repository LICENSE and model card license separately;
- record source commit, notices and attribution;
- scan dependencies and native binaries;
- pin hashes and SBOM;
- check redistribution and commercial-use restrictions;
- do not import an entire app merely because its README says open source.

## Sources
- https://github.com/KingPegasus/OfflineMate
- https://github.com/RisorseArtificiali/vesta
- https://github.com/PocketOrca/PocketOrca-LLM
- https://github.com/kuafuai/andee
- https://github.com/MRDarshan7/OffHand
- https://github.com/ushanboe/pocketminder
- https://github.com/Nido007-cmyk/Nido
- https://github.com/flagdizero/jenny-android-ai-agent
- https://github.com/ggml-org/llama.cpp
- https://github.com/mlc-ai/mlc-llm
- https://github.com/google-ai-edge/LiteRT
- https://github.com/microsoft/BitNet
- https://github.com/facebookresearch/MobileLLM
- https://github.com/stevelaskaridis/awesome-mobile-llm
- https://github.com/ggml-org/llama.cpp/blob/master/docs/speculative.md
- https://memlocal.dev/

## Limitations
This review is a curated global sample from public repositories and primary documentation discovered on 2026-11-10, not a complete crawl of GitHub, GitLab, Gitee, Codeberg and every national forge. Repository features and benchmark claims are self-reported unless independently measured. No third-party source code has been copied into the Jarvis APK in this audit.
