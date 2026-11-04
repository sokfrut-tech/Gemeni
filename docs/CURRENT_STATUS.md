# CURRENT STATUS — Jarvis / Gemeni

Last handoff update: 2026-10-28

## Overall state
**NOT RELEASE READY.**

The project builds in CI, but the product is not accepted until runtime tests prove real voice, tools, system integration, and visual rendering.

## Latest user-observed regression
On the user's OPPO device, the installed app displayed a black screen with only the text `Джарвис / Русский голосовой ассистент / для ColorOS`. The user reported that the app had no working functionality. This is a hard regression and the release gate must catch it.

## What has been identified
- Previous MainActivity UI contained placeholder behaviour.
- Microphone interaction was not connected to AudioRecord/ASR.
- Quick-command buttons changed text instead of executing Android intents.
- VoiceInteractionService/session implementations were incomplete placeholders.
- Accessibility and AIDL paths contained no-op behaviour.
- Native renderer linkage was not sufficient evidence that a visible frame was rendered.

## Work direction
The project is being converted from a buildable scaffold into an evidence-tested product.

### Required runtime chain
`KWS -> VAD -> AudioRecord -> Sherpa Russian ASR -> Intent/Tool Registry -> Android action -> Piper TTS -> visual response`

### Required system chain
`VoiceInteractionService -> VoiceInteractionSessionService/Session -> Jarvis core -> tools/AIDL -> UI`

### Required visual chain
`Compose shell -> native GLSurfaceView -> JNI -> EGL/GLES2 -> visible animated sphere/AlphaVideo-style rendering`

## AI swarm
Specialist review tracks are defined for:
- ColorOS/Android system integration
- UI/UX
- NDK/GLES/JNI
- Speech stack
- Functional tools/AIDL
- Security
- QA/release

External models may be used only through explicitly authorized connections. The repo contains the durable handoff specification; agents must leave reports and test evidence.

## Release gate
Do not provide an APK to the user as “ready” unless:
- clean install launches the intended UI;
- screenshot is not the black placeholder screen;
- microphone capture is verified;
- Russian ASR path is verified;
- at least three real commands execute their intended Android actions;
- TTS response is verified;
- VoiceInteraction registration is verified;
- Accessibility path is verified where applicable;
- AIDL calls are non-no-op;
- native renderer produces a visible frame;
- security/ABI/manifest checks pass;
- emulator smoke tests pass;
- OPPO device test is documented when available.


## 2026-11-04 update — active handoff

**Current PRs:** [#55 orb visualizer](https://github.com/sokfrut-tech/Gemeni/pull/55), [#57 architecture roadmap](https://github.com/sokfrut-tech/Gemeni/pull/57), [#58 Android tool gateway](https://github.com/sokfrut-tech/Gemeni/pull/58), [#59 parser tests/package visibility](https://github.com/sokfrut-tech/Gemeni/pull/59). All are still review/draft work; none is declared merged or release-ready here.

- Added tested pure Russian command parser and JUnit coverage in PR #59.
- Added Android launcher package visibility declaration.
- Existing VAD/KWS engine wrappers are present in source but are not wired to JarvisRuntimeController. Current runtime uses a fixed 4.5-second one-shot recording. No connected foreground microphone service or persistent listening yet.
- CI run 41903416302 on earlier PR #59 head: secret scan passed and Kotlin compile passed; APK build was in progress at last evidence. Docs then changed branch head; recheck exact-head CI. Emulator/OPPO tests not confirmed.
- Safety: dialer opens ACTION_DIAL only. No automatic calls/SMS, hidden recording, purchases or destructive actions.

**Next:** recheck exact-head CI for PR #59; resolve failures; only then integrate opt-in foreground microphone service, acoustic KWS and VAD with visible notification/stop control. See docs/JARVIS_VOICE_RUNTIME_GAP_ANALYSIS_2026-11-04.md and docs/CONTINUATION_STATE.md.
