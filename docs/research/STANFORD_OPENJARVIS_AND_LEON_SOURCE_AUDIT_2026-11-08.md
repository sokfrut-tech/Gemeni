# Stanford OpenJarvis + Leon 2.0 source audit for Gemeni/Jarvis — 2026-11-08

## Scope

Second targeted source-level research pass. Repositories:
- https://github.com/open-jarvis/OpenJarvis (main)
- https://github.com/leon-ai/leon (develop, Leon 2.0 developer preview)

This is not an exhaustive audit of either repository, not a full GitHub survey, and not a security certification. Selected architecture docs and source files were read directly. No third-party source code was copied into Gemeni. The two projects have different deployment targets: Stanford OpenJarvis is a broad local-AI research framework, while Leon is a desktop/server-oriented assistant with Node/Python runtimes. Their architectural ideas may be adapted, but neither can be transplanted wholesale into an Android/ColorOS app.

Leon explicitly describes develop as a 2.0 Developer Preview and says the new docs are incomplete; its current core context documents and source should be treated as the reference rather than older documentation.

## Executive findings

The most valuable new ideas for Jarvis are:

1. **Trace-driven improvement with measurable outcomes** (Stanford OpenJarvis): record task route, model/provider, tool steps, result, latency, token/energy cost and user feedback; use conservative statistics to improve routing only after enough evidence.
2. **Data provenance and taint labels** (Stanford OpenJarvis): track whether information is external, private, PII, or secret, and restrict where that information may flow.
3. **One shared, non-renewable execution budget** (Leon): all model calls, tool calls, retries, recovery, and finalization consume the same deadline; cancellation must propagate.
4. **Owned tool-execution handles** (Leon): an execution has an ID, owner/session, progress, result, cancellation, retention limits and explicit state; polling must not reissue the action.
5. **Layered memory with source, confidence, scope, expiry and supersession** (Leon): persistent, daily, discussion and temporary memory should be distinct; retrieval is bounded by relevance and token budget.
6. **Structured skills and explicit capability boundaries** (Leon): schema validation, enabled/disabled/allow-only controls, typed action and tool contracts.
7. **Bounded proactive suggestions** (Leon): suggestions should have deduplication fingerprints, cooldowns, decline suppression, explicit accept/decline feedback and hard limits.
8. **Energy, latency and quality as first-class routing criteria** (Stanford OpenJarvis): the best model is not necessarily the largest; select by task class, device capacity, temperature, battery, measured quality and privacy.

## A. Stanford OpenJarvis — source-level observations

### A1. Five-primitive architecture and trace feedback

Source/docs:
- `docs/architecture/overview.md`
- `docs/architecture/learning.md`
- `rust/crates/openjarvis-traces/src/analyzer.rs`
- `rust/crates/openjarvis-learning/src/trace_policy.rs`

The framework separates Intelligence (model catalogue), Engine (inference runtime), Agents (execution strategies), Memory (retrieval/storage), and Learning (routing/feedback). Trace records connect the primitives.

The trace analyzer groups outcomes by agent and model and computes sample count, success/failure, average latency, token use and success rate. The trace-driven policy groups queries into broad categories and only uses a learned mapping after a minimum sample count (the inspected Rust policy defaults to five samples).

**Useful for Jarvis**
- Build a local, privacy-preserving trace record for every task: task category, selected local model/engine, thermal state, battery band, latency, token count, tool/action result, verification result, user correction/feedback.
- Separate attempted, accepted, verified, failed and cancelled outcomes.
- Require a minimum sample size and confidence threshold before changing model routing.
- Keep a safe deterministic fallback when data is sparse or model/provider health is unknown.

**Limitations to avoid**
- The inspected query classifier is broad (code/math/short/long/general), not a Russian semantic classifier.
- A sample-count threshold alone does not ensure unbiased data or statistically reliable improvement.
- Do not train or update model weights on the phone in the first release. Begin with explainable routing statistics and offline evaluation.
- User-private content should not be copied into traces by default; record metadata and redacted summaries.

### A2. Security: scanners, taint and information flow

Source/docs:
- `docs/architecture/security.md`
- `rust/crates/openjarvis-security/src/injection.rs`
- `rust/crates/openjarvis-security/src/taint.rs`

