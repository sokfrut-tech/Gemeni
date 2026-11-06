# Repository-wide AI and Jarvis engineering audit — 2026-11-06

Status: source inventory and targeted code review completed; no application code changed in this audit.
Primary product repository: `sokfrut-tech/Gemeni`.
Scope: all 11 repositories visible in the connected GitHub installation were enumerated through their default-branch recursive Git trees. Relevant source and project documents were inspected across languages. This is a source-level audit, not a complete line-by-line audit of every file in every project.

## Product identity — controlling direction

Jarvis is intended to become an artificial intelligence agent, not merely a speech-to-command wrapper or a ChatGPT skin. It must understand goals, reason and plan, use tools, perceive relevant device context, remember approved knowledge, learn from outcomes, coordinate specialist workers, and verify actions. Essential capabilities must remain free/local/offline-first. Cloud models are optional adapters, never mandatory for core operation. Self-improvement must be controlled: memory/feedback -> candidate skill or patch -> independent review -> tests/security -> owner approval -> versioned install/rollback. No silent self-modification, privilege escalation or execution of generated code.

## Repository inventory inspected

| Repository | Languages / technologies observed in default-branch tree | Jarvis relevance |
|---|---|---|
| `Gemeni` | Kotlin, C++17, Python 3, Bash, Gradle Kotlin DSL, Protobuf, XML, YAML | Primary Jarvis Android agent, native visual renderer, speech stack, model/factory tooling |
| `RussSearchOPPO` | Kotlin, Python, Gradle Kotlin DSL, XML, YAML, JSON/model manifests | Reusable Russian offline voice commands, Vosk baseline, translation engine, OPPO/ColorOS integration and QA probes |
| `voice-assistant` | Java, Gradle, XML, YAML, JSON | Earlier Vosk-based Android voice assistant; useful as a command/voice reference, not the target architecture |
| `Antigravity-Mobile` | Kotlin, JavaScript, SQL, OpenAPI YAML, XML, Gradle | Mobile factory UI, secure Factory API, provider adapters, task ledger, role/skill packs |
| `Universal-AI-Factory` | Python, YAML, Markdown, workflow shell, JSON | Cross-project coordinator, agent exchange protocol, security policy, CI and project memory |
| `russ-translation-models` | JSON manifests, Markdown, YAML workflow | Offline translation model catalogue and provenance |
| `breeno-apks` | APK/reference documentation only in inspected tree | Breeno compatibility/reference material; no application source was present in the tree |
| `stamp-cleaner` | Kotlin, JavaScript, HTML, CSS, SVG, Gradle | Reusable image-processing, UI, browser pipeline and test patterns; not Jarvis core |
| `avtoy-korea-auto` | JavaScript, SQL/configuration, YAML, HTML/media assets | Worker/API, private messaging, moderation, retry/idempotency and deployment patterns |
| `happ-proxy-pool` | Python, JSON, YAML | Proxy/config generation and security workflow; optional networking infrastructure only if later justified |
| `sleep-button` | Documentation/continuation brief only in inspected tree | No reusable application source identified in this snapshot |

All repository trees were read from the default branch. The audit did not assume that unmerged PR branches are part of main.

## Primary Jarvis source findings on `main`

### Genuine building blocks present
- Android app and visual layer: Kotlin/Compose, Android manifest, native C++/JNI/GLES2 shader renderer.
- Speech components: AudioRecord capture, Sherpa-ONNX Russian ASR wrapper, Silero VAD wrapper, Sherpa keyword spotter wrapper, Piper-compatible Sherpa TTS wrapper, system TextToSpeech speaker.
- Model handling: app-private model download managers and SHA-256 verification for several model files.
- Device integration scaffolding: VoiceInteraction service/session classes, AccessibilityService, AIDL service/callback, ColorOS/Breeno bridge/provider and dynamic card proto definitions.
- Engineering factory: Python model router, worker capability router, deterministic decision engine, continuous-improvement ledger, technician dispatcher, repair scripts, runtime diagnostics, resource-federation manifests and GitHub Actions workflows.

