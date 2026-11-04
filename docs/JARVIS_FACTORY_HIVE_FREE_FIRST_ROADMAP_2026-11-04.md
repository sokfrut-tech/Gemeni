# Jarvis Factory Hive — product architecture and delivery plan

Date: 2026-11-04
Status: approved product direction; implementation not yet delivered
Repository: `sokfrut-tech/Gemeni`
Related: PR #60; `docs/CONTINUATION_STATE.md`; `docs/OPEN_SOURCE_AI_INTEGRATION_REVIEW_2026-11-04.md`

## 1. User's controlling requirements

Jarvis must be:
- Free to use for its essential everyday functions; no mandatory paid subscription, paid API, hosted backend or proprietary cloud model.
- Functional, Russian-first, local-first and useful without an internet connection after required free model assets have been downloaded.
- Extensible: new skills, tools, models and specialist agents can be added without rewriting the core.
- Able to coordinate a "hive of factories": a coordinator decomposes tasks, routes bounded subtasks to specialist workers, checks evidence, reconciles results and records durable knowledge.
- Honest about capability: no fake agent activity, fabricated progress, or claims of self-improvement unsupported by tests and traceable changes.
- Safe by default. No model or agent may bypass the central permission and Android tool gateway.

No Google Play publication is currently planned.

## 2. Meaning of "self-developing intelligence"

Jarvis should improve through a controlled learning loop, not uncontrolled self-modification:
1. Capture user-approved preferences, corrections and task outcomes.
2. Store them locally in a searchable, inspectable memory with provenance and deletion controls.
3. Turn recurring needs into candidate skills or workflow improvements.
4. Ask the factory coordinator to draft a change brief and tests.
5. Have independent reviewer/QA/security workers inspect the proposal.
6. Run tests in an isolated build/CI environment.
7. Present a change summary and request user approval before installing/updating executable code or changing permissions.
8. Keep versioned checkpoints and a rollback path.

The runtime model must never silently rewrite its own APK, grant itself permissions, change security policy, or execute generated code.

## 3. Factory hive topology

### Coordinator
- Owns the task state machine, priority, dependencies, budgets and deadlines.
- Converts a user request into a structured task brief with acceptance criteria.
- Assigns only the minimum required specialist workers.
- Collects artifacts, checks provenance and rejects unsupported claims.
- Maintains one canonical task record and handoff file.

### Specialist factories
1. **Research factory** — searches approved local docs/repositories and, when allowed, public sources; returns cited findings and license notes.
2. **Android/ColorOS factory** — Kotlin, Compose, permissions, services, lifecycle, package visibility and device compatibility.
3. **Voice factory** — AudioRecord, VAD, KWS, Russian ASR, TTS, audio routing and latency.
4. **Model factory** — local model selection, quantization, download integrity, RAM/CPU/NPU profiling and provider adapters.
5. **Tools/automation factory** — typed skills and Android intents through the one execution gateway.
6. **UI/visual factory** — accessible Russian interface, orb states, cards, animations and performance.
7. **Security factory** — threat modelling, permission review, dependency/license checks, secret scanning and abuse cases.
8. **QA/release factory** — unit/integration/UI tests, emulator/device evidence, APK signing/reproducibility and rollback.
9. **Memory/knowledge factory** — local memory indexing, provenance, summarization and user-controlled retention.

Workers are logical roles first. They may be implemented as local deterministic modules, local model-backed workers, or optional external agents. A role is not considered active until an actual executor is connected and has produced a traceable result.

### Work protocol
Every worker receives a versioned brief containing:
- task ID, role, scope and forbidden actions;
- input references and expected output schema;
- acceptance criteria and test requirements;
- time/resource/network budget;
- allowed tools and permission level.

Every result must include:
- files/artifacts inspected or created;
- findings and confidence;
- commands and tests actually run, with exit codes;
- evidence links/log references;
- limitations, risks and next handoff;
- exact commit SHA when repository changes were made.

Coordinator status values: QUEUED, ASSIGNED, RUNNING, WAITING_FOR_USER, BLOCKED, REVIEW, VERIFYING, DONE, FAILED, CANCELLED. Progress percentage must be evidence-based, not invented.

## 4. Free-first runtime and model strategy

### Always available without cloud
- Russian command parser and deterministic Android tools.
- Sherpa-ONNX local Russian ASR (existing project direction).
- Local TTS using a license-compatible Piper/ONNX voice after exact model/voice license and size verification.
- Local rules, timers, notes, device information and offline skills.
- Local SQLite memory and task history.
- Download manager with SHA-256 verification and resumable downloads where practical.
- No account or API key required for core features.

### Optional local reasoning model
Investigate Android-compatible runtimes and small quantized models (for example llama.cpp Android/NDK or another actively maintained mobile runtime). Do not select a model solely from benchmark headlines. Benchmark on the actual target OPPO hardware for:
- RAM peak and app stability;
- first-token and total response latency;
- Russian quality and tool-selection accuracy;
- battery/thermal impact;
- APK size versus separately downloaded model size;
- license permitting the intended distribution and use.

Model weights must be optional downloads, not bundled blindly in the APK. Provide a small-model profile and a no-LLM deterministic profile. The app must remain useful if the model cannot fit in memory.

