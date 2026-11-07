# Jarvis VAD Voice Loop Integration Brief

Date: 2026-11-07
Status: Design brief; implementation must follow PR #107 secure downloader verification.
Primary runtime: Android / Kotlin / Sherpa-ONNX / local Russian ASR.
Scope: Replace the current fixed 4.5-second push-to-talk capture window with local VAD endpointing. This is not an always-on wake-word implementation.

## Confirmed current state

- `AudioRecordPipeline.frames()` provides 16 kHz mono PCM frames and releases AudioRecord in a `finally` block.
- `SileroVadEngine` already wraps Sherpa-ONNX Silero VAD at 16 kHz, accepts PCM frames, and can return completed segments.
- `SileroVadModelManager` was previously a simple downloader; PR #107 hardens it and requires explicit consent.
- `JarvisRuntimeController.listenOnce()` currently collects audio for a fixed 4.5 seconds, then transcribes the concatenated PCM.
- The VAD and hotword pipeline classes exist, but the active push-to-talk runtime does not yet use them.
- The current ASR consent dialog discloses only the Russian ASR model. It must be updated before Silero is downloaded by the UI.

## Product behavior

1. User taps the microphone. Jarvis checks whether the Russian ASR model is installed and verified.
2. If ASR is missing, show the existing consent dialog with an accurate disclosure of all files that will be downloaded.
3. The updated dialog must clearly name both model families:
   - Russian streaming ASR model from Alpha Cephei (pinned revision, Apache-2.0; approximately 29 MB as currently described in UI).
   - Silero VAD model from sherpa-onnx (16 kHz; official docs list approximately 629 KiB).
4. State the network disclosure: only model files are downloaded; the hosting server sees ordinary connection metadata such as IP address; microphone audio and transcripts are not uploaded.
5. No request for either model may occur before the user explicitly taps the consent action. Do not treat Android microphone permission as model-download consent.
6. Download both models sequentially with bounded progress and cancellation. Each downloader owns its own URL allowlist, size cap, checksum verification, temporary-file cleanup and atomic installation.
7. If both models are already installed and verified, microphone startup must perform no network request.
8. Load ASR and VAD locally. If VAD is missing/corrupt or cannot initialize, report a clear error and retain an explicitly labelled short push-to-talk fallback only if the product owner approves it; never silently pretend VAD is active.

## Endpointing behavior (initial targets; tune with device evidence)

- Sample rate: 16,000 Hz, mono, PCM16.
- VAD provider: CPU, one thread initially.
- Speech threshold: begin with Sherpa-ONNX default/current 0.5; tune only with recorded test fixtures and device measurements.
- Minimum speech duration: 0.20 s.
- Minimum trailing silence: start at 0.55 s (current 0.25 s may cut natural pauses too aggressively).
- Maximum speech segment: 25 s.
- No-speech timeout after microphone activation: 8 s.
- Hard command capture ceiling: 30 s; this prevents a stuck microphone session.
- Stop capture after a completed endpoint segment; always close AudioRecord on success, timeout, cancellation and exception.
- Avoid cutting long utterances: either set VAD max segment to the command ceiling or accumulate consecutive segments until endpoint/ceiling, then transcribe the combined utterance.
- Empty/no-speech result returns to IDLE with a clear prompt; it must not execute an empty command.

## Implementation sequence

A. Complete PR #107 and obtain exact-head JVM, CodeQL, APK and emulator evidence.
B. Add VAD readiness/load/close lifecycle to `JarvisRuntimeController`; never download from `prepare()`.
C. Update the consent dialog copy and state flow so consent explicitly covers both ASR and VAD downloads; do not start the mic until both model files are verified and loaded.
D. Replace the fixed 4.5-second collector with a cancellable endpointing collector driven by `SileroVadEngine.accept()` and `pollSegment()`.
E. Add a small pure-Kotlin endpoint policy/state machine behind an injectable VAD interface so timing, no-speech timeout, endpoint and max-duration behavior can be unit-tested without native ONNX.
F. Add Android instrumentation tests for permission denial, model-not-installed consent, no-network-before-consent, no-network-when-ready, microphone resource release and cancellation.
G. Verify on OPPO Find X6 Pro / ColorOS: quiet room, TV/background noise, normal speech, pauses, long utterance, short utterance, no speech, interruption/cancel, screen rotation and app backgrounding.

## Required tests

- Consent false -> zero connection factory calls for both downloaders.
- Consent true -> both models install only after digest verification; no .part files remain.
- Existing verified models -> no HTTP connection is opened.
- Missing or corrupt VAD model -> explicit recoverable error; no silent VAD fallback.
- Speech + trailing silence -> one completed segment and one ASR invocation.
- Mid-sentence pause shorter than configured silence threshold -> command remains open.
- No speech -> exits at no-speech timeout and returns IDLE.
- Long utterance -> no truncation before the hard command ceiling.
- Cancellation during blocking AudioRecord read -> coroutine ends and recorder is released.
- Cancellation during model download -> active connection disconnects; no final or partial model is left.
- ASR/VAD load failure -> mic session closes safely and UI shows ERROR.
- All tool actions remain behind the existing risk/confirmation policy.

## Non-goals for this slice

- Always-on hotword / background microphone service.
- Cloud speech recognition or cloud upload of raw audio.
- Changing the Qwen reasoning provider.
- Automatic model download on app launch.
- Removing explicit user confirmation for sensitive Android actions.

## Release gate

Keep this integration in Draft until the full JVM suite, CodeQL, APK assembly, emulator instrumentation with a non-zero test count, and a real OPPO device smoke test have all passed. A successful Gradle build alone is not proof that microphone/VAD behavior works on the target phone.
