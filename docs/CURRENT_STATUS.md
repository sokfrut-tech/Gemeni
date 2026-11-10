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

## 2026-11-09 — voice pipeline continuation checkpoint

**Current state: NOT RELEASE READY.** No PR in the current voice/CI chain has been merged and no APK release is authorized.

- PR #123: responsive Compose home and explicit listening Stop control; Draft, stacked on `main`.
- PR #124: serialized single-owner AudioCaptureCoordinator; Draft, stacked on #123.
- PR #126: injectable KWS/VAD/local-ASR contracts and fail-closed interruption handling; Draft, stacked on #124. Factory Security Baseline #382 passed CodeQL and secret scanning only; Android build/JVM/emulator checks for this exact head remain unverified.
- PR #125: Android build/emulator workflow; Draft. Build Jarvis run #200 attempt 1 passed APK build, JVM unit tests, security/native checks and APK manifest/ABI inspection, but failed Android emulator runtime smoke. Failed job was re-run as attempt 2 and was still in progress at this checkpoint.

### Verified runtime gap

`JarvisRuntimeController.listenOnce()` still captures a fixed 4.5-second push-to-talk sample into memory, then runs Russian ASR after capture stops. The new KWS/VAD pipeline is not wired to the controller or Compose UI. Existing KWS/VAD adapters and model managers are building blocks, not proof of a working wake-word session.

### Immediate next milestone

Implement a user-started, visibly active wake-word session using one lifecycle-managed capture coordinator shared by all voice modes. Keep model downloads explicitly consent-gated; keep Stop synchronous with resource cleanup; add typed UI states; keep phone-call/system-audio interruption fail-closed; and validate thermal policy before inference. Full acceptance criteria and the staged plan are in [JARVIS_KWS_VAD_RUNTIME_INTEGRATION_2026-11-09.md](JARVIS_KWS_VAD_RUNTIME_INTEGRATION_2026-11-09.md).

No emulator, physical OPPO, always-on wake-word, Russian acoustic accuracy, or full voice-to-action acceptance is claimed by the above checks.

## 2026-11-10 — Android assistant integration and APK recovery checkpoint

The user paused the separate Jarvis AI Core track pending future briefs and requested continued work on the Jarvis Android Assistant only. Do not expand AI Core work in this iteration.

