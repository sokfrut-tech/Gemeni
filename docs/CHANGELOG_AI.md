

## 2026-11-04 — free-first Jarvis Factory Hive roadmap
- User set the controlling product goal: essential Jarvis use must be free, functional, feature-rich, offline-capable and able to improve its intelligence over time.
- Added `docs/JARVIS_FACTORY_HIVE_FREE_FIRST_ROADMAP_2026-11-04.md` defining the coordinator, specialist factory roles, structured task/result protocol, cost guardrails, offline-first model strategy, security boundary, phased roadmap and definition of done.
- Defined self-improvement as a controlled proposal/review/test/user-approval/version/rollback loop, not autonomous self-modification.
- Defined no mandatory cloud/paid API; optional providers must be opt-in, pricing-verified and safely fall back to local mode. Local model choice is pending real OPPO hardware benchmarks and license review.
- Immediate implementation target is Phase A only: local coordinator, typed tool proposals, SAFE/CONFIRM/BLOCK policy, zero-network offline tests, cancellation and bounded retries.
- This checkpoint is docs-only; no runtime code, CI test, external agent execution, merge or release is claimed.


## 2026-11-04 — Phase A local Factory Hive core (PR #61)
- Discovered existing engineering-time factory assets: `tools/agent_swarm/`, `tools/orchestrator/`, `tools/model_fabric/` and factory/security/QA workflows. Explicitly distinguished them from an on-device runtime hive.
- Added provider-neutral `JarvisModelProvider` contract and deterministic local parser-backed provider; no network implementation or API key.
- Added strict allowlisted JSON tool proposal codec, input bounds and SAFE/CONFIRM classification; dial requires confirmation.
- Added deterministic factory task state machine with bounded retries and cancellation transitions.
- Added JVM tests for proposal validation and coordinator transitions.
- PR #61 opened as draft. Exact-head security workflow run 41906523425 was queued; unit-test/APK results not yet verified. No merge/release.
