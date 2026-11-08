# Open Jarvis Android source audit — 2026-11-08

## Scope and evidence

This is a targeted source-code audit of the public repository `tokenarc/open-jarvis`, not a claim that all GitHub repositories have been reviewed. The audit inspected the current `main` tree and selected Kotlin sources, tests, and docs on 2026-11-08.

Repository: https://github.com/tokenarc/open-jarvis
Inspected source areas:
- Android manifest and Accessibility service
- Action plan parser and action executor
- Screen reader and screenshot capture
- Prompt sanitizer and risky-action confirmation
- JSON skill engine
- Task router and task working memory
- Graphify Room/SQLite memory repository and analysis engine
- Screen watcher and security/skills/Graphify documentation

No source code was copied into Gemeni. Findings below are architectural observations and implementation risks, not endorsements of the repository's reliability. Third-party license compatibility was not fully audited in this pass; perform a separate license and dependency review before any code reuse.

## Executive result

Open Jarvis contains several useful *design directions* for Jarvis:
1. A local graph-shaped memory connecting apps, tasks, contacts, patterns, and events.
2. Declarative JSON skills with triggers, variables, action templates, and success-verification metadata.
3. A separation between task routing, working memory, action plans, screen reading, and action execution.
4. A visible risk-preview concept before sensitive operations.
5. A screen-watcher concept for contextual assistance.

However, the inspected implementation has material gaps. It must not be treated as production-ready reference code or copied wholesale. In particular, risk classification is narrow, prompt-injection handling is only a keyword heuristic, action execution does not prove postconditions, screen watcher can autonomously tap UI, and several Android resource-lifecycle paths need correction and tests.

## Findings by subsystem

### 1. Accessibility and screen interaction

Files:
- `app/src/main/java/com/openjarvis/accessibility/JarvisAccessibilityService.kt`
- `app/src/main/java/com/openjarvis/accessibility/ScreenReader.kt`
- `app/src/main/java/com/openjarvis/accessibility/ActionExecutor.kt`

Useful idea:
- Keep Android UI interaction behind a dedicated adapter/service instead of letting the language model call Android APIs directly.
- Expose a bounded action vocabulary (open app, tap, type, back/home/recents) and return a structured per-step result.

Observed risks:
- The inspected service subscribes to `TYPE_ALL_MASK` and sets enhanced web accessibility flags; this is broad access and needs a least-privilege justification and explicit user-facing disclosure.
- The service's `onAccessibilityEvent` and `onInterrupt` are empty in the inspected file; there is no demonstrated event-driven state model there.
- `ActionExecutor` reports success for some global actions without verifying that the expected screen or state appeared.
- `openAppByPackage` does not return or validate launch success.
- Node traversal and recycling need a dedicated lifecycle review. In the inspected breadth-first traversal, nodes are recycled while child references are being enqueued; ownership and cleanup should be rewritten around a single clear ownership rule and tested to avoid stale/recycled node access and leaks.

Jarvis decision:
- Adopt the adapter boundary and typed action result model.
- Do not adopt broad event subscriptions by default. Configure only the event types needed for an explicitly enabled capability.
- Every action must have a precondition, timeout, cancellation path, and postcondition. If verification is unavailable, report "action sent; result not verified", never "completed".
- Screen text and accessibility nodes are untrusted input, not instructions. Treat them as data and isolate them from system/developer policy.

### 2. Typed action plans and execution

Files:
- `app/src/main/java/com/openjarvis/agent/ActionPlan.kt`
- `app/src/main/java/com/openjarvis/accessibility/ActionExecutor.kt`

Useful idea:
- Model a plan as a finite ordered list of typed steps.
- Return partial results and stop on the first failed step rather than continuing blindly.

Observed risks:
- The parser is permissive and catches broad exceptions, returning null without a typed parse error.
- A plan is not visibly validated against a strict schema, allowed-action registry, parameter constraints, or a capability/permission policy before execution.
- Fixed delays between actions are not a substitute for waiting on a state-specific condition.
- The executor does not demonstrate idempotency keys, an execution budget, a maximum plan length, or rollback/compensation semantics.

Jarvis decision:
- Keep the deterministic typed tool gateway already being built in Gemeni.
- Require strict schema versioning, allowlisted tool IDs, bounded strings/numbers/timeouts, maximum steps, explicit execution budget, cancellation, and a structured error taxonomy.
- Require risk evaluation *after* resolving the concrete target app, recipient, amount, destination, and payload—not merely from a generic action name.
- Add a post-action verifier for each tool. Tool output must include attempted, accepted, and verified states separately.

