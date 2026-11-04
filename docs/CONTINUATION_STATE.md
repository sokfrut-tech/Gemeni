

## 18. Checkpoint — 2026-11-04: free-first Factory Hive direction

User's explicit priority: Jarvis and the connected projects must remain free for essential use, functional, feature-rich and able to improve its intelligence over time. The intended product is a local-first Jarvis with a coordinated hive of specialist factories.

Canonical architecture and roadmap: `docs/JARVIS_FACTORY_HIVE_FREE_FIRST_ROADMAP_2026-11-04.md`.

Decisions:
- No mandatory paid subscription, hosted backend, API key or cloud model for core functions.
- Local deterministic commands, Sherpa-ONNX Russian ASR, compatible local TTS, local memory and offline skills remain the baseline.
- Optional local reasoning model must be benchmarked on the actual OPPO device for RAM, latency, Russian quality, thermal/battery impact and license before selection.
- Gemini/Qwen/Kimi/GLM are optional adapters only. Never promise permanent free tiers; verify current official pricing/terms before enabling. No surprise paid requests or silent cloud fallback.
- Factory Hive roles: coordinator, research, Android/ColorOS, voice, model, tools, UI, security, QA/release and memory/knowledge. A role is not active until a real executor is connected and returns traceable evidence.
- Coordinator creates structured task briefs, routes bounded work, reconciles evidence, tracks QUEUED/ASSIGNED/RUNNING/WAITING_FOR_USER/BLOCKED/REVIEW/VERIFYING/DONE/FAILED/CANCELLED and stores durable handoffs.
- "Self-improving" means a controlled loop of local memory, candidate skills/patches, independent review, tests, user approval, versioning and rollback. No silent self-modification or permission escalation.
- Only AndroidJarvisToolRegistry executes device actions. Provider/model outputs are untrusted typed proposals; risk policy is SAFE/CONFIRM/BLOCK.
- MCP remains disabled until explicit trusted-server allowlisting and scoped permissions exist.

Immediate next slice: Phase A safe local coordinator core only. Do not add live provider networking, API keys, microphone upload, unrestricted shell, MCP execution or generated-code execution in Phase A. Recheck PR #59/#60 exact heads and CI before choosing an integration base. No merge/release/device-validation claim is made by this documentation checkpoint.


## 19. Checkpoint — 2026-11-04: Phase A implementation started (PR #61)

Implementation branch: `feat/jarvis-factory-hive-phase-a-2026-11-04`, based on the documentation research branch from PR #60.
PR: https://github.com/sokfrut-tech/Gemeni/pull/61 (open, draft, not merged).
Latest known head: `70801d7bdce54ac09ae0c3ded5a5f654f20f7026`.

### Existing factory infrastructure discovered
The repository already contains engineering-time factory infrastructure: `tools/agent_swarm/`, `tools/orchestrator/`, `tools/model_fabric/`, and workflows including `jarvis-factory.yml`, `jarvis-technicians.yml`, `jarvis-test-factory.yml`, and `factory-security.yml`. This is not proof that the Android runtime has an operational factory hive. The Android app still needs a bounded runtime coordinator and safe provider/skill boundary. External workers are considered active only when a real run and evidence report exist.

### Phase A code added in PR #61
- `core/ai/JarvisModelProvider.kt`: provider-neutral plain-text request/result contract and deterministic local parser-backed provider; no network implementation.
- `core/ai/ToolProposal.kt`: closed tool allowlist, strict JSON shape/string validation, and SAFE/CONFIRM classification. Dial is CONFIRM; there is no ACTION_CALL or executor.
- `core/ai/JarvisFactoryCoordinator.kt`: deterministic task state transitions, cancellation and bounded retry counter.
- JVM tests added for proposal validation and task-state transitions.

### Verification state
- GitHub Actions run 41906523425 (Factory Security Baseline) was observed queued for PR #61 at head `70801d7bdce54ac09ae0c3ded5a5f654f20f7026`.
- Exact-head unit-test/APK build status has not yet been observed. Do not claim tests pass until the relevant workflow jobs finish and logs are checked.
- No emulator, physical OPPO, voice runtime, local LLM, persistent memory integration, external agent execution, merge or release is claimed.

### Next
1. Inspect PR #61 security workflow results and trigger/check full build + unit tests on exact head.
2. Fix any compile/test/security failures without weakening tests.
3. Add tests for the local provider mapping and prove there is no network client/path in the offline provider.
4. Add the approval/execution bridge only after a dedicated exact-action confirmation design; keep AndroidJarvisToolRegistry the sole executor.
5. Integrate task persistence and factory UI only after reviewing existing memory/UI architecture; do not add speculative database dependencies.
6. Update this file, CURRENT_STATUS, CHANGELOG and the Phase A agent report with every new head SHA and actual evidence.
