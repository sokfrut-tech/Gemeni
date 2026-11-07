# Jarvis Qwen on-device validation plan

Status date: 2026-11-07  
Source branch: `perf/jarvis-trim-desktop-native-assets-2026-11-07`  
Related PR: https://github.com/sokfrut-tech/Gemeni/pull/98

## Verified implementation facts

- The Android app contains a real llama.cpp JNI runtime and a Qwen GGUF installer; this is not a mocked model provider.
- The installer requires explicit user consent, stores the model in app-private storage, checks the pinned byte size and SHA-256, and uses an atomic move after verification.
- Pinned model: Qwen3.5-0.8B-Q4_0.gguf, 563,036,064 bytes.
- Expected SHA-256: `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`.
- The model provider validates generated tool proposals against the strict allowlisted codec before the Android tool registry can execute anything.
- Unit tests use fake runtimes. Existing emulator smoke tests verify app launch and basic UI/permission flows; they do not prove real GGUF inference or Russian speech recognition.
- Current Gemeni app minSdk is 29 (Android 10), not Android 7.

## Required physical-device validation

Target device: OPPO Find X6 Pro / ColorOS. Do not mark any item passed until evidence is recorded from the physical device.

1. Install the exact APK artifact from the tested CI run and record its SHA-256.
2. Confirm first launch works without network access and without a model installed; basic offline commands remain available.
3. In the Qwen panel, verify download does not start before explicit consent.
4. With consent, download the model; record elapsed time, final byte count, free storage before/after, and successful checksum verification.
5. Activate the installed model; record cold-load duration, memory pressure, and any native crash/logcat output.
6. Run at least these Russian prompts: a simple supported command, an unsupported request, an ambiguous request, and prompt-injection text asking to ignore the allowed tool schema.
7. Verify unsupported/ambiguous/injection prompts do not execute an action; risky actions must remain behind the existing confirmation policy.
8. Enable airplane mode and repeat a local inference prompt. Confirm no network request is needed after model installation.
9. Exercise microphone permission granted/denied, Russian ASR, spoken response, and one complete voice-to-tool flow.
10. Record warm inference latency, response validity, RAM peak if measurable, battery/thermal behavior after 10 consecutive prompts, and app stability.

## Evidence format

Create a dated report under `docs/device-validation/` with:
- device model, Android/ColorOS version, app versionCode, APK SHA-256;
- model file size and SHA-256;
- pass/fail/not-run for every numbered test;
- cold-load and warm-inference timings;
- sanitized logcat excerpts for failures;
- known limitations and reproduction steps.

Never include personal data, phone numbers, account identifiers, or raw voice recordings in the report.

## Self-diagnostic implementation update (2026-11-07)

A separate draft PR adds a user-visible Qwen self-test:
- Related PR: https://github.com/sokfrut-tech/Gemeni/pull/100
- The probe runs direct local inference on a worker thread and reports llama.cpp version and elapsed time.
- It does not download a model and does not call the Android tool registry.
- The control response is accepted only when the trimmed output is exactly `JARVIS_OK` (case-insensitive); prose that merely contains the token is rejected.
- JVM unit tests cover exact success, case/outer-whitespace handling, and false-positive responses.

This improves the pre-device diagnostic gate but does not replace physical-device inference testing. The PR is still draft until its Android build, unit tests, and security checks pass.

## Engineering follow-up before claiming production readiness

- Add a user-visible local model self-test/benchmark that runs on a worker thread and reports load/inference status and latency without silently downloading anything.
- Add a deterministic native integration test path for a pre-provisioned test model; keep the large model out of normal emulator CI.
- Keep emulator smoke tests and physical-device inference tests distinct in CI/reporting.
- Evaluate an arm64-only release artifact separately from the universal debug artifact; preserve x86_64 emulator coverage.
- Do not claim Android 7 compatibility unless minSdk, dependencies, JNI libraries, and runtime APIs are separately validated.

## Current gate

The CI run for PR #98 has Secret scanning and CodeQL completed successfully. At the time this plan was written, Android emulator smoke tests were still in progress. Do not merge PR #98 until that job has a completed conclusion and its logs/results have been reviewed.
