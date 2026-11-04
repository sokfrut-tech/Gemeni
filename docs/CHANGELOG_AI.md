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


## 2026-11-04 — command parser, package visibility and voice audit
- PR #58: Android tool gateway, typed result contract, assistant state lifecycle and quick-action routing.
- PR #59: extracted Russian command parser, added JUnit tests and Android launcher package visibility query.
- Added docs/JARVIS_VOICE_RUNTIME_GAP_ANALYSIS_2026-11-04.md after source inspection: VAD/KWS wrappers exist but runtime does not yet call them; microphone capture remains a fixed 4.5-second one-shot; no connected foreground microphone service.
- Updated docs/CONTINUATION_STATE.md and docs/CURRENT_STATUS.md with PR references, CI evidence caveats, safety boundaries and exact next steps.
- CI run 41903416302 on earlier PR #59 head: secret scanning and Kotlin compilation passed; APK build was still in progress at last check. Documentation commits changed the branch head, so exact-head verification remains required.
- No merge, physical OPPO validation or release publication performed.
