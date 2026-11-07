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
- Latest Director/Repair Crew workflow successes are not proof of autonomous coding agents: specialist lanes currently run deterministic evidence collectors; Test Factory is mostly static grep/file-contract checks. The 2026-11-06 Director artifact review also found the configured Qwen endpoint returned HTTP 404 and all seven OpenRouter role reviews returned HTTP 401; live LLM reviewers are currently unavailable. The workflows now label this state explicitly instead of implying that a successful job means a successful model review.
- Overall product remains NOT RELEASE READY. No physical OPPO runtime test was performed in this audit.

## 2026-11-06 — safe provider-to-tool runtime integration (verification pending)

A dedicated integration branch `integration/jarvis-safe-agent-runtime-2026-11-06` starts from PR #62, restores the three truncated handoff documents from current main, and adds a vertical slice from Russian ASR through a provider interface, strict typed proposal validation and the Android tool gateway. Dialer actions now require a visible user approval and still use ACTION_DIAL only. Tests were added for the deterministic local provider and malformed proposals.

Important limitation: `LocalOnlyJarvisModelProvider` is a deterministic parser adapter, not a neural LLM or autonomous reasoning engine. This work establishes the safe execution boundary; a real on-device model remains future work. See [the implementation report](engineering/2026-11-06-safe-agent-runtime-integration.md). CI has not yet verified this integration branch. Product remains **NOT RELEASE READY**.

## 2026-11-07 — emulator test reliability checkpoint

- PR #92 (JNI runtime), #93 (emulator diagnostics), #94 (Unicode JNI + cancellable model installer), #95 (conversation transcript display), and #96 (installer cancellation-hook tests) remain separate open/draft changes; none is merged. APK builds and CodeQL/secret scans have passed on several exact heads, but the emulator smoke-test jobs for #93–#95 and #96 were still reported in progress at this checkpoint. This is not evidence of successful emulator execution.
- PR #97 adds a deterministic microphone-permission UI test that deliberately denies RECORD_AUDIO. It does not download ASR assets and does not claim live microphone/ASR operation. It also corrects the settings test to use the actual Compose accessibility label, `Модель и настройки`.
- PR #97 branch: `test/jarvis-permission-ui-no-network-2026-11-07`; latest recorded head at this checkpoint: `138940d9fa0edc8d28e4dd90284487c07278b1e2`. Workflow run #165 (42341080195) was still running at an older branch head; the newer run #166 (42341238471) was pending after the follow-up test-label correction.
- The earlier emulator jobs are abnormally long-running at the step `Run Android emulator smoke tests`; job logs could not be retrieved through the connected GitHub log endpoint (Azure BlobNotFound). Do not label them passed or failed until GitHub reports a terminal result. PR #93 includes failure-only diagnostics, which should be inspected if that job terminates unsuccessfully.
- Test strategy going forward: keep emulator UI/permission smoke tests deterministic and offline; test ASR/model download/inference separately with explicit network/model prerequisites; validate Qwen on the physical OPPO Find X6 Pro before claiming on-device or offline generation.
- Product remains **NOT RELEASE READY**. No merge, release, or physical OPPO verification is claimed.


## 2026-11-07 — bounded emulator diagnostics follow-up

- PR #97 latest head: `484a33dc327640d75b0d96428f0dc901e04a7b4b`; run #168: https://github.com/sokfrut-tech/Gemeni/actions/runs/42342370703.
- On the earlier PR #93 diagnostics branch, the emulator smoke step did fail, but the subsequent diagnostic shell step itself remained in progress and no diagnostic artifact was produced. This shows the original diagnostic commands can also hang when ADB/emulator communication is unhealthy; it does not identify the underlying test failure.
- PR #97 workflow now runs each ADB diagnostic command under a short timeout and uploads emulator logs plus connected-test reports/results on failure. This is a diagnostics reliability improvement, not a test pass.
- At the time of this update, run #168 had completed secret scanning successfully; CodeQL was still running and the emulator job had not yet started. Do not infer a result for either pending job.
- Older PR #95 emulator run #164 (42340498895) ended in failure after the permission-test revision was not yet present. PR #97 is the intended deterministic test path. Previous run #167 was cancelled by the newer workflow commit.
- APK builds from previous exact heads are successful, but instrumentation still needs a fresh terminal result. Product remains **NOT RELEASE READY**; no merge/release and no physical OPPO test claimed.
