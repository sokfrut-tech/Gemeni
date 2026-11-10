# Chinese/open-source AI components for Russian Jarvis

Research date: 2026-11-10
Scope: public GitHub/Gitee-style repositories and OPPO public materials. This is a component audit, not a claim that AndesGPT weights or private OPPO APIs are public.

## Executive decision

Do not try to "translate AndesGPT" by modifying a Chinese APK or reverse-engineering Breeno. Separate the problem into:
1. Russian voice input/output,
2. local on-device reasoning,
3. optional authorized AndesGPT provider,
4. safe Android action execution.

A Chinese-origin model can already support Russian if its published model card/license and runtime support it. The Android UI and system prompts can be localized independently of model weights.

## Candidates

### 1. sherpa-onnx — primary speech stack
- Project: https://github.com/k2-fsa/sherpa-onnx
- Docs: https://k2-fsa.github.io/sherpa/onnx/
- Android, Kotlin/Java and ONNX Runtime support; local ASR/TTS/VAD without network after models are installed.
- Russian model families listed include Zipformer RU and GigaAM; Qwen3-ASR 0.6B ONNX lists Russian among its supported languages.
- Recommendation: keep the existing Android Sherpa integration boundary, benchmark Russian Zipformer/GigaAM against Qwen3-ASR 0.6B on OPPO Find X6 Pro. Do not replace a smaller fast model without measuring memory, latency and accuracy.

### 2. Qwen3-ASR via sherpa-onnx — candidate multilingual recognizer
- Model documentation: https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/
- The documented 0.6B ONNX model supports Russian, Chinese, English, German and many other languages.
- This is ASR (speech-to-text), not the conversational LLM and not AndesGPT.
- Recommendation: benchmark as an optional Russian/Chinese multilingual recognizer; its 0.6B footprint may be heavier than the current compact Russian model.

### 3. MiniMind-O Android — useful reference, not a drop-in
- Repository: https://github.com/YanTianlong-01/minimind-o-android-public
- Android multimodal voice pipeline, ONNX Runtime, streaming input/output and interruption.
- Current public preview README describes English speech input with Chinese text/audio output; Russian support is not established.
- Recommendation: inspect streaming, turn-taking and interruption design only. Do not import its preview model into Russian production.

### 4. OffHand — useful architecture reference
- Repository: https://github.com/MRDarshan7/OffHand
- Offline Android assistant with local Qwen2.5-1.5B via llama.cpp, constrained JSON output, independent validation, user-confirmed actions and durable outbox.
- Its published sample ASR is English Vosk; it is not a ready Russian assistant.
- Recommendation: borrow the strict structured-action pipeline and validation/confirmation pattern, not its ASR model or whole app.

### 5. Andee — configurable model-provider and device-agent reference
- Repository: https://github.com/kuafuai/andee
- Android agent can use an OpenAI-compatible endpoint or local backend; documents a tool contract and accessibility-based device control.
- README warns that some configured speech/model traffic leaves the device. Permissions and safety need careful review.
- Recommendation: use only as an architectural reference for provider abstraction; do not copy broad accessibility automation or its cloud defaults.

### 6. On-device-llm — reference for local model management
- Repository: https://github.com/sebytzr16/on-device-llm
- Android/Windows app built around llama.cpp and GGUF, supports Qwen/Gemma and local voice stack.
- Public README currently lists English/French/Spanish UI; Russian UI is not claimed.
- Recommendation: inspect model download/catalog, hash pinning, memory sizing and phone/PC hybrid inference. Do not transplant the Flutter app into native Kotlin Jarvis.

## AndesGPT / OPPO conclusion

- OPPO's public AI white paper describes AndesGPT and model/API/agent capabilities, but that is not equivalent to publishing third-party Android SDK access or model weights.
- OPPO developer portal/service access and terms must be confirmed for the Jarvis package.
- Do not call private Breeno endpoints, scrape traffic, redistribute OPPO model files, or claim AndesGPT is running when a Qwen fallback is active.
- Keep `AndesGptProvider` as an optional adapter. Implement the real adapter only after the official SDK/API, auth flow, model identifiers, supported devices, quotas and privacy terms are available.

## Russian localization plan

- UI: all Compose strings/resources in Russian; separate locale resources so future languages do not require code changes.
- System prompt: Russian persona/instructions, explicit locale `ru-RU`, preserve user speech verbatim for action extraction.
- ASR: choose Russian-capable model; test actual noisy speech and OPPO microphone capture.
- LLM: evaluate Russian instruction following and structured intent accuracy, not just Chinese/English benchmark scores.
- TTS: local Russian Piper/Kokoro-compatible voice only after verifying model license and Android runtime; otherwise clearly label Android system TTS.
- Actions: model produces typed intent JSON only. Kotlin validates allowed action, parameters, permissions and confirmation. Model text never directly executes shell/Intents.
- Model files: explicit consent, resumable download, fixed source revision, SHA-256 verification, atomic rename, corrupted-file cleanup, storage budget and user-visible delete option.

## Recommended Jarvis architecture

```
Mic / wake-word / push-to-talk
        |
Russian ASR (Sherpa-ONNX; Qwen3-ASR optional)
        |
Conversation Orchestrator (Kotlin; session + memory)
        |
ReasoningProvider interface
  |-- Local Qwen GGUF (llama.cpp; offline default)
  |-- Authorized AndesGPT (only when official access is granted)
        |
Typed Intent / Answer
        |
Kotlin validator + permission/confirmation policy
  |-- Navigator / browser / phone / reminders / settings
  |-- safe fallback when model/network unavailable
        |
Russian TTS (Piper or verified local engine)
```

## Priority order

1. Fix current Jarvis runtime, download integrity, mic lifecycle and Android actions.
2. Benchmark Russian ASR candidates on the target OPPO device.
3. Add local LLM provider interface and evaluate small Qwen GGUF sizes against RAM, speed and Russian quality.
4. Add Russian local TTS and full interruptible conversation loop.
5. Enable AndesGPT only if OPPO grants an official supported interface.
6. Port only proven improvements from reference projects; retain licenses and attribution for every dependency/model.

## Sources checked

- sherpa-onnx Android/local docs: https://k2-fsa.github.io/sherpa/onnx/
- Qwen3-ASR supported languages and ONNX docs: https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/
- MiniMind-O Android: https://github.com/YanTianlong-01/minimind-o-android-public
- OffHand: https://github.com/MRDarshan7/OffHand
- Andee: https://github.com/kuafuai/andee
- On-device-llm: https://github.com/sebytzr16/on-device-llm
- OPPO AI Smartphone White Paper: https://www.oppo.com/content/dam/oppo/common/mkt/footer/OPPO-AI-white-paper-EN.pdf

## Important limits

Repository READMEs are project claims, not independent benchmarks. Before importing code or models, inspect the exact license, maintenance activity, dependency tree, APK footprint, Android API compatibility and target-device performance. Russian support must be verified on Russian test audio; a multilingual label alone is insufficient.
