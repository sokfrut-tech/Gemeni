# Jarvis — Master Product Vision and Architecture
Date: 2026-11-09
Status: architecture baseline / implementation contract
Repository: `sokfrut-tech/Gemeni`

## 1. Product identity

Jarvis is a private, offline-first, Russian-speaking personal AI agent for the Chinese OPPO Find X6 Pro (PGEM10) and ColorOS. It is not merely a speech-to-text demo or a collection of command buttons. It should understand requests, reason within verifiable limits, plan work, use approved tools, remember user-approved context, observe execution results, and explain what it did.

The phone must remain useful without internet for essential tasks. Network services and remote models are optional accelerators for research or workloads that exceed the phone's safe resource budget. No paid provider is assumed to be available.

The ambition is broad capability, but every capability must be measurable, testable, permissioned, and honest about uncertainty. "Universal" describes the intended breadth of skills, not a claim of unlimited knowledge or guaranteed correctness.

## 2. Non-negotiable product principles

1. **Offline-first:** core UI, local voice path, basic reasoning, personal notes/memory, device tools and safety controls work without a network once explicitly selected models are installed.
2. **Privacy by default:** microphone capture is visible and user-controlled; personal memory is consent-gated; local processing is preferred; cloud transfer is explicit and inspectable.
3. **One microphone owner:** a single lifecycle-managed capture coordinator owns AudioRecord. No competing service/process may silently open a second recorder.
4. **Fail closed:** calls, competing system audio, permission loss, thermal critical state, model failure, and Stop cancel active work and release resources. No automatic microphone reacquisition after interruption.
5. **Real actions only:** a tool reports success only after checking the actual Android/system result. A changed label or generated response is not proof of execution.
6. **Human authority:** sensitive, irreversible, financial, communication, permission, and external side-effect actions require confirmation. Stop and revoke controls remain available.
7. **Evidence over claims:** every feature has acceptance tests, runtime evidence and a known status. A green build is not product readiness.
8. **ColorOS first:** validate the actual Chinese OPPO firmware and system assistant routing. AOSP emulator success is not a substitute.
9. **No silent degradation:** if a model, provider, permission or device capability is unavailable, explain the limitation and use only a declared fallback.
10. **Incremental delivery:** first make one stable vertical slice genuinely work; then add breadth without destabilizing the core.

## 3. Target device and compatibility

- Primary device: OPPO Find X6 Pro / PGEM10, Chinese firmware / ColorOS.
- Russian is the primary interaction language.
- Chinese and English speech/text must be supported for transcription and translation; transcription must not silently translate.
- Desired compatibility floor: Android 7 / API 24 where dependencies and OEM APIs permit. The currently inspected Gemeni main branch declares minSdk 29; this is an explicit unresolved compatibility conflict, not a solved requirement.
- Keep platform-neutral logic separate from ColorOS-specific adapters.
- Preserve Breeno and normal phone operation. Jarvis must not assume it can replace the OEM assistant until VoiceInteraction registration and OEM dispatch are proven on the target firmware.

## 4. Layered architecture

### A. Experience layer
Jetpack Compose shell with an original Jarvis visual identity: animated central orb/visualizer, listening/processing/responding states, compact dynamic cards, one-hand controls, accessible typography, and persistent visible Stop while capture is active. The UI must reflect real runtime state, not simulate it.

### B. Interaction and orchestration layer
A typed state machine and task orchestrator:
- resolve intent and ambiguity;
- build a plan with explicit preconditions and expected results;
- select tools by declared capabilities and permission requirements;
- request confirmation when required;
- execute one bounded step at a time;
- verify postconditions;
- persist a checkpoint and action log;
- recover, retry safely, or stop on failure.

Long tasks must have durable checkpoints and cancellation. Never claim an external agent or model was used unless a real authorized call succeeded.

### C. Intelligence/provider layer
Provider-neutral interfaces for:
- small local language model (candidate family: Qwen 0.8B–2B quantized GGUF, subject to measured quality and RAM);
- deterministic intent/rule engine for critical basic commands;
- optional remote providers for heavy reasoning/research;
- routing policy based on privacy, connectivity, latency, confidence, battery, thermal state, RAM and task risk.

