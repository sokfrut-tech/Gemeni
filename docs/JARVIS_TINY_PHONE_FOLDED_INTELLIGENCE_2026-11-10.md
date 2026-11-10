# Jarvis Tiny-Phone Folded Intelligence (TPFI)
Date: 2026-11-10
Status: design + first platform-neutral runtime policy implementation. Device performance is not yet benchmarked.

## Core insight

Do not attempt to squeeze a desktop-sized model into a phone by simply shrinking weights. Build a *small always-ready cognitive shell* that calls expensive capabilities only for the fraction of turns that need them. Intelligence comes from routing, retrieval, specialist models, deterministic tools and user-approved learning—not from keeping a giant model resident.

Working name: **TPFI — Thermally-aware Progressive Folded Intelligence**.

## What "folding" means

Four distinct folds cooperate:

1. **Capability fold:** split product intelligence into voice, intent, reasoning, retrieval, tools and speech modules. Each module is independently versioned and loaded on demand.
2. **Context fold:** retain the last few dialogue turns verbatim; compress older turns into a bounded carry capsule with facts, decisions, unresolved questions and provenance. Never silently truncate: compression failure blocks the turn or asks the user.
3. **Compute fold:** use a cascade. Deterministic rules first; compact model second; specialist model only when needed; large/cloud model only with explicit network consent. Neural engines run sequentially, not concurrently.
4. **Thermal fold:** measure Android thermal headroom/status, free memory, battery saver and foreground state. Dynamically reduce context/output, suspend reasoning, unload models and fall back to deterministic voice/actions before the device throttles.

This is not arbitrary tensor slicing. Transformer weights, tokenizer, KV cache and layer dependencies must remain in the model's supported format. Official GGUF split shards remain a single logical model and must be complete before activation.

## New data stream: Folded Cognitive Stream (FCS)

```
Mic -> VAD/KWS -> streaming ASR -> transcript + confidence
                                      |
                                      v
                              Intent gate (tiny)
                     / direct command | chat | plan \
                    v                 v              v
             Kotlin tool        compact local      retrieve only
             validator          reasoning          relevant memory
                    |                 |              |
                    +-----------> bounded task graph <+
                                      |
                         if uncertainty remains:
                         specialist reasoning pass
                                      |
                         if user explicitly allows:
                         authorized cloud/Andes
                                      |
                        typed result + provenance
                                      |
                       Kotlin validates tools
                                      |
                       stream answer to UI/TTS
                                      |
                        fold session into memory
```

Every stage emits a small envelope: task ID, stage, locale, confidence, token/memory/time budget, provenance, privacy/network policy and cancellation token. Raw microphone audio is discarded after processing unless the user explicitly opts into recording.

## Inference cascade

### Stage A — no neural LLM
- Known commands: navigation, browser, phone, settings, alarms and local app launches use deterministic parsers and Android APIs.
- No LLM load, no network, no token generation.
- Ambiguous/high-risk commands ask a clarification or confirmation.

### Stage B — compact local brain
- Candidate: Qwen3-0.6B Instruct, quantized GGUF, run through llama.cpp Android JNI.
- Short context (initial target 512–2,048 tokens), short output, one sequence.
- Used for intent clarification, concise answers, schema-constrained proposals and light planning.
- This is a benchmark candidate, not an assumed fit. The Qwen model card and phone measurements decide.

### Stage C — specialist pass
- Load only one specialist (translation, summarization, vision, code) when explicitly needed.
- Unload compact LLM first unless memory telemetry proves safe.
- Keep specialist artifacts on storage and activate sequentially.

### Stage D — optional remote/desktop reasoning
- Only if user enabled network for this request or paired a trusted PC/server.
- Send the minimum task capsule, not the full transcript/memory.
- Explicitly show cloud indicator. AndesGPT only after official OPPO access; other providers are separately named.
- Offline fallback remains functional.

## Memory and heat discipline

