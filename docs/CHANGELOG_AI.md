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


## 2026-11-04 — Kimi / Qwen / Z.ai open-source review
- Reviewed MoonshotAI/kimi-code (MIT), MoonshotAI/Kimi-Audio, QwenLM/Qwen-Agent (Apache-2.0), THUDM/GLM-4 and unofficial agent-next/zagent (MIT with stated non-commercial/personal interoperability restriction).
- Chose to adapt protocols and safety patterns rather than copy desktop/Python stacks into Android.
- Added docs/OPEN_SOURCE_AI_INTEGRATION_REVIEW_2026-11-04.md and docs/agents/BRIEF_JARVIS_MULTI_PROVIDER_FOUNDATION_2026-11-04.md.
- No runtime code, provider endpoint, API key, audio upload, model weights or external agent execution in this research slice. Next implementation is Phase A with strict typed proposals, risk policy and offline tests.
