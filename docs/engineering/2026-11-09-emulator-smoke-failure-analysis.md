# Android emulator smoke failure analysis — 2026-11-09

## Scope and evidence

Repository: `sokfrut-tech/Gemeni`
CI PR: #125 (`ci/android-build-on-pr-2026-11-08`)
Workflow run: 42512739273, attempt 2
Build job: 129363886846
Runtime-test artifact: `jarvis-runtime-test-results`, artifact ID 13701916068
Test device: Android 15 emulator (`emulator-5554 - 15`)

## What passed

- Debug APK assembly
- JVM unit tests
- Security and native compatibility checks
- APK manifest, services and native ABI inspection
- Emulator boot and instrumentation launch

## Exact failing assertions

All three instrumentation tests failed in 36.7 seconds:

1. `launcherShowsFunctionalJarvisUi`: `Navigator action missing` at `JarvisRuntimeSmokeTest.kt:90`, called from line 35.
2. `settingsOpensAndroidAccessibilitySettings`: `Accessibility settings did not open` at line 80.
3. `microphoneRequestsPermissionAndEntersRuntimeState`: `Microphone button is missing` at line 47.

This is not an emulator boot timeout. Instrumentation ran, launched the app and reached UI assertions; the expected UI controls / navigation transition were not observed.

## Source comparison

The smoke test expects:
- exact visible text `ДЖАРВИС`, `Рабочие команды`, `Навигатор`, `Поиск`, `Телефон`
- content description `Микрофон`
- content description `Настройки`
- native sphere description `JarvisSphere`
- a visible Runtime status

The inspected Compose `MainActivity` source contains the expected labels and content descriptions in its current implementation, so the test failure is not yet explained by a proven source-level missing label. The captured artifact contains per-test logcat and test XML, but does not contain the failure screenshots or a UI hierarchy dump. Do not weaken assertions or declare this gate green without diagnosing that mismatch.

## Next required diagnostic change

1. On every failed UiAutomator assertion, save both screenshot and `device.dumpWindowHierarchy(...)` output to the test output directory.
2. Update the CI workflow to pull `/sdcard/Download/jarvis-test/` (or the selected instrumentation output directory) before emulator shutdown and upload it even on failure.
3. Capture the launched activity, package/process state and UI hierarchy at the point of failure.
4. Re-run the exact PR head and use the new evidence to decide whether the issue is Compose semantics, app startup/lifecycle, test selector assumptions, or test ordering/state.
5. Keep the smoke gate red until the real functional UI and settings/microphone flows are observed. Do not replace the tests with weaker static checks.

## Release status

NOT RELEASE READY. This run does not authorize merging PR #125 or publishing an APK. The PR remains Draft pending a diagnosed fix and passing exact-head emulator smoke tests.
