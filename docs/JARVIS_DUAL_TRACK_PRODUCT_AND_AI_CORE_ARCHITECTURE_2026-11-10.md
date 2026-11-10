# Jarvis dual-track product and AI Core architecture
Date: 2026-11-10
Status: proposed architecture baseline; documentation only
Primary repository: `sokfrut-tech/Gemeni`
Related master brief: [JARVIS_MASTER_PRODUCT_VISION_AND_ARCHITECTURE_2026-11-09.md](JARVIS_MASTER_PRODUCT_VISION_AND_ARCHITECTURE_2026-11-09.md)

## 1. Decision: two product tracks, one shared contract

Jarvis must be developed as two distinct but cooperating products:

- **Track A — Jarvis Android Assistant:** the OPPO/ColorOS product and its user experience.
- **Track B — Jarvis AI Core:** the reusable intelligence runtime that reasons, plans, retrieves knowledge, manages memory, selects tools, evaluates results and improves through controlled feedback.

These are separate product and engineering tracks, not two copies of the same application and not two independent brains that drift apart. Track A consumes Track B through stable, versioned contracts. Track B must not depend on Android UI, Android Context, Activity, AudioRecord, Compose or ColorOS classes.

The first implementation should remain in the existing Gemeni repository as a pure Kotlin/JVM `:ai-core` module (or a clearly isolated pure-JVM package until the Gradle module boundary is proven). This keeps the integration and test loop short. Extracting a separate `Jarvis-AI-Core` repository is a later option after the public contracts and first vertical slice are stable. Do not create a second application or a mandatory cloud server now.

## 2. Product A — Jarvis Android Assistant

### Mission

Deliver a reliable, attractive, Russian-first personal assistant for OPPO Find X6 Pro / PGEM10 and Chinese ColorOS. It must remain useful for essential tasks offline and preserve the phone's normal operation and user authority.

### Owns

- Compose interface, orbital/core visualizer, cards, transcript, settings, onboarding and visible runtime status.
- Microphone permission, user-started push-to-talk, optional explicitly enabled wake-word session, and visible Stop.
- AudioRecord ownership, KWS, VAD, local ASR and TTS adapters.
- Android VoiceInteraction, notification/foreground-service and ColorOS integration.
- Android intents and narrowly scoped Accessibility operations.
- Device capability and thermal/battery/resource adapters.
- User-facing permission/approval dialogs and truthful action results.
- Installation and lifecycle of local Android model packs.

### Does not own

- The canonical agent planning algorithm.
- Persistent knowledge semantics or cross-device memory rules.
- Provider-specific reasoning logic.
- Tool policy decisions implemented only in prompts.
- A second, Android-specific copy of the model router or skill engine.

Track A translates Android events into core requests and executes approved core tool calls through platform adapters. The platform adapter reports evidence back to the core; the core does not assume that an Android intent or Accessibility action succeeded.

### Immediate Track A gates

1. Preserve the PR #123 → #124 → #126 dependency chain: responsive screen/Stop, one serialized microphone owner, and testable KWS/VAD/ASR lifecycle.
2. Keep PR #125's build/emulator workflow independent from the audio feature branch; a successful APK build is not a successful runtime test.
3. Wire one end-to-end audio path through the single capture owner. Push-to-talk remains the default; wake-word is explicit opt-in with a visible active state and Stop.
4. Prove Russian «Джарвис» acoustic recognition, call/system-audio interruption, resource release and thermal behavior on the actual OPPO device.
5. Route recognized speech through the same typed proposal and policy gateway used by typed text. Do not create a weaker voice-only execution route.

## 3. Product B — Jarvis AI Core

### Mission

Build an independent, model-agnostic, local-first intelligence runtime. It should be able to serve the Android assistant first and later desktop, PC, local server, CLI or other clients without rewriting its cognitive logic.

The goal is not an enormous model stored inside the APK. The goal is a small, dependable runtime that can use different local models, optional remote models, structured memory and independently installable knowledge packs.

### Responsibilities

1. Understand a request and determine its task type, constraints and ambiguity.
2. Retrieve only relevant memories and knowledge, with source and trust metadata.
3. Choose a suitable reasoning provider based on capability, privacy and device/resource budget.
4. Produce a bounded, typed plan; the model proposes, it never directly executes platform actions.
5. Validate the plan against tool schemas, permissions, risk and data-flow policy.
6. Request human approval when necessary and bind that approval to the exact target and payload.
7. Execute through an authorized tool gateway with one task ID, one deadline and cancellation propagation.
8. Verify postconditions and distinguish attempted, accepted, completed, verified, failed and cancelled outcomes.
9. Persist safe checkpoints and approved memories with provenance and retention rules.
10. Evaluate outcomes and propose improvements without silently changing its own executable code or permissions.

