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


## 2026-11-06 — safe provider-to-tool runtime slice
- Created integration branch from PR #62, restoring complete project handoff documents that PR #60 had truncated.
- Wired the active ASR transcript path through the provider-neutral model contract, strict proposal validation and the single Android action gateway.
- Added explicit approval/cancel controls for CONFIRM-risk dialer actions; no direct call is placed.
- Added unit tests for deterministic offline proposal generation, unsupported utterances and dialer-number validation.
- Clarified that the current local provider is a deterministic parser adapter, not an LLM. Runtime integration and CI verification remain pending; no merge or release claimed.

## 2026-11-07 — deterministic microphone permission test and CI checkpoint
- PR #97 adds an offline deterministic instrumentation test for the Android microphone permission prompt. It denies the permission and verifies return to the Jarvis screen; it deliberately does not start ASR model downloads.
- Corrected the settings smoke test selector to the real accessibility description `Модель и настройки` (previously it searched for `Настройки`, which did not match MainActivity).
- Latest PR #97 head: `138940d9fa0edc8d28e4dd90284487c07278b1e2`; run #166 (42341238471) was pending at checkpoint. Earlier emulator jobs remained non-terminal at the instrumentation step, and log retrieval returned Azure BlobNotFound.
- This change improves determinism but does not validate microphone capture, ASR, TTS, Qwen inference or OPPO device behavior. No merge/release claimed.