### 3. Risk confirmation

File:
- `app/src/main/java/com/openjarvis/agent/RiskyActionConfirmation.kt`

Useful idea:
- Show a preview with risk level, summary, and details before executing a risky operation.
- Keep risk classification in a dedicated component.

Observed risks:
- The inspected classifier only considers a narrow subset of `TYPE` and `OPEN_APP`; it does not comprehensively classify all action types or sensitive destinations.
- Messaging risk depends on `action.packageName`, which may be absent or unresolved when the plan is evaluated.
- The preview text says it auto-proceeds after eight seconds. Jarvis must not copy an auto-proceed timeout for sending messages, purchases, payments, deleting data, or changing security settings.
- An app/package allowlist is not enough to assess intent or impact. A seemingly ordinary app can contain a high-impact action.

Jarvis decision:
- No automatic proceed for high-impact actions. Require an explicit, fresh user confirmation bound to the exact normalized action payload and target.
- Confirmation expires on any material payload/target change and cannot be reused.
- Risk tiers must include read-only, reversible local change, external communication, financial/legal commitment, destructive/security-sensitive action, and emergency/telephony boundary.
- Default-deny unknown tools and unknown risk classifications.

### 4. Prompt injection and untrusted content

File:
- `app/src/main/java/com/openjarvis/agent/PromptSanitizer.kt`

Useful idea:
- Bound command length, reject empty input, remove control characters, and mark suspicious content for additional handling.

Observed risks:
- Regex matching phrases such as "ignore instructions" or "jailbreak" is not a reliable prompt-injection defense. Attacks can be indirect, multilingual, encoded, or embedded in screen text and documents; benign text can also match the patterns.
- A suspicious result is not itself a security boundary.
- The sanitizer operates on user command text; the larger threat surface includes OCR, accessibility labels, notifications, files, webpages, tool outputs, and model-generated plans.

Jarvis decision:
- Do not rely on keyword filters as the security boundary.
- Use a strict trust model: policy instructions are immutable; user intent is separate; screen/document/web/tool content is untrusted data.
- Never allow content read from the screen or a retrieved document to grant permissions, change policies, reveal secrets, or authorize a tool.
- Enforce tool permissions and confirmation in deterministic code outside the LLM.
- Add adversarial tests for multilingual, indirect, quoted, encoded, and benign-lookalike prompt-injection examples.

### 5. Declarative skills

Files:
- `app/src/main/java/com/openjarvis/skills/SkillEngine.kt`
- `docs/SKILLS.md`
- `app/src/main/assets/skills/*.json`

Useful idea:
- Skills can be portable, versioned, reviewable JSON manifests with trigger phrases, typed variables, action templates, tags, and verification requirements.
- Usage and success telemetry can help rank or maintain skills, if collected transparently and locally.

Observed risks:
- Trigger phrases are converted to regex and matched with `find()`; matching is not anchored and the wildcard/regex construction is not a robust natural-language parser.
- Variable extraction only handles a small hard-coded set of keys and can assign the same captured group to multiple variables.
- `buildContext` constructs JSON through string interpolation without proper JSON escaping; quotes or control characters in values can corrupt the plan.
- A generated skill can be saved from a prior action sequence with a generic "Task completed" verification string. That is not evidence that the sequence is safe or repeatable.
- Skill files in app-private storage are loaded as executable action templates; there is no demonstrated signature, capability grant, provenance review, or per-skill permission manifest in the inspected path.

Jarvis decision:
- Adopt declarative skills only after adding a strict schema and manifest fields: ID, version, source/provenance, required capabilities, permissions, risk tier, input schema, action list, timeout/budget, verification contract, and rollback strategy.
- Parse/serialize with a real JSON library; never hand-build JSON strings.
- Treat imported/generated skills as disabled drafts until validated and explicitly enabled.
- Generated skills may be proposed from successful traces, but must not silently become executable.
- Store a per-skill audit trail and allow one-tap disable/revoke.

### 6. Graphify memory

Files:
- `app/src/main/java/com/openjarvis/graphify/GraphifyRepository.kt`
- `app/src/main/java/com/openjarvis/graphify/AnalysisEngine.kt`
- `app/src/main/java/com/openjarvis/graphify/nodes/*`
- `docs/GRAPHIFY.md`