### Current integration branch
- Integration branch: `integration/jarvis-assistant-ci-smoke-repair-2026-11-10`
- Draft PR: [#133](https://github.com/sokfrut-tech/Gemeni/pull/133)
- Exact current head at handoff: `55c21c0add369d446f23366775181d85272123fa`
- Base: `main`
- This branch includes the stacked implementation from #123 (responsive UI), #124 (single-owner AudioCaptureCoordinator), #126 (KWS/VAD/ASR lifecycle contracts), and the Android build workflow from #125. It is a consolidation/qualification branch; none of those PRs has been merged by this checkpoint.

### Current exact-head CI
- Build Jarvis run [#42868868943](https://github.com/sokfrut-tech/Gemeni/actions/runs/42868868943): in progress at SDK/NDK setup when inspected.
- Factory Security Baseline run [#42868869102](https://github.com/sokfrut-tech/Gemeni/actions/runs/42868869102): in progress; secret scanning succeeded, CodeQL was installing Android build components.
- Previous attempts on earlier branch heads were cancelled by concurrency after the integration branch received additional commits. Only the latest run on the exact head above is authoritative.

### Previous APK recovery artifact
- Build Jarvis run [#201](https://github.com/sokfrut-tech/Gemeni/actions/runs/42697490427), tested commit `19df605ba1962c46d67845ed74c56389901c765d`.
- APK assembly, JVM tests, security/native checks and manifest/ABI inspection passed; all three emulator UI smoke assertions failed.
- The exact APK was downloaded and archived outside the repo. SHA-256: `f03c6ad05e330354a101bcb8724788076f8e42091109e226b8183a7221dc5775`.
- This is an experimental debug backup, NOT a release-qualified APK. It predates the responsive UI/audio integration and must not be presented as a working OPPO release.

### Diagnosed smoke-test failures
- The old launcher test searched for a quick action without scrolling the old fixed-height screen.
- Settings actually opened: logcat recorded `com.android.settings/.Settings$AccessibilitySettingsActivity`; the test was a false negative because it searched for a localized heading text node.
- The microphone test expected a legacy content description; the current responsive UI exposes `ГОВОРИТЬ С ДЖАРВИСОМ` and a consent-gated model preflight.
- PR #133 replaces those assertions with scrolling selectors, a foreground Settings-package assertion, and a test that verifies the ASR model consent dialog and decline path without downloading a model or opening an unattended microphone session.
- Failure diagnostics now attempt to save screenshot + UI hierarchy and pull them before emulator shutdown.

### APK size correction
Inspection of the exact #201 APK found 8 desktop-only Sherpa files under `sherpa-onnx/native/`, totalling 114,746,624 bytes (109.4 MiB). PR #133 copies only the packaging exclusion from the earlier #98 idea while preserving all four configured Android ABIs. The Build Jarvis workflow now fails if `sherpa-onnx/native/` appears in the APK. Do not claim a size reduction until the new artifact is built and measured.

### Remaining gates
1. Wait for exact-head Build Jarvis and Factory Security Baseline results on `55c21c0...`; refresh before relying on any status.
2. Confirm all JVM tests pass, including #124/#126 microphone and voice pipeline lifecycle tests.
3. Inspect new APK archive: desktop Sherpa payload absent, Android JNI libraries present for configured ABIs, manifest and alignment valid; record exact size and SHA-256.
4. Confirm updated emulator UI smoke suite passes and inspect uploaded XML/logcat/screenshot/UI hierarchy artifacts.
5. Keep PR #133 Draft until exact-head CI is green. Do not merge, publish or promote the debug APK to release-ready.
6. Physical OPPO Find X6 Pro/ColorOS validation remains separate and mandatory for real microphone capture, model load, voice lifecycle, call interruption, KWS acoustic quality, TTS, thermal/battery behavior and system integration.

## 2026-11-10 — PR #133 UI smoke results and CI evidence fix

Active scope: **Jarvis Android Assistant only**. The owner has paused the separate Jarvis AI Core direction pending future briefs. Do not start AI Core implementation in this continuation.

### Integration PR and exact source
- Repository: `sokfrut-tech/Gemeni`
- Draft integration PR: [#133](https://github.com/sokfrut-tech/Gemeni/pull/133)
- Branch: `integration/jarvis-assistant-ci-smoke-repair-2026-11-10`
- Current branch head: `ae5038e17f4335fad0693203f69580433da0af52`
- Consolidates the PR #123 responsive Compose UI, #124 serialized single-owner audio coordinator, #126 testable KWS/VAD/ASR lifecycle and the #125 Android build/emulator workflow.

### Exact-head CI evidence before the latest diagnostic fix
- Build Jarvis run [#42869150657](https://github.com/sokfrut-tech/Gemeni/actions/runs/42869150657), PR head `0210b2ec3675640b0f6dc08a946af3a27be4ba2e`:
  - Debug APK build passed.
  - JVM unit tests passed.
  - Native ABI, APK manifest, zip integrity and alignment checks passed.
  - Gradle Android instrumentation reported `BUILD SUCCESSFUL`; XML test result: 3 tests, 0 failures, 0 errors, 0 skipped.
  - All three UI test methods passed: responsive launcher, explicit model-download consent/decline, and Android Accessibility Settings launch.
  - The GitHub Actions job was nevertheless marked failed because the post-test wrapper ran `adb pull /sdcard/Download/jarvis-test` when that directory did not exist. The directory was absent because no test failed and the old test code only captured screenshots on failures. This is a CI diagnostic collection bug, not a failing UI assertion.
- Factory Security Baseline run [#42869150448](https://github.com/sokfrut-tech/Gemeni/actions/runs/42869150448), same PR head `0210b2...`: secret scan and CodeQL both passed.

### Corrective change in current head
- `JarvisRuntimeSmokeTest.kt` now captures screenshot + window hierarchy on successful launcher, model-consent and settings states as well as on failures. This also supplies visual evidence to inspect the previous black-screen regression.
- `.github/workflows/build.yml` now checks whether the remote evidence folder exists before pulling it. A missing optional evidence folder is recorded and does not override the actual Gradle test exit code. Real failed Gradle tests still keep the job red.
- Relevant commits immediately before this checkpoint: test evidence capture `2bf654bcad23b53496ad7aa04083d9a43882cc15`; robust optional evidence pull `ae5038e17f4335fad0693203f69580433da0af52`.
- Exact-head CI was triggered again by the latest change. Refresh Actions on the current SHA before declaring the workflow green.

### New integrated debug APK candidate
- Downloaded from CodeQL artifact for PR #133 head `0210b2...` (merge-ref artifact name differs from PR head SHA).
- Size: 110,451,439 bytes.
- SHA-256: `895a09c70ed6f7915e7ef167972b1217de45fbe2738d02f7ca8e1dfa546cf605`.
- Verified ZIP integrity, four Android ABI native library sets, and absence of `sherpa-onnx/native/` desktop-only payload.
- Compared with the earlier #201 APK (146,859,069 bytes), reduction: 36,407,630 bytes (34.72 MiB; 24.79%). The old uncompressed desktop payload was 114,746,624 bytes (109.4 MiB); do not confuse uncompressed resource size with actual APK-size reduction.
- Candidate APK and info are available in the assistant's current session artifacts. It is an experimental DEBUG APK, not a signed release or physical-OPPO-qualified release.

### Remaining verification / risk
- Review the new successful-state emulator screenshots and UI hierarchy to confirm the native orbital surface is actually visible/non-black. The prior emulator logs include graphics errors (`Failed to find ColorBuffer`, invalid texture binding, GLES `0x502`), so merely seeing the Compose AndroidView node is not sufficient visual-rendering proof.
- Full KWS/VAD pipeline contracts are present but a continuous Russian wake-word → VAD → ASR → tool → TTS session is not yet proven end-to-end. Push-to-talk and consent flows still need actual device testing.
- Physical OPPO Find X6 Pro / Chinese ColorOS validation remains mandatory for real audio capture, acoustic «Джарвис» recognition, call/system-audio interruption, TTS, thermal/battery behavior, native rendering and VoiceInteraction routing.
- Keep PR #133 Draft. Do not merge or label the APK release-ready until current exact-head CI is green and the physical-device gates are documented.
