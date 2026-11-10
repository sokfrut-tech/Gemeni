# Jarvis KWS/VAD runtime integration plan — 2026-11-09

## Purpose

Continue the voice stack from the existing single-owner microphone foundation without enabling hidden or unattended microphone capture. This document records the inspected source state and defines the next safe vertical slice.

## Verified repository state

- Repository: `sokfrut-tech/Gemeni`, default branch `main`.
- PR #123 (responsive Compose home + visible stop control) is open Draft, based on `main`.
- PR #124 (serialized `AudioCaptureCoordinator`) is open Draft, based on PR #123.
- PR #126 (testable KWS/VAD/local-ASR pipeline with interruption gating) is open Draft, based on PR #124.
- PR #125 (Android PR build/emulator CI) is open Draft, based on `main`.
- PR #126 head: `283da9e4cdcf1f995f0e17a33ec4e1f3c706bba9`.
- PR #126 Factory Security Baseline run #382 passed CodeQL and secret scanning. This is not Android build or emulator evidence.
- PR #125 Build Jarvis run #200 built the debug APK, passed JVM unit tests, native/security checks and APK manifest/ABI inspection, but its Android emulator runtime smoke step failed on attempt 1. Failed job was re-run (attempt 2); it was in progress at the time of this handoff. Do not call the emulator gate green until the rerun finishes and its result is inspected.
- No PR in this chain is merged and no APK release is authorized.

## Source-level findings

### Existing working foundations

- `AudioRecordPipeline.frames()` owns AudioRecord creation and releases it in a `finally` block.
- `AudioCaptureCoordinator` serializes capture start/stop/suspension and waits for Flow cleanup with `cancelAndJoin()`.
- `JarvisVoicePipeline` now has injected `WakeWordGate`, `SpeechSegmenter`, and `LocalSpeechTranscriber` contracts.
- The pipeline rejects frames while suspended; resume requires explicit user opt-in.
- Sherpa KWS, Silero VAD and Russian ASR adapters exist.
- KWS and VAD model managers exist and store model files outside the APK. The KWS manager writes a keyword manifest after verifying downloaded model files.

### Critical remaining integration gap

The current `JarvisRuntimeController.listenOnce()` is still a bounded push-to-talk path:
1. It collects microphone frames into an in-memory list for 4.5 seconds.
2. It concatenates the collected PCM frames.
3. It runs Russian ASR after capture stops.
4. It dispatches a limited set of intents and uses Android TextToSpeech.

The new KWS/VAD pipeline is not yet connected to `JarvisRuntimeController` or the Compose screen. The KWS/VAD classes existing in source do not prove that wake-word recognition works end-to-end.

The current runtime creates a new coordinator per push-to-talk operation. Before adding a persistent, explicitly started wake-word session, the runtime should own one lifecycle-managed capture coordinator and make all voice modes share it. The `:session` Android process must not independently open AudioRecord; the existing process-wide atomic guard does not cross Android process boundaries.

### Model and safety boundaries

- KWS and VAD model files are separate from the APK.
- Model downloads must remain explicit-consent, user-initiated operations; do not silently download on microphone start.
- KWS model is configured for an English-phoneme pronunciation of “JARVIS”. Recognition of the Russian pronunciation «Джарвис» is not yet proven and needs acoustic evaluation on the target device.
- Push-to-talk remains the default. Any wake-word session must have a clear start action, visible active state, persistent Stop control, and explicit consent for model download.
- Incoming calls and competing system audio must preempt capture. Release AudioRecord/native resources before publishing a suspended state. Never capture or transcribe call audio. No automatic microphone reacquisition after interruption.
- Thermal policy must be checked before model initialization and inference. Do not bypass Android/ColorOS thermal management.
- Do not claim real-time ASR or always-on readiness based on JVM fake-adapter tests.

## Next implementation slice

Implement an explicitly user-started, visible wake-word session that shares the single capture owner:

1. Refactor `JarvisRuntimeController` to own one capture coordinator for its lifetime and expose typed runtime states rather than relying only on free-form status strings.
2. Add a preparation operation that verifies the ASR, KWS and VAD model files. Downloads require separate explicit user consent and must be cancellable, bounded, hash-verified and atomically installed.
3. Load KWS/VAD/ASR adapters off the main thread; release all native objects deterministically on stop, failure and Activity/runtime destruction.
4. Feed each captured frame through `JarvisVoicePipeline` from the one capture owner. Never send the wake-word trigger frame to command VAD/ASR.
5. Route completed local transcripts into the existing command path; retain deterministic offline fallback and the safe action-confirmation boundary.
6. Add visible Compose controls and states: inactive, preparing, listening for wake word, capturing command, processing, responding, suspended, error. A single Stop action must cancel capture and wait for AudioRecord cleanup.
7. Keep call/system-audio interruption adapter separate from the pure pipeline. Add the platform adapter only after API/permission review; do not add broad phone-state permissions without a demonstrated need.
8. Add deterministic tests for repeated start/stop, cancel during model load, model failure, KWS hit, wake-frame exclusion, VAD segmentation, blank ASR, ASR exception, stop while processing, interruption during every active state, no auto-resume, and resource release exactly once.
9. Add emulator smoke assertions for the visible state and stop control. Validate actual model load, Russian acoustic quality, thermal behavior, call preemption and ColorOS lifecycle on OPPO Find X6 Pro before release.

## Release blockers

- Exact-head Android build and JVM tests for PR #126.
- Emulator smoke test with preserved diagnostics and stable assertions.
- A single end-to-end voice path wired to the runtime and UI.
- Verified model consent/download/cancellation and native resource cleanup.
- Real OPPO Find X6 Pro validation for audio lifecycle, wake-word quality, calls, thermals and battery.
- Existing product-level gates remain: real Android actions, TTS, VoiceInteraction, Accessibility/AIDL behavior, visible native renderer and security/ABI/manifest checks.

## Agent handoff

Work only in `sokfrut-tech/Gemeni`. Inspect current branch heads and PR dependencies before writing. Keep PRs Draft until exact-head checks pass. Do not merge, publish, deploy or label an APK release-ready without explicit authorization and complete evidence.
