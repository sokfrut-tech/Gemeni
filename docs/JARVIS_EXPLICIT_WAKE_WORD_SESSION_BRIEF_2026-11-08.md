# Jarvis explicit wake-word session — engineering brief (2026-11-08)

## Status
Planning/implementation boundary: this branch starts from PR #114 head `6d9099774cc91297e773d42936273b72990ba20d`. This document records the scoped design; it does not claim acoustic KWS is integrated or working. Keep this work separate from the emulator-stability PR.

## Confirmed current architecture
- `MainActivity.requestAndListen()` requests RECORD_AUDIO and then calls `startRealListening()`.
- `JarvisRuntimeController.listenOnce()` opens `AudioRecordPipeline.frames()`, feeds frames to Silero VAD, converts completed speech segments to PCM, runs Sherpa Russian ASR, then sends the transcript through the guarded tool registry.
- `AudioRecordPipeline.frames()` owns AudioRecord in a Flow and releases it in `finally`; it checks RECORD_AUDIO permission before constructing the recorder.
- `SherpaJarvisKwsEngine` wraps Sherpa ONNX KeywordSpotter and accepts 16 kHz mono PCM16 frames.
- `HotwordDetector` can bind an acoustic engine and gate KWS, but no user-facing capture path currently invokes it.
- `KwsModelDownloadManager` verifies four model files by SHA-256 and validates the generated `keywords.txt`. Its download must remain behind a separate, explicit user consent action.
- Existing KWS model is labelled zh/en and maps the wake word to English phonemes. Recognition quality for a Russian speaker saying “Джарвис” is not yet demonstrated; it must be measured, not assumed.

## Root cause / gap
The KWS engine and detector exist as isolated components, while the only wired microphone path is a one-shot VAD → ASR flow. Therefore acoustic wake-word detection is not currently active in the app. The engine's old “always-on” wording is misleading: no background microphone session is implemented and none should be silently enabled.

## Required user experience
1. Push-to-talk remains the default and continues to work without downloading KWS assets.
2. Add a separate, clearly labelled “Режим ключевого слова” opt-in control.
3. Starting that mode is an explicit user action. Check RECORD_AUDIO permission at start and fail closed if permission is absent/revoked.
4. If KWS assets are absent, show a separate consent screen explaining model download size/source and do not make network requests until consent.
5. Show a persistent, visible listening state and a clear Stop control while the session is active.
6. Do not run a microphone in the background or after the user leaves/stops the session. No boot receiver, hidden service, or automatic restart is part of this scope.
7. On stop, cancellation, Activity destruction, permission revocation, audio failure, or model error: cancel collection and release AudioRecord, KWS stream/spotter, and any active VAD state.

## Target pipeline
Explicit opt-in session → permission/model readiness checks → load KWS → acquire 16 kHz mono PCM frames → acoustic KWS gate → after detection, transition to VAD capture → ASR → guarded command proposal/confirmation → speech response. After a completed command, return to KWS only while the same explicit session remains active; otherwise stop and release all resources.

## Implementation constraints
- Prefer a dedicated session coordinator/state machine over adding complex branches to the existing one-shot `listenOnce()`.
- States should be explicit and testable, e.g. STOPPED, PREPARING, WAITING_FOR_HOTWORD, CAPTURING_COMMAND, PROCESSING, ERROR.
- Use structured coroutine cancellation and `try/finally`; ensure only one active AudioRecord owner.
- Reuse the audio pipeline only if its Flow lifecycle can be safely controlled and its ownership is unambiguous. If needed, expose a bounded frame stream with cancellation semantics rather than a second concurrent recorder.
- KWS and VAD must receive the correct sample rate, mono channel layout, and PCM normalization. Validate frame handling and empty heartbeat behavior.
- Avoid calling Sherpa native APIs concurrently; synchronize engine lifecycle and processing.
- Do not claim “always-on”, “background”, “hands-free”, or Russian wake-word accuracy until device/emulator evidence supports those claims.
- Keep network access limited to the explicit KWS model consent/download path. Verify SHA-256 before loading.
- Never bypass Android microphone permission, user-visible indicators, or platform privacy restrictions.

## Required tests before calling this functional
### JVM/unit
- State-machine transitions and invalid transitions.
- Stop during preparation, KWS wait, VAD capture, ASR, and response.
- Cancellation releases session resources exactly once.
- Permission absent/revoked fails closed.
- KWS not ready causes a consent-required result and no download.
- Model download is never invoked without explicit consent.
- KWS positive/negative frame outcomes and disarmed detector behavior.
- Empty frames/heartbeats do not cause false detection or unbounded waits.
- A completed command returns to KWS only if the opt-in session remains active.

### Android instrumentation
- Start session with permission granted.
- Deny permission and verify no recorder is opened.
- Stop control cancels the session and returns UI to STOPPED.
- Activity destruction releases microphone resources.
- KWS model missing: consent UI appears; declining performs no network request.
- KWS model invalid/hash mismatch fails closed.
- No duplicate AudioRecord instances during transitions.

### Device validation
- Test on OPPO Find X6 Pro / target ColorOS with real microphone.
- Measure false accepts/rejects and latency in realistic noise.
- Verify Russian pronunciation of “Джарвис”; if zh/en KWS is inadequate, evaluate a Russian-capable model or phonetic variants before shipping.

## Logging and evidence
For every implementation commit, update this document with:
- exact commit SHA and files changed;
- root cause and behavior change;
- test command/workflow run IDs and exact head SHA;
- unit/instrumentation counts and failures;
- relevant logs/artifact IDs and SHA-256 where available;
- explicit NOT TESTED items.
A red CI blocks release. Emulator success is not physical OPPO validation.

## Guardrails
- Draft PR only; do not merge.
- Do not publish or distribute APK from this branch.
- Keep PR #114 isolated; do not add KWS changes to it.
- No claim of Claude/Gemini review unless an actual review artifact exists.
