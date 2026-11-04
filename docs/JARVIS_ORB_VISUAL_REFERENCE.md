# Jarvis Orb visual reference and implementation brief

Date: 2026-11-04  
Repository: `sokfrut-tech/Gemeni`  
Branch: `feat/jarvis-orb-visualizer-2026-11-04`

## User reference
The user supplied a six-panel reference image of abstract luminous orbital spheres (amber and cyan/teal filaments, intersecting tilted rings, particle trails and a bright central core). The desired Jarvis identity is a holographic energy core, not a static flat circle and not a copied proprietary movie asset. The Instagram carousel also describes an open-source desktop Jarvis workflow: clone a project, let a coding agent install it, and optionally provide voice via ElevenLabs. Treat those as inspiration, not verified feature claims for our Android app.

## External open-source comparison
- [hasibjaan/jarvis](https://github.com/hasibjaan/jarvis): useful lightweight orb principles. Its orb is deliberately cheap to draw, follows measured microphone/playback amplitude, uses explicit IDLE/LISTENING/PROCESSING/SPEAKING/ERROR states, and avoids a heavy fullscreen repaint that can interrupt audio. Adapt the state-driven visual design and performance discipline; do not port its Python/PySide implementation into Android.
- [Dodaios/Dodaios](https://github.com/Dodaios/Dodaios): useful architecture/safety lessons. Route every tool through one supervisor gateway; use argument-aware risk levels (safe/monitor/ask/block), exact-call approval, and audit traces. Do not copy its desktop stack into the Android client.
- [isair/jarvis](https://github.com/isair/jarvis): useful local-first model/speech and privacy concepts, but it is desktop-first and explicitly has no mobile app; not a source to transplant.
- [aviarytech/jarvis](https://github.com/aviarytech/jarvis): useful voice-first harness concept, but not a drop-in Android implementation.

## Work started in this branch
The native GLES2 renderer is being upgraded from a simple glowing ring to a low-overhead orbital core: three tilted orbit planes, animated orbit traces, moving light points, radial filaments, a breathing central core and a listening intensity multiplier. Shader compile/link failures are now logged to Android logcat instead of silently producing a black view. The visual remains a single GLES2 pass and does not require downloaded assets.

## Required follow-up before claiming done
1. Connect actual runtime states (IDLE, LISTENING, PROCESSING, SPEAKING, ERROR) to the renderer; current Activity only supplies a listening boolean.
2. Feed measured audio amplitude from AudioRecord and TTS playback to the visualizer; do not synthesize a fake waveform and label it real.
3. Add shader compile/link and JNI lifecycle instrumentation, EGL error capture, non-black frame assertion and pause/resume test.
4. Profile on low-end Android and OPPO ColorOS; reduce render rate when idle and verify that rendering does not cause ASR/TTS underruns.
5. Verify on an emulator and physical OPPO Find X6 Pro / Chinese ColorOS before claiming the black-screen issue is fixed.
6. Preserve offline ASR/TTS as the default; ElevenLabs is optional cloud TTS and requires the user's own key/consent. Never put API keys in source or GitHub.

## Safety and product boundaries
- The supplied image is a design reference; do not redistribute third-party Instagram content or copy another project's source/assets without checking its license.
- No provider API key, subscription, paid request, production deployment, or Google Play release is part of this change.
- Do not claim this makes Jarvis a PC-control agent. Android device actions remain separately scoped and confirmation-gated.