The security subsystem separates scanners from the inference engine and supports warn/redact/block modes. The taint module attaches labels such as PII, Secret, UserPrivate and External, propagates labels through derived text, and checks whether a tool sink accepts those labels.

**Useful for Jarvis**
- Add provenance/trust labels to content from screen OCR, Accessibility nodes, webpages, notifications, files, user speech and local memory.
- Track sensitive labels for credentials, personal data, and user-private content.
- At each outbound boundary (cloud inference, web search, message sending, logging, file export), apply a deterministic data-flow policy.
- Use explicit allowlists for destinations and data categories; if a policy is unknown, deny the transfer rather than silently allowing it.
- Keep scanner output as a warning signal, not the sole authorization mechanism.

**Observed limitations in inspected implementation**
- The injection scanner is a regex/pattern detector. It is useful for telemetry and known-pattern detection, but cannot prove text is safe or detect every indirect/multilingual attack.
- The inspected taint sink policy is a small static map. `check_taint` returns no violation when a tool name has no policy entry; Jarvis must not inherit this permissive unknown-tool behavior.
- Automatic PII/secret detection is pattern-based and language/format limited. Labels must also come from source provenance and explicit user choices, not regex alone.
- The security docs state streamed model output is scanned after the stream completes; therefore BLOCK mode cannot retract tokens already delivered. Jarvis must buffer sensitive outbound content until the relevant checks pass, or use a safe streaming policy that never streams untrusted/private material to an unauthorized sink.

### A3. Model/engine routing and hardware budget

Source/docs:
- `docs/architecture/overview.md`
- `docs/architecture/design-principles.md`
- OpenJarvis engine and learning abstractions.

The architecture allows multiple inference backends behind one interface and treats local engines as primary while cloud providers are optional. The wider project describes energy, latency, FLOPs and monetary cost as evaluation dimensions.

**Useful for Jarvis**
- Define a Kotlin `InferenceEngine` contract for local ASR/NLU/LLM and optional remote providers.
- A routing decision should include capability, offline availability, thermal budget, battery budget, memory footprint, latency target, privacy classification and measured quality.
- Remote fallback is never automatic for sensitive tasks; require explicit consent when content would leave the device.
- Keep model metadata (quantization, RAM estimate, supported languages, context limit, warm-up cost, thermal class) separate from provider implementation.

### A4. Memory retrieval and knowledge graph

The OpenJarvis architecture supports lexical, sparse/dense and hybrid retrieval, with a knowledge-graph storage option in its Rust tools crate.

**Useful for Jarvis**
- Start with a dependable local SQLite store and full-text retrieval.
- Add embeddings/hybrid retrieval only after a measurable quality/performance test on OPPO.
- Represent entities and relationships as typed records; do not equate a graph database with intelligence.
- Return source attribution and confidence with each recalled item.

## B. Leon 2.0 — source-level observations

### B1. Layered memory and correction lifecycle

Source:
- `server/src/core/memory-manager/types.ts`
- `server/src/core/memory-manager/memory-repository.ts`
- `server/src/core/memory-manager/memory-manager.ts`

Leon models memory scopes (persistent, daily, discussion, temporary), kinds (fact, preference, event, note, summary, knowledge, task), and source types (explicit_user, inferred, tool_output, conversation, system). Records include confidence, importance, source reference, timestamps, expiry, pinning and metadata. The memory manager uses bounded recall and a constrained extraction schema; it also has supersession/forgetting concepts and retention maintenance.

**Useful for Jarvis**
- Replace the current key/value-only memory with typed records over time, not in one risky rewrite.
- Distinguish explicit user statements from inferred or tool-derived content.
- Add confidence, source reference, created/updated times, expiry and supersedes relation.
- Keep current-task working memory separate from durable user memory.
- Provide correction and deletion flows; stale/contradictory facts should not silently coexist.
- Retrieval must be query-specific, bounded, and provenance-preserving.

**Privacy requirements**
- No silent extraction of sensitive personal data into long-term memory.
- Explicit consent for sensitive categories and learning from screen/app activity.
- Local storage encryption and backup policy must be designed separately.
- The user must be able to inspect, correct, export and delete stored items.

### B2. Shared run budget and cancellation

Source:
- `server/src/core/llm-manager/llm-duties/react-llm-duty/agent-run-budget.ts`

