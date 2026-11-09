# Android Emulator CI Speed and Jarvis Release Pipeline

Date: 2026-11-08

## Findings from the current Jarvis workflow

The current `.github/workflows/build.yml` used a single Ubuntu 24.04 job. It built the APK and ran static/native checks successfully, then created a fresh API 35 x86_64 AVD on every run (`force-avd-creation: true`). It did not explicitly enable KVM, did not cache the AVD, and did not preserve emulator logcat/system diagnostics. The emulator step had a 20-minute timeout, while the workflow job had a 35-minute timeout.

The observed run 42503116128 remained reported as `in_progress` with its emulator smoke-test step in progress; GitHub's job-log download endpoint returned BlobNotFound during investigation. This is evidence of a stuck/opaque run, not proof that the Android guest OS itself was the cause.

## Changes applied to the CI workflow

- Explicitly enable runner access to `/dev/kvm` and fail early if KVM is unavailable.
- Cache the API 35 x86_64 default AVD and its boot snapshot using a key pinned to OS/API/ABI/profile.
- Generate a clean snapshot only on cache miss; reuse the cached AVD on subsequent runs.
- Use headless software GPU rendering, disable audio, boot animation and camera to remove unused emulator services.
- Bound emulator boot to 5 minutes and the complete runtime test step to 15 minutes.
- Add workflow concurrency cancellation so superseded commits on the same ref do not waste runner time.
- Capture ADB device state, boot-complete property, logcat, ActivityManager and memory diagnostics even when tests fail.
- Keep APK packaging/security/native ABI checks mandatory; emulator failure remains visible and is not silently ignored.

The first cache miss still needs a full AVD creation/boot. Speed improvement must be measured over subsequent cache-hit runs; no speedup percentage is claimed before those measurements.

## Recommended test lanes

### Lane A — every commit / pull request (fast feedback)
- Kotlin/JVM unit tests and deterministic state-machine tests.
- Gradle compile and debug APK assembly.
- Manifest, permissions, native ABI and dependency/security checks.
- Cache Gradle dependencies and compiler outputs.
- Avoid booting Android for tests that do not require Android framework APIs.

### Lane B — Android runtime smoke (PR and release candidate)
- One pinned API 35 x86_64 AVD with KVM and cached clean snapshot.
- Minimal UI Automator smoke tests: launch, permission screen, navigation, cancel/stop, crash-free startup.
- Keep test count small and deterministic; do not load large ASR/KWS models during generic UI smoke tests.
- Always save logcat, test XML, screenshots and emulator state on failure.

### Lane C — nightly / release qualification
- Broader Android API coverage and native ABI checks.
- Real model-load and local ASR/KWS/VAD tests with explicit model fixtures.
- Long-duration thermal, cancellation, audio-interruption and memory tests.
- Physical OPPO Find X6 Pro qualification is separate; an emulator cannot validate ColorOS OEM behavior, acoustic wake-word quality, real thermal throttling or call routing.

## Engineering and release acceleration

- Use path filters and concurrency cancellation to avoid rebuilding unchanged areas and obsolete commits.
- Cache Gradle, NDK/Sherpa artifacts and AVD snapshots with explicit versioned keys.
- Run independent static analysis, JVM tests and APK build jobs in parallel where artifact dependencies allow.
- Use test sharding only after measuring suite duration; parallel emulators can exhaust runner CPU/RAM and make tests slower or flaky.
- Adopt a failure taxonomy: build/configuration, emulator boot, ADB readiness, app launch, test assertion, timeout, resource exhaustion. A timeout must include last completed step and diagnostics.
- Require green build, unit tests, static/security checks and required runtime smoke tests before release. Never turn an emulator timeout into a passing/ignored check.

## Research sources

- Android Emulator hardware acceleration: https://developer.android.com/studio/run/emulator-acceleration
- Android Emulator command-line options and snapshots: https://developer.android.com/studio/run/emulator-commandline
- Android Emulator troubleshooting: https://developer.android.com/studio/run/emulator-troubleshooting
- Gradle Managed Devices, snapshots, test result caching, ATD and sharding: https://developer.android.com/studio/test/managed-devices
- ReactiveCircus Android Emulator Runner and AVD cache recipe: https://github.com/ReactiveCircus/android-emulator-runner
- GitHub Actions cache: https://github.com/actions/cache

## Verification status

The workflow edits are committed to the existing draft PR branch. They are not yet validated by a completed CI run. Verify:
1. KVM device exists and is usable by the runner.
2. First-run AVD creation produces a reusable snapshot/cache.
3. Second run reports an AVD cache hit and boots successfully.
4. Emulator diagnostics upload even after a timeout.
5. Runtime tests still execute and fail visibly on real assertions.

Do not merge this workflow change until those checks pass.