### Core implementation boundary

The AI Core must be usable from plain JVM tests without Android SDK, emulator, microphone, network or native model files. Core contracts should be deterministic and injectable. Android-specific APIs live in adapters in the app layer.

Initial contract families:

- `InferenceProvider`: local or optional remote text-generation providers; no tool execution.
- `ModelRouter`: task capability, privacy, offline state, measured quality, latency, RAM, battery and thermal constraints.
- `TaskOrchestrator`: request → context → plan → policy → execution → verification → response.
- `MemoryStore`: typed records, scope, source, confidence, expiry, supersession, correction and deletion.
- `KnowledgeRetriever`: source-attributed passages and domain-pack metadata.
- `ToolCatalog` / `ToolGateway`: allowlisted, schema-validated tool calls and structured results.
- `SkillRegistry`: versioned declarative skills with capability and verification requirements.
- `PolicyEngine`: risk checks, approval requirements and provenance/taint data-flow rules.
- `ExecutionJournal`: durable task states, checkpoint references, idempotency keys and audit metadata.
- `ResourceBudget`: monotonic deadline, cancellation, token/output limits, memory/CPU/thermal budget.
- `TraceRecorder` / `Evaluator`: redacted outcome metadata, quality and resource measurements.

Do not prematurely split every family into a separate Gradle module. Start with one pure-JVM module and package-level boundaries; split only when a real dependency or independent test/release need justifies it.

## 4. Cognitive loop: model proposes, deterministic runtime governs

Canonical flow:

```text
User / Android / PC / CLI
        |
        v
RequestEnvelope (locale, goal, constraints, provenance, sensitivity)
        |
        v
ContextAssembler ----> bounded working state
        |              approved memory / knowledge retrieval
        v
ModelRouter ----> deterministic rules | local LLM | consented remote LLM
        |
        v
Planner -> strict typed PlanProposal (no Android Context, no executable code)
        |
        v
PolicyEngine -> schema / capabilities / risk / data-flow / budget
        |
        +---- requires approval? ----> exact action preview + explicit approval
        |                                      |
        v                                      v
ExecutionJournal -> authorized ToolGateway / platform adapter
        |
        v
Postcondition verifier -> VERIFIED / UNVERIFIED / FAILED / PARTIAL
        |
        v
Response composer -> user-visible explanation
        |
        v
Memory policy + redacted trace + optional user feedback
```

A model response is untrusted data, including a response from a local model. It must be parsed by a closed schema. Unknown tools, unknown risk classes, extra fields, malformed values and missing policy rules are rejected. Retrieved web pages, OCR, notifications, files, accessibility labels and tool output are data, not instructions that can change system policy or grant permissions.

## 5. Memory: separate state, history and knowledge

Do not call every stored string "memory". Use separate stores and lifecycles:

| Store | Purpose | Default lifetime / policy |
|---|---|---|
| Working state | Variables and partial results for the current task | Task-scoped; cleared or checkpointed on completion |
| Conversation context | Recent dialogue needed for continuity | Bounded by time and token/byte budget |
| Episodic memory | What happened: tasks, actions, corrections and outcomes | Local, redacted, retention-controlled |
| Semantic memory | Approved facts and preferences with provenance | Explicit consent, editable and deletable |
| Procedural memory | Reviewed skills and workflows | Versioned; disabled until explicitly enabled |
| Knowledge corpus | External/domain documents with citations and dates | Independently versioned packs; source and license recorded |
| Runtime checkpoints | Where a long-running execution stopped and how it can resume | Separate from user knowledge; idempotent recovery rules |

Initial storage should favour app-private SQLite with migrations and full-text search (FTS) behind a `MemoryStore` interface. Add embeddings, hybrid retrieval or graph indexes only when a Russian evaluation set demonstrates a measurable recall improvement worth their storage, RAM and battery cost.

Every memory record should be able to represent: ID, type, scope, content/reference, source, provenance, confidence, sensitivity, created/updated time, expiry, supersedes relation and user approval state. The user must be able to inspect, correct, export and delete records. Sensitive categories are never silently promoted into permanent memory.

