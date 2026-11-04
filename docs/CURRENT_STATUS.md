

## 2026-11-04 — free-first Factory Hive plan

**Product direction approved by user:** Jarvis should be free for core use, feature-rich, functional offline and improve through a controlled learning/development loop. A persistent Factory Hive should coordinate specialist work inside Jarvis.

- Canonical plan: [docs/JARVIS_FACTORY_HIVE_FREE_FIRST_ROADMAP_2026-11-04.md](JARVIS_FACTORY_HIVE_FREE_FIRST_ROADMAP_2026-11-04.md).
- Core baseline: local Russian command parser, Sherpa-ONNX ASR, license-compatible local TTS, deterministic Android tools, local memory and offline skills. No mandatory cloud, paid API or subscription.
- Optional local LLM is not selected yet; runtime/model selection requires actual OPPO RAM, latency, Russian quality, battery/thermal and license benchmarks.
- Cloud providers remain opt-in adapters; no permanent free-tier guarantee and no surprise billing. Local fallback is mandatory.
- Factory roles and task protocol are specified. External Claude/Gemini/other agents count as active only after an actual authorized execution and evidence report.
- Controlled self-improvement only: memory -> candidate skill/patch -> independent review/security/tests -> user approval -> versioned update/rollback. No silent APK rewrite or permission escalation.
- Phase A is the immediate implementation target: local coordinator, typed proposals, SAFE/CONFIRM/BLOCK policy, offline zero-network tests, cancellation and bounded retries.
- This is a documentation/architecture checkpoint only: no runtime implementation or tests are claimed here. PR #60 remains open/draft; verify exact CI and PR #59 integration before coding. Overall product remains **NOT RELEASE READY**.


## 2026-11-04 — Phase A implementation started (PR #61)

- PR #61: https://github.com/sokfrut-tech/Gemeni/pull/61 — open, draft, not merged. Branch: `feat/jarvis-factory-hive-phase-a-2026-11-04`.
- Existing repository factory infrastructure was found under `tools/agent_swarm/`, `tools/orchestrator/`, `tools/model_fabric/` and several factory GitHub workflows. These are development/CI orchestration components, not yet an integrated on-device Jarvis hive.
- Added provider-neutral local-only interface, parser-backed offline provider, strict typed proposal codec, SAFE/CONFIRM classification and deterministic task state machine. Added JVM tests.
- Latest known head: `70801d7bdce54ac09ae0c3ded5a5f654f20f7026`.
- Factory Security Baseline run 41906523425 was queued at that head. Unit-test/APK build results have not yet been verified. No runtime integration, device test or release claim.
- Product remains **NOT RELEASE READY**. Next: verify security workflow and exact-head Gradle/unit-test/APK workflow; repair failures before expanding scope.