The current `LocalLlmEngine` was audited as an echo placeholder on main. A real inference engine is not yet proven integrated. Never label echo/rule output as an LLM response.

### D. Memory and knowledge layer
Use distinct stores with different retention and trust rules:
- short-lived conversation context with a strict token/byte budget;
- user-approved profile/preferences and explicit memories;
- episodic task/action history with retention controls;
- semantic knowledge index with provenance and timestamps;
- model-independent structured facts and skill metadata.

Memory writes require an explicit policy: what is saved, why, retention, sensitivity, and how the user can review/correct it. Do not silently turn every conversation into permanent memory.

### E. Tool and skill layer
Typed, schema-validated tools with capability declarations, permission checks, timeouts, cancellation, bounded inputs/outputs, audit events and postcondition verification. Skills can combine tools but cannot bypass the same policy. Android actions use explicit intents or narrowly scoped platform adapters; Accessibility is only used where an official API is insufficient and user permission is granted.

### F. Perception and voice layer
- Audio capture: one application-scoped AudioRecord source and one coordinator.
- Speech path: KWS (optional explicit wake session) → VAD segmentation → local Russian ASR → intent/planner → approved tool → TTS and visual response.
- Push-to-talk remains the default until persistent wake mode is separately consented to and validated.
- KWS trigger audio is not reused as command audio.
- TTS: local Piper/VITS or a verified supported engine; fallback must be labelled.
- Future perception: OCR, images, video frames and contextual signals only when user initiated or explicitly enabled.

### G. Device and resource layer
Central device capability registry and resource governor. All expensive operations request a budget before starting. It monitors memory pressure, CPU, battery, temperature/thermal status, storage, network and foreground/background state.

### H. Distributed factory / companion layer
Phone is the autonomous core for private essentials. PC/server/remote agents are optional execution nodes for heavy compilation, research, model conversion and parallel specialist work. Exchange only task-scoped artifacts and approved context. Use signed/identified handoffs, checkpoints, provenance, explicit permissions and verifiable results. A remote node disappearing must not corrupt local state.

## 5. Making broad knowledge fit on a phone

Do not attempt to store an encyclopedia or every domain dictionary inside the APK or a single always-loaded model. Separate the **reasoning engine** from **retrievable knowledge**.

1. Keep the APK and always-resident core small: orchestration, policy, typed tools, UI, core language resources and compact indexes.
2. Store models as independently versioned optional packs outside the APK. Download only after explicit consent; support pause, resume, cancellation, integrity verification, atomic install and rollback.
3. Load one model/adapter at a time where practical. Unload inactive models and native sessions deterministically.
4. Prefer quantized ARM64 models with measured memory/latency tradeoffs. Do not assume a nominal parameter count guarantees fit.
5. Use domain packs and retrieval (RAG): compact metadata/indexes stay available; detailed documents and embeddings are loaded or queried on demand.
6. Shard dictionaries by language, domain and task. Load common Russian vocabulary first; fetch specialist terms only when needed. Cache with size limits and least-recently-used eviction.
7. Keep user data, model files, downloaded knowledge and temporary task artifacts in separate storage quotas.
8. Before inference, estimate peak memory including model weights, KV cache, audio buffers, embeddings, JNI/native allocations and UI. Refuse/defer work if the budget is unsafe.
9. Use streaming/chunked processing for long audio, documents and video; never concatenate unbounded input into RAM.
10. When the phone is hot, low on battery or under memory pressure, degrade gracefully: smaller context, fewer background tasks, lower frame rate, defer optional indexing, or ask before offloading private data.

Model size, vocabulary size and knowledge coverage must be measured separately. A growing knowledge base should not require rebuilding or inflating the APK.

## 6. Resource and thermal policy

A single governor arbitrates model loading, inference, capture, TTS, rendering, indexing and background synchronization.

Required states:
- **NORMAL:** normal foreground budgets.
- **WARM:** reduce concurrent work, background indexing and rendering load.
- **HOT:** stop optional/heavy inference, unload nonessential models when safe, reduce audio/visual workload and defer downloads.
- **CRITICAL:** immediately stop nonessential compute and active optional inference; preserve safe shutdown and user-visible controls. Do not fight Android/ColorOS thermal management.