Runtime checkpoints are not the same thing as knowledge memory: a task must be resumable after a crash without turning every internal execution detail into a permanent user fact.

## 6. Knowledge and model packs: broad capability without a huge APK

Keep reasoning separate from knowledge:

- The APK/Core package contains orchestration, schemas, policies, compact indexes and essential Russian resources.
- LLM, ASR, VAD, KWS and TTS weights are separately versioned optional packs, not bundled indiscriminately into the APK.
- Domain knowledge is stored in independent packs with metadata, source URLs, dates, licenses, checksums and a compact index.
- Load detailed content on demand. Keep hot metadata/indexes available and use bounded caches with an LRU or equivalent eviction policy.
- Stream large files and retrieval results; never concatenate unbounded corpora into memory.
- Reserve storage quotas separately for model weights, knowledge packs, user memory and temporary task artifacts.
- Downloads require clear consent, HTTPS validation, size limits, integrity checks, cancellation, atomic installation and rollback.
- Unload native models deterministically and measure peak memory including weights, KV cache, JNI buffers, ASR/VAD and UI.

Intended domains can be extensive (radio/RF, physics, mathematics, chemistry, electronics, medicine, astronomy, psychology, robotics, AI, computer science, networks, geography, navigation, construction, gardening and others). This does not mean placing all dictionaries or facts into a model or APK. Domain packs, retrieval and provenance provide breadth without requiring the phone to load everything at once.

A language model does not become a trustworthy specialist merely because a domain pack exists. Answers need source attribution, date, confidence, assumptions and clear separation between retrieved evidence, deterministic calculation, model inference and speculation.

## 7. Inference and model routing

Use provider-neutral contracts. Start with a deterministic Russian command path for basic critical operations, then a real local LLM for broader planning. A remote provider is an optional adapter, never a prerequisite for essential offline tasks and never an automatic destination for private context.

Candidate local path already present in unmerged work:

- PR #92 contains a pinned llama.cpp JNI implementation, GGUF loading, bounded generation, cancellation/unload and a SHA-256-verified Qwen 0.8B Q4_0 installation candidate.
- PR #78 contains the safer provider → typed proposal → validation → Android gateway seam.
- Their existence on feature branches is not proof that the code is integrated into `main`, that the model has been installed, or that Russian inference works on OPPO.

Treat Qwen 0.8B Q4_0 as a first benchmark candidate, not a permanent architectural assumption. Benchmark candidate sizes/quantizations on the target device for cold-load time, time-to-first-token, tokens/sec, peak RAM, battery, thermal behavior, Russian instruction following and structured-output validity. CPU-only is the conservative baseline; GPU/NPU offload is opt-in only after measured stability.

The router should use task category, required capability, offline availability, privacy label, quality history, latency target, model footprint, current RAM, battery and thermal state. A larger model is not automatically better. If the budget is exceeded, use a smaller model, deterministic fallback, defer the task or ask before offloading approved data.

## 8. Tools, skills, execution and trust

### Tool contract

Each tool declares: stable ID/version, input/output schema, required capabilities, permission, risk tier, time limit, side-effect class, idempotency support and postcondition verification method.

### Execution lifecycle

Recommended states:

`CREATED → CONTEXT_READY → PROPOSED → VALIDATED → AWAITING_APPROVAL → RUNNING → VERIFYING → VERIFIED`

Terminal alternatives include `FAILED`, `CANCELLED`, `PARTIAL` and `UNVERIFIED`. Approval is skipped only when policy explicitly permits the exact low-risk action.

Every execution has a unique ID and one non-renewable monotonic deadline. Model calls, tools, retries, recovery and finalization all consume the same budget. Cancellation propagates to model generation, active HTTP requests, audio capture and tools. Polling or refreshing a task reads the existing execution; it must never repeat an external side effect.

Sensitive or irreversible actions require fresh explicit approval tied to the normalized target and payload hash. Editing the recipient, destination, amount or message invalidates the approval. Unknown tools and unknown policy sinks default to deny.

### Provenance and data-flow policy

Track where information came from (user, device, screen/OCR, notification, file, web, memory, model or tool) and whether it is public, personal, PII, secret or user-private. Before sending information to a cloud provider, web service, external message, log or file export, apply deterministic policy. Pattern scanners may add warnings but are not authorization controls.

### Skills

