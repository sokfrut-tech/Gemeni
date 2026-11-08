# Jarvis emulator smoke failure analysis — 2026-11-08

## Scope and evidence

Workflow run: [#351](https://github.com/sokfrut-tech/Gemeni/actions/runs/42477951516)  
Run ID: `42477951516`  
PR: [#115](https://github.com/sokfrut-tech/Gemeni/pull/115)  
Tested merge commit: `d0c787b03b0fef8727301bec4ace4a504bedf377` (PR head `b42d9c04b1a8efc0b96c72a3bee56b99cde29ca1))  
Emulator evidence artifact: `jarvis-emulator-evidence-d0c787b03b0fef8727301bec4ace4a504bedf377` (artifact ID `13616519322`).

## Confirmed workflow results

- Secret scanning: passed.
- CodeQL static analysis: passed.
- JVM unit tests: passed.
- Full debug APK build, SHA256 verification and artifact upload: passed.
- Android emulator smoke tests: failed (2 of 6 tests).

## What actually happened

The first failed test, `mainScreenLaunchesAndShowsReadyState`, did not fail because the Jarvis Activity was never launched. Its per-test log records:

- Activity lifecycle reached `RESUMED`.
- `JarvisOrb`: native GLES2 orbital renderer ready.
- ActivityManager: Jarvis MainActivity displayed in approximately 9.3 seconds.

The saved screenshot `ui-captures/jarvis-test/main-screen-missing.png` shows the Jarvis UI underneath an Android system dialog: **“System UI isn't responding”**. Therefore the test's missing-header assertion was contaminated by a system-level ANR overlay; it is not evidence that the app's Compose screen failed to render.

The full emulator log records multiple platform ANRs during the run:

- `system`: timed out while trying to bind.
- `com.android.systemui`: executing KeyguardService.
- `com.android.providers.media.module`: executing ExternalStorageServiceImpl.
- `com.android.phone`: telephony services.
- `com.android.bluetooth`: Bluetooth media browser service.

SystemUI later crashed after repeated failures. This is strong evidence of a severely overloaded/unhealthy emulator image during the run. It does not prove that Jarvis contributes zero load; app startup and rendering should still be profiled separately.

The microphone-permission test also failed to locate a deny button. Its log shows PermissionController's grant activity was eventually displayed after about 10.9 seconds, while the test's resource/text selector windows were shorter. This failure is not proof that the user-facing permission flow is absent. The system ANRs and delayed UI make the result unreliable. The test must capture the permission dialog hierarchy/screenshot and use robust, bounded waits rather than simply removing the assertion.

## Correct interpretation

- Do not mark emulator smoke as passed.
- Do not claim a confirmed Jarvis crash: no Jarvis fatal exception is established by this evidence.
- Do not attribute all failures solely to the emulator without qualification. The evidence establishes platform ANRs and delayed UI; Jarvis performance remains to be measured.
- The screenshot proves that the main UI was present behind a system ANR overlay.
- A CI emulator result is not a substitute for physical OPPO Find X6 Pro testing.

## Next engineering actions

1. Keep PR #115 Draft and unmerged.
2. Preserve the current strict smoke assertions; do not weaken tests just to turn CI green.
3. Add bounded preflight checks to emulator smoke: boot completion, package/activity manager responsiveness, free memory and repeated system-service checks. Fail with a clear infrastructure classification when the emulator is unhealthy before app assertions begin.
4. Capture the permission dialog hierarchy and screenshot when the deny selector is not found. Extend the wait only after verifying the dialog is actually present; support Android 14's actual resource IDs/text variants from captured evidence.
5. Separate app launch performance evidence from platform overlays: collect ActivityManager display time, app process CPU/memory, frame/jank metrics and app-specific ANR/crash markers.
6. Re-run on a clean emulator with adequate resources and compare. If platform ANRs recur before Jarvis interaction, classify the run as infrastructure-unhealthy, but keep the run visibly non-green and retain the evidence.
7. Continue thermal/workload protection independently; this emulator failure is not a thermal test and does not validate thermal behavior on OPPO hardware.

## Release gate

No merge, release, or APK publication is authorized by this analysis. The emulator smoke gate remains failed pending a reproducible, trustworthy run.


## Follow-up run #352 — repeated failure

Workflow: [#352](https://github.com/sokfrut-tech/Gemeni/actions/runs/42481057977)  
Run ID: `42481057977`  
Tested head: `930e81b2d66944e9b78e0fd02b00f1ffe365af69`  
Evidence artifact: [emulator diagnostics](https://github.com/sokfrut-tech/Gemeni/actions/runs/42481057977/artifacts/13618861566), artifact ID `13618861566`.

Results:
- Secret scanning: passed.
- CodeQL, Kotlin compilation, JVM tests: passed.
- Full debug APK build, SHA256 verification, artifact upload: passed.
- Emulator smoke: failed, again 2/6 tests.

New evidence from the second artifact:
- ActivityManager logged Jarvis MainActivity as displayed after `+28s548ms` (not the approximately 9.3 seconds observed in run #351).
- JarvisOrb GLES2 renderer did initialize.
- Before/during Jarvis tests, the emulator logged ANRs in `com.android.systemui`, `system`, `android.ext.services`, `com.android.phone`, `com.android.keychain`, `com.android.printspooler`, `com.android.providers.media.module`, and `com.android.bluetooth`.
- SystemUI and phone/Bluetooth processes were killed/restarted during the run.
- Emulator graphics logged `Failed to find ColorBuffer` and `Failed to bind to texture: invalid color buffer`.
- The first test's screenshot again captures a system ANR overlay over the app. The app's launch delay (28.5s) remains a real performance concern even though the platform was unhealthy.
- The microphone denial test still fails to launch the Activity within the instrumentation 45-second window in this run; this is separate from the earlier short permission-button selector timeout. The actual permission-dialog hierarchy still needs direct capture in a healthy emulator.

Updated conclusion:
Two consecutive emulator runs now show broad Android platform ANRs/process restarts and graphics-buffer errors, alongside a variable Jarvis launch time (about 9.3s in #351, 28.5s in #352). This supports classifying the emulator as unhealthy during these runs, but does not exonerate Jarvis startup/rendering. Keep the smoke gate failed. Next, isolate launch profiling in a healthy emulator and capture a clean permission-controller hierarchy. Do not remove assertions or increase timeouts as a substitute for diagnosis.


## Follow-up run #353 — instrumentation UI assertions still failing

Workflow: [#353](https://github.com/sokfrut-tech/Gemeni/actions/runs/42484753120)  
Run ID: `42484753120`  
PR head tested: `779e91f354e30e902e22847ca6e4b459ecf1cccd`  
Test merge SHA shown by evidence artifact: `395f06f754261ace30cfd0836cddca6d8627a860`  
Evidence artifact: [jarvis emulator evidence](https://github.com/sokfrut-tech/Gemeni/actions/runs/42484753120/artifacts/13620146274), artifact ID `13620146274`.

### Exact failures

- `JarvisLaunchSmokeTest.mainScreenLaunchesAndShowsReadyState`: assertion `Jarvis main screen did not appear` after waiting for exact text `ДЖАРВИС` (30 seconds).
- `JarvisRuntimeSmokeTest.denyingMicrophonePermissionShowsActionableStatus`: assertion `Microphone button is missing` after waiting for content description `Микрофон` (10 seconds), before the permission dialog interaction.
- Emulator graphics emitted `Failed to find ColorBuffer: 107`.
- Instrumentation summary: 6 tests, 2 failures; Gradle task `:app:connectedDebugAndroidTest` failed after 14m30s.
- Secret scanning and CodeQL jobs passed. This job's log does not establish the status of any separate JVM/APK jobs; don't infer them from the emulator result.

### What this changes

Earlier runs showed system-wide ANRs and graphics errors. Run #353 again contains a graphics-buffer error, but the two direct test failures are UI-element discovery failures. The permission-denial test failed before trying to locate the Android permission dialog: it could not find the app microphone control. This makes it necessary to investigate app UI composition, semantics/content descriptions, launch state, and screenshots/window hierarchy—not merely increase permission-selector timeouts.

The launch test looks for exact visible text `ДЖАРВИС`; the runtime test looks for content description `Микрофон`. Compare these selectors against the actual Compose UI semantics and captured XML/screenshot in the artifact. Preserve assertions until a real mismatch is understood. Do not replace them with weaker generic checks.

### Next actions

1. Inspect artifact screenshots and XML hierarchy for `main-screen-missing` and the microphone-button failure; identify whether the activity, system overlay, or wrong semantics are present.
2. Inspect the actual Compose screen source and align test selectors with intentional, stable accessibility semantics; prefer stable test tags/content descriptions, not brittle localized display text, where feasible.
3. Capture app process logs and activity lifecycle around each failure; distinguish activity launch failure, Compose first-frame delay, and selector mismatch.
4. Keep emulator smoke red until all assertions pass on a healthy emulator. Keep PR Draft and do not merge/release.

No physical OPPO validation is implied by this CI run.


## Artifact-level diagnosis of run #353 — 2026-11-08

The evidence ZIP (artifact 13620146274) was downloaded and inspected, including the PNG, XML hierarchy, JUnit XML, and full instrumentation log.

### Confirmed from the screenshot and hierarchy

- The saved screenshot visibly shows the Jarvis home screen rendered behind an Android system modal: **“Process system isn't responding”** with “Close app” and “Wait”.
- The captured XML hierarchy is dominated by that Android parentPanel; the app's Compose nodes are not exposed in the captured accessibility hierarchy while the system modal is on top. This explains why the launch test's exact-text selector can fail even though the screenshot shows the app underneath.
- JUnit XML confirms 6 tests, 2 failures, 0 errors. launcherShowsFunctionalJarvisUi, model consent decline, local model panel, and missing-model runtime rejection passed in that run.
- The app Activity reached RESUMED. ActivityManager logged the Jarvis Activity displayed after approximately 19.5 seconds in this run. This is still slow and must be profiled; the platform overlay does not make the launch-time concern disappear.
- The instrumentation log also contains severe frame stalls: Skipped 826 frames, Skipped 231 frames, and renderer frame durations in the multi-second range. The emulator's system dialog is direct evidence of a system process ANR, not a Jarvis Java/Kotlin fatal exception.

### A real accessibility defect found in source/log correlation

The microphone content description was attached to the inner Icon, while the clickable action belongs to the parent FloatingActionButton. UiAutomator logged the Микрофон node as an android.view.View with clickable=false. This is a genuine accessibility/testability mismatch independent of the platform ANR.

A source fix was committed on the PR branch:
- Commit: 6d8e87629e05ed898a92c9d566f468b73b2132e3
- Move contentDescription = Микрофон to the clickable FloatingActionButton via Compose semantics.
- Set the decorative inner microphone Icon description to null, so accessibility exposes one actionable control rather than a non-clickable child.

This change is awaiting its own CI run; it is not yet verified by instrumentation.

### Updated interpretation

Run #353 was not simply “the emulator is bad”: it exposed both (1) a platform ANR that obscured the accessibility tree and (2) a concrete microphone semantics issue. Keep both causes separate. Do not weaken assertions. Re-check the exact UI selector after the new semantics fix and require the emulator smoke job to complete successfully before treating it as resolved.