- No continuous background LLM.
- Wake-word model is tiny and isolated; full ASR activates only after wake/tap.
- ASR → LLM → TTS are serialized where possible. Release ONNX sessions/native contexts before loading next engine.
- Use Q4/Q5 weights only after accuracy tests; quantized KV cache (e.g. Q4/Q8 where supported) and strict context limits reduce memory. Quantization may reduce quality; evaluate it.
- Keep model weights memory-mapped only if measurements show acceptable page faults and memory pressure. Avoid pinning all weights in RAM.
- Do not run inference while charging if thermal headroom is poor; charging is not proof of safe thermal state.
- If Android signals HOT: stop LLM generation at a token boundary, unload the LLM, keep voice/action shell.
- If CRITICAL: stop all optional neural work, release native resources, retain only deterministic UI/actions and user-facing cooling notice.
- Use Android `PowerManager.getThermalHeadroom()`, `getCurrentThermalStatus()` and thermal listeners when available. The API warns that headroom is device-dependent and should not be polled excessively. See https://developer.android.com/reference/android/os/PowerManager and https://developer.android.com/games/optimize/adpf/thermal.

## Training and learning without cooking the phone

"Self-learning" is separated into four safe mechanisms:

1. **Preference learning:** user-approved preferences, corrections and routines stored as compact records, with provenance and deletion.
2. **Retrieval learning:** local notes/documents indexed on demand; embeddings can be computed in small batches while charging/cool or on a trusted PC.
3. **Skill learning:** new deterministic skills/grammars and tool schemas installed as signed versioned packs with tests.
4. **Model adaptation:** optional LoRA/adapter training or distillation happens on a PC/server or deferred maintenance job, never during voice interaction. Export a small adapter only after offline evaluation and user approval.

No online gradient updates on every conversation. That would consume energy, create catastrophic forgetting/privacy risk and is not needed for a personal assistant. A phone can curate examples and feedback; heavy training is delegated to a stronger machine when one is available.

## Thermal governor states

Initial conservative policy implemented in `JarvisFoldedRuntimePolicy.kt`:
- COOL: allow one bounded local model if free memory is above the measured minimum; context/output capped.
- WARM: smaller context/output, sequential engines, no long reasoning.
- HOT: suspend LLM; deterministic actions and voice shell only.
- CRITICAL: stop neural inference and release native resources.

Thresholds in code are starting values, not calibrated OPPO temperature limits. Final thresholds must be tuned with device traces and thermal status; Android thermal headroom scales differ by device.

## What is new and testable

- The scheduler chooses *how much cognition to spend* per turn from memory/thermal/power signals.
- Context is folded into a compact state capsule instead of growing forever.
- Reasoning is cascaded by uncertainty and task complexity, not by a fixed model size.
- Specialist models are loaded sequentially and unloaded immediately after their work.
- The same task envelope can be sent to local, desktop or authorized cloud provider without changing action safety.

## Device validation plan for OPPO Find X6 Pro

Before enabling any model by default, record:
- SoC/ABI, RAM class and actual free memory at app start;
- model file size, peak PSS/native heap, KV cache size;
- cold-load latency, first-token latency, tokens/sec, ASR real-time factor;
- 5/10/20-minute surface/thermal headroom trend, CPU/GPU/NPU utilization where accessible;
- battery drain per 10-minute conversation;
- Russian command accuracy and answer quality;
- performance while navigation/music/camera are active.

Run separate trials for Qwen3-0.6B Q4, a smaller compact model, and no-LLM deterministic mode. Do not claim "no overheating" from a single short run.

## Implementation delivered in this branch

- `JarvisFoldedRuntimePolicy.kt`: pure policy object for COOL/WARM/HOT/CRITICAL and context folding.
- `JarvisFoldedRuntimePolicyTest.kt`: unit tests for heat tiers, battery saver and context capsule.
- Existing `JarvisCognitiveKernel.kt`: provider routing and model pack contracts.

This is the scheduler foundation only. Android PowerManager sampling, llama.cpp runtime, actual model pack download/verification, and on-device benchmarks remain separate implementation tasks.