### Optional external providers
Gemini, Qwen, Kimi, GLM and other providers are adapters, never required dependencies. Only enable a provider after official endpoint, pricing/free-tier limits, terms, privacy, regional availability and data handling are verified. Do not promise a permanent free tier. User must opt in and supply their own credential if required; store secrets only in Android Keystore-backed storage. Cloud unavailable, quota exhausted or key absent => automatic return to local mode, with visible status. Never upload microphone audio by default.

### Cost guardrails
- No paid endpoint may be called automatically.
- No surprise billing or silent cloud fallback.
- Show provider, network status and whether a request leaves the device.
- Allow cloud use to be disabled globally and per skill.
- Set request/time/token budgets and cancellation.
- Keep all core local functions independent of provider billing.

## 5. Runtime architecture and security boundary

`User -> Jarvis UI/Voice -> Coordinator -> Planner -> Typed Skill Proposal -> Policy/Approval Gate -> AndroidJarvisToolRegistry -> Result -> TTS/UI/Memory`

- Providers receive a narrow request/response interface, never Android Context, PackageManager, raw filesystem access or unrestricted shell.
- Model output is untrusted data. Parse into a closed schema; reject unknown tools, extra fields, oversized values, malformed arguments and prompt-injection attempts.
- Only `AndroidJarvisToolRegistry` executes Android actions.
- Risk classes: SAFE (allowlisted, reversible/read-only), CONFIRM (exact action preview + explicit user approval), BLOCK (unsupported or unsafe).
- Calls, messages, purchases, payments, deletion, permission changes, arbitrary shell, unrestricted Accessibility and installing code are blocked or confirmation-gated according to a separately reviewed policy. No ACTION_CALL; phone action remains ACTION_DIAL.
- MCP is disabled by default. Later MCP support must use explicit trusted-server allowlists, scoped capabilities, timeouts, audit records and per-server revocation.
- Microphone background operation requires user opt-in, runtime permission, visible foreground-service notification, persistent stop control and clear recording state. No covert recording; no raw audio persistence by default.

## 6. Phased implementation roadmap

### Phase 0 — repository and factory foundation
- Preserve current PR #60 research and reconcile active PRs/branches.
- Establish canonical factory protocol, task schema, agent report template and status/evidence format.
- Add architecture decision records and a free-first compatibility matrix.
- No runtime code changes until exact base/head and CI are understood.

### Phase A — safe local coordinator core
- Provider-neutral `JarvisModelProvider` contract and deterministic local-only provider.
- Closed typed skill/tool proposal schema and strict parser.
- SAFE / CONFIRM / BLOCK policy.
- Coordinator task model, task states, cancellation, timeout and bounded retries.
- Local task/memory persistence with provenance.
- Unit tests proving disabled/offline mode makes zero network calls and existing parser/gateway behavior remains intact.
- No live provider, MCP, microphone upload or generated-code execution.

### Phase B — first on-device reasoning model
- Compare candidate runtimes and small quantized models on actual device.
- Add optional model download, checksum, license record, storage estimate, cancellation and removal.
- Benchmark Russian task accuracy, memory, latency and thermals.
- Keep deterministic fallback if model load fails or RAM is low.

### Phase C — specialist workers and skill ecosystem
- Add workers one by one behind stable interfaces.
- Start with research, Android tools, voice diagnostics, memory and QA.
- Add skill manifest, schema, permission declarations, tests and version compatibility.
- Use worker isolation and a single integration owner.

### Phase D — optional external AI adapters
- Add one provider at a time after official API/free-tier/privacy review.
- Text-only opt-in first; no audio uploads.
- Add redaction, quotas, timeout, cancellation and local fallback tests.

### Phase E — controlled self-improvement
- Local preference/outcome memory.
- Candidate skill generation as a proposal/patch only.
- Independent code review, security scan, test suite and user approval.
- Versioned releases, rollback and changelog.

### Phase F — always-available voice and ColorOS integration
- Foreground microphone service with visible opt-in and stop.
- Connect KWS -> VAD -> ASR -> coordinator -> tool gate -> TTS.
- Verify battery, thermal and ColorOS background restrictions.
- Physical OPPO validation is mandatory before claiming device-ready.

## 7. Definition of done

A phase is done only when:
- code and docs are committed to a named branch;
- acceptance criteria map to tests and evidence;
- CI is green on the exact head SHA;
- security and dependency/license checks pass for the changed scope;
- no secrets are committed;
- offline/no-provider behavior is demonstrated;
- status, changelog, continuation state and agent report are updated;
- limitations and unverified hardware behavior are explicit.

The full product is not release-ready until the existing black-screen, voice, tool, TTS, native-renderer and OPPO runtime gates in `docs/CONTINUATION_STATE.md` pass.

## 8. Immediate next work

1. Recheck PR #59 and PR #60 exact heads and CI; reconcile their bases before code integration.
2. Implement Phase A only on a clean branch based on the selected integration base.
3. Add tests for typed tool proposals, risk policy, offline no-network mode, cancellation and existing parser compatibility.
4. In parallel, research local model runtimes and free/open model licenses without adding dependencies yet.
5. Create specialist briefs with non-overlapping file ownership; request Claude/Gemini only through working authorized channels. If unavailable, coordinator proceeds sequentially and records the unavailable handoff honestly.
6. Do not merge, publish or claim device validation without explicit authorization and fresh evidence.

## 9. Continuity rule

This file, `docs/CONTINUATION_STATE.md`, `docs/CURRENT_STATUS.md`, `docs/CHANGELOG_AI.md` and the latest agent reports are the source of truth. Every session must read them first and update them before handing off. Chat history is supplementary, not required.
