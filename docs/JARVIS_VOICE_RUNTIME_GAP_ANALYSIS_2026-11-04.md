# Jarvis voice runtime gap analysis — 2026-11-04

## Verified repository state

The repository already contains these Android-native building blocks:

- `AudioRecordPipeline`: 16 kHz mono PCM microphone stream, permission check, coroutine cancellation cleanup.
- `SileroVadEngine`: Sherpa-ONNX Silero VAD wrapper with speech segmentation methods.
- `SherpaJarvisKwsEngine`: Sherpa-ONNX keyword spotting wrapper configured for the English acoustic keyword "JARVIS".
- `HotwordDetector`: gates wake-word decisions and can bind the KWS engine.
- `JarvisVoiceInteractionService` and `JarvisSessionService`: Android VoiceInteractionService/session entry points.
- `JarvisRuntimeController`: currently prepares Russian ASR and captures a fixed 4.5-second one-shot audio window before transcription.

## Important distinction

The presence of VAD/KWS classes does **not** mean always-listening or wake-word activation is working. Inspection of the current runtime controller shows it does not instantiate or call `SileroVadEngine`, `SherpaJarvisKwsEngine`, or `HotwordDetector`. The Android voice interaction service is currently only a lifecycle shell. No persistent foreground microphone service is connected to these components.

## Recommended implementation order

1. Add model lifecycle management for VAD and KWS. Store model files in app-private storage, verify SHA-256 before loading, use atomic downloads, and expose offline-ready status. Do not ship unverified model URLs or checksums.
2. Add a user-controlled foreground microphone service. Require explicit opt-in, visible ongoing notification, clear stop action, microphone permission, and Android foreground-service microphone type/permission declarations for supported API levels.
3. Integrate KWS first. On a positive wake event, transition state and begin a bounded utterance capture. Do not continuously run Russian ASR for wake-word detection.
4. Integrate Silero VAD for speech endpointing. Use bounded pre-roll, maximum utterance duration, silence timeout, and cancellation cleanup.
5. Route captured speech through local Russian ASR and the existing restricted tool registry.
6. Add battery/thermal tests, false-positive tests, permission-revocation tests, process-death recovery, notification-stop tests, and OPPO ColorOS device validation.

## Safety and privacy requirements

- Microphone use must be visible and user-controlled. No hidden background recording.
- No raw audio persistence by default. If diagnostics are opted into, redact and expire data.
- No call/SMS sending, arbitrary shell, unrestricted Accessibility actions, purchases, or destructive file operations.
- Keep high-impact actions behind explicit confirmation. The current `ACTION_DIAL` path only opens the dialer and does not place calls.
- If models are missing or corrupt, fail closed to push-to-talk; never silently download or enable continuous listening.
- Provide a persistent stop control and a clear microphone-active state.

## Verification gates

A feature is not considered implemented until:
- exact-head CI compiles Kotlin and assembles the debug APK;
- unit tests cover state transitions, audio segmentation, wake-word gating, cancellation and failure cases;
- emulator tests verify permission, foreground notification, stop action and process lifecycle;
- a physical OPPO/ColorOS test confirms microphone routing, battery behaviour and system restrictions.

## Current status

This document records an inspection of the source files. It does not claim that continuous listening, VAD endpointing, acoustic wake-word activation, or foreground microphone operation is implemented or tested.