### Critical gap: architecture classes do not yet make a working AI

Source inspected on `main` includes these placeholders or incomplete paths:
- `LocalLlmEngine.complete(prompt)` returns the prompt itself; it is not an inference engine.
- `JarvisToolRegistry.dispatch()` only handles a clock query and otherwise echoes that the request was accepted.
- `JarvisCore` simply delegates to that registry; no reasoning/planning loop is connected.
- `JarvisSessionCoordinator` is only an in-memory Boolean.
- `JarvisMemoryRepository` is key/value SharedPreferences storage, not semantic/episodic memory, retrieval, provenance or conflict resolution.
- `RussianIntentDispatcher` only classifies text into SEARCH/OPEN/CHAT labels.
- `RussianSpeechEngine` only normalizes text.
- `JarvisAccessibilityService` has empty event/interrupt callbacks.
- `JarvisRemoteService` echoes submitted text; `startListening()` and `stopListening()` are empty.
- `JarvisVoiceInteractionService` only calls superclass lifecycle methods.
- `JarvisVoiceSession` shows a text-only session surface; no real session-to-agent tool execution was found in the inspected implementation.

### Speech stack is only partially connected
- The current Activity runtime controller follows a push-to-talk path: AudioRecord frames for a short bounded window -> Sherpa ASR -> basic intent dispatch -> a few Android intents -> Android system TTS.
- The standalone `JarvisVoicePipeline`, KWS detector and VAD engine are not wired into that Activity runtime controller in the inspected path.
- `PiperTtsEngine` exists but the active `JarvisSpeaker` uses Android system TextToSpeech.
- Therefore continuous hotword -> VAD segmentation -> ASR -> reasoning -> action -> neural TTS is NOT verified as an end-to-end runtime path.
- The configured KWS model is described in source as zh/en; the custom `JARVIS` pronunciation/phoneme mapping is experimental and needs false-positive/false-negative testing. It is not proof of robust Russian "Джарвис" detection.
- Model downloads are not enough to prove inference works. Need checksums for every file (including token/config assets), model license review, atomic installation, offline inference tests, memory/latency/battery measurements.

### Device integration and compatibility
- The current `Gemeni` app Gradle configuration sets `minSdk = 29`, conflicting with the owner's desired Android 7/API 24+ compatibility. This must be resolved deliberately, not silently raised.
- VoiceInteraction, Accessibility and AIDL declarations/classes exist, but the inspected implementations include no-op paths. Registration alone is not integration proof.
- The C++ GLES shader renderer exists, but source linkage alone is not proof of a visible frame or stable lifecycle on OPPO.
- The currently inspected active Android tool set is limited. The unmerged PR #58 adds a better centralized Android gateway, but it must be tested and integrated safely.

## Unmerged Jarvis work that must be preserved and reconciled

