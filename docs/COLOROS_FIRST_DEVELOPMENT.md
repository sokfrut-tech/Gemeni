# ColorOS-first development policy

## Target

Primary target is the Chinese OPPO / ColorOS build used for the project. Other Android variants are compatibility targets after the ColorOS path is functional.

## Engineering order

1. Validate the exact Chinese ColorOS firmware and available assistant roles/components.
2. Implement the real VoiceInteractionService and VoiceInteractionSession path.
3. Validate assistant selection through the system default-assistant mechanism.
4. Validate OEM dispatch paths (power/gesture) without assuming AOSP routing is unchanged.
5. Validate background survival, battery/freeze behavior, permissions, reboot persistence and lock-screen behavior.
6. Validate Russian ASR -> intent -> Android action -> TTS end-to-end.
7. Run independent QA and security gates on the ColorOS target.
8. Only after the ColorOS baseline is green, add adapters/compatibility for AOSP and other Android skins.

## Evidence rules

Research from China, Japan, Russia, India, Europe and other sources may produce hypotheses. A hypothesis is not accepted as a fix until reproduced or validated against our target firmware/build artifacts or a controlled test environment.

Community research currently indicates that China-region ColorOS can rewrite assistant dispatch paths toward OEM components and that only one voice-interaction service is active at a time. These are investigation targets, not assumptions about every firmware version.

## Hard release rule

A generic Android emulator PASS cannot substitute for ColorOS validation. If ColorOS behavior is unknown, release status is BLOCKED/UNVERIFIED rather than PASS.

## Compatibility strategy

Keep the core assistant implementation platform-neutral where practical, but isolate ColorOS-specific behavior behind explicit adapters. Do not weaken the ColorOS implementation merely to make AOSP tests pass.
