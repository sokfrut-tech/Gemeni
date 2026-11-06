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
