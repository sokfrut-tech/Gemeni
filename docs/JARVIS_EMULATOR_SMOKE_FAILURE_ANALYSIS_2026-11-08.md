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