Leon’s `AgentRunBudget` uses a single non-renewable deadline, an AbortSignal, a finishing reserve and timeout capping. It distinguishes caller deadline from provider stalls and cancellation.

**Useful for Jarvis**
- Introduce a task-scoped monotonic deadline and cancellation token.
- Model inference, tool execution, retry, recovery and final response all consume the same budget.
- Reserve time for safe cleanup and a user-facing result.
- Never let each retry reset or extend the total budget.
- On cancellation, stop work, release resources, persist a safe checkpoint and report what did/did not complete.

**Android adaptation**
- Use Kotlin coroutine Job/withTimeout and a monotonic clock; do not port Node.js timers literally.
- Audio capture cancellation must release AudioRecord in finally.
- A deadline cannot override emergency-call/system-audio interruption; those interruptions cancel/suspend the task immediately.

### B3. Owned tool execution and no duplicate side effects

Source:
- `server/src/core/tool-manager/tool-execution-manager.ts`

The manager creates an execution ID, binds it to profile/session ownership, tracks progress and state, propagates cancellation, retains results temporarily, and enforces per-profile active/retained execution limits. Reading an execution result is separate from issuing the tool again.

**Useful for Jarvis**
- Every action gets a unique execution ID and idempotency key where supported.
- State machine: CREATED → VALIDATED → AWAITING_CONFIRMATION (if required) → RUNNING → SUCCEEDED/FAILED/CANCELLED → VERIFIED/UNVERIFIED.
- A UI refresh or status poll must read the existing execution; it must never replay a call, send a second message, dial again, or repeat a purchase.
- Bind execution to user session, exact payload hash and target app/recipient.
- Bound active jobs, queue depth, retention and progress event volume.

### B4. Skills and tools as explicit contracts

Source:
- `server/src/schemas/skill-schemas.ts`
- `server/src/schemas/tool-schemas.ts`
- `server/src/core/skill-tool-bridge.ts`
- `server/src/core/tool-manager/tool-executor.ts`
- `bridges/runtime/contracts/skill-tool-protocol.ts`

Leon uses structured schemas and separates skills, actions, tools and runtime functions. It also exposes explicit enable/disable/allow-only commands for skills and tools.

**Useful for Jarvis**
- A skill manifest should declare input schema, tool IDs, required capabilities, risk class, confirmation requirement, time budget, output schema, verification and provenance.
- Unknown fields/actions should fail validation.
- A skill is not allowed to grant itself permissions; user-approved capability grants live outside the skill file.
- Disabled-by-default imported skills; no auto-enabling generated workflows.
- Allow-only mode is valuable for a locked-down release profile.

### B5. Bounded proactive pulse

Source:
- `server/src/core/pulse-manager.ts`

Leon’s pulse system stores pending/completed/declined/suppressed matters, fingerprints, confidence, source labels, owner feedback, cooldowns, suppression policies, maximum pending counts, and recent tick history. It includes cooldowns after completion/failure and longer suppression after repeated declines.

**Useful for Jarvis**
- Proactive assistant behavior should start as suggestions, not actions.
- Deduplicate suggestions by fingerprint and topic; use explicit accept/decline feedback.
- Respect cooldown after completion and stronger suppression after declines.
- Cap pending suggestions and daily/thermal/battery work.
- Suppress proactive inference during calls, active user tasks, high thermal state, low battery or user-defined quiet time.
- No unsolicited continuous background inference in the first release.

**Limitations**
- Leon’s pulse is a desktop/server feature, not evidence that Android background execution is reliable.
- Android/ColorOS process lifecycle and battery policy need native testing; do not port the scheduler directly.

## C. Direct comparison with Gemeni/Jarvis main

The inspected Gemeni default branch is still an early foundation. On main at base SHA `43ddc89850637fad045924e43cbbbe6a5719d961`:

- `JarvisToolRegistry.kt` is a minimal dispatcher: recognizes a time request and otherwise echoes the text.
- `JarvisMemoryRepository.kt` is SharedPreferences key/value storage.
- `JarvisRuntimeController.kt` provides a basic microphone → local Russian ASR → intent dispatch → basic Android intent/TTS path.
- `AudioRecordPipeline.kt` creates an AudioRecord inside a cold Flow collection and releases it in `finally`; this is a useful cleanup baseline, but it does not itself guarantee one global owner or coordinate competing Android audio users.
- `AndroidManifest.xml` includes RECORD_AUDIO, INTERNET, VoiceInteractionService, SessionService and AccessibilityService declarations.

