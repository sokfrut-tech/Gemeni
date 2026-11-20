# Jarvis Android Assistant — status and source audit
Date: 2026-11-21
Repository: `sokfrut-tech/Gemeni`
Audited ref: `integration/jarvis-assistant-ci-smoke-repair-2026-11-10`
Audited head at start of audit: `bc1ea81e2fa7a0bc1f21f6b20891e5d8d95c4026`
Base: `main` at `43ddc89850637fad045924e43cbbbe6a5719d961`
Scope: Jarvis Android Assistant only. Do not expand the separate Jarvis AI Core track without a new user-approved brief.

## Executive status

**NOT RELEASE READY.** PR #133 remains open and Draft. Do not merge or publish an APK based on this audit. No physical OPPO Find X6 Pro / ColorOS qualification is evidenced.

PR: https://github.com/sokfrut-tech/Gemeni/pull/133

## Exact-head CI evidence

- Current PR head at audit start: `bc1ea81e2fa7a0bc1f21f6b20891e5d8d95c4026` (`test(jarvis): cover resource-gated local provider`).
- GitHub's commit-workflow-runs lookup returned **no PR-triggered workflow runs** for this exact head.
- Prior Build Jarvis run #42872570035 tested older commit `733f25b704e2a331f9f613835b757dd1a10ddb5f` and was **cancelled** during the NDK installation step. APK build, JVM tests, native checks and emulator tests were skipped. It is not a passing build.
- Prior Factory Security Baseline run #42872570007 for `733f25b...` completed successfully: secret scanning and CodeQL passed; a debug APK and SARIF artifacts were uploaded. This does not replace Android build/emulator qualification on current head.
- A PR comment records that an earlier commit `53eaf441ecabaf147aa7f7ca108b089d28b1dfe7` passed build/JVM/native/manifest/emulator checks (3 UI tests, 0 failures), but screenshot inspection showed the central native visual core was black. Commit `733f25b...` attempted a GLES shader/context repair; its Build Jarvis run was cancelled before validating that repair.
- No CI result observed in this audit proves the current `bc1ea81...` head.

## Source-level findings on audited head

### Present foundations
- Compose home screen with animated/native visual surface, responsive layout and explicit stop interaction.
- Permission-gated microphone entry and explicit-consent ASR model download.
- AudioRecord pipeline and serialized `AudioCaptureCoordinator`.
- Sherpa Russian ASR, Silero VAD and KWS adapters.
- Separate `JarvisVoicePipeline` contracts and interruption-safe frame-routing logic.
- Some real deterministic Android actions in `JarvisRuntimeController`: ACTION_DIAL, browser/search intents and navigation intents.
- Piper/Sherpa speech wrapper and thermal/resource policy classes.
- AIDL service, VoiceInteraction service/session and Accessibility service declarations.

### Critical integration gaps confirmed by source inspection
1. **KWS/VAD are not wired into the active UI runtime.** `JarvisRuntimeController.listenOnce()` creates a new coordinator for each turn, records a fixed 4.5-second sample, then runs ASR. It does not invoke `JarvisVoicePipeline`. Current UX is bounded push-to-talk, not a working wake-word assistant.
2. **Local LLM is still a placeholder.** `LocalLlmEngine.complete(prompt)` returns `prompt` unchanged. The local inference decision document selects llama.cpp as a candidate and Qwen2.5-0.5B Q4_K_M as a profiling candidate, but neither runtime nor model is integrated.
3. **Cognitive kernel is not in the active speech path.** `JarvisCognitiveKernel` and `ResourceGuardedLocalProvider` exist, but `JarvisRuntimeController` routes ASR text through deterministic intents and does not call the kernel. The resource guard is therefore not proof of live inference governance.
4. **System assistant implementation remains skeletal.** `JarvisVoiceInteractionService.onReady()` only calls super; the session surface displays static text. `JarvisAccessibilityService` has empty event/interrupt handlers.
5. **AIDL is incomplete.** `JarvisRemoteService.submitText()` only replies with “Принял”; startListening/stopListening are empty. This is not a functioning remote control surface.
6. **Cross-process audio ownership is unresolved.** `JarvisSessionService` is declared in process `:session`, while the microphone coordinator's guard is process-local. The session process must not open AudioRecord independently; broker capture through one main-process service/Binder owner before enabling session capture.
7. **Native visual rendering is not yet accepted.** Earlier emulator evidence showed a black central surface and GLES errors. The shader repair has not been validated by a completed Build Jarvis run on its commit. A UI test asserting the AndroidView exists is not enough; inspect screenshot and logcat.
8. **Call interruption and wake-word acoustic quality are unverified.** Existing pipeline suspension methods are testable contracts, not proof of Android telephony callbacks or OPPO ColorOS preemption. The KWS model's Russian “Джарвис” recognition is not qualified.
9. **Some commands are intentionally limited.** The runtime has only a few deterministic intents. Unknown OPEN commands return explanatory text; this is not broad natural-language tool use.

## Required next sequence (dependency ordered)

1. Re-run/obtain exact-head Build Jarvis and Factory Security results for the current PR head; if no run exists, trigger the documented workflow on the current head through an authorized workflow-dispatch or PR update path. Do not treat old SHA results as current.
2. Fix any real NDK/setup/build failures without weakening required gates. Require APK build, all JVM tests, APK integrity/secret/native ABI/manifest/alignment checks and emulator smoke tests.
3. Inspect actual visual evidence (launcher screenshot + logcat) and confirm the GLES core is visibly rendered, not black.
4. Refactor runtime to own one lifecycle-managed audio coordinator and connect the explicit user-started KWS → VAD → ASR path to UI state and Stop cleanup. Keep push-to-talk as a safe fallback until the session path is validated.
5. Resolve cross-process capture ownership via Binder/main-process service; add call/system-audio interruption adapters with fail-closed release and no auto-resume.
6. Implement real VoiceInteraction, Accessibility and AIDL actions with typed/allow-listed tools and postcondition checks; do not mark no-op methods as complete.
7. Pin and integrate llama.cpp plus a legally compatible model only after build/device profiling. Keep weights outside APK, consent-gated, resumable and checksum-verified. Wire cognitive kernel and thermal/memory guard into the actual runtime before claiming offline conversational AI.
8. Validate on physical OPPO Find X6 Pro / Chinese ColorOS: permissions, ASR, Russian wake-word, command actions, TTS, assistant selection, call preemption, screen/background behavior, thermals, memory and battery.
9. Keep PR #133 Draft and do not merge or release until all required exact-head and physical-device gates are evidenced.

## Safety and scope
- No code behavior was changed by this audit; this is a documentation-only checkpoint.
- Do not claim a functional offline LLM, always-on wake word, call-aware capture, complete VoiceInteraction integration, or OPPO device compatibility.
- Do not merge PRs or publish APKs without user authorization and the complete release evidence.