Skills are versioned declarative manifests, not arbitrary code. A manifest declares inputs, outputs, tool IDs, capabilities, risk, time budget, provenance, verification and rollback. Imported or generated skills are disabled drafts until reviewed and explicitly enabled. A skill cannot grant itself permission.

## 9. Controlled learning and self-development

"Self-development" means measurable, reversible improvement—not uncontrolled self-modification.

Approved improvement loop:

1. Record redacted metadata: task category, selected provider/model, latency, resource state, tool outcome, verification status and explicit user feedback. Do not record raw audio, screenshots, secrets or full private content by default.
2. Detect repeated failure or a quality opportunity with enough evidence.
3. Create a candidate change: parser rule, prompt, routing threshold, knowledge correction or skill draft.
4. Run regression, adversarial, privacy, performance and compatibility tests in an isolated branch/worktree.
5. Produce a reviewable evidence bundle and rollback plan.
6. Require owner approval before activating the change or updating the installed app.

Initially, do not train model weights on the phone. Start with explainable routing statistics, reviewed knowledge updates and user-approved corrections. A repeated behaviour is a suggestion, not authorization to perform it again.

## 10. External project comparison and adopted decisions

This comparison extends the existing research in PRs #119–#121. It is not a claim that every public repository has been exhaustively audited.