Separate feature branches/PRs contain newer work on KWS, thermal policy and interruption states, but those changes are not all merged into main. Do not conflate a branch prototype or state machine with a complete integrated Android feature.

This means the research priorities should be sequenced: stabilize runtime and audio ownership first; then tool safety and execution tracking; then memory and skills; only then proactive behavior and adaptive routing.

## D. Jarvis implementation roadmap

### P0 — Runtime safety (must precede always-on)
1. Single global microphone owner and one AudioRecord Job.
2. Native Android interruption adapter: call, emergency call, system audio capture, privacy toggle, permission loss, device route changes.
3. Stop and release audio immediately on interruption; no call audio to KWS/VAD/ASR/logs.
4. No automatic resume after calls unless explicit listening consent remains active and all availability checks pass.
5. Apply thermal and battery budgets to KWS, VAD, ASR, TTS and inference.

### P1 — Deterministic action runtime
1. Typed tool registry and strict JSON/schema validation.
2. Execution ID, exact payload hash, session ownership, bounded queue and idempotency.
3. Shared task deadline/cancellation budget.
4. Confirmation bound to exact target and payload for external/destructive/financial/security actions.
5. Separate attempted/accepted/completed/verified states; no false success messages.
6. Default-deny unknown tool IDs and unknown risk classes.

### P2 — Privacy and trust flow
1. Provenance labels: user, device, screen, notification, file, web, memory, model output, tool output.
2. Sensitivity labels: public, personal, PII, secret, user-private.
3. Deterministic sink policy for cloud, web, messaging, logging and file export.
4. Unknown sink policy means deny.
5. Redaction and adversarial tests; scanner regexes are auxiliary only.

### P3 — Memory and skills
1. Typed SQLite memory with migrations and full-text search.
2. Explicit/inferred/tool-derived source distinction, confidence, expiry and supersession.
3. Separate task memory and durable memory.
4. Strict skill manifest with declared capabilities, risk, confirmation and verification.
5. User correction, export, deletion and consent controls.
6. No automatic skill activation from generated traces.

### P4 — Evaluation and bounded learning
1. Local trace schema with no raw audio/screenshots/secrets by default.
2. Quality, verification rate, latency, energy, thermal impact, battery and memory usage.
3. Minimum samples, confidence and regression checks before changing routing.
4. Offline evaluation set in Russian.
5. No online weight training on OPPO in initial releases.

### P5 — Proactive suggestions
1. Suggestions only; no automatic side effects.
2. Fingerprint deduplication, user feedback, cooldown and decline suppression.
3. Quiet hours, active-call/active-task suppression, thermal and battery limits.
4. Explicit master enable/disable switch and transparent activity log.

## E. Required tests

- Cancellation reaches every model/tool/audio operation; deadline is not renewed by retries.
- Status polling never repeats a side effect.
- Repeated action request with the same idempotency key cannot send duplicate messages or payments.
- Unknown tool/sink/risk policy is denied.
- Taint labels propagate through retrieved memory and model-produced plans.
- Private/secret content cannot flow to web/cloud/logging without explicit authorization.
- Streaming output cannot leak content before required outbound checks.
- Memory retrieval obeys scope, expiry, provenance, confidence, deletion and supersession.
- Generated/imported skills remain disabled until reviewed and enabled.
- Declined proactive suggestions are suppressed according to policy.
- Proactive work is paused during calls, high thermal load and low battery.
- Android-specific tests on OPPO Find X6 Pro validate audio interruption, ColorOS process limits, thermal behavior and background work.

## Final recommendation

The next strong Jarvis milestone is not adding more autonomous features. It is a **safe task runtime** combining:
- single-owner audio,
- shared deadlines and cancellation,
- typed tools and owned execution IDs,
- postcondition verification,
- provenance/taint policy,
- local trace-based evaluation.

Once this foundation is reliable, add layered memory and declarative skills. Proactive behavior and adaptive routing come later and remain user-controlled.

The goal is a capable Russian-first, local-first assistant that can explain what it knows, where it learned it, what it is about to do, what actually happened, and what it could not verify.
