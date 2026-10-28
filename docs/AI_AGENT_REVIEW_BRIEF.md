# Jarvis Runtime Failure — Independent AI Agent Brief

## Mission
Independently diagnose why the user-installed Jarvis APK can show the legacy placeholder screen instead of the current Compose + GLES2 UI, and verify the full runtime stack. Do not accept a green `assembleDebug` as proof of functionality.

## Evidence
- User screenshot shows legacy UI: `Джарвис / Русский голосовой ассистент / для ColorOS` with no mascot, sphere, quick commands or microphone button.
- Current `MainActivity` in `main` contains the expected Compose UI and `AlphaVideoSurfaceView`.
- `AndroidManifest.xml` declares `MainActivity` as the launcher, plus VoiceInteractionService and AccessibilityService.
- CI Run #52 passed build/security/manifest checks but did not perform runtime UI testing.
- Android documentation recommends UI Automator for external UI testing and screenshot assertions.
- `GLSurfaceView` has separate surface lifecycle callbacks and must be tested at runtime.

## Agent A — Android Runtime / APK Forensics
1. Inspect the delivered APK, not the source tree.
2. Verify `versionCode`, `versionName`, package name, launcher component and DEX/resource strings.
3. Prove whether the installed screenshot could have come from the delivered APK.
4. Check for duplicate launcher activities, old resources, stale build outputs or install/update problems.
5. Produce a deterministic command/script that prints package version, launcher activity and APK SHA-256.

## Agent B — Compose/UI QA
1. Launch the APK from a clean state.
2. Assert the presence of: `ДЖАРВИС`, `Русский ассистент • ColorOS`, `Быстрые команды`, `Навигатор`, `Поиск`, `Телефон`, microphone button, and `JarvisSphere`.
3. Capture a screenshot on every failure.
4. Tap microphone and assert `Слушаю…` appears.
5. Verify the UI is not merely a static placeholder.

## Agent C — GLES2/NDK
1. Verify `libjarvis_native.so` for the test ABI.
2. Check JNI symbol names against `AlphaVideoSurfaceView$JarvisSphereRenderer`.
3. Instrument shader compile/link errors and `glGetError()`.
4. Verify `onSurfaceCreated`, `onSurfaceChanged`, and `onDrawFrame` are reached.
5. Verify the SurfaceView does not obscure the Compose layer with an opaque black surface.
6. Capture a renderer-health state that can be asserted by UI tests.

## Agent D — ColorOS/System Assistant
1. Verify VoiceInteractionService registration and metadata.
2. Verify AccessibilityService registration and configuration.
3. Test default-assistant selection on the target ColorOS build.
4. Test microphone permission and audio capture.
5. Test assistant invocation independently of the launcher Activity.
6. Do not claim that a third-party VoiceInteractionService is equivalent to a preinstalled ColorOS/Breeno assistant; document platform restrictions.

## Agent E — Voice Stack
1. Test Sherpa-ONNX native loading.
2. Test VAD initialization.
3. Test KWS initialization and wake phrase.
4. Test Russian ASR with a known WAV fixture.
5. Test Piper TTS with a known Russian phrase and verify generated audio.
6. Test cloud fallback only after local path is proven.

## Acceptance gate
The build is NOT release-ready until all of these are true:
- clean install launches the current UI;
- automated UI smoke test passes;
- screenshot matches the expected current UI rather than the legacy placeholder;
- native renderer health passes;
- no startup crash / `UnsatisfiedLinkError` / EGL / shader errors;
- VoiceInteractionService and AccessibilityService are registered and testable;
- microphone path is proven;
- ASR/TTS smoke tests pass or are explicitly marked as blocked by missing model assets/permissions;
- the exact APK SHA-256 is recorded in the artifact report.

## Required deliverables
1. Root cause report.
2. Minimal code fix.
3. Automated regression test.
4. Failure screenshot/logcat artifact.
5. Exact APK SHA-256.
6. Short list of remaining blockers, with evidence.