| Project | Strong pattern worth adapting | What Jarvis should not copy blindly |
|---|---|---|
| [tokenarc/open-jarvis](https://github.com/tokenarc/open-jarvis) | Android task routing, finite action plans, app capability model, graph-shaped memory, declarative skills and MCP | Broad Accessibility collection, heuristic-only injection filtering, unverified success claims or autonomous screen tapping |
| [open-jarvis/OpenJarvis](https://github.com/open-jarvis/OpenJarvis) | Separate Intelligence / Engine / Agents / Memory / Learning primitives; trace-driven model routing and explicit energy/latency/quality evaluation | A server/Python research stack transplanted wholesale into the Android process |
| [leon-ai/leon](https://github.com/leon-ai/leon) | Layered memory, structured skill/tool schemas, shared run deadline, cancellation, owned execution IDs and bounded proactive suggestions | Desktop/server scheduling semantics assumed to work under ColorOS background restrictions |
| [Bwarhness/jarvis-assistant](https://github.com/Bwarhness/jarvis-assistant) | Keep the assistant client thin around a self-hosted model endpoint; isolate wake-word, STT/TTS and VoiceInteraction adapters | Mandatory network/server dependence or paid speech dependencies |
| [Thrash-PRX/Jarvis-for-Android](https://github.com/Thrash-PRX/Jarvis-for-Android) | Typed tools, explicit risk policy, local-first UI and honest platform capability boundaries | Treating interface scaffolding as proof that an offline wake-word model or physical-device feature is complete |
| GitLab public examples (for example [xjarvis](https://gitlab.com/xjarvis) and the [Android Jarvis topic](https://gitlab.com/explore/projects/topics/android)) | Lightweight voice-to-command and device-integration experiments | These surfaced examples are not equivalent to a mature, local-first reasoning core |

The direct source-level audits already stored in PR #120 and PR #121 found important weaknesses in the Android Open Jarvis candidate: narrow risk classification, regex-only injection heuristics, gaps in action postcondition verification, broad Accessibility event access and incomplete memory consent/retention. Treat those reports as implementation review evidence, not just repository feature lists.

The Stanford OpenJarvis and Leon analysis in PR #121 supports the core separation: model/runtime/agent/memory/learning should have explicit boundaries; task checkpoints differ from user memory; execution IDs and a shared deadline protect side effects; energy, latency, quality and privacy belong in routing/evaluation.

GitLab limitation: the public search was useful for discovering public examples. The separately referenced private project `sokfrut-tech/jarvis` was not accessible through the current GitHub connection or public web fetch. Its source, branches and merge history remain unverified. Do not infer its state from Gemeni.

## 11. Repository and branch strategy

Current inspected GitHub state on 2026-11-10:

- `sokfrut-tech/Gemeni` is the active public GitHub product repository; its default branch is `main`.
- PR #131 is the existing draft master architecture document. This dual-track proposal belongs in that same documentation review rather than opening another duplicate research PR.
- PRs #119–#121 already hold comparative and source-level research.
- PR #123 → #124 → #126 is the current UI/audio ownership/KWS-VAD dependency chain.
- PR #125 is a separate Android build/emulator CI path.
- PR #78 holds the safe model-proposal/tool-gateway direction.
- PR #92 holds the llama.cpp/Qwen local inference implementation spike.
- PR #88 and #82 contain memory-consent and local-learning prototypes that must be reviewed before replacing or duplicating them.

The connected GitHub listing returned 59 open PRs authored by the repository owner. This is a coordination risk. Avoid creating one PR per small idea. Before integrating, refresh exact base/head SHAs, map stacked dependencies and preserve useful work. Do not merge PRs based on titles, stale summaries or green checks from a different head.

### Working ownership

- Track A implementation owner: Android app adapters, audio, UI, ColorOS integration and physical-device evidence.
- Track B implementation owner: pure-JVM core contracts, orchestration, memory semantics, model routing, policy and evaluation.
- Integration owner: one coordinator reconciles the shared API, checks dependency direction and runs fresh tests on the integrated commit.

Do not let Track A and Track B edit the same shared contract simultaneously. Agree the contract first, then implement behind it.

## 12. Dependency-ordered delivery plan

### Track A — phone product

A1. Finish one stable, visible push-to-talk path with one capture owner, explicit Stop, bounded model preparation and real Russian ASR/TTS.
A2. Integrate the tested KWS/VAD lifecycle into the same capture owner; keep wake-word opt-in and prove interruption handling.
A3. Finish the typed Android gateway and real verified low-risk actions; add explicit approval for sensitive actions.
A4. Prove the UI and native renderer are non-black and state-driven; verify VoiceInteraction and ColorOS routing on OPPO.
A5. Add optional phone capabilities only through the same policy/tool interface.

### Track B — independent intelligence

B0. Add the pure-JVM `:ai-core` boundary and contract tests. First deliver a deterministic end-to-end task loop with fake model/tool adapters.
B1. Implement task lifecycle, one execution ID, non-renewable deadline, cancellation, idempotency, typed plan validation and postcondition results.
B2. Connect the existing provider-neutral seam and verified llama.cpp/Qwen candidate through the gateway; test offline inference and strict structured output.
B3. Add typed local memory with consent, provenance, FTS retrieval, correction, expiry, export and deletion.
B4. Add versioned declarative skills and one independently installable knowledge pack; benchmark retrieval before adding embeddings/graph storage.
B5. Add redacted trace evaluation, confidence/sample thresholds and human-approved improvement proposals.
B6. Add optional remote workers/MCP/PC offload only after local task execution, trust boundaries and recovery are reliable.

### Shared integration gates

- Android code depends on AI Core interfaces; AI Core never imports Android types.
- Model output cannot call tools directly.
- Unknown tool/risk/data-sink policy is default-deny.
- Every external side effect is attributable to one execution ID and is verified or reported unverified.
- A task's retries share the same deadline and cancellation scope.
- All stored memory has a scope, source and retention/deletion rule.
- Offline core tests do not require a network provider or model weights.
- The installed app remains usable when optional models, knowledge packs, cloud services or companion computers are unavailable.

## 13. Definition of Done for the two-track model

The dual-track design is accepted only when:

1. The two responsibilities and dependency direction are visible in the repository.
2. A plain-JVM test can instantiate the AI Core without Android.
3. An Android request can call the same core contract used by a future PC/CLI client.
4. The core can complete a deterministic task using a fake provider and a fake verified tool.
5. A real local-model proposal passes the same validator/policy/gateway path.
6. The task ledger distinguishes completion from verified completion and never repeats side effects on status reads.
7. Memory is typed, scoped, sourced and user-controllable.
8. A resource governor can refuse/defer work without corrupting the active task.
9. Every improvement is reviewable, tested, owner-approved and reversible.

## 14. Immediate next engineering slice

Do not begin by adding a new large model or by broadening the Android UI. Start Track B0/B1 as a thin vertical slice:

- Create `:ai-core` with no Android dependencies.
- Define `JarvisRequest`, `PlanProposal`, `ToolDefinition`, `ToolExecution`, `ExecutionResult` and `TaskBudget`.
- Implement a deterministic provider and one fake/read-only tool.
- Run request → plan → policy → execute → verify using JVM tests only.
- Connect the Android app only after the contracts pass.
- In parallel, Track A continues the single-owner audio work without depending on the AI Core implementation details.

No application source, permissions, native model weights, secrets, deployment settings, APK release or merge are changed or authorized by this architecture document.
