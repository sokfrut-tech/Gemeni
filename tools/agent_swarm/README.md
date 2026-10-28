# Jarvis Agent Swarm

This directory is an isolated engineering/review harness for **Gemeni only**. It does not modify other repositories.

## Purpose

Run independent reviewer roles against the current source tree before an APK is considered releasable:

- `android_coloros` — VoiceInteractionService, ColorOS integration, permissions, lifecycle
- `ui_ux` — Compose UI, visual states, real click actions, accessibility semantics
- `ndk_gles` — JNI, EGL/GLES2, surface lifecycle, native crashes
- `speech` — AudioRecord, VAD, KWS, Sherpa ASR, Piper TTS, offline model lifecycle
- `functional` — real intent/tool execution, AIDL, navigation/search/phone
- `security` — exported components, permissions, secrets, native loading, model downloads
- `qa` — emulator smoke/e2e strategy and release gates

## Model backends

The harness is model-provider neutral. It can call any OpenAI-compatible endpoint through `JARVIS_LLM_BASE_URL` and `JARVIS_LLM_API_KEY`. This allows a local Ollama/vLLM/Qwen endpoint or a DeepSeek-compatible endpoint without putting model weights in the Android repository.

DeepSeek's current DeepInfer is aimed at server-class NVIDIA SM100 / Huawei Ascend 950, so it is **not** treated as an Android-device runtime. It may be used as an external review backend where hardware is available. Qwen-Agent is suitable for tool/MCP orchestration and can use self-hosted OpenAI-compatible services.

No closed-source or unauthorized access is required or attempted.

## Commands

```bash
python3 tools/agent_swarm/run_swarm.py --repo . --out build/agent-review
```

With a local OpenAI-compatible model:

```bash
export JARVIS_LLM_BASE_URL=http://127.0.0.1:11434/v1
export JARVIS_LLM_MODEL=qwen3.5
python3 tools/agent_swarm/run_swarm.py --repo . --out build/agent-review --llm
```

Without an LLM endpoint the harness still produces deterministic evidence: source inventory, forbidden placeholders, exported components, test inventory, native library inventory and a release-gate report. It never labels the product functional solely because compilation succeeds.
