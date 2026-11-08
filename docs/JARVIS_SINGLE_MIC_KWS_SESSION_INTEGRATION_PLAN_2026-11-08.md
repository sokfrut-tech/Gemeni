# Jarvis single-microphone KWS session integration plan — 2026-11-08

## Status

Engineering plan based on source inspection of the KWS feature branch. This document does not claim that wake-word listening is integrated or operational. The current UI remains push-to-talk; the KWS model installer and detector are not wired into the runtime controller.

## Confirmed current architecture

- `AudioRecordPipeline.frames()` is a cold Flow that creates one AudioRecord per collection and releases it in `finally`.
- `JarvisRuntimeController.listenOnce()` collects that Flow, feeds Silero VAD, then runs Russian ASR, reasoning/tool proposal, and TTS.
- `SherpaJarvisKwsEngine` owns a Sherpa KeywordSpotter and stream, but has no runtime caller.
- `KwsModelDownloadManager` requires a dedicated consent boolean and verifies model-file SHA-256 values; it is not connected to a UI consent dialog or runtime.
- `WakeWordAudioFrameRouter` routes a frame to KWS or VAD capture, but is not currently instantiated by the runtime.
- `WakeWordSessionStateMachine` describes lifecycle transitions but owns no coroutine, microphone, or native resources.
- `MainActivity` currently offers a one-shot microphone action. Its lifecycle closes the runtime in `onDestroy`.

## Non-negotiable invariants

1. There is exactly one active microphone capture owner. KWS, VAD, and ASR must consume frames from the same `AudioRecordPipeline` collection; never open a second AudioRecord for wake detection.
2. Wake listening is opt-in, visible, and stoppable. No automatic startup, hidden background listening, or implicit re-enable after process recreation.
3. KWS model download requires its own explicit consent, separate from ASR/VAD consent. No model network request during app startup or push-to-talk.
4. Stop, permission loss, Activity/service teardown, coroutine cancellation, and any native/model failure must cancel capture and release KWS/VAD resources deterministically.
5. The keyword-detection frame is consumed by KWS only. VAD starts on the following frame to avoid including the wake-word tail in the spoken command.
6. After command processing, return to KWS only while the user-enabled session remains active. Otherwise stop and release the microphone.
7. Thermal protection applies to the always-on KWS loop as well as ASR, inference, and TTS. Under severe/unknown thermal state, stop or suspend wake listening safely and communicate why.
8. A successful build or emulator smoke test does not prove microphone behavior, acoustic accuracy, battery impact, or OPPO ColorOS background behavior.

## Confirmed gaps and risks

- The current `listenOnce` path does not call the KWS engine, router, or state machine.
- The KWS model's keyword manifest encodes English phonemes for “JARVIS”; Russian-speaker detection of “Джарвис” has not been measured. Treat it as experimental until tested with real Russian speakers and noise conditions.
- `AudioRecordPipeline.frames()` owns the recorder inside a Flow collection. The coordinator must own exactly one Job collecting it; do not independently collect the Flow for KWS and VAD.
- `listenOnce` currently processes frames inside `takeWhile` and collects an empty downstream body. Refactoring into a single explicit frame consumer is clearer for KWS/VAD routing and cancellation.
- The current Activity has no dedicated KWS model consent UI, no always-listening toggle, and no visible stop control.
- `JarvisSessionCoordinator` is currently only a minimal boolean holder; it is not the owner of the audio session lifecycle.
- The current runtime's `close()` closes speaker, Qwen, thermal guard, ASR, and VAD, but has no KWS session Job/engine to cancel yet.

## Target lifecycle

```text
User explicitly enables wake listening
  -> check RECORD_AUDIO permission
  -> if KWS model missing, show dedicated consent and wait
  -> verify installed model and load KWS/VAD/ASR
  -> start one capture Job over AudioRecordPipeline.frames()
  -> PREPARING -> WAITING_FOR_HOTWORD
  -> each frame goes only to KWS
  -> on hit: consume hit frame; CAPTURING_COMMAND
  -> subsequent frames go only to VAD
  -> VAD endpoint / silence / hard deadline
  -> PROCESSING -> Russian ASR -> guarded reasoning/tools -> TTS
  -> if still opted in and not cancelled: WAITING_FOR_HOTWORD
  -> otherwise STOPPED and release all resources
```

## Implementation slices

### Slice A — session owner and test seams

- Introduce a single `WakeWordSessionController` (or equivalent) as the sole owner of the capture Job, state machine, KWS engine, and router.
- Expose state as a read-only Flow/StateFlow for UI; expose explicit `startAfterConsent()` and `stop()` methods.
- Make capture Job single-instance; repeated start must not create another collection.
- Add deterministic tests for repeated start, stop during preparation, cancellation while waiting, cancellation while capturing, and failure cleanup.

### Slice B — route frames without opening a second recorder

- Collect `AudioRecordPipeline.frames()` exactly once.
- While waiting, send each non-empty frame to KWS only.
- On keyword hit, transition to capture and do not forward that frame to VAD.
- Feed only subsequent frames to VAD; poll all completed segments after each frame.
- Reuse a shared command-processing method so push-to-talk and KWS use the same ASR/tool/TTS path without duplicating tool safety logic.

### Slice C — UI consent and controls

- Add a separate KWS download consent dialog naming model source, approximate size, network/IP metadata, and SHA-256 verification.
- Add an explicit “Wake word listening” toggle and persistent visible state (“Ожидание слова”, “Слушаю команду”, “Обрабатываю”, “Остановлено”, “Ошибка”).
- Add a prominent stop control; stop must synchronously signal cancellation and UI must not report stopped until the capture Job has completed cleanup.
- Do not auto-enable on launch, resume, reboot, or after a crash.

### Slice D — thermal, battery, and device validation

- Check thermal budget before enabling and during KWS capture.
- Keep KWS on CPU with bounded thread count; never run local LLM inference while waiting for a keyword.
- Define a conservative policy for severe thermal state, low battery, power saver, and prolonged background restrictions.
- Test with OPPO Find X6 Pro / ColorOS: foreground listening, screen off, app switching, incoming calls, permission revocation, hot environment, charging, and repeated command cycles.
- Record battery drain and thermal-status changes; do not infer actual component temperature from Android thermal status alone.

## Acceptance criteria

- At most one AudioRecord instance exists during the full KWS -> VAD -> ASR -> response cycle.
- Stop completes within a bounded timeout and no microphone capture continues afterward.
- No model download occurs without the dedicated KWS consent action.
- No KWS session starts implicitly after process/activity recreation.
- State transitions and cleanup paths have unit/instrumentation coverage.
- Existing push-to-talk behavior remains functional and shares the same safe command execution path.
- CI security scan, JVM tests, APK build, and emulator smoke tests all pass on the exact PR head.
- Real-device acoustic, thermal, battery, and ColorOS lifecycle tests are documented separately before declaring always-on listening production-ready.

## Next engineering action

Implement Slice A only, add lifecycle tests, then integrate Slice B in a separate reviewable increment. Do not wire an untested background microphone service or claim Russian wake-word accuracy before device measurements.
