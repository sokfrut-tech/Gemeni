# JARVIS / GEMENI — CONTINUATION STATE

> This file is the persistent hand-off record for any future AI agent. Read it before modifying the project. Do not rely on chat history being available.

## 1. Project identity
- Repository: `sokfrut-tech/Gemeni`
- Protected scope: work only in this repository unless the user explicitly authorizes another repository.
- Main branch: `main`
- Product: Jarvis — Russian-speaking replacement/extension for Chinese OPPO/ColorOS Breeno.
- Target: OPPO Chinese firmware / ColorOS, with a usable installable Android APK.
- User priority: real functionality + visual quality + Russian language; not a mockup.

## 2. Non-negotiable acceptance rule
A green Gradle build is NOT product readiness.
An APK is releasable only after proving at runtime that the actual installed APK provides:
1. Real UI/visual layer, not a text-only placeholder.
2. Real microphone capture.
3. Russian ASR (Sherpa-ONNX) operating on captured audio.
4. VAD and wake-word/KWS integration where implemented.
5. Intent/tool dispatch producing real Android actions.
6. Real TTS response (Piper/VITS preferred; fallback must be explicitly labelled).
7. VoiceInteractionService/session registration and runtime behaviour.
8. Accessibility actions where required.
9. AIDL IPC methods that actually perform work.
10. Native GLES/EGL/AlphaVideo rendering that is visibly rendered, not merely linked.
11. Model download/update flow and offline operation after models are present.
12. Security/ABI/manifest inspection.
13. End-to-end tests on emulator and, ultimately, the real OPPO device.

## 3. Current known failure from user testing
The user installed an APK that launched to a black screen containing only:
- `Джарвис`
- `Русский голосовой ассистент`
- `для ColorOS`

The user correctly identified that this is not a working product. Treat this as the canonical regression to prevent. Do NOT claim success merely because the Activity starts.

## 4. Important forensic findings
Earlier code contained multiple placeholders:
- MainActivity microphone button only changed UI state; it did not start AudioRecord.
- Quick commands only changed text; they did not launch real intents.
- VoiceInteractionService was effectively empty.
- VoiceInteractionSession was effectively empty.
- AccessibilityService was effectively empty.
- Remote AIDL service methods were empty/no-op.
- ASR implementation existed but was not connected to the UI/runtime voice path.
- Model downloader existed but was not sufficient proof of an active speech pipeline.

These findings must remain in regression tests.

## 5. Current intended architecture
Kotlin / Jetpack Compose / Coroutines
C++17 / NDK r26b / JNI / EGL / OpenGL ES 2.0
System:
- VoiceInteractionService
- VoiceInteractionSession / VoiceInteractionSessionService
- AccessibilityService
- RomUpdateReceiver
- AIDL `IJarvisService` + callback
Speech:
- Sherpa-ONNX Russian ASR
- Silero VAD
- Hotword KWS: `Джарвис`
- Piper TTS / VITS ONNX
- Gemini cloud fallback
Rendering:
- AlphaVideo / OES split-alpha style visual sphere
- Native GLES renderer
UI:
- Jarvis/Breeno-inspired assistant surface
- dynamic cards
- shelf widget
- setup/onboarding

## 6. Runtime voice target
Expected flow:
`"Джарвис" -> KWS -> VAD -> AudioRecord -> Russian ASR -> intent/tool registry -> real Android action -> Piper TTS -> visual/card response`

Example acceptance tests:
- `Джарвис, открой Яндекс Навигатор` => actual navigation app launch/intent, not text change.
- `Джарвис, найди ближайшую заправку` => actual search intent/tool.
- `Джарвис, позвони ...` => real ACTION_DIAL/call flow with appropriate confirmation.
- Arbitrary Russian sentence => recognized text displayed and/or cloud fallback if local intent is unsupported.

## 7. Visual acceptance
The UI must visibly contain the intended assistant experience:
- animated central sphere/visualizer;
- mascot/assistant visual;
- listening state;
- processing state;
- response state;
- microphone control that actually controls the voice pipeline;
- cards/actions that actually execute actions.

