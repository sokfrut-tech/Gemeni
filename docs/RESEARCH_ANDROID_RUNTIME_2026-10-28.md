# Android runtime repair research — 2026-10-28

## Confirmed findings

1. GitHub Actions Ubuntu 24.04 has a known Android emulator/AVD path issue: `avdmanager` may create the AVD under `$HOME/.config/.android` while `emulator` looks under `$HOME/.android`. A documented workaround is to link the directories. Source: GitHub `actions/runner-images` issue #11482.
2. ReactiveCircus `android-emulator-runner` supports API 35, `google_apis`, `x86_64`, and explicit hardware profiles. Its current implementation passes the selected profile to `avdmanager` as `--device`.
3. Current examples in public repositories successfully run API 35 `google_apis` `x86_64` emulators on GitHub Actions. A maintained example uses a valid profile such as `Nexus 6` and KVM setup.
4. Google's current Android CLI supports creating an emulator from a named profile and, when omitted, can create a default `medium_phone` profile. This is a newer CLI path and should be evaluated separately from the legacy `avdmanager` path.

## Repair direction

- Do not hard-code `Pixel_2` unless the runner proves that profile exists.
- Before creating an AVD, run `avdmanager list device` and select a verified profile; record the selected profile in evidence.
- Set and verify `ANDROID_AVD_HOME` consistently before `avdmanager` and `emulator` are invoked.
- On Ubuntu 24.04, detect the known `.config/.android` versus `.android` split and repair the path without disabling runtime tests.
- Prefer API 35 + `google_apis` + `x86_64` for the standard x86_64 GitHub runner unless the application requires another ABI.
- Verify `adb wait-for-device` and `sys.boot_completed=1` before tests.
- Assert the checked-out SHA equals the intended workflow SHA; never silently test stale source.

## Sources reviewed

- GitHub actions/runner-images issue #11482 (Ubuntu 24.04 Android emulator AVD lookup).
- ReactiveCircus/android-emulator-runner current action and emulator manager.
- Android Developers: managing AVDs and current emulator CLI.
- Public GitHub Android CI examples using API 35 emulator runners.

## Release rule

Research findings are hypotheses until reproduced in the project's runner. No source is treated as authority over repository evidence. A runtime failure remains a release blocker until independently reproduced as fixed.
