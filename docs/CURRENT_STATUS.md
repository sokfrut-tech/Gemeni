# CURRENT STATUS — Jarvis / Gemeni

Last handoff update: 2026-10-28

## Overall state
**NOT RELEASE READY.**

The project builds in CI, but the product is not accepted until runtime tests prove real voice, tools, system integration, and visual rendering.

## Latest user-observed regression
On the user's OPPO device, the installed app displayed a black screen with only the text `Джарвис / Русский голосовой ассистент / для ColorOS`. The user reported that the app had no working functionality. This is a hard regression and the release gate must catch it.

## What has been identified
- Previous MainActivity UI contained placeholder behaviour.
- Microphone interaction was not connected to AudioRecord/ASR.
- Quick-command buttons changed text instead of executing Android intents.
- VoiceInteractionService/session implementations were incomplete placeholders.
- Accessibility and AIDL paths contained no-op behaviour.
- Native renderer linkage was not sufficient evidence that a visible frame was rendered.

## Work direction
The project is being converted from a buildable scaffold into an evidence-tested product.

### Required runtime chain
`KWS -> VAD -> AudioRecord -> Sherpa Russian ASR -> Intent/Tool Registry -> Android action -> Piper TTS -> visual response`

### Required system chain
`VoiceInteractionService -> VoiceInteractionSessionService/Session -> Jarvis core -> tools/AIDL -> UI`

### Required visual chain
`Compose shell -> native GLSurfaceView -> JNI -> EGL/GLES2 -> visible animated sphere/AlphaVideo-style rendering`

## AI swarm
Specialist review tracks are defined for:
- ColorOS/Android system integration
- UI/UX
- NDK/GLES/JNI
- Speech stack
- Functional tools/AIDL
- Security
- QA/release

External models may be used only through explicitly authorized connections. The repo contains the durable handoff specification; agents must leave reports and test evidence.

## Release gate
Do not provide an APK to the user as “ready” unless:
- clean install launches the intended UI;
- screenshot is not the black placeholder screen;
- microphone capture is verified;
- Russian ASR path is verified;
- at least three real commands execute their intended Android actions;
- TTS response is verified;
- VoiceInteraction registration is verified;
- Accessibility path is verified where applicable;
- AIDL calls are non-no-op;
- native renderer produces a visible frame;
- security/ABI/manifest checks pass;
- emulator smoke tests pass;
- OPPO device test is documented when available.

## 2026-11-06 — repository-wide AI architecture audit

- Full recursive default-branch inventory completed for all 11 repositories visible in the connected GitHub installation; targeted source review across Kotlin, C++17, Python, Java, JavaScript, SQL, Bash, Protobuf, Gradle, YAML and XML is recorded in [REPOSITORY_WIDE_AI_AUDIT_2026-11-06.md](REPOSITORY_WIDE_AI_AUDIT_2026-11-06.md).
- Product identity clarified: Jarvis is an AI agent with reasoning, planning, memory, tool use, perception, controlled learning and verified execution—not only voice command recognition.
- Main-branch audit found real building blocks (AudioRecord, Sherpa ASR/VAD/KWS/TTS wrappers, native GLES, factory scripts) but also critical stubs: LocalLlmEngine echoes the prompt; JarvisToolRegistry mostly echoes commands; memory is simple SharedPreferences; Accessibility and parts of AIDL/VoiceInteraction are no-op.
- Active Activity runtime uses bounded push-to-talk -> ASR -> basic intent routing -> limited Android actions -> system TTS. Standalone KWS/VAD/Piper classes are not connected end-to-end in this inspected runtime path.
- minSdk=29 in Gemeni conflicts with the desired Android 7/API 24+ target.
- PRs #58/#59/#60/#61/#62/#63 and #65 remain open/draft as observed; they contain useful implementation work but are branch-dependent and not safe to merge blindly. Exact-head logs confirm #61 has Kotlin compilation errors; #62 fixes compilation and builds the APK, but connected Android instrumentation still fails; #63 passes CodeQL and secret scanning but its emulator job times out during boot. See [the 2026-11-06 PR integration checkpoint](engineering/2026-11-06-pr-integration-checkpoint.md) for exact SHAs, logs and the safe integration sequence.
- Latest Director/Repair Crew workflow successes are not proof of autonomous coding agents: specialist lanes currently run deterministic evidence collectors; Test Factory is mostly static grep/file-contract checks.
- Overall product remains NOT RELEASE READY. No physical OPPO runtime test was performed in this audit.