Use hysteresis and cooldown to avoid rapid state oscillation. Every task has a deadline, memory ceiling, CPU/thermal class, cancellation path and recovery behavior. The app must not promise sustained always-on inference until measured battery and thermal tests on the actual OPPO device pass.

## 7. Knowledge domains and evidence model

The intended knowledge library is broad and modular, including:
- radio engineering, RF propagation, antennas, SDR, radio stations and communications;
- physics, mathematics, chemistry, energy and electronics;
- medicine and health information (careful sourcing, uncertainty, no diagnosis claims);
- astronomy and space science; astrology may be treated as a cultural/entertainment topic, never presented as established science;
- psychology and cognitive science;
- robotics, programming, computer science, cybernetics, AI and neural networks;
- networks: mesh, MANET, cellular, Wi-Fi, routers, modems and network security;
- navigation, geography, cartography and orientation;
- construction, gardening and horticulture;
- calculations, planning, lotteries and probability (clearly distinguish mathematics from prediction or guaranteed outcomes).

Every factual answer should carry an internal evidence profile: source, date, confidence, domain, assumptions, and whether it is retrieved knowledge, deterministic calculation, model inference or speculation. High-stakes domains need stricter source quality and careful escalation. Astrology, lottery guesses and speculative forecasts must never be described as scientific certainty.

## 8. Phone control and safety

Tools are divided into risk classes:
- **Read-only:** device status, time, battery, permitted local search.
- **Low-impact reversible:** open app, change a noncritical UI preference, start navigation.
- **Sensitive:** contacts, messages, calls, location sharing, personal files, camera/microphone.
- **High-impact:** purchases, payments, deletion, account/security changes, sending external messages, factory/system changes.

Require explicit confirmation appropriate to risk. Use ACTION_DIAL rather than placing a call silently. Never send a message or make a purchase merely because the model inferred intent. Verify package availability and actual action result; return truthful failure when an intent cannot be resolved.

## 9. Security, updates and privacy

- HTTPS only for model/knowledge downloads.
- SHA-256 or stronger integrity verification against trusted metadata; signed manifests where feasible.
- ZIP path traversal protection, size limits, content-type checks, timeouts, cancellation and atomic staging.
- Rollback to the last known-good model pack.
- No API keys or personal data in logs, repository docs or artifacts.
- Least-privilege Android permissions; request them just in time and explain why.
- No call audio capture, hidden recording or automatic post-interruption resume.
- Cloud requests disclose what leaves the device and require user approval for sensitive context.
- Keep auditable tool events while minimizing sensitive payload retention.

## 10. Current verified implementation status (2026-11-09)

This section reflects inspected GitHub evidence, not a claim that the product is complete.

- The accessible working repository is `sokfrut-tech/Gemeni`. A separate private GitLab repository named `sokfrut-tech/jarvis` has also been referenced in project history; its current state must not be inferred from Gemeni.
- Main-branch audit documented that `LocalLlmEngine` is an echo placeholder, the tool registry is incomplete, memory is simple key/value storage, and Accessibility/AIDL/VoiceInteraction paths contain no-op or incomplete behavior.
- PR #123: responsive Compose home and explicit Stop control; Draft.
- PR #124: serialized single-owner AudioCaptureCoordinator; Draft, stacked on #123.
- PR #126: injectable KWS/VAD/local-ASR pipeline and fail-closed interruption logic; Draft, stacked on #124. The exact-head Factory Security Baseline passed CodeQL and secret scanning; this is not Android runtime proof.
- PR #125: Android build/emulator CI; Draft. The debug APK build, JVM tests, native/security checks and manifest/ABI inspection passed, but emulator runtime smoke failed on both observed attempts. The emulator gate is not green.
- Source audit says the runtime still uses a bounded 4.5-second push-to-talk path; the KWS/VAD abstractions are not yet wired end-to-end into the runtime/UI.
- No release-ready APK or successful physical OPPO validation is established.
- minSdk 29 on the inspected main branch conflicts with the desired API 24+ goal and must be resolved deliberately.

## 11. Delivery roadmap — dependency ordered

### Phase 0 — repository and evidence integrity
- Keep the canonical target repository explicit; do not mix GitLab and GitHub histories.
- Reconcile open PRs by exact base/head SHA and dependency order.
- Fix emulator smoke root cause without weakening assertions.
- Keep all PRs Draft until exact-head build, tests, security and runtime evidence pass.

