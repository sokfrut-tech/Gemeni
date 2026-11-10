# Jarvis Android assistant CI integration and smoke-test correction
Date: 2026-11-10
Status: integration proposal; waiting for exact-head CI
Repository: `sokfrut-tech/Gemeni`

## Integrated source baseline

This branch starts from PR #126 head `6b77a87a31427c1ab8c09fb578d250d9a473837e`, which is stacked on the current Android assistant work:

- PR #123 — responsive, scrollable Jarvis UI and visible stop/cancel state.
- PR #124 — serialized single-owner AudioRecord capture coordinator.
- PR #126 — injectable KWS/VAD/ASR contracts and interruption-safe lifecycle.

It brings in the Android build/emulator workflow from PR #125 so the integrated source chain is tested together rather than building the old `main` UI separately from the audio branch.

## Evidence from previous Build Jarvis run #201

Run: https://github.com/sokfrut-tech/Gemeni/actions/runs/42697490427
Commit under test: `19df605ba1962c46d67845ed74c56389901c765d`
Artifact: `jarvis-debug-apk`, artifact ID `13709284779`
The APK assembled successfully, JVM tests passed, and static/native/manifest/ABI checks passed. Emulator instrumentation ran but three UI assertions failed.

The exact failures were:

1. The old launcher smoke test expected a non-scrollable legacy layout. The current PR #123 UI uses a responsive vertically scrollable layout, so tests must scroll to the action row and the talk control before asserting/interacting.
2. The old settings test searched for localized screen-title text. The captured ActivityTaskManager log proves Android opened `com.android.settings/.Settings$AccessibilitySettingsActivity`; the assertion was a false negative. The updated test checks the foreground Android Settings package.
3. The old microphone test searched for a legacy content description. The current PR #123 UI presents an explicit `ГОВОРИТЬ С ДЖАРВИСОМ` button. The updated test finds and clicks the actual user-facing control, then verifies the missing-model consent dialog.

This is not an emulator boot failure. Instrumentation executed all three tests. The APK is a valid debug build artifact, but runtime smoke was red and the APK was not release-qualified. The extracted APK also contains desktop-only files under `sherpa-onnx/native/`; those are not used by Android and unnecessarily inflate the package. The integrated branch now excludes that resource path and adds a CI assertion that fails if any such payload remains.

## Test correction principles

The revised smoke tests do not lower the release gate or pretend a full voice session was validated:

- Verify the current visible Jarvis header, core visual, identity and initial runtime status.
- Scroll the actual UI and verify all three quick actions and the talk control.
- Verify Settings launches the real Android Settings package.
- Verify tapping the talk control reaches the explicit ASR model-consent dialog when no model is installed.
- Decline model installation and assert the UI reports that nothing was downloaded.
- Do not download a large model or start an unattended microphone session inside generic UI smoke tests.
- On failures, save both screenshot and UI hierarchy to `/sdcard/Download/jarvis-test`; the emulator script pulls this evidence before shutdown.

The JVM test lane remains responsible for deterministic audio coordinator and KWS/VAD lifecycle tests. Physical OPPO testing remains mandatory for the microphone, call preemption, ColorOS routing, acoustic wake-word quality, thermal load and battery.

## Release gate

The branch is not release-ready until the exact integrated commit passes:

1. Gradle Kotlin compilation and `:app:assembleDebug`.
2. Full JVM unit tests, including PR #124/#126 audio and voice tests.
3. APK ZIP integrity, secret scan, native ABI, manifest and alignment checks.
4. All Android UI smoke tests with their XML results and captured diagnostics.
5. Physical OPPO Find X6 Pro validation.

A passing APK build or successful static/security workflow alone is not sufficient. No check is skipped or converted to success on failure.

## Next step

Inspect the exact-head GitHub Actions results from this branch, repair any real compile/runtime defects with regression tests, then consolidate the stacked PRs. Keep the PR Draft until all required gates are green. No APK release or physical-device success is claimed here.
