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

## Follow-up: captured UI evidence and corrective patch — 2026-11-08

Artifact `13592421561` was downloaded and inspected. It contains screenshots, UIAutomator hierarchy XML, per-test logcat, and JUnit XML.

### Evidence-based diagnosis

- JUnit confirms 6 instrumentation tests, 4 failures, 0 errors.
- The screenshot `ui-captures/jarvis-test/Navigator-action-missing.png` shows the Jarvis header, status, and command card, but the microphone FAB is below the visible viewport.
- UI hierarchy reports the FAB's button bounds as `[0,0][0,0]`; the fixed `Spacer(height(330.dp))` in a full-height Column pushes controls beyond the 320x640 emulator viewport. This directly explains missing microphone and navigator selectors in the compact emulator layout.
- The separate `main-screen-missing.xml` capture is Android's system ANR dialog (`Process system isn't responding`), not a Jarvis screen. At the same time, logcat reports Jarvis frames taking 11,060 ms, 518 skipped frames, and repeated GLES invalid-program errors (`glUseProgram`, error 0x501). The emulator also recorded system/SystemUI ANRs earlier in the run. This supports GPU/emulator starvation during continuous native rendering; it does not establish that Jarvis alone caused every system ANR.

### Corrective changes committed on the PR #111 branch

- `MainActivity.kt`: replaced the fixed 330dp vertical spacer / top overlay placement with a flexible weighted middle region containing the sphere, so controls retain space on compact screens.
- `AlphaVideoSurfaceView.kt`: changed GLSurfaceView from continuous redraw to `RENDERMODE_WHEN_DIRTY`; listening-state changes request a new frame. This retains the orbital artwork while avoiding a permanent shader redraw loop. Continuous animation is intentionally paused pending stable performance measurements.
- `JarvisRuntimeSmokeTest.kt`: added an assertion that the microphone control has non-empty visible bounds, not merely an accessibility description.

### Verification state

The changes are committed; latest known PR head is `6e14f849d4ef3ef6a07d6f41ebc9404ee0dd638c`. At the time of this update GitHub had not yet created a workflow run or check-runs for this exact SHA (0 check-runs; combined status pending with no statuses). Therefore these changes are NOT yet build- or emulator-verified. Do not merge or release until fresh exact-head JVM, APK, and emulator checks complete.


## Follow-up: Orchestrator verification and permission assertion — 2026-11-08

Run #331 (workflow run 42450820913, PR #114 head before follow-up commit) confirms Android Test Orchestrator is active: all six instrumentation tests completed and the earlier instrumentation-process SIGKILL did not recur. Secret scanning, CodeQL, JVM tests, debug APK build, and APK hash verification passed. The emulator suite had one failure.

The only failing test was `denyingMicrophonePermissionShowsActionableStatus`. Its log shows the Android Permission Controller displayed the microphone permission dialog, the deny selector was found, and a button was clicked. The final test assertion queried permission state through the shell command `pm check-permission` and failed. This assertion used a shell interface unnecessarily and was not equivalent to checking the app's runtime permission state through Android's framework API.

Follow-up commit `aef42ba9019ea6b8db41df0a59f4a72de7496927` changes the test to:
- Revoke microphone permission through `UiAutomation.revokeRuntimePermission(packageName, Manifest.permission.RECORD_AUDIO)`.
- Verify the resulting state with `Context.checkSelfPermission` against `PackageManager.PERMISSION_DENIED`.
- Keep the permission-dialog interaction and user-visible denial-status assertion intact.

This is a test reliability correction, not a production permission behavior change. Await a fresh exact-head CI run before claiming the permission test is fixed. No merge or release.


## Follow-up: emulator smoke-test hang and hard timeout correction — 2026-11-08

### Observed run
- Workflow run: [42455416539](https://github.com/sokfrut-tech/Gemeni/actions/runs/42455416539)
- PR: #114, branch `fix/jarvis-emulator-permission-render-fallback-2026-11-08`
- Exact head before timeout correction: `c00db7c44d991d4e5ff3a5440c6baa91eb106eed`
- Secret scanning: passed.
- CodeQL job: passed, including Kotlin compilation, JVM unit tests, full debug APK build, APK verification/hash, and SARIF analysis.
- Android emulator smoke job: remained in progress far beyond the expected duration as reported by the project owner. Its step was `Run Android emulator smoke tests`; no conclusion or usable live log was available during inspection.

### Root cause found in smoke script
The script's readiness loop claimed a 360-second limit but used 180 iterations, not a wall-clock deadline. Each iteration performed several serial ADB calls. Most had individual timeouts, but `adb get-state` had no timeout at all. A stalled ADB command could therefore hold the loop indefinitely; even when calls returned, the aggregate loop duration could substantially exceed the stated six minutes. This invalidated the claimed bounded-readiness guarantee.

### Corrective change
Commit `3449c3dcde0bd0e960102e61ad0905d50dd36ca5` updates `.github/scripts/run-android-emulator-smoke.sh`:
- Uses a real `SECONDS`-based six-minute readiness deadline.
- Bounds `adb get-state` and groups framework readiness probes into a single bounded ADB shell call.
- Adds a hard 24-minute timeout to Gradle instrumentation, leaving time within the 32-minute step / 35-minute job budget for diagnostics and artifact upload.
- Emits an explicit timeout error when instrumentation exceeds its budget.
- Preserves diagnostics capture and test-report validation.

### Verification state / next actions
- This is a targeted CI reliability correction based on direct source inspection; it is not yet emulator-verified.
- The push should create a fresh workflow run for the new exact branch head; inspect that run and its emulator diagnostics.
- Confirm whether the prior stuck run terminates under GitHub's configured timeout/concurrency behavior; do not infer cancellation.
- If the new run fails, inspect uploaded diagnostics and logs before changing emulator configuration. Keep `swiftshader_indirect` unchanged.
- No merge, release, or APK publication is authorized by this investigation.


## Follow-up: exact-head emulator verification passed — 2026-11-08

### Exact workflow evidence
- Workflow run: [42455974159](https://github.com/sokfrut-tech/Gemeni/actions/runs/42455974159), run #338.
- PR: #114, branch `fix/jarvis-emulator-permission-render-fallback-2026-11-08`.
- Exact tested head: `66d46b10367dfaf484155a1bc682df5a1648238d`.
- Overall workflow conclusion: `success`.
- Secret scanning job `128654963983`: success.
- CodeQL static analysis job `128654964214`: success. Steps include Kotlin source build, JVM unit tests, full debug APK build, APK verify/hash, artifact upload, and CodeQL analyze.
- Android emulator job `128656135348`: success. The bounded smoke-test step completed successfully; failure-only diagnostics step was skipped; evidence upload succeeded.

### Inspected emulator artifact
- Artifact ID: `13607902431`, name `jarvis-emulator-evidence-9e7512c264ec81f3b3fc13659e0628a851b8073b`.
- Artifact SHA-256 digest reported by GitHub: `b3090f7723a79a5ee3a996f0ecab4e1ee7e2b218b83501878784f983e45efd63`.
- Downloaded and unpacked the artifact; inspected JUnit XML, UTP textproto, instrumentation test log, and per-test logcat files.
- JUnit XML: 6 tests, 0 failures, 0 errors, 0 skipped; total test time 25.285 seconds.
- Instrumentation runner log ends with `OK (6 tests)`.
- UTP reports `test_status: PASSED` and `use_orchestrator: true`.
- All six tests passed:
  1. `mainScreenLaunchesAndShowsReadyState`
  2. `runtimePrepareRejectsMissingModelWithoutDownloading`
  3. `denyingMicrophonePermissionShowsActionableStatus`
  4. `launcherShowsFunctionalJarvisUi`
  5. `settingsOpensLocalModelPanel`
  6. `decliningModelDownloadConsentDoesNotStartNetworkSetup`

### Build artifact
- Debug APK artifact ID: `13607647764`, name `jarvis-debug-apk-9e7512c264ec81f3b3fc13659e0628a851b8073b`.
- Size: 86,965,171 bytes.
- GitHub artifact SHA-256 digest: `14a7521b2f996cbc6d138f3cef2a25e0343bd70503225360a6ccd0fc1de09b84`.
- This confirms a CI-built debug APK artifact exists for the exact tested head. It is not a production release and has not been verified on the owner's OPPO device.

### Conclusion and guardrails
The previous emulator hang is resolved for this exact CI run: the real wall-clock readiness deadline and bounded instrumentation command completed, all six instrumentation tests passed, and the evidence artifact uploaded. This validates the CI emulator smoke suite, not every runtime feature or hardware behavior.
PR #114 remains open and Draft, unmerged. No release or APK publication was performed. Do not merge or release without the owner's explicit authorization and any required independent review.


## Follow-up: exact-head CI run #339 independently inspected — 2026-11-08

### Exact workflow evidence
- Workflow run: [42458733893](https://github.com/sokfrut-tech/Gemeni/actions/runs/42458733893), run #339.
- PR: #114, branch `fix/jarvis-emulator-permission-render-fallback-2026-11-08`.
- Exact tested head: `5d8d2af69f71012f3ea912eb36a3e5b13279bec6`.
- Secret scanning job `128663211938`: success.
- CodeQL static analysis job `128663212112`: success, including Kotlin compilation, JVM unit tests, full debug APK build, APK verification/hash, artifact upload, and CodeQL analysis.
- Android emulator smoke job `128664208688`: success. Smoke-test step completed; failure-only diagnostics were skipped; emulator evidence upload succeeded.
- Overall workflow: all three jobs completed successfully.

### Independently inspected emulator evidence
- Artifact ID: `13608663765`, name `jarvis-emulator-evidence-c942a866001bd09d4f8bdf9acc1c2762aac5a644`.
- GitHub artifact SHA-256: `417ab4c7b4c6d3ff31df3b45f121353088faf39d81a064b53430b694c3c2d0bc`.
- Downloaded and unpacked the artifact. Inspected JUnit XML, UTP textproto, instrumentation runner output, and six per-test logcat files.
- JUnit XML: 6 tests, 0 failures, 0 errors, 0 skipped; total test time 22.422 seconds.
- Instrumentation runner: `OK (6 tests)`.
- UTP: `test_status: PASSED`.
- All six tests passed:
  1. `mainScreenLaunchesAndShowsReadyState`
  2. `runtimePrepareRejectsMissingModelWithoutDownloading`
  3. `denyingMicrophonePermissionShowsActionableStatus`
  4. `launcherShowsFunctionalJarvisUi`
  5. `settingsOpensLocalModelPanel`
  6. `decliningModelDownloadConsentDoesNotStartNetworkSetup`

### Exact-head build artifact
- Debug APK artifact ID: `13608971908`, name `jarvis-debug-apk-c942a866001bd09d4f8bdf9acc1c2762aac5a644`.
- Size: 86,965,167 bytes.
- GitHub artifact SHA-256: `3aaf5c66e0c0a4bebd85de67abfd8e50ffe789ae8aff4dbb267015cfb9d8ec98`.
- Unit-test report artifact ID: `13609205911`, GitHub SHA-256: `951dd2a77388666462b35caf7b3c5a3c723fb561a41c7d29c31a4cadbccd6be5`.
- CodeQL SARIF artifact ID: `13609266007`, GitHub SHA-256: `3981b42330cf99edd9e5534ebf7c783a2aeab5675f074f130dd5d1a4c66b8f79`.
- Secret-scan SARIF artifact ID: `13609180787`, GitHub SHA-256: `f9fcfb958b041932550760e8e26711a6cf826aded95912c3bd54f2812cfb7858`.

### Conclusion and limitations
Run #339 independently confirms the exact PR head `5d8d2af` builds and passes the complete configured CI suite, including all six Android emulator instrumentation tests. The bounded emulator workflow and evidence upload both completed successfully. This remains emulator/CI verification only: it does not establish behavior on physical OPPO Find X6 Pro / ColorOS hardware, production-release readiness, or complete feature acceptance.
PR #114 remains open and Draft, unmerged. No release or APK publication was performed. Do not merge or release without the owner's explicit authorization.


## Follow-up: exact-head CI run #340 independently inspected — 2026-11-08

### Workflow evidence
- Workflow run: [42459924283](https://github.com/sokfrut-tech/Gemeni/actions/runs/42459924283), run #340.
- PR #114, branch `fix/jarvis-emulator-permission-render-fallback-2026-11-08`.
- Exact tested head: `83526048dbaa6e9f52a08465f9cb0d2d488fdb27`.
- Secret scanning job `128666814109`: success.
- CodeQL/build job `128666813937`: success; Kotlin compilation, JVM unit tests, full debug APK build, APK verification/hash, artifact upload, CodeQL analysis and SARIF staging all completed.
- Android emulator job `128667693591`: success; smoke tests and evidence upload completed; failure-only diagnostics step was skipped.

### Downloaded and inspected emulator evidence
- Artifact ID: `13609219469`, GitHub SHA-256: `9ab77db694b84ef0e7fb0dee958774d230da7a9e6f779a377126905b5d5d4a9d`.
- Downloaded the ZIP and inspected its JUnit XML, test reports, UTP textproto and per-test logcat files.
- JUnit: 6 tests, 0 failures, 0 errors, 0 skipped; total test time 29.596 seconds.
- UTP/instrumentation result: passed. Six test cases: launcher ready state, missing-model fail-closed behavior, microphone permission denial, functional launcher UI visibility, settings model panel, and declining model download without network setup.

### Exact-head APK artifact
- Artifact ID: `13608829468`, size 86,965,169 bytes.
- GitHub artifact SHA-256: `e3cf9c56eeb7573a2bc32edcce8fdca297986cda4ef4a80e94f76654e19762f5`.
- Unit test report artifact: `13609620563`, SHA-256 `386402f7aa34a6fc0ecc174ecd3ac8de1a160f5763b19f495d7727d0f75f086d`.
- CodeQL SARIF artifact: `13608994069`, SHA-256 `e3698d3fadc935d5c7f41c6e9f75798268efc306672dd3573d5bc42e67c2a2fe`.
- Secret scan SARIF artifact: `13609027185`, SHA-256 `65d1be823ccd55c7abcc858f7065d0cf2f17bbf61adca51cfa726f57b70613aa`.

### Conclusion and limitations
Run #340 confirms the exact PR head passes all three configured CI jobs and all six emulator instrumentation tests. This is repeatable CI/emulator evidence, not proof of complete voice-runtime functionality, neural local reasoning, visible non-black native frame, or physical OPPO/ColorOS compatibility. The PR remains Draft/open/unmerged; no release or APK publication was performed.
