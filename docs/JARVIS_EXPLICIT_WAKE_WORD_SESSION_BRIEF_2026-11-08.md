# Jarvis KWS frame-routing seam — 2026-11-08

## Change

Added `WakeWordAudioFrameRouter`, a pure Kotlin routing seam for the planned single-recorder session coordinator.

- In `WAITING_FOR_HOTWORD`, each non-empty frame is sent only to KWS.
- On a positive KWS hit, the session transitions to `CAPTURING_COMMAND`; the triggering frame is not also sent to VAD.
- Subsequent frames are sent only to the VAD command-capture callback.
- Empty frames and all frames in stopped/preparing/processing/error states are ignored.
- The router does not instantiate AudioRecord and cannot create a second microphone owner.

## Tests added

- `frameIsSentToKwsOrVadButNeverBoth`: verifies exclusive routing and that the trigger frame is not reused as command audio.
- `noMatchKeepsFramesInKwsAndStopPreventsFurtherDispatch`: verifies no-match behavior and no further dispatch after stop.

## Integration status

This is a tested seam, not yet wired into `JarvisRuntimeController` or the Compose UI. The next commit must integrate it into one `audio.frames()` collection and ensure coroutine cancellation closes AudioRecord/KWS/VAD in `finally`. Do not describe KWS as operational until that integration and lifecycle instrumentation are complete.

## CI note

Run #347 was cancelled after CodeQL Kotlin build and JVM unit tests succeeded on SHA `9a2055bb7fc50fcd168b923cc64b02f8d50b04fe`; APK build and emulator smoke tests did not run. This new commit requires its own full CI result.