Useful idea:
- A graph can represent relationships among app, task, contact, provider, and learned-pattern entities.
- Repeated sequences can become candidate patterns with confidence and recency.
- Retrieval should be relevance-based rather than injecting the entire history into every prompt.

Observed risks:
- The inspected `buildMemoryContext` uses `runBlocking`; calling it from the main thread or another constrained coroutine context can block responsiveness and create deadlock/performance hazards.
- Memory context is assembled from recent tasks, contacts, and patterns; the inspected path does not show a consent-aware sensitivity filter, per-category retention, provenance, correction, or deletion workflow.
- The sample pattern learner is a simple repeated-sequence heuristic. It should not be described as semantic understanding or neural learning.
- Storing contact names and interaction patterns creates sensitive personal data even if the database is local.

Jarvis decision:
- Adopt graph-shaped memory as a future architecture, not as a direct dependency on Open Jarvis's implementation.
- Build a local, typed memory store with provenance, timestamps, confidence, sensitivity labels, expiry/retention, user correction, export, and deletion.
- Separate episodic memory (what happened), semantic memory (verified facts), procedural memory (approved skills), and working memory (current task).
- Retrieve only the minimum relevant memories for a task; never inject secrets or sensitive records by default.
- Pattern learning must be opt-in, explainable, reversible, and confidence-calibrated. A repeated sequence is a suggestion, not authorization to act.
- Keep database IO off the UI thread; use suspend APIs/Flow and bounded queries.

### 7. Task routing and working memory

Files:
- `app/src/main/java/com/openjarvis/intelligence/TaskRouter.kt`
- `app/src/main/java/com/openjarvis/intelligence/TaskWorkingMemory.kt`

Useful idea:
- Keep temporary variables for a multi-step task separate from long-term memory.
- Route to a capability/provider based on task requirements and evidence.

Observed risks:
- The inspected router relies on a small set of English substring checks; it is not suitable as a Russian intent router.
- It scores candidate apps with a simple trust/capability/recency formula and currently returns a plan containing only an app-open step.
- Working memory is a plain mutable map with no type constraints, lifetime, provenance, or thread-safety contract.

Jarvis decision:
- Build Russian-first intent parsing with deterministic entities/slots and LLM fallback only when necessary.
- Route by capability, permission, offline/online availability, thermal budget, latency, privacy, and measured provider reliability.
- Keep task-scoped memory typed, bounded, auto-cleared at task end, and isolated from persistent memory.

### 8. Screen watcher and proactive behavior

File:
- `app/src/main/java/com/openjarvis/watch/ScreenWatcher.kt`

Useful idea:
- Contextual assistance can be event-driven and bounded by cooldowns rather than requiring the user to repeat context.

Observed risks:
- The inspected watcher polls and can execute a tap action when text appears (including a built-in YouTube "Skip Ad" rule).
- Autonomous screen actions can become surprising or unsafe, especially when screen text is adversarial or the foreground app changes.
- The inspected `getCurrentPackage` path does not demonstrate reliable foreground-package detection.

Jarvis decision:
- No autonomous UI taps in the initial release.
- Proactive behavior should begin as notification/suggestion-only, be explicitly enabled per rule, be package-scoped, have cooldown/rate limits, and be easy to pause.
- Any action that changes external state requires fresh user confirmation.

### 9. Screenshot capture and privacy

File:
- `app/src/main/java/com/openjarvis/vision/ScreenshotCapture.kt`

Observed risks:
- The inspected `startCapture()` creates capture intents but does not launch an Activity consent flow or obtain a MediaProjection.
- `getActivities()` returns an empty list in the inspected source, so `startCapture()` cannot establish a working capture flow as written.
- The capture lifecycle and virtual-display dimensions/density handling need a full Android API review and device tests.
- Screenshots can contain messages, banking details, authentication codes, and health information.

Jarvis decision:
- Do not adopt this capture implementation.
- Prefer Accessibility text only where user enabled it; add screenshot/vision only behind the Android system consent flow, a visible session indicator, explicit one-shot/continuous mode, secure in-memory processing, and immediate disposal by default.
- Exclude sensitive app windows and never transmit screen content to a cloud model without a separate explicit confirmation.

## Comparison with current Gemeni/Jarvis foundation

