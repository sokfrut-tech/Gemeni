# Jarvis Factory — autonomous infrastructure plan

## Goal
Use only legitimately accessible compute. Never scrape, steal, reuse, or bypass credentials. The Director discovers workers, verifies health/capabilities, schedules work, stores evidence, and falls back when a worker disappears.

## Worker classes
1. GitHub-hosted GPU runner when enabled for the repository/account.
2. Self-hosted GPU runners explicitly registered by the owner.
3. Hugging Face Spaces/ZeroGPU workers explicitly owned or authorized by the project.
4. External vLLM/SGLang/OpenAI-compatible endpoints for which the project has credentials.

## Model roles
- Qwen: planning, code review, tool-use and multilingual reasoning.
- DeepSeek: independent reasoning/code review where an authorized endpoint or self-hosted weights are available.
- Specialist workers: Android/ColorOS, NDK/GLES, speech, UI, security, QA.

## Scheduling contract
A worker must expose:
- health endpoint
- model identity/version
- capabilities
- max context / concurrency
- authentication status
- evidence output location

The Director must mark workers unavailable on failed health checks. It must never claim a task ran on a worker unless a successful response/evidence record exists.

## GPU discovery
GitHub currently documents GPU-powered larger runners, including T4 16 GB configurations, but access depends on the account/organization plan. Hugging Face ZeroGPU dynamically allocates GPU capacity but is quota-limited. These are opportunities for authorized execution, not guaranteed free infrastructure.

## Security
Never put API keys in Git. Use GitHub Secrets or the worker's own secret store. Never obtain credentials from leaks or bypass provider access controls.

## Product release rule
A green build is not a release. Jarvis must pass functional runtime tests for microphone, ASR, VAD/KWS, intent dispatch, real Android actions, TTS, VoiceInteraction, Accessibility/AIDL, UI rendering and native graphics before release.

## Continuation
Every worker run must write its status/evidence to `docs/` or CI artifacts so another agent can resume without relying on chat history.
