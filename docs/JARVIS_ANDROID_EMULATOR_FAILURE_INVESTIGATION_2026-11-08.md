# Jarvis Android emulator failure investigation — 2026-11-08

## Scope
Evidence collected from GitHub Actions runs for the stacked draft PRs #111, #112 and #113 in `sokfrut-tech/Gemeni`. No PR was merged and no release was produced.

## Run results

| PR / run | Secret scan | CodeQL | Android emulator |
|---|---|---|---|
| #111 / 42410215969 | pass | pass | fail: 4 of 6 tests |
| #112 / 42408671067 | pass | pass | fail: emulator system services unavailable during APK install |
| #113 / 42408926828 | pass | pass | fail: same emulator infrastructure issue during APK install |

Run #111 emulator job: 128518029681. Evidence artifact: 13590350929.
Run #112 emulator job: 128514560860. Evidence artifact: 13589621397.
Run #113 emulator job: 128514141714. Evidence artifact: 13589386431.

## Confirmed PR #111 test failures

JUnit XML from artifact 13590350929 reports 6 tests, 4 failures, 0 errors:
- `mainScreenLaunchesAndShowsReadyState`: "Jarvis main screen did not appear".
- `denyingMicrophonePermissionShowsActionableStatus`: activity launch did not become idle within 45 seconds; test framework reported possible main-thread work / animation.
- `launcherShowsFunctionalJarvisUi`: "Navigator action missing".
- `decliningModelDownloadConsentDoesNotStartNetworkSetup`: "Microphone button is missing".
- `runtimePrepareRejectsMissingModelWithoutDownloading`: passed.
- `settingsOpensLocalModelPanel`: passed.

The first failure is at `JarvisLaunchSmokeTest.kt:35`; other UI selector failures are in `JarvisRuntimeSmokeTest.kt`.

## Runtime / rendering observations

- Instrumentation logcat reports repeated severe frame delays, including 1,223 and 3,045 skipped frames for the Jarvis process.
- The UI thread stack captured in the diagnostic dump is inside `Choreographer.doFrame`; it does not identify a specific blocking app method.
- `AlphaVideoSurfaceView.kt` extends `GLSurfaceView` and uses `RENDERMODE_CONTINUOUSLY`.
- `alphavideo_gl_engine.cpp` runs a GLES2 fragment shader with a 48-iteration ray/spoke loop on every frame. The emulator log also contains GLES translator errors and invalid color-buffer / texture binding messages.
- These observations make the GL surface / UI composition and rendering cost a strong investigation target, but do not yet prove it is the cause of missing Compose accessibility nodes or the UI-thread stalls.
- Do not remove the visual sphere or change renderer behavior blindly. First capture a screenshot and UI hierarchy for a deterministic launch, then run an A/B instrumentation test with the sphere disabled or replaced by a static Compose placeholder. Compare accessibility nodes, first-frame time, and frame-jank.

## PR #112 / #113 emulator infrastructure failure

The emulator job logs show repeated adb exit code 1 while Android services are unavailable. PR #112 specifically failed APK installation because `cmd: Can't find service: package`; PR #113 failed earlier while `cmd: Can't find service: activity`. This is distinct from app-level test failures and should be treated as emulator boot/service readiness instability.

## Required next steps

1. Inspect and retain the full emulator diagnostic artifacts for PR #112 and #113; identify whether package/activity services ever become available after boot.
2. The current smoke script already checks `sys.boot_completed`, PackageManager and ActivityManager before instrumentation. However, the later APK install logs show those services disappearing after the initial readiness gate. Add a bounded re-check/monitor immediately before installation and capture the exact service state at the point of failure; do not blindly restart ADB.
3. For PR #111, add deterministic screenshot + UI hierarchy capture on first launch failure and pull those files into the Actions evidence artifact.
4. After UI captures are available, run an A/B test with `AlphaVideoSurfaceView` disabled/static to determine whether SurfaceView/GL rendering causes missing UI nodes or stalls. Keep the animated orb only if the functional controls remain visible and responsive.
5. Re-run JVM tests, debug APK build, and Android instrumentation separately. Do not merge or publish until all required checks pass.

## Guardrails
- No PR merges, release publishing, or user-consent bypass.
- Do not describe an APK as device-verified; these are emulator results only.
- Preserve explicit consent before any model download.