Inspected Gemeni refs:
- KWS branch: `feat/jarvis-explicit-kws-session-2026-11-08` at `7a808cc5c10ccc9391b6134f2c20f08fe0df7abc`
- Thermal PR #116 head: `0a3f5f6cade15a7d1cfd481cdd24e675d660e21b`
- Audio interruption PR #118 head: `e964198dd5525e38c5fc806cbe64f63cabf68ac8`

Current Gemeni foundation already has:
- A local Russian ASR/VAD pipeline and a separate Sherpa KWS engine.
- Explicit model-download consent and SHA-256 verification.
- A thermal budget abstraction and cooldown/hysteresis work under PR #116.
- A KWS audio-frame router and explicit interruption states under PR #118.
- A typed tool proposal/gateway direction and tests for proposal encoding.
- A simple key-value memory repository and early runtime wiring.

Important gap confirmed in inspected `main` source:
- `JarvisToolRegistry.kt` on main is still a tiny placeholder dispatcher.
- `JarvisMemoryRepository.kt` on main is only SharedPreferences key/value storage.
- `JarvisAccessibilityService.kt` on main is an empty service shell.
- `JarvisRuntimeController.kt` has the local voice pipeline, but KWS session ownership and end-to-end interruption handling are not yet integrated.
Therefore, do not represent the product as having full screen automation, graph memory, or always-on wake-word behavior yet.

## Recommended adoption plan for Gemeni

### P0 — Safety and runtime integrity
1. Complete a single-owner microphone session controller; one AudioRecord capture job only.
2. Wire Android interruption signals to cancellation and release; calls/emergency calls/system audio must never reach KWS/VAD/ASR.
3. Apply thermal/battery/resource budgets to KWS, VAD, ASR, TTS, and inference—not only token generation.
4. Keep all screen/action content untrusted and keep authorization outside the model.

### P1 — Deterministic tool gateway
1. Strict, versioned tool schemas and allowlists.
2. Risk policy based on resolved action payload and target.
3. Explicit confirmation for external, financial, destructive, and security-sensitive actions; no auto-proceed timer.
4. Per-step preconditions, bounded waits, cancellation, idempotency where possible, and postcondition verification.
5. Clear "attempted / accepted / verified" result states.

### P2 — Skill system
1. JSON manifests validated against schema.
2. Required capabilities, permissions, risk tier, input constraints, provenance, timeout, verification, rollback.
3. Imported/generated skills remain disabled drafts pending review.
4. Russian-first intent/slot matching; deterministic parser before LLM.

### P3 — Memory graph
1. Typed local database and migrations.
2. Separate working, episodic, semantic, and procedural memory.
3. Provenance, confidence, sensitivity, retention, correction, export, deletion, consent.
4. Retrieval tests for relevance, leakage prevention, stale facts, and contradictory memories.
5. Never use learned patterns as permission to perform actions.

### P4 — Screen intelligence
1. Accessibility service with minimal event subscriptions and explicit user enablement.
2. Screen summaries are suggestions; no automatic taps in first release.
3. MediaProjection capture only after Android consent and visible active-session indication.
4. Redaction/exclusion tests for sensitive apps and content.

## Required tests before any adoption is called complete

- Strict schema rejects unknown tools, missing required fields, oversized values, invalid coordinates, and unbounded timeouts.
- Prompt injection in OCR/web/notification/tool output cannot change tool permissions or policy.
- Risk confirmation is bound to exact payload and expires after any target/payload change.
- Action executor stops on failure/cancellation and verifies each postcondition.
- Skill manifests reject malformed JSON, unknown action IDs, undeclared capabilities, and unsafe generated templates.
- Memory tests cover consent, retention/expiry, deletion, correction, sensitivity filtering, and retrieval leakage.
- Accessibility node traversal proves correct recycling/ownership under nested trees and interrupted traversal.
- Screen watcher cannot trigger an action unless the exact rule is explicitly enabled and its target package/state still matches.
- Thermal and audio-interruption tests prove microphone release and no auto-resume after call/system audio.
- Real OPPO Find X6 Pro validation is mandatory for audio focus/interruption, Accessibility behavior, thermal behavior, battery use, and ColorOS background restrictions.

## Final assessment

Adopt the *ideas*: graph-shaped memory, declarative skill manifests, task-scoped working memory, typed action plans, action previews, and postcondition verification.

Do not adopt the inspected implementation as-is. The best contribution to Jarvis is a safer, more rigorous version of these patterns integrated with Gemeni's local-first Russian voice stack, single-owner microphone design, thermal guard, explicit consent, and release gates.
