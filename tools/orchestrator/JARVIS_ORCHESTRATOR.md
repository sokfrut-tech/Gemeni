# JARVIS ORCHESTRATOR — директор конвейера

## Назначение

Оркестратор — центральный директор проекта Jarvis. Он не является ещё одним разработчиком: его задача — держать непрерывный конвейер из специализированных агентов, распределять работу, сохранять состояние и не позволять объявить продукт готовым только потому, что APK собрался.

Рабочий каталог: **только `sokfrut-tech/Gemeni`**. Другие репозитории не изменяются.

## Производственная схема

`STATE -> PLAN -> PARALLEL REVIEW -> IMPLEMENT -> BUILD -> RUNTIME -> DEVICE -> SECURITY -> RELEASE GATE -> ARTIFACT -> STATE`

### Цеха

1. `android_coloros` — VoiceInteractionService, session, Accessibility, OPPO/ColorOS ограничения.
2. `speech` — AudioRecord, VAD, KWS, Sherpa Russian ASR, Piper TTS, offline lifecycle.
3. `functional` — intents, tools, AIDL, real actions (navigation/search/phone/etc.).
4. `ui_ux` — Compose, Breeno-inspired visual language, animations, state correctness.
5. `ndk_gles` — JNI, EGL, GLES2, AlphaVideo/OES, native lifecycle.
6. `security` — permissions, exported components, IPC boundaries, secrets, native attack surface.
7. `qa` — emulator/runtime tests, screenshots, functional assertions and regression tests.
8. `research` — external technical research and comparison with authoritative docs/open-source implementations.
9. `director` — this orchestrator; resolves conflicts, records evidence and decides which lane may advance.

## Правила директора

- APK build success is **not** a release criterion.
- A visual placeholder is a failure if the corresponding runtime function is absent.
- Every important claim needs evidence: test output, log, screenshot, APK inspection, or source reference.
- Agents work in parallel where their outputs are independent.
- Only the director may promote work from `review` to `implementation` and from `implementation` to `release`.
- Failed lanes remain visible; they are never silently skipped.
- A newer failure does not erase an older failure. The state ledger is append-only.
- Every handoff must leave enough information for another agent to continue without conversation history.
- No agent may modify repositories outside `Gemeni`.
- Secrets and API keys never enter source, artifacts, logs or state files.

## Model backends

The director supports OpenAI-compatible endpoints so the same worker protocol can use different engineering models. Recommended roles:

- strong coding model: implementation/root-cause lane;
- DeepSeek-family endpoint: code review/root-cause/rewrite suggestions;
- Qwen-family endpoint: Android/agent/tooling review;
- independent model: adversarial QA/release challenge.

Models are **engineering workers**, not runtime dependencies of the APK. If a model endpoint is unavailable, deterministic checks continue; the project is not falsely marked green.

Expected optional GitHub secrets/variables:

- `JARVIS_LLM_BASE_URL`
- `JARVIS_LLM_API_KEY`
- `JARVIS_LLM_MODEL`
- `JARVIS_LLM_MODEL_2`
- `JARVIS_LLM_MODEL_3`

## Release gate

Release requires all of the following:

- [ ] real UI, not placeholder screen
- [ ] microphone opens and records
- [ ] Russian ASR produces a transcript
- [ ] VAD detects speech boundaries
- [ ] KWS recognizes “Джарвис”
- [ ] intent dispatcher invokes a real Android action
- [ ] navigation/search/phone tools are verified on-device or emulator
- [ ] Piper/local TTS produces audio
- [ ] cloud fallback is optional and bounded
- [ ] VoiceInteraction session works
- [ ] Accessibility actions work where required
- [ ] AIDL IPC has a real caller and real effect
- [ ] GLES/AlphaVideo produces visible frames and survives lifecycle changes
- [ ] security checks pass
- [ ] runtime UI tests pass
- [ ] APK inspector passes
- [ ] artifact hash recorded
- [ ] current state and changelog updated

## Handoff contract

Every agent writes a machine-readable result with:

`agent`, `timestamp`, `commit`, `status`, `findings`, `evidence`, `files_changed`, `next_action`, `blocking`.

The director consumes these records on the next cycle. If an agent disappears, another agent can resume from the latest record without relying on chat memory.
