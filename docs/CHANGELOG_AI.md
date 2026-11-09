# CHANGELOG_AI — persistent work log

This file is a durable replacement for relying on conversational history.

## 2026-10-28 — handoff system
- User requested that all meaningful work context be preserved in GitHub so other AI agents can continue without chat history.
- Added `docs/CONTINUATION_STATE.md` with architecture, known failures, acceptance criteria, agent protocol, and handoff rules.
- Added `docs/CURRENT_STATUS.md` with the current release state and hard release gate.
- Future agents MUST update this log after meaningful implementation or diagnosis.

## Product lessons
- A successful Gradle build does not prove runtime functionality.
- A launched Activity does not prove the assistant works.
- A screen containing the word `Джарвис` does not prove Jarvis functionality.
- A native `.so` inside an APK does not prove JNI/GLES rendering works.
- An ASR class in source does not prove microphone audio reaches the recognizer.
- A button labelled `Навигатор` does not prove navigation launches.

## Required evidence style
For every fix, record:
1. Problem and root cause.
2. Exact files changed.
3. Test/command executed.
4. Actual result.
5. Remaining risk.
6. Commit SHA.

Do not record secrets, API keys, tokens, private credentials, or personal data.


## 2026-11-09 — KWS/VAD runtime audit and handoff

- Inspected current PR dependency chain #123 → #124 → #126 and the separate CI PR #125.
- Confirmed #126 Factory Security Baseline run #382 passed CodeQL and secret scanning; this does not verify Android compilation or emulator behavior.
- Inspected #125 Build Jarvis run #200: debug APK build, JVM unit tests, security/native checks and APK manifest/ABI inspection passed; emulator runtime smoke failed on attempt 1. Re-ran the failed job; attempt 2 was still in progress at handoff.
- Confirmed the current runtime still uses fixed 4.5-second buffered push-to-talk capture and post-capture ASR. KWS/VAD pipeline contracts and adapters exist but are not yet wired into runtime/UI.
- Added [JARVIS_KWS_VAD_RUNTIME_INTEGRATION_2026-11-09.md](JARVIS_KWS_VAD_RUNTIME_INTEGRATION_2026-11-09.md) with the verified source state, next implementation slice, model/consent boundaries, test matrix and release blockers.
- No runtime source code was changed in this checkpoint. No PR was merged and no APK was released.