A black screen with static text is an automatic FAIL.

## 8. Native rendering acceptance
Do not count native library linkage as proof.
Test:
- `System.loadLibrary` succeeds;
- JNI symbols resolve;
- `nativeCreate/nativeResize/nativeDraw/nativeDestroy` execute;
- `onSurfaceCreated/onSurfaceChanged/onDrawFrame` execute;
- EGL/GLES errors are captured;
- rendered frame is non-black/non-empty under the expected state;
- lifecycle survives pause/resume.

## 9. AI-agent swarm
The repository is intended to support independent specialist agents:
- Android/ColorOS system integration
- UI/UX
- NDK/GLES/JNI
- speech (ASR/VAD/KWS/TTS)
- functional tools/AIDL
- security
- QA/release

Agent work must be evidence based. Each agent must report:
- files inspected;
- concrete defects;
- fixes proposed/applied;
- commands/tests run;
- test results;
- remaining risks;
- exact commit(s).

Do not let agents silently rewrite unrelated areas.

## 10. Required evidence artifacts
Every substantial iteration should leave durable evidence in the repository under `docs/evidence/` where practical:
- `CURRENT_STATUS.md`
- `CHANGELOG_AI.md`
- `TEST_MATRIX.md`
- `RUNTIME_DIAGNOSTICS.md`
- `FAILURES.md` when failures exist
- agent reports under `docs/agents/`

Never store secrets, API keys, tokens, personal data, or private credentials in these files.

## 11. Handoff protocol
Before ending any work session, update:
1. `docs/CONTINUATION_STATE.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/CHANGELOG_AI.md`
4. relevant agent report(s)
5. test evidence / failure evidence

The next agent must be able to continue from these files without seeing the previous chat.

## 12. Current development policy
- Prefer root-cause fixes over cosmetic patches.
- Do not hide failures by weakening tests.
- Do not replace real functionality with placeholder text merely to pass UI tests.
- Do not claim a feature is implemented until the execution path is connected and tested.
- Do not mix unrelated repositories.
- Do not overwrite user-provided source/reference material without preserving it.
- Search official Android/OPPO/ColorOS documentation and reputable technical/community sources when platform behaviour is uncertain; record useful findings and URLs in agent reports.
- For external AI agents, use only explicitly connected/authorized services. Never fabricate that another model/agent was run.

## 13. User's explicit product goal
The final product should be beautiful, Russian-speaking, functional, installable, and suitable for the user's Chinese OPPO/ColorOS device. The user wants the implementation to be based on their supplied/reference Breeno materials and source analysis, while remaining an original implementation rather than pretending to be the proprietary Breeno source.

## 14. 2026-11-06 — repository-wide AI audit and product identity

Read first: [docs/REPOSITORY_WIDE_AI_AUDIT_2026-11-06.md](REPOSITORY_WIDE_AI_AUDIT_2026-11-06.md).

The owner clarified that Jarvis is an artificial intelligence, not merely a voice assistant. Its target architecture must include reasoning/planning, provider/model routing, user-controlled memory, typed tools/skills, device perception, voice pipeline, controlled learning, specialist-worker coordination and action verification. Free/local/offline-first operation remains mandatory for essential features; cloud providers are optional and paid usage is not assumed.

All 11 repositories visible in the connected GitHub installation were inventoried by recursive default-branch tree, with targeted source inspection across Kotlin, C++17, Python, Java, JavaScript, SQL, Bash, Protobuf, Gradle, YAML and XML. Reusable work is mapped in the audit report. Do not restart from scratch or lose unmerged work.

Critical verified source gaps on main: LocalLlmEngine is an echo placeholder; JarvisToolRegistry is mostly echo/clock; memory is SharedPreferences key/value; Accessibility callbacks are empty; AIDL start/stop listening are no-ops; VoiceInteraction service is only lifecycle boilerplate. Current Activity runtime is a short push-to-talk ASR path and does not wire KWS/VAD/Piper into one full pipeline. Current app minSdk 29 conflicts with the API 24+ goal. The product remains NOT RELEASE READY.

