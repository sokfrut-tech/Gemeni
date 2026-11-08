# Jarvis single-owner audio capture coordinator — 2026-11-08

## Status

This increment adds and wires a serialized owner around the existing AudioRecord frame Flow for the current push-to-talk runtime. It is a foundation for the explicit wake-word session; it does not enable always-on capture and does not yet connect Android telephony callbacks.

## Implementation

- `AudioCaptureCoordinator` is the only collector used by `JarvisRuntimeController.listenOnce()`.
- Start, stop, call suspension and system-audio suspension are serialized by a coroutine Mutex.
- `stop()` and suspension use `cancelAndJoin()`; they do not return until the source Flow's cancellation cleanup has completed.
- The source remains `AudioRecordPipeline.frames()`, whose `finally` stops and releases the recorder.
- Capture failures are surfaced through an injected callback and a FAILED state rather than escaping as an unhandled exception from the owner scope.
- Resume after interruption requires the caller to confirm the user's explicit opt-in and returns to IDLE. It never reacquires the microphone automatically.
- Runtime cancellation executes coordinator cleanup under `NonCancellable`, so cancellation of the parent listening operation cannot skip recorder release.

## Tests

JVM tests cover:
- duplicate start rejection while capture is active;
- stop waiting for Flow cleanup;
- call suspension releasing capture;
- no implicit resume after call interruption;
- system-audio suspension requiring explicit resume.

## Not yet implemented

- No Android TelephonyCallback / PhoneStateListener adapter is wired.
- No READ_PHONE_STATE permission is added.
- No guarantee of incoming-call detection on OPPO is claimed.
- The KWS pipeline and VoiceInteractionService are not yet connected to this owner.
- No persistent microphone session or always-on wake word is enabled.

## Next integration gate

1. Validate the Android call-state API and least-privilege permission flow for API 29–35 and ColorOS.
2. Add an injectable call/system-audio interruption source and platform adapter.
3. Connect the same owner to KWS/VAD and typed UI states.
4. Prove call preemption and resource release on OPPO Find X6 Pro; do not rely on emulator-only evidence.
5. Require all CI checks green on the exact head before merge.
