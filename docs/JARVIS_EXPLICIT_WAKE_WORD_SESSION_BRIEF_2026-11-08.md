# Jarvis explicit KWS implementation handoff — 2026-11-08

## Verified repository state

- Repository: `sokfrut-tech/Gemeni`
- Working branch: `feat/jarvis-explicit-kws-session-2026-11-08`
- PR: #115 (Draft, open, not merged)
- Parent branch: `fix/jarvis-emulator-permission-render-fallback-2026-11-08`
- Current verified implementation baseline before this commit: `9a2055bb7fc50fcd168b923cc64b02f8d50b04fe`

## CI evidence

- Run #346 / ID `42464117074` targeted old SHA `fd5fcbdcb2c55c65b5f847bed4dc5142bfde29df`.
- Its retry was cancelled. CodeQL init, Kotlin compilation, JVM tests, APK build, and emulator tests were skipped; secret scanning passed.
- Run #347 / ID `42464420951` targets `9a2055bb7fc50fcd168b923cc64b02f8d50b04fe` and was in progress when this note was written. Do not treat it as passed until all jobs finish.

## Source-level audio-path findings

- `MainActivity.startRealListening()` calls `runtime.listenOnce()`.
- `JarvisRuntimeController.listenOnce()` currently owns a one-shot call to `audio.frames()`, runs Silero VAD, collects completed segments, then runs Russian ASR and the guarded tool pipeline.
- `AudioRecordPipeline.frames()` creates exactly one AudioRecord per collection and releases it in `finally`.
- `SherpaJarvisKwsEngine` currently has no wired caller in the user-facing capture flow.
- Therefore KWS is NOT yet an operational wake-word mode. Do not claim hands-free or always-on operation.
- Calling `listenOnce()` after KWS detection would open a second sequential AudioRecord collection; the correct design is to consume a single `audio.frames()` flow and switch frame routing from KWS to VAD inside that same collection.
- `lifecycleScope` is used by MainActivity; verify cancellation propagation and release on Activity destruction, Stop, permission revocation, and audio errors before declaring lifecycle complete.

## Safety correction in this commit

`KwsModelDownloadManager.ensure()` now requires `userConsent: Boolean` and rejects false before starting network work. This is a separate consent gate from ASR/VAD model consent. The manager documentation explicitly states that model installation does not activate microphone listening. The call site must only pass true after a dedicated KWS-download confirmation dialog.

## Next implementation sequence

1. Add an explicit KWS model download confirmation in the UI, separate from current ASR/VAD consent. No startup download.
2. Refactor runtime to process a single AudioRecord frame stream: KWS until hit, then VAD command capture, ASR, safe proposal/tool pipeline, then resume KWS only while explicit session remains active.
3. Add a visible session status and Stop control. Push-to-talk remains default.
4. Ensure coroutine cancellation and permission revocation close the single AudioRecord flow, KWS stream/spotter, and VAD state in `finally`.
5. Add unit tests for routing/state transitions and instrumentation tests for permission denial, Stop, Activity destruction, and no duplicate recorder.
6. Validate acoustic accuracy on the actual OPPO Find X6 Pro; the current zh/en KWS model's “JARVIS” phoneme mapping is not validated for Russian speakers.

## Verification status

This commit is a safety gate and engineering handoff only. It does not claim a working KWS UI, microphone integration, emulator pass, or physical-device validation.