| PR | Work | Current observation |
|---|---|---|
| [#35](https://github.com/sokfrut-tech/Gemeni/pull/35) | Official Gemini cloud client | Open; optional cloud path only; not a free/offline core |
| [#51](https://github.com/sokfrut-tech/Gemeni/pull/51) | Emulator smoke tests | Open; test suite changes need review; target OPPO validation remains separate |
| [#55](https://github.com/sokfrut-tech/Gemeni/pull/55) | Animated orbital core | Open draft; visual change only, not proof of agent intelligence |
| [#58](https://github.com/sokfrut-tech/Gemeni/pull/58) | Central Android tool gateway and assistant lifecycle | Open draft; valuable typed gateway, real intents and visible state; based on an earlier emulator-test branch |
| [#59](https://github.com/sokfrut-tech/Gemeni/pull/59) | Pure Russian parser and unit tests | Open draft; based on PR #58; must preserve as parser/test layer |
| [#60](https://github.com/sokfrut-tech/Gemeni/pull/60) | Kimi/Qwen/GLM and open-source research | Open draft; documentation and phased architecture decisions |
| [#61](https://github.com/sokfrut-tech/Gemeni/pull/61) | Offline provider-neutral model contract, typed tool proposals, risk classification, task state machine | Open draft; its observed exact-head CodeQL/build job failed at Kotlin compilation |
| [#62](https://github.com/sokfrut-tech/Gemeni/pull/62) | Compilation repair for Phase A | Open draft; Kotlin proposal validator correction is useful, but observed exact-head emulator test failed to install split APKs / ADB emulator failure |
| [#63](https://github.com/sokfrut-tech/Gemeni/pull/63) | Separate CodeQL from emulator smoke tests | Open draft; CI design follow-up, exact-head evidence still needs recheck |
| [#65](https://github.com/sokfrut-tech/Gemeni/pull/65) | Continuity and full Agent Autoloop roadmap | Open draft; useful durable handoff, docs-only |

Important branch topology: PRs #58 -> #59 -> #60 form a dependency chain; PR #61 and #62 are sibling implementations based on the #60 line, not a sequential merge chain. Do not merge them blindly. Reconcile the fixed Kotlin implementation from #62 into a single integration branch, preserving #61 tests/docs, then run fresh exact-head CI. PR #63 is a CI follow-up on the #62 line. The CodeQL failure logs for #61 show `:app:compileDebugKotlin FAILED`; #62's observed emulator job failed to install the split APK after repeated ADB/emulator errors. These are separate issues and need separate fixes.

## Factory automation: what success currently means

- The latest observed Jarvis Director run `42268102507` completed successfully. It includes a model-worker step and specialist jobs.
- The specialist role jobs call `tools/agent_swarm/run_swarm.py` in deterministic evidence-collector mode; they are not automatically independent LLM agents merely because the jobs are named specialist roles.
- The latest Repair Crew run `42268177384` completed successfully; technician jobs inspect repository evidence and produce tickets, not autonomous code patches.
- The latest Test Factory run `42265580359` passed static checks such as file existence, grep-based UI/speech/service checks, APK manifest presence and a secret pattern scan. This is useful static evidence but is not Gradle build, Android runtime, emulator acceptance or OPPO device proof.
- The Director workflow can call configured Qwen/DeepSeek workers when endpoints are actually configured. A successful workflow alone is not evidence of a specific independent model's findings unless the worker artifact/report is inspected.

## Cross-repository assets to reuse

1. `RussSearchOPPO`: proven code baseline for Vosk offline Russian ASR, phone-action parsing, HTTPS/SHA-256 model installation, translation model routing and ColorOS-aware Android work. Keep as fallback/reference; do not duplicate its parser blindly.
2. `voice-assistant`: earlier Java/Vosk implementation and real navigation command examples; useful as a historical reference.
3. `Antigravity-Mobile`: provider-neutral Factory API adapters, explicit provider status, idempotent task creation, task status/event ledger, secure token handling and agent role/skill packs. Use its provider/task patterns on the factory side, not its server code inside the APK.
4. `Universal-AI-Factory`: shared agent exchange protocol, handoffs, CI/security standards and project index. Use as the canonical cross-project coordination layer.
5. `russ-translation-models`: model provenance/catalogue patterns for offline model packs.
6. `stamp-cleaner`: reusable UI/testing and image processing ideas only where a Jarvis vision feature requires them.
7. `avtoy-korea-auto`: retry/idempotency and private messaging patterns for future Jarvis communications; no direct code reuse without a scope/license/security review.

## External architecture research to continue

- Qwen-Agent: Python tool/function calling, multi-step planning, RAG and MCP patterns; adapt contracts, not Python runtime into Android.
- Kimi Code: subagent roles, approvals, checkpoints and handoffs; development/factory tooling rather than an Android runtime dependency.
- Kimi-Audio: research candidate for advanced audio, but benchmark model size, runtime and license before considering mobile use.
- GLM ecosystem: optional provider adapter only after current official endpoint/terms review.
- Open Jarvis / Android automation projects: screen understanding, tool verification, Accessibility and app workflows; inspect licenses and real source before adopting.
- Local-first memory candidates such as Alice, mem-port and LaRuche offer useful persistence, retrieval, audit and memory-lifecycle ideas. Evaluate embedded Android fit, dependencies, resource cost and licenses before selecting one.

## Recommended target architecture

1. **Cognitive core:** provider-neutral `ReasoningEngine`; local model first where hardware permits; optional remote providers as explicit opt-in. Model router profiles tool-calling, context, vision, memory, RAM, latency and cost.
2. **Planner:** converts a goal into typed steps, dependencies, expected outcomes and stop conditions; bounded loops and cancellation.
3. **Memory:** local SQLite-backed episodic + semantic + preference memory, full-text/vector retrieval, provenance, conflict handling, consolidation and user controls. Do not save raw audio by default.
4. **Tool/skill runtime:** typed schemas, versioned skills, permission scopes, risk classification, per-tool timeout and normalized results. One Android execution gateway only.
5. **Perception:** microphone/audio pipeline; optional screen context/OCR and image understanding through explicit Android permissions. Accessibility is a constrained sensor/action interface, not unrestricted control.
6. **Voice:** AudioRecord -> KWS -> VAD -> ASR -> reasoning -> validated tool proposal -> Android gateway -> TTS + visual state. Every edge must be connected and tested.
7. **Learning:** approved feedback/outcome capture -> candidate memory/skill -> evaluation -> independent review -> user approval -> versioned rollout and rollback. No self-modifying APK.
8. **Hive/factory:** task coordinator, specialist workers, evidence store and handoff. Workers are marked active only after actual executor output is available.
9. **Observability:** event trace for each task (plan, model/provider, tool, arguments after redaction, permission decision, result, verification, latency, errors). No secrets or raw sensitive content in logs.

## Priority execution order

- P0: reconcile the PR branch chain (#58/#59/#60/#61/#62/#63) into one safe, testable integration line; fix Kotlin compilation and emulator split-APK/ADB test reliability. Do not merge drafts blindly.
- P0: bring Android tool gateway + parser into the actual app runtime; remove echo-only tool stubs and prove three real actions end-to-end.
- P0: align Android minSdk with API 24 target where feasible; gate newer APIs and dependencies. Verify Sherpa AAR ABI/API compatibility.
- P1: wire AudioRecord, KWS, VAD, ASR and TTS into one managed lifecycle; make wake-word opt-in with visible foreground notification, stop control and battery/privacy controls.
- P1: implement actual local inference behind `LocalLlmEngine` and benchmark small quantized models on OPPO; until then label it NOT_IMPLEMENTED, not offline reasoning.
- P1: replace SharedPreferences key/value memory with a user-controlled persistent memory/retrieval design; add provenance, retention and delete/forget controls.
- P1: connect provider-neutral typed proposals to the single Android gateway with SAFE/CONFIRM/BLOCK enforcement and tests.
- P2: screen context/vision, MCP adapters, routines, multi-agent runtime, skill learning and model marketplace after core reliability is proven.

## Evidence and caution

Repository inventory and source checks were performed on 2026-11-06. Main-branch commit observed for `Gemeni`: `32cceff83ce6233a5f59f71c2afe9941588ef7bc`. PR status and workflow evidence were refreshed during this audit. No APK was installed or tested on the user's OPPO during this audit. No application source code was modified. All runtime claims above remain NOT_TESTED unless explicitly identified as source or CI evidence.

## Next agent handoff

Read this report, `docs/CONTINUATION_STATE.md`, `docs/CURRENT_STATUS.md`, and the PR branch graph before coding. Start with a single integration branch that combines the validated gateway/parser and the corrected offline provider/typed proposal/task state machine. Run Gradle unit tests, APK build, security scan, emulator instrumentation and finally target OPPO tests. Keep user-visible progress tied to artifacts and test evidence.