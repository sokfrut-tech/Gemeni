# Jarvis thermal protection — OPPO Find X6 Pro

## Current implementation

The runtime owns an `AndroidThermalGuard` backed by Android `PowerManager` thermal status. It unregisters its listener when the runtime closes. The policy is intentionally conservative:

| Android thermal status | Jarvis policy |
| --- | --- |
| NONE | 160 generation tokens |
| LIGHT | 96 generation tokens |
| MODERATE | 48 generation tokens |
| SEVERE / CRITICAL / EMERGENCY / SHUTDOWN | Block heavy local LLM inference |

Unknown status values also fail closed to the critical budget. When the LLM is blocked, the provider returns a user-readable message and ordinary offline command handling remains available.

## Important limits

- Android thermal status is device-wide and vendor-dependent; it is not a guaranteed per-component temperature sensor.
- This code does not control CPU/GPU frequencies, bypass ColorOS thermal management, or promise a specific device temperature.
- The current guard samples the budget before a generation request. It reduces output size and blocks new heavy LLM requests, but does not yet cancel an inference already in progress.
- ASR, VAD, TTS, camera/sensor workloads, and concurrent-task scheduling are not yet thermally budgeted.
- No physical OPPO Find X6 Pro thermal validation has been performed.

## Required next engineering steps

1. Expose thermal level and protective mode in the Compose UI without showing false numeric temperatures.
2. Add a shared workload gate for ASR, TTS, VAD and LLM, with bounded concurrency and cancellation of queued heavy tasks.
3. Add sustained-load / cooldown hysteresis before restoring full workload, to avoid rapid mode oscillation.
4. Where supported, collect Android thermal headroom as an additional signal, but keep device-wide thermal status as the fail-safe.
5. Add instrumentation for transitions, lifecycle cleanup, and thermal-mode messaging.
6. Validate with repeated inference and voice sessions on the physical OPPO Find X6 Pro; record ambient conditions, Android thermal status transitions, battery/charging state, and user-visible throttling. Do not claim safe temperatures from emulator tests.

## Verification record

- Commit: `0096b3ec46e5c165abc79ce9e636c678748cbe1a` added the first thermal guard and pure policy tests.
- CI run #350: CodeQL/Kotlin build, JVM unit tests, APK assembly/hash/upload, and secret scan passed. Emulator smoke tests were still running at the time this document was written.
- Keep PR #115 in Draft until the emulator result is known and the broader thermal integration and physical-device validation are completed.
