# AI AGENT PROTOCOL — Jarvis / Gemeni

## Purpose
Enable any authorized AI coding agent to continue the project safely after another agent stops.

## First actions for every agent
1. Read `docs/CONTINUATION_STATE.md`.
2. Read `docs/CURRENT_STATUS.md`.
3. Read the latest section of `docs/CHANGELOG_AI.md`.
4. Inspect relevant source files before editing.
5. Check current `main` HEAD and recent commits.
6. Run the smallest relevant test before changing code when possible.

## Scope
Work only inside `sokfrut-tech/Gemeni` unless the user explicitly authorizes another repository. Other repositories may be inspected if necessary, but do not modify them.

## Agent roles
### android_coloros
Owns Android system integration: VoiceInteractionService, session, ColorOS compatibility, manifest, setup flow, permissions.

### ui_ux
Owns Compose UI, animation, visual states, mascot, cards, accessibility of controls. Must verify that controls execute real behaviour.

### ndk_gles
Owns C++17/JNI/EGL/GLES2, native lifecycle, shader correctness, renderer visibility and crash safety.

### speech
Owns AudioRecord, Silero VAD, KWS, Sherpa-ONNX Russian ASR, Piper TTS, model lifecycle and offline path.

### functional
Owns intent dispatch, tool registry, AIDL IPC, real Android actions, confirmation/safety rules.

### security
Owns permissions, exported components, IPC validation, secrets, network security, model integrity, ProGuard/R8 and attack surface.

### qa
Owns release gates, emulator smoke tests, runtime diagnostics, screenshot evidence, regression tests and artifact verification.

## No-placeholder rule
A feature is NOT implemented if it only:
- changes a label;
- shows a Toast/dialog;
- toggles a Boolean without invoking the real subsystem;
- contains an unused class;
- contains an unused native method;
- ships a model file without invoking inference;
- registers a service without exercising it.

## Evidence rule
Every claimed fix needs evidence. Preferred evidence:
- unit/instrumentation test result;
- emulator logcat;
- screenshot/video evidence;
- native log output;
- APK inspection;
- exact GitHub Actions run.

## Failure rule
If a test fails, do not weaken the test merely to make CI green. Record the failure in `docs/FAILURES.md` or the relevant agent report, diagnose root cause, then fix it.

## Handoff rule
Before finishing:
- update `docs/CURRENT_STATUS.md`;
- append to `docs/CHANGELOG_AI.md`;
- write/update the relevant `docs/agents/<role>.md` report;
- include commit SHA and test evidence.

## Security rule
Never put API keys, access tokens, passwords, private user data, or signing keys into the repository or handoff documents.