Preserve and reconcile the open PR dependency chain #58 -> #59 -> #60 and Phase A sibling PRs #61/#62 plus CI follow-up #63. PR #61 has an observed Kotlin compilation failure; PR #62 has an observed emulator split-APK install/ADB failure. No draft PR was merged during this audit. Build/CI workflow success is not equivalent to real AI reasoning or OPPO runtime validation.

Next: create one integration branch that combines the typed Android gateway/parser with the corrected local provider/proposal/task coordinator; resolve compilation and emulator test reliability; then implement a real local inference engine, persistent memory, and connected voice/action/verification loop. Keep all risky actions behind explicit confirmation and user-controlled permissions.

## 2026-11-06 — PR integration diagnosis refreshed

Read [the exact PR integration checkpoint](engineering/2026-11-06-pr-integration-checkpoint.md) before changing branches.

Important correction to older notes:
- PR #61 exact-head Kotlin compilation fails because the parser branch (#59) is not included in its base and because `ToolProposal.kt` uses early returns inside an expression-bodied function.
- PR #62 is the compile repair: exact-head logs show `:app:compileDebugKotlin` and `:app:assembleDebug` succeeded. Its later connected instrumentation test failed.
- PR #63 separates the CI jobs. Its CodeQL and secret-scan jobs passed; emulator smoke test failed with `Timeout waiting for emulator to boot`.
- PR #65 is docs-only and its checks passed, but GitHub refused merging while it remained draft. Do not bypass draft state or merge it by force.

Do not describe #62 as a Kotlin compile failure. Preserve its compile correction. Next work is a carefully reconciled integration branch combining the #58/#59/#60 gateway/parser dependency chain with the corrected Phase A implementation from #62, retaining the #61 tests and documentation where they add value. Refresh all branch heads and inspect exact diffs before any branch mutation. No PRs were merged and no app source was changed in this checkpoint.


## 2026-11-06 — safe agent runtime integration handoff

Read [the implementation report](engineering/2026-11-06-safe-agent-runtime-integration.md) before continuing.

Branch: `integration/jarvis-safe-agent-runtime-2026-11-06`, created from PR #62 exact head and intended for review against the PR #63 CI branch.
- Restored full `CONTINUATION_STATE.md`, `CURRENT_STATUS.md` and `CHANGELOG_AI.md` from current main because PR #60 truncated those durable records.
- Active microphone runtime now uses the provider-neutral `JarvisModelProvider` seam with `LocalOnlyJarvisModelProvider`, validates typed proposals, then routes to the sole Android action gateway.
- The local provider remains deterministic parser logic, not an LLM. Do not claim local neural reasoning, autonomous planning or self-learning.
- Only SAFE proposals execute automatically. Dialer proposals require explicit in-app approval and are executed with ACTION_DIAL only. Cancellation and controller close clear pending state.
- Unit tests were added for proposal creation, unsupported commands and dialer validation. Exact branch CI is pending.
- PR #63 emulator shell quoting was simplified in commit `82f2358a27415598deb35f742b33996a62719ca2`; inspect the new run. Earlier failure also showed ADB failures, color-buffer errors and instrumentation process crash.
- Do not merge the original PR chain blindly. Keep the restored docs, compare exact source diffs and run JVM/build/security/emulator checks before any merge.


## Phase B handoff — real local reasoning model

Before implementing neural inference, read `docs/agents/BRIEF_LOCAL_REASONING_RUNTIME_PHASE_B_2026-11-06.md`. It defines the runtime evaluation (llama.cpp / MLC / ONNX Runtime GenAI), model licensing, on-device benchmark, structured planning, offline/privacy gates and acceptance tests. Current provider remains deterministic parser logic; do not call it a local LLM.
