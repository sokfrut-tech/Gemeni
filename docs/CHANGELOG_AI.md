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


## 2026-11-09 — KWS/VAD runtime audit and handoff

- Inspected current PR dependency chain #123 → #124 → #126 and the separate CI PR #125.
- Confirmed #126 Factory Security Baseline run #382 passed CodeQL and secret scanning; this does not verify Android compilation or emulator behavior.
- Inspected #125 Build Jarvis run #200: debug APK build, JVM unit tests, security/native checks and APK manifest/ABI inspection passed; emulator runtime smoke failed on attempt 1. Re-ran the failed job; attempt 2 was still in progress at handoff.
- Confirmed the current runtime still uses fixed 4.5-second buffered push-to-talk capture and post-capture ASR. KWS/VAD pipeline contracts and adapters exist but are not yet wired into runtime/UI.
- Added [JARVIS_KWS_VAD_RUNTIME_INTEGRATION_2026-11-09.md](JARVIS_KWS_VAD_RUNTIME_INTEGRATION_2026-11-09.md) with the verified source state, next implementation slice, model/consent boundaries, test matrix and release blockers.
- No runtime source code was changed in this checkpoint. No PR was merged and no APK was released.

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
