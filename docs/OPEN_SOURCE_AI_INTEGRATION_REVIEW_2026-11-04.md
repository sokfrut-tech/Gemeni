# Open-source AI integration review — Jarvis (2026-11-04)

## Decision summary
Do not copy whole desktop/Python agent applications into the Android APK. Reuse their architecture and protocols behind Jarvis' existing Android-native safety gateway. Keep local speech and basic commands operational without network or provider key.

## Candidates reviewed

| Project | License / shape | Reusable value | Decision for Android Jarvis |
|---|---|---|---|
| [Qwen-Agent](https://github.com/QwenLM/Qwen-Agent) | Apache-2.0; Python agent framework | Tool/function calling, multi-step agent flow, MCP, RAG/memory abstractions, OpenAI-compatible model servers | Adopt patterns and protocol contracts, not Python runtime. Translate tool calls into typed proposals and route all actions through AndroidJarvisToolRegistry. |
| [Kimi Code](https://github.com/MoonshotAI/kimi-code) | MIT; terminal coding agent | Subagents, plan/explore/code roles, MCP configuration, lifecycle hooks, approval gates, checkpoints | Factory/development tool only; not Android runtime dependency. Reuse approval/checkpoint/handoff ideas. |
| [Kimi-Audio](https://github.com/MoonshotAI/Kimi-Audio) | Audio foundation model; Python/PyTorch-oriented and large model family | Audio understanding, ASR, speech conversation, audio captioning | Research candidate only. Do not bundle 7B-class stack in APK. Benchmark separately; Sherpa-ONNX remains mobile-first. |
| [Z.ai GLM ecosystem](https://github.com/THUDM/GLM-4) and [unofficial zagent](https://github.com/agent-next/zagent) | GLM model/runtime ecosystem; zagent is unofficial, MIT with non-commercial/personal interoperability restriction | Optional cloud reasoning; permission prompts, rewind/checkpoints, MCP bridge patterns | Do not embed zagent or depend on ZCode desktop runtime in Android. Consider GLM only through provider-neutral HTTPS adapter after official endpoint and terms review. |

## Highest-value capabilities to bring into Jarvis
1. Provider-neutral reasoning interface: local-first, optional Gemini/Qwen/Kimi/GLM-compatible backends. Provider selection and key storage explicit. No secrets in source, BuildConfig, logs or repository.
2. Typed tool proposals: model may propose OpenApp, Search, Navigate, Dial and other allowlisted operations; app validates schema/risk and calls existing AndroidJarvisToolRegistry. Unknown tools rejected.
3. Approval policy: safe read-only actions may execute; sensitive actions require NeedsConfirmation; destructive, payment, message-send, call placement, accessibility-control and arbitrary shell/file actions blocked unless separately designed and explicitly approved. Existing NeedsConfirmation is currently only a result type, not a complete approval gate.
4. MCP bridge later: only explicitly configured trusted servers. Android cannot assume arbitrary stdio subprocess servers can run; prefer a companion/gateway or supported HTTPS transport. Every tool gets Jarvis-side permission policy.
5. Local memory and retrieval: SQLite-backed, user-visible, deletable memory with provenance and consent. Never upload memory automatically.
6. Offline fallback: local commands, ASR, basic TTS and memory remain usable offline. Remote failure/timeout/quota falls back locally with clear status; never silently upload audio or personal data.
7. Factory agent roles: plan/research/implementation/review as scoped roles with isolated work and evidence-based handoff. This is development workflow, not unrestricted device control.

## Integration constraints
- Keep current Russian Sherpa-ONNX ASR and Android tool registry as execution backbone.
- No hidden/continuous microphone. Always-on mode requires opt-in, foreground service, persistent notification, visible stop action and bounded capture.
- Do not persist raw audio by default.
- No automatic calls/SMS, purchases, payments, deletion, arbitrary shell, unrestricted filesystem or silent accessibility actions.
- Provider calls need bounded timeouts, cancellation, response-size limits, TLS, redacted diagnostics and explicit privacy disclosure.
- API keys must be entered by user and stored with Android Keystore-backed encryption; never add provider secrets to CI.
- Do not add heavyweight Python/PyTorch dependencies to Android Gradle app.
- Respect each model's separate weight/license/acceptable-use terms; repository code license does not automatically license weights.

## Implementation sequence
### Phase A — safe foundation
- Add provider-neutral JarvisModelProvider contract and deterministic local-only implementation.
- Add provider configuration without secrets in source; disabled by default.
- Add typed tool-call proposal schema, parser validation and risk classification.
- Add tests for malformed JSON, unknown tool, argument bounds, unsafe action rejection, timeout/cancellation and offline fallback.
- Keep cloud adapters disabled until endpoint, model identifier, privacy UX and secure key storage are implemented.

### Phase B — first remote provider
- Implement one OpenAI-compatible adapter only after official endpoint and request/response compatibility are confirmed.
- Text-only prompts initially; do not upload microphone audio.
- User explicitly chooses provider and confirms data-sharing disclosure.
- Add mock-server contract tests; no live API key in CI.

### Phase C — MCP and memory
- Add trusted-server registry, capability allowlist, per-tool approval and audit log.
- Add local SQLite memory with inspect/delete controls and explicit save/recall policy.

### Phase D — model evaluation
- Compare Qwen, Kimi and GLM on fixed Russian benchmark: intent accuracy, schema validity, refusal correctness, latency, token/cost, privacy, offline fallback.
- Evaluate Kimi-Audio separately against Sherpa ASR and Piper TTS on Android-compatible hardware before any substitution.

## Acceptance tests
- Airplane mode: local parser, app launch, time and local ASR/TTS continue where available.
- Provider disabled/no key: no network request; clear local-only status.
- Provider timeout/network loss: cancellation works; local fallback used and reported.
- Dial proposal permits ACTION_DIAL only; never ACTION_CALL.
- Unknown tool, malformed args, oversized text, unsafe URL scheme, package or file path rejected without side effects.
- Sensitive proposal requires explicit confirmation.
- Secrets absent from logs, crash reports, Git history and diagnostics.
- No provider can bypass AndroidJarvisToolRegistry.

## Sources reviewed
- Qwen-Agent Apache-2.0: https://github.com/QwenLM/Qwen-Agent
- Qwen-Agent MCP guide: https://github.com/QwenLM/Qwen-Agent/blob/main/qwen-agent-docs/website/content/en/guide/core_moduls/mcp.md
- Kimi Code CLI MIT: https://github.com/MoonshotAI/kimi-code
- Kimi-Audio: https://github.com/MoonshotAI/Kimi-Audio
- Moonshot AI projects: https://github.com/MoonshotAI
- Z.ai GLM-4 source: https://github.com/THUDM/GLM-4
- Unofficial zagent and its license restriction: https://github.com/agent-next/zagent

## Evidence boundary
This is a source/repository assessment and integration design. No external agent was run, no provider credentials supplied, no live cloud request made, no model weights downloaded, and no new provider runtime is claimed as implemented. The next code change must be separately tested and recorded.