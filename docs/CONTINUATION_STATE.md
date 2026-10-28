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
