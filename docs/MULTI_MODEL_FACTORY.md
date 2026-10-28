# Jarvis Factory — Multi-model brain

## Goal
Use several independent model workers behind the Director instead of trusting one model. The product repository remains `sokfrut-tech/Gemeni`; other repositories are read-only.

## Supported workers
- Qwen via `JARVIS_QWEN_BASE_URL`, `JARVIS_QWEN_API_KEY`, `JARVIS_QWEN_MODEL`
- DeepSeek via `JARVIS_DEEPSEEK_BASE_URL`, `JARVIS_DEEPSEEK_API_KEY`, `JARVIS_DEEPSEEK_MODEL`
- Any OpenAI-compatible endpoint via `JARVIS_LLM_BASE_URL`, `JARVIS_LLM_API_KEY`, `JARVIS_LLM_MODEL`

The router is `tools/agent_swarm/model_router.py`. It never stores credentials in Git.

## Roles
The Director fans the same evidence out to independent workers:
1. Android/ColorOS
2. Speech/ASR/VAD/KWS/TTS
3. UI/UX/runtime rendering
4. NDK/EGL/GLES/JNI
5. Functional tools/AIDL
6. Security
7. QA/device acceptance
8. Research

Each worker must return blockers, concrete fixes and tests. The Director merges evidence; disagreement is a blocker, not a vote.

## Real connection requirement
A model is **not** considered connected because its SDK is installed. A worker is connected only when a live `/chat/completions` request succeeds and its response is archived as CI evidence.

## Security
Do not commit API keys, cookies, tokens or private model endpoints. Configure them as GitHub Actions secrets/variables or use a self-hosted runner with the endpoint on a private network. GitHub recommends using encrypted secrets and minimum permissions for workflow credentials. See the official GitHub guidance: https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions

## Model selection
Qwen-Agent supports tool calling and MCP, and can work with Qwen3-family models. OpenHands Agent SDK supports multiple agents, tools, workspaces and remote/local agent servers. These are useful orchestration building blocks, but they do not replace the Jarvis Director or product runtime tests.

## Release rule
Model reviews accelerate engineering; they never replace Android runtime evidence. A green model review can never override a failed device/e2e test.