### Phase 1 — stable usable phone MVP
- Real visible Compose UI and Stop.
- One lifecycle-managed microphone owner; bounded push-to-talk first.
- Explicit model preparation and consent.
- Local Russian ASR → typed intent → a small set of real, verified Android actions → TTS.
- Robust cancellation, permission denial, no speech, model failure and app lifecycle tests.
- Thermal/memory governor baseline and diagnostic screen.

### Phase 2 — safe voice session
- Wire KWS/VAD into the single capture owner.
- Push-to-talk remains default; wake-word is explicit opt-in with visible active indicator and Stop.
- Prove Russian acoustic recognition on device; English-phoneme KWS configuration is not sufficient evidence for «Джарвис».
- Call/system-audio preemption and no automatic resume.

### Phase 3 — real local intelligence and memory
- Replace echo LocalLlmEngine with a real, measured local inference backend (llama.cpp/GGUF or another verified ARM64 runtime).
- Benchmark candidate Qwen sizes for peak RAM, token latency, battery and thermal behavior.
- Add consent-based bounded memory, retrieval, provenance and deletion/retention controls.
- Typed planner, task checkpoints and tool postcondition verification.

### Phase 4 — broader assistant skills
- Planner, calendar, tasks, alarms and timers.
- Web search/research and summaries when online.
- Navigation, local search, notes, messaging/calling with confirmation.
- Music/radio and media controls where APIs and licenses permit.
- OCR, translation, image/video understanding as optional modules.
- Domain knowledge packs and scientific source evaluation.

### Phase 5 — advanced agent and factory capabilities
- Skill authoring and schema validation.
- Multi-agent delegation to connected workers with truthful availability and evidence.
- PC/server offload and cross-device checkpoints.
- Controlled learning from user-approved corrections and test outcomes.
- Advanced robotics/IoT integrations only behind explicit device permissions and safety policy.

## 12. Definition of done

A feature is complete only when:
- its user-visible behavior is specified;
- the implementation is connected end-to-end;
- permission and risk boundaries are tested;
- success and failure states are truthful;
- cancellation and resource release are verified;
- unit and relevant integration/runtime tests pass on the exact commit;
- security and performance impact are measured where relevant;
- documentation and continuation state are updated.

A release additionally requires clean install, non-black UI, real microphone/ASR/action/TTS behavior, correct system integration, non-no-op IPC/Accessibility where applicable, visible native rendering if claimed, model update safety, ABI/manifest/security checks, emulator smoke and physical OPPO validation.

## 13. Immediate next engineering slice

Do not expand into dozens of new features yet. First close the critical gap between the existing voice abstractions and actual runtime:

1. Fix and understand the emulator smoke failure with preserved logcat and test artifacts.
2. Refactor `JarvisRuntimeController` to own one application-lifecycle capture coordinator rather than creating a bounded recorder per call.
3. Keep push-to-talk as the first integrated mode; feed bounded frames through a typed state machine and guarantee Stop waits for AudioRecord cleanup.
4. Add model preparation state and explicit consent boundaries; no automatic downloads on microphone start.
5. Wire one real local Russian ASR → typed intent → verified action → TTS path.
6. Add regression tests for repeated start/stop, cancellation, permission denial, blank ASR, exceptions, app backgrounding and thermal suspension.
7. Only after this vertical slice is green, connect KWS/VAD and expose optional wake-word mode.

Do not merge, publish or mark an APK release-ready without explicit authorization and exact-head evidence.

## 15. Dual-track continuation

The product is now explicitly organized into two cooperating development tracks: Jarvis Android Assistant and the reusable Jarvis AI Core. See [JARVIS_DUAL_TRACK_PRODUCT_AND_AI_CORE_ARCHITECTURE_2026-11-10.md](JARVIS_DUAL_TRACK_PRODUCT_AND_AI_CORE_ARCHITECTURE_2026-11-10.md) for the ownership boundary, pure-JVM core design, memory and model strategy, repository comparison, integration rules and next implementation slice. The Android app consumes the AI Core through stable contracts; the core does not depend on Android APIs.
