# Jarvis audio interruption state model — 2026-11-08

## Status

This increment adds a pure Kotlin lifecycle model for microphone interruption. It does **not** yet connect Android telephony callbacks, AudioRecord cleanup, KWS, VAD, or the UI. No claim is made that live call protection is implemented.

## Changes

- Added `SUSPENDED_FOR_CALL` and `SUSPENDED_FOR_SYSTEM_AUDIO` lifecycle states.
- Added explicit transition methods for call and competing system/app audio interruptions.
- Added `resumeAfterInterruption(userOptInStillActive)`; resume is rejected unless the caller confirms the user's explicit opt-in remains active.
- A successful resume returns to `PREPARING`, never directly to hotword capture. The owner must re-check permission, thermal policy, model readiness, and microphone availability before acquiring resources.
- Calls and system audio can interrupt every active stage: preparation, wake-word wait, command capture, and processing.

## Safety boundary

The state machine owns no Android/native resources. The future session controller must cancel the sole capture Job and release AudioRecord/KWS/VAD before publishing a suspended state. Android telephony/audio policy remains authoritative. No call audio may reach KWS, VAD, ASR, or logs.

This increment deliberately adds no phone-state permission and no telephony adapter. The correct Android API/permission strategy must be validated against supported API levels and OPPO ColorOS before wiring platform callbacks.

## Tests

Added deterministic JVM tests for:
- call interruption from every active lifecycle stage;
- system audio contention;
- no implicit resume without explicit opt-in;
- resume returning to preparation;
- rejection of frame/lifecycle events while suspended.

## Remaining work

1. Implement a single-owner capture controller with cancellation and exactly-once cleanup.
2. Add injectable interruption source and Android adapter after API/permission review.
3. Route KWS/VAD frames from one AudioRecord collection.
4. Add instrumentation tests for capture release and competing microphone use.
5. Validate incoming/active/emergency call behavior on OPPO Find X6 Pro. Emulator results are insufficient.
6. Run full CI on this exact PR head. Keep Draft until all required jobs pass.
