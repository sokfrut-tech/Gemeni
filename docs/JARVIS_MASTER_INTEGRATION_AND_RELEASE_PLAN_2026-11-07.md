# Jarvis — Master Integration & Release Plan
**Date:** 2026-11-07  
**Status:** Active integration plan; documentation only.  
**Owner repository:** `sokfrut-tech/Gemeni`  
**Device target:** OPPO Find X6 Pro / ColorOS (ARM64), Russian-first, local-first.

## 1. Product direction and non-negotiable gates

Jarvis is one product, not a collection of disconnected demos:
- **Gemeni** is the primary Android assistant/runtime and product integration repository.
- **RussSearchOPPO** is a supporting OPPO/ColorOS integration and safe-agent prototype. Reuse validated components; do not create a competing assistant brain.
- **Universal-AI-Factory / Antigravity-Mobile** are development coordination and agent-workflow infrastructure, not runtime dependencies required for Jarvis to answer locally.

Product constraints:
- Free/open-source core, Russian-first, local/offline capability as the default; cloud APIs are optional fallbacks only.
- No secrets in source, APK, logs or screenshots; no hidden telemetry, uploads, microphone capture or clipboard polling.
- Android actions remain behind a typed allowlist, risk policy and explicit confirmation where required.
- A build, a green unit test, an emulator test, and a physical OPPO test are different evidence levels. Never conflate them.
- Keep feature PRs Draft until exact-head checks pass. Any red required CI blocks merge/release.

## 2. Current repository snapshot (checked 2026-11-07)

### Gemeni PR inventory

| PR | Scope | Current branch relationship | Latest exact-head evidence / disposition |
|---|---|---|---|
| #84 | Isolated worker UID/permission boundary spike | based on `integration/jarvis-safe-agent-runtime-2026-11-06` | Draft; keep isolated; security boundary proof required before adoption |
| #85 | Explicit consent before speech-model download | based on safe-agent integration branch | Draft; must remain in the consent path |
| #92 | Pinned llama.cpp JNI inference spike | parent: safe-agent integration branch | Draft; exact-head CodeQL and APK build passed, but emulator instrumentation failed because process crashed (run 42336140479); no physical-device inference evidence |
| #94 | Download cancellation + JNI Unicode safety | child of #92 | Draft; exact-head Factory Security Baseline failed (run 42339883558) |
| #95 | Recognized speech/reply display | child of #94 | Draft; exact-head baseline failed (run 42340498895) |
| #96 | Qwen active HTTP disconnect cancellation test | sibling of #95, also child of #94 | Draft; exact-head baseline failed (run 42340745223) |
| #97 | Deterministic microphone/emulator smoke test | child of #95 | Draft; exact-head baseline failed (run 42342436315); do not assume it fixed emulator reliability |
| #98 | Exclude desktop Sherpa native assets from APK | child of #97 | Draft; exact-head baseline failed (run 42346984667) |
| #99 | Qwen physical-device validation plan | based on #98 branch | Draft documentation; exact-head baseline failed (run 42346510482) |
| #100 | Local Qwen self-diagnostic | child of #98 | Draft; exact-head baseline failed (run 42346830463); three simple OK responses are not a knowledge benchmark |
| #101 | Reliable Russian TTS initialization queue | child of #100 | Draft; exact-head baseline failed (run 42347172763) |
| #102 | Interactive orb + SaveVideo product/engineering brief | independent docs branch from main | Draft; latest Factory Security Baseline passed (run 42381001156); documentation only, no implementation implied |
| #55 | Holographic orbital renderer | separate older branch based on emulator-smoke test branch | Draft; renderer exists as a visual slice, but full live state/audio mapping and physical OPPO validation remain |
| #89 | Offline video trim/export studio | based on memory UI branch | Draft; distinct from SaveVideo URL intake/downloader |

Important: the latest exact-head run available for PRs #92 and #94–#101 is a **Factory Security Baseline** workflow. A failed baseline does not automatically mean Kotlin compilation failed; inspect its jobs and logs to identify the failing gate. For PR #92, the recorded job breakdown is explicit: CodeQL/static analysis, Kotlin build and full debug APK succeeded; Android emulator smoke failed at `connectedDebugAndroidTest` because the instrumentation process crashed after reporting 0 tests completed. This is a real release blocker until reproduced and fixed or the test harness is shown to be faulty with evidence.

### Supporting RussSearchOPPO repository

- PR #15 safe-agent contracts is still Draft and currently reports `mergeable=false`; its latest schema changes do not have fresh exact-head CI evidence. Earlier green runs on an older commit do not qualify the current head.
- PR #12 Qwen model downloader has received hardening commits: bounded manual redirect validation, streaming SHA-256, immutable model revision, staging/atomic install recovery and tamper checks. Its review still records remaining gaps: active abort of a currently blocked socket read, broader redirect-loop/multi-hop tests, oversized/chunked body tests, ENOSPC and crash-boundary tests. No Qwen inference or physical OPPO test is claimed.
- Do not merge either PR merely to unblock Gemeni. First reconcile interfaces and prove exact-head tests in the owning repository.

## 3. Dependency graph and safe integration order

Current stacked chain (based on PR base branches; verify commit ancestry before any merge/rebase):
1. Safe-agent integration foundation: `integration/jarvis-safe-agent-runtime-2026-11-06` (includes prerequisites represented by #84/#85).
2. #92 JNI runtime spike.
3. #94 cancellation + JNI Unicode.
4. From #94, parallel children #95 (speech/reply UI) and #96 (network cancellation regression coverage).
5. #97 deterministic mic/emulator smoke work depends on #95.
6. #98 APK native-asset trimming depends on #97.
7. From #98, parallel #99 (device qualification documentation) and #100 (Qwen self-diagnostic).
8. #101 Russian TTS initialization depends on #100.

Separate product tracks:
- Orb: #55 renderer foundation; #102 defines the authoritative typed state/event contract. Implement state wiring only after the runtime/task/audio sources are identified. Reuse the existing GLES renderer; do not build a parallel renderer.
- Media: #89 is offline editing/export. SaveVideo is a separate link intake/download manager and must follow the provider/legal/security matrix in #102. Do not describe #89 as SaveVideo.
- Memory: #88 and its parent multimodal-memory branch must preserve explicit consent and local-first storage.

No PR should be merged based only on `mergeable=true`; branch ancestry, exact-head checks, review comments, and changed-file overlap must be checked. Never merge stacked children before their parents are safely integrated.

## 4. Priority execution plan

### P0 — Restore trustworthy CI signal
1. Inspect the failing emulator instrumentation run and its uploaded diagnostics (especially #92 and #97).
2. Reproduce with the exact PR head and determine whether the failure is test-process crash, emulator startup instability, native library load/ABI issue, or instrumentation runner configuration.
3. Fix the root cause; add a deterministic regression test. A smoke test that executes zero tests is not a pass.
4. Rerun the full required workflow on the exact resulting commit. Preserve CodeQL, secret scan and APK build gates.

**Exit criteria:** all required exact-head CI jobs green; test count > 0 for instrumentation; no skipped/empty smoke run misreported as success.

### P1 — Qualify the local Qwen runtime
1. Pin and verify the exact llama.cpp commit and Qwen3.5 `qwen35` architecture support.
2. Confirm JNI lifecycle, cancellation, UTF-8/Unicode handling, native error propagation and ARM64 build.
3. Keep model download and runtime separate: downloader must validate every redirect hop, bound bytes, hash while streaming, recover safely after interruption, and abort blocked I/O on cancellation.
4. Add deterministic tests for redirect chains/loops, untrusted hosts, digest mismatch, truncation, oversized/chunked responses, low storage, ENOSPC, process death at each install boundary, and cancellation during blocked reads.
5. Only after emulator checks pass, install the APK on the physical OPPO Find X6 Pro, download the pinned model with consent, load it, generate a deterministic Russian response, cancel a generation, relaunch and verify recovery.

**Exit criteria:** exact-head CI green + attached OPPO evidence for install, model load, Russian inference, cancellation, relaunch and resource behavior. No claim of offline intelligence before this.

### P2 — Complete the first usable voice loop
1. Show recognized speech and assistant responses (#95) with clear listening/processing/speaking/error states.
2. Make microphone smoke tests deterministic (#97); test permission denied, granted, revoked, app background/foreground and no-network mode.
3. Stabilize Russian TTS initialization (#101); verify actual speech output and cancellation, not merely object initialization.
4. Verify the local ASR/TTS path without cloud credentials and with network disabled.

**Exit criteria:** user can speak a Russian command, see the recognized text, receive a local answer, hear Russian TTS, and see honest status/error feedback.

### P3 — Interactive orb / digital brain
1. Convert #102 state contract into typed app state/events: IDLE, WAKE_WORD, LISTENING, PROCESSING, SPEAKING, SEARCHING, DOWNLOADING, WORKING, WAITING_FOR_APPROVAL, SUCCESS, ERROR, OFFLINE.
2. Wire actual events from voice, model, task, network and media managers. Never fake progress or amplitude.
3. Reuse #55 GLES renderer; add immutable state snapshots, lifecycle/context-loss fallback, reduced-motion and accessibility controls.
4. Physical OPPO frame/thermal/battery validation before release.

**Exit criteria:** every visual state is event-driven and testable; GL failure cannot leave a black screen; no microphone capture just to animate.

### P4 — Media and knowledge
1. Keep #89 offline editing/export separate from SaveVideo.
2. Implement SaveVideo foundation: URL parser, allowlisted provider adapters, official/authorized sources only, explicit confirmation, real progress/cancel, MediaStore save, history and safe cleanup.
3. Do not bypass DRM, access controls, private streams, paywalls or platform restrictions; never claim universal YouTube/Instagram/TikTok downloading.
4. Turn #90 scientific/engineering domain taxonomy into a versioned, source-attributed knowledge pipeline with offline retrieval and tests. Documentation alone is not an implemented knowledge engine.
5. Build domain packs incrementally: mathematics/physics, chemistry, medicine (informational, cautious), energy, astronomy, psychology, radio/electronics, robotics, software, networks (mesh/MANET), antennas/modems/routers, navigation/cartography, construction, gardening and calculations. Separate verified facts, hypotheses and user notes; cite sources and dates.

**Exit criteria:** one permitted media provider end-to-end and one measurable local knowledge retrieval slice, each tested. Expand domains only after provenance and update mechanisms exist.

## 5. Merge and release checklist

For every PR:
- [ ] Confirm base/head and parent PR dependencies.
- [ ] Review changed files and diff; no unrelated generated files or secrets.
- [ ] Run focused unit/contract tests.
- [ ] Run Android build and native ABI checks where relevant.
- [ ] Run instrumentation tests with non-zero executed test count.
- [ ] Run CodeQL/static analysis and secret scanning.
- [ ] Review workflow logs, not only green/red summary badges.
- [ ] Verify exact commit SHA matches the reviewed and tested head.
- [ ] Attach physical OPPO evidence for device-dependent features.
- [ ] Keep Draft until all applicable gates pass.

Release is blocked if any required gate is red, skipped without justification, ran against an older SHA, or did not exercise the feature.

## 6. Work log / continuity

- 2026-11-07: refreshed Gemeni PR inventory and exact-head workflow metadata.
- 2026-11-07: confirmed #92 Kotlin/native build and CodeQL succeeded, but emulator instrumentation crashed; this is the first P0 investigation target.
- 2026-11-07: confirmed #102 is a passing documentation-only brief; it does not mean orb or SaveVideo implementation exists.
- 2026-11-07: reviewed RussSearchOPPO PR #12 update and recorded remaining downloader cancellation/recovery test gaps; PR #15 remains unqualified at its current head.
- No application source code, merge, release, deployment or physical-device test was performed as part of this planning update.

## 7. Immediate next task

**Investigate and repair the emulator instrumentation crash on the current JNI/runtime chain, starting with PR #92 diagnostics and then validate #97's deterministic smoke-test changes.** Do not begin a broad UI feature or merge stacked PRs until this CI blocker is understood.


## 8. Emulator crash investigation — diagnostic evidence (2026-11-07)

Downloaded and inspected the failure artifact from PR #97 workflow run 42342436315 (artifact 13563300338, exact head `6b283e4247693c81fa05fa2146b2e6b18a535baf`). Findings:
- Instrumentation report: `INSTRUMENTATION_FAILED`, “Test run failed to complete. Instrumentation run failed due to Process crashed.” The generated XML reports **0 tests**, so this is not a failed assertion from the permission test.
- The post-failure ADB snapshot lists `emulator-5554` as **offline**. `adb logcat -d` returned “waiting for device -”; package/process dumps are empty. The diagnostic artifact therefore cannot identify an application stack trace.
- PR #97's deterministic permission test itself is narrow and avoids ASR downloads. Its diff also adds bounded diagnostic timeouts and artifact upload. Those changes improve diagnosability but the exact-head emulator run still failed.
- Conclusion: the available evidence points to an emulator/ADB transport or process-lifecycle failure, but does **not** prove whether the emulator process, instrumentation runner, or app process caused the disconnect. Do not patch app behavior or weaken assertions based on this artifact alone.

Next diagnostic slice:
1. Inspect the emulator runner setup and full emulator boot log for the exact failing run; determine whether ADB became offline before instrumentation began or only after app/test launch.
2. Add a bounded preflight that waits for `adb get-state=device`, `sys.boot_completed=1`, and package installation before starting instrumentation; fail early with the preflight log if readiness is not reached.
3. Run instrumentation with one test class/method at a time (permission flow first, settings second) to isolate a crash trigger; keep non-zero executed test count as a hard acceptance condition.
4. If the emulator disconnects before tests, repair runner/AVD configuration. If the app/test process crashes while ADB remains online, capture logcat immediately and fix the app/test root cause.
5. Re-run the exact resulting PR head; only a terminal green emulator job with executed tests closes P0.

The artifact is from PR #97, not PR #92. PR #92's own run 42336140479 also reported instrumentation process crash, but no equivalent artifact was established in this review. The shared symptom does not prove a shared root cause.


## 9. CI instrumentation diagnostic capture improvement (2026-11-07)

- PR #97 now captures emulator diagnostics **inside** the `reactivecircus/android-emulator-runner` script, immediately after a failed `connectedDebugAndroidTest` and before the action's AVD cleanup. The prior workflow attempted ADB diagnostics in a later GitHub step, after the emulator-runner action had already returned; the ADB snapshot could therefore show an offline emulator and empty logcat even if the runner cleanup—not the app—had disconnected it.
- The separate failure diagnostics step remains as a bounded fallback for failures occurring before the emulator script starts. The app tests and assertions were not weakened.
- Commits: `5813c29ffff82eb6b9c6babcb6d1393dbbe36f1e` (move primary capture into action) and `0aeec94f9606bb8c50e3d043d92f63d86372409e` (remove an unnecessary unbounded ADB wait; emulator-runner already performs boot/readiness handling).
- Exact current PR #97 head: `0aeec94f9606bb8c50e3d043d92f63d86372409e`. Workflow run #186 (42386335558) has started; at this update CodeQL is in progress and secret scanning is queued. Emulator instrumentation has not yet started, so this is not a test result.
- Run #185 (42386317407) on the preceding workflow commit was superseded/cancelled after the next commit. Do not use it as evidence for the current head.
- Next action: inspect run #186 after it reaches a terminal state. If emulator smoke fails, inspect the new artifact's XML and logcat captured before AVD cleanup. If it passes, confirm that the XML reports a non-zero test count.


## 10. Consent-path dependency review (2026-11-07)

A source-level review found a merge-order issue that must be resolved before the voice runtime can be considered safe:
- PR #85 (`fix/jarvis-explicit-model-download-consent-2026-11-06`) implements `isRussianAsrReady()`, an explicit dialog explaining the Hugging Face model download and its network metadata, and a separate `downloadRussianAsrAfterConsent()` path. Its `prepare()` refuses to proceed if the model is not already installed.
- PR #95 (`fix/jarvis-response-display-2026-11-07`) is based on PR #94, whose parent chain starts from the safe-agent integration branch; PR #85 is a **sibling** branch off that same integration branch, not an ancestor of #95. Therefore the explicit ASR download-consent behavior from #85 is not present in #95's current branch.
- PR #95's current `MainActivity.requestAndListen()` calls `runtime.prepare()` directly, while its corresponding `prepare()` calls `models.ensureRussianAsr()`. This means tapping the microphone can initiate a model download without the explicit consent dialog in PR #85. This is a concrete privacy/UX blocker, not a theoretical concern.

Required resolution before #95 can be considered for merge:
1. Rebase/cherry-pick or otherwise integrate the consent implementation from #85 into the #95 stack without dropping transcript/state/agent changes.
2. Preserve separate microphone permission and model-download consent: permission to use the microphone is not consent to download a multi-file model.
3. Verify the dialog clearly explains the model source, approximate download size, local storage destination, IP/connection metadata disclosure, and that voice/audio are not uploaded.
4. Verify “Not now” performs no network request and returns to the idle UI; explicit approval starts the download; cancellation/failure leaves a recoverable state; already-installed verified model does not prompt again.
5. Add tests that assert no network request occurs before explicit model-download approval. Keep instrumentation deterministic and avoid granting microphone permission in a way that triggers downloads.

Updated safe order: integrate #85's consent gate into the #92→#94→#95 runtime chain before approving #95 or subsequent #97/#98/#100/#101 children. The branch graph must be reconciled explicitly; do not assume sibling PRs are included because they share a parent.


## 11. PR #95 explicit model-download consent integration (2026-11-07)

The previously identified sibling-branch privacy blocker has now been addressed directly on the PR #95 branch (without merging):
- `JarvisRuntimeController.isRussianAsrReady()` performs a local file/hash readiness check.
- `MainActivity.requestAndListen()` checks readiness first. If absent, it opens a dedicated consent dialog and does not request microphone permission or call the downloader yet.
- The explicit “Разрешить загрузку” button calls `downloadRussianAsrAfterConsent()`; the downloader verifies model files before the runtime can proceed.
- `prepare()` now rejects an absent/unverified model instead of silently downloading it.
- “Не сейчас” closes the dialog and makes no downloader call. The consent text identifies Hugging Face, app-private storage, checksum validation, IP/connection metadata and states that voice recordings are not uploaded.
- Android instrumentation tests were added/updated to cover decline behavior, the prepare-without-consent guard (no model files created), and the approved download → microphone permission flow.

Exact PR #95 head: `15a0a970b32d3bbd33fb8407cb83611fc5230a73`. Its Factory Security Baseline run #191 (42386652892) started; secret scanning passed and CodeQL/Gradle was still running at the time of this update. The code is not yet verified by terminal CI and must remain Draft.

Remaining validation: ensure UI cancellation test passes, verify exact instrumentation test count is non-zero, inspect emulator diagnostics if it fails, and verify already-installed verified models bypass the consent dialog. Physical OPPO validation remains required.


## 12. Consent-first emulator smoke branch re-based (2026-11-07)

- The earlier PR #97 was based on the pre-consent #95 head. Once #95 changed, GitHub reported #97 as non-mergeable and its microphone permission smoke test no longer matched the new consent-first UX.
- Created replacement PR #104 directly on the updated #95 head (`15a0a970b32d3bbd33fb8407cb83611fc5230a73`). It carries the improved in-action ADB/logcat capture and a deterministic smoke suite: launch UI, decline model-download consent, assert no model setup begins, verify runtime `prepare()` rejects a missing model without creating files, and open the Qwen settings panel. CI does not approve a download or depend on downloading a model.
- Closed stale PR #97 with an explanation and link to #104; its branch remains in Git history.
- PR #104 exact head: `023446aeafbf20f9f368322a4b513b60a7fdfd94`; Factory Security Baseline run #193 (42386800592) started. Secret scanning passed; CodeQL/build and emulator instrumentation are pending.
- PR #95 exact head: `15a0a970b32d3bbd33fb8407cb83611fc5230a73`; run #191 (42386652892) has secret scanning passed; CodeQL is still running before compile/build steps have completed.
- Neither PR is merge-ready. Keep #95 and #104 Draft until their exact-head required checks finish green and emulator test reports show a non-zero test count.


## 13. Android compatibility target gap (2026-11-07)

The current `app/build.gradle.kts` on the PR #95 stack declares `minSdk = 29` (Android 10). This does not meet the previously requested Android 7+ compatibility goal (API 24+). Do not simply lower `minSdk` without an API/dependency audit.

Compatibility work is a separate release track:
1. Inventory every Android API call and library native ABI/minimum-OS requirement across the app, Sherpa-ONNX, llama.cpp JNI, Compose and background/service components.
2. Replace or guard APIs introduced after API 24; use runtime feature detection and backward-compatible alternatives where safe.
3. Confirm NDK native libraries are built for the intended ABIs and supported API level; inspect ELF dependencies and linker symbols.
4. Add API 24 emulator/build coverage (if supported by dependencies), then test API 24–28 permission/background behavior and ColorOS-specific restrictions.
5. Lower `minSdk` only after all build, static, instrumentation and native-load checks pass. If a core dependency genuinely requires a newer API, document the minimum accurately rather than claiming Android 7 support.

Current verified state: Android 10+ only is declared by Gradle; Android 7+ compatibility has not been implemented or tested.


## 14. ASR downloader security hardening started (2026-11-07)

- Created PR #105, stacked on PR #104, to bring the stronger download guarantees already present in the Qwen installer to the Russian ASR downloader.
- The ASR downloader now uses manual HTTPS redirects with an explicit Hugging Face redirect-host allowlist, a bounded redirect count, declared-length and streamed-byte limits, in-stream SHA-256 verification, preflight storage checks, fsync + same-directory atomic install, partial-file cleanup, and active-connection disconnection on coroutine cancellation.
- Added deterministic unit tests for verified installation, untrusted redirect rejection, approved redirect handling, oversized and truncated responses, digest mismatch, low storage, redirect loops and cancellation cleanup.
- PR #105 exact head: `977d0fabc725b1af394439f949560c1f113a8c91`; Factory Security Baseline run #195 (42387110040) is queued. These changes are not yet CI-verified.

## 15. PR #95 first successful build gate (2026-11-07)

- PR #95 exact head: `15a0a970b32d3bbd33fb8407cb83611fc5230a73`.
- Factory Security Baseline run #191 (42386652892): Secret scanning passed; CodeQL static analysis passed; Kotlin compile passed; full debug APK assembly passed; APK existence/hash check passed; SARIF analysis and archive passed.
- Android emulator smoke job is still running. Therefore PR #95 is **not** fully verified and remains Draft. Await emulator terminal result and ensure its test report shows a non-zero test count before treating this as a successful end-to-end check.


## 16. ASR downloader hardening implementation details (2026-11-07)

PR #105 now contains the first implementation and regression suite:
- `ModelDownloadManager` is injectable for deterministic tests (file directory, connection factory, file manifest, base URL, free-space provider).
- Redirects are disabled at the HTTP layer; the downloader permits the pinned Hugging Face origin and validates each HTTPS redirect hop against an explicit allowlist, with a maximum of five redirects.
- Each file has a streaming byte cap; declared Content-Length is checked before reading and against actual bytes; known SHA-256 hashes are calculated while streaming; unknown-length responses still have a hard cap.
- The destination is committed only after successful verification via same-directory atomic move; temporary files are removed on all thrown failures.
- Active HTTP connection is registered for cancellation-time disconnect; tests include a blocking stream fixture to exercise that path.
- Tests cover valid installation, untrusted and approved redirects, redirect loops, oversized/truncated bodies, digest mismatch, low storage and cancellation cleanup.

Review caveats before accepting #105:
1. `tokens.txt` currently has no pinned SHA-256 in the upstream file manifest; it is only size/non-empty checked. Obtain and pin its digest from a trusted immutable source before claiming every model file is cryptographically pinned.
2. Verify the exact Hugging Face redirect host set against real model delivery responses; do not broaden it to arbitrary domains.
3. Confirm coroutine cancellation test actually observes disconnect and terminates quickly under CI.
4. Check API/minSdk implications of `java.nio.file.Files.move` (the app currently declares API 29, so this is available at the declared minimum).

PR #105 exact head: `977d0fabc725b1af394439f949560c1f113a8c91`; run #195 (42387110040) has secret scanning passed and CodeQL/build still running. Do not claim the hardening is verified until the exact-head run completes.


## 17. Russian ASR model provenance and licensing correction (2026-11-07)

A license review found that the previously selected republished model repository `csukuangfj/sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16` has no license declared. It must not be treated as Apache-2.0 merely because its README says the weights originated from Alpha Cephei.

The downloader in PR #105 has therefore been switched to the official upstream repository `alphacep/vosk-model-small-streaming-ru`, whose Hugging Face model card declares **Apache-2.0**. It downloads only the quantized streaming subset needed by Sherpa-ONNX:
- `am-onnx/encoder.int8.onnx` (SHA-256 pinned)
- `am-onnx/decoder.onnx` (SHA-256 pinned)
- `am-onnx/joiner.int8.onnx` (SHA-256 pinned)
- `lang/tokens.txt` (small text file, currently no SHA-256 pin; revision is pinned and this remaining integrity gap is documented)

The source tree shows the official license and files under the streaming model repository. References: Hugging Face model page `https://huggingface.co/alphacep/vosk-model-small-streaming-ru`; model card README `https://huggingface.co/alphacep/vosk-model-small-streaming-ru/blob/main/README.md`; selected file metadata for encoder/decoder/joiner is published by Hugging Face. The three ONNX hashes match the current pinned file manifest.

The consent dialog now identifies Alpha Cephei, Apache-2.0 and an approximate 29 MB first download. The source revision prefix is pinned to `5fbd908`; verify the revision resolves and retain its full immutable commit SHA in the manifest before release. Do not ship if the source revision or license cannot be verified.


## 18. Model provenance document added (2026-11-07)

- Added `docs/MODEL_PROVENANCE_RUSSIAN_ASR.md` to PR #105. It records official Alpha Cephei source, declared Apache-2.0 license, selected files, three upstream SHA-256 values, the approximately 29 MB subset, and explicit release checklist.
- The old unlicensed mirror is no longer the downloader source. The official upstream is used directly.
- Open item remains: establish SHA-256 for `lang/tokens.txt` and replace short revision prefix `5fbd908` with the full immutable commit SHA before production release. The code currently limits token file size and pins its revision, but does not claim a cryptographic digest for that file.


## 19. CI now executes JVM unit tests (2026-11-07)

- The Factory Security Baseline previously compiled Kotlin and assembled the APK but did not execute `testDebugUnitTest`; this would have left the new downloader regression tests unexecuted.
- Added a dedicated `Run JVM unit tests` step after Kotlin compilation and uploads JUnit XML reports as an artifact. The workflow now needs to pass compile, unit tests, APK build, CodeQL, secret scanning, and emulator instrumentation with a non-zero test count.
- This workflow change is on the PR #105 stack and will be verified at its new exact head; prior CI results do not cover this added gate.


## 20. Voice pipeline source review and remaining work (2026-11-07)

Source review of the current #95 runtime found:
- `AudioRecordPipeline` is a real mono 16 kHz PCM recorder, but the controller currently captures a fixed 4.5-second window. It is not yet a continuous conversation loop and has no integrated Silero VAD endpointing or wake-word trigger.
- The current `JarvisSpeaker` on #95's branch calls Android `TextToSpeech.speak()` immediately without waiting for asynchronous TTS initialization and has no `shutdown()` lifecycle. This can lose early speech or leak the TTS engine.
- PR #101 (`fix/jarvis-tts-init-queue-2026-11-07`) contains a lifecycle-safe queue that waits for TTS initialization, sets Russian locale, queues the newest utterance, and shuts down cleanly. It is stacked on the Qwen self-diagnostic branch, so do not cherry-pick blindly; integrate it after the #98/#100 ancestry is reconciled, or transplant the isolated class with tests and preserve exact dependency history.

Next voice acceptance work after downloader/security gates:
1. Integrate the TTS queue and test initialization success/failure, repeated speech, cancellation and shutdown.
2. Add VAD-driven endpointing so speech can end naturally instead of being cut at 4.5 seconds; retain a bounded maximum duration.
3. Add explicit capture cancellation and recorder lifecycle tests for permission revocation, app backgrounding and interruption.
4. Add wake-word/background listening only behind user opt-in, Android foreground-service requirements, privacy indicators and battery tests.
5. Verify Russian end-to-end: recognized transcript, model answer, audible Russian response, no network, and honest error state.


## 21. Consent enforced at downloader boundary (2026-11-07)

PR #105 now requires `userConsent: Boolean` on `downloadRussianAsrAfterConsent()` and rejects `false` before any call into the model downloader. The UI passes `true` only from the explicit consent button. The deterministic instrumentation test now verifies the runtime rejects a false consent flag and creates no model files; `prepare()` also rejects a missing model without downloading. This is stronger than relying on the UI flow alone.


## 22. Full immutable ASR source revision pinned (2026-11-07)

The official Alpha Cephei model commit prefix `5fbd908` was resolved to full immutable commit `5fbd908cb21cbd585fa21461f463ba41fbcbcb68`. PR #105's downloader and `docs/MODEL_PROVENANCE_RUSSIAN_ASR.md` now use the full SHA rather than a moving branch or short prefix. The remaining model provenance gap is the SHA-256 of `lang/tokens.txt`.


## 23. Latest CI snapshot (2026-11-07)

- PR #95 run #191 (42386652892): Secret scanning passed; CodeQL/Kotlin compile/full Debug APK/SARIF all passed. Android emulator smoke is still in progress.
- PR #104 run #193 (42386882534): Secret scanning and CodeQL/Kotlin compile/full Debug APK/SARIF passed. Android emulator smoke is still in progress. This branch includes the deterministic consent-decline/no-download tests and a hard non-zero instrumentation-test-count check with evidence artifacts.
- PR #105 current exact head: `d359e817f91fe5aa82bf992f20f773f19aadd1a8`; run #214 (42387501472) has secret scanning passed; CodeQL job is still setting up Gradle before Kotlin compile, JVM unit tests, APK build and analysis. The run was restarted after the full revision SHA and test changes.
- No emulator run has yet reached a terminal state; no merge or release is authorized.


## 24. Superseded consent PR closed (2026-11-07)

- PR #85 contained only the two ASR consent implementation files; those changes are now integrated in PR #95, with stronger runtime consent enforcement and tests in PR #104/#105.
- Closed PR #85 with a comment linking to the active integrated stack. Its branch remains available for history; no code was lost.


## 25. ASR downloader cancellation race found and corrected (2026-11-07)

- PR #105 run #214 (42387501472) executed the new JVM unit suite: **43 tests, 1 failure**. The failing case was `cancellationDisconnectsActiveConnectionAndCleansPartialFile`; the failure occurred at the post-stream completeness check because a socket disconnect can unblock a blocking `read()` by returning EOF instead of throwing.
- Corrected the downloader loop to call `currentCoroutineContext().ensureActive()` immediately after each blocking `input.read(buffer)`, before interpreting EOF. Cancellation is now observed before a disconnect-induced EOF can be mistaken for a completed response.
- Fix commit: `18ce5f8aceb80a3c7732df939a7a2f25cc3f80d1`. Exact-head CI rerun is required; no passing claim until the full unit suite completes.


## 26. Unknown-length response size-cap test added (2026-11-07)

- Static review found the original oversized-response test only exercised the declared Content-Length pre-check; it did not prove the streaming byte cap for chunked/unknown-length responses.
- Added `enforcesStreamingByteLimitWhenContentLengthIsUnknown`, which serves a body larger than the per-file cap while reporting unknown length and asserts rejection plus cleanup of both partial and final files.
- This is an additional regression test; exact-head run must execute it along with cancellation and the rest of the JVM suite.


## 27. Current PR #105 verification rerun (2026-11-07)

- PR #105 exact head: `e27b7c263f36f4401c3910e9d04db355f663119c`.
- Run #221 (`42387826682`) has Secret scanning passed; CodeQL job is setting up Gradle. It includes the cancellation race fix and unknown-length body-cap regression test.
- Prior run #214 failed the JVM suite with 42/43 tests passing; the failure was the cancellation/EOF race. Run #219 was superseded when the additional unknown-length regression test was added. No current exact-head pass is available yet.
- PR #95 and #104 emulator smoke jobs remain in progress; neither has a terminal result or a verified non-zero instrumentation count at this snapshot.


## 28. ASR decoder aligned with upstream int8 reference (2026-11-07)

- Reviewed Alpha Cephei's official `decode8.py` for the pinned streaming model revision. It configures `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx` and `lang/tokens.txt` together.
- Changed PR #105 to download the int8 decoder as well (local runtime filename remains `decoder.onnx` to match the Sherpa loader). Its pinned SHA-256 is `2b0df458692e1d090075c8249001136ef05240dd0d726a6b56552fd46c538b2d`.
- The runtime now uses all three quantized ONNX components, avoiding an unnecessary mixed-precision model configuration and reducing download size. Provenance doc updated.


## 29. Cancellation is checked before atomic installation (2026-11-07)

- Extended the cancellation hardening beyond the blocking-read race: after stream completion, fsync and checksum verification, the downloader now calls `ensureActive()` immediately before the atomic move.
- Extended the cancellation regression test to assert that neither a partial file nor a final installed model file remains after cancellation.
- Commits: `aa508b43cb707895522b341b9d81a52a757e5384` and `8ce186437ac134a9b4292f2c05688c20f61e6581`. Both require exact-head CI verification.


## 30. Third-party attribution added for optional ASR model (2026-11-07)

- Added an explicit entry to the repository `NOTICE` for the optional user-downloaded Alpha Cephei Russian streaming ASR model, with full immutable revision, source link and Apache-2.0 license link.
- Clarifies the model is not bundled in the APK, is downloaded only after explicit consent, and retains its third-party licensing rather than being relicensed by Gemeni.


## 31. Unknown-length storage preflight refined (2026-11-07)

- Static review found the unknown-length branch used a fixed 256 MiB free-space requirement even for small files, and did not guarantee the configured file cap plus the safety margin for larger files.
- Changed it to require `item.maxBytes + MIN_FREE_MARGIN_BYTES` before opening the body. This scales to the per-file cap and avoids an arbitrary 256 MiB requirement for the small token file.
- Added a deterministic test asserting insufficient storage is rejected before the response body is opened for an unknown-length response.


## 32. PR #105 latest exact-head pipeline after expanded hardening (2026-11-07)

- Current PR #105 head: `46013ce1ddd09dd6281ef72248a6288a478c12e2`.
- Run #233 (`42388091023`) is the current exact-head pipeline. Secret scanning has passed; CodeQL is still provisioning the Android SDK. It includes: cancellation checks after blocking reads and immediately before atomic install; final-file absence assertion after cancellation; unknown-length streaming size-cap test; per-file-cap-plus-margin storage preflight test; all-int8 upstream model manifest; and optional-model attribution in NOTICE.
- Previous run #214 (43 tests, one cancellation race failure) is historical and must not be used as the final verdict. The next successful exact-head run must show the full unit test count and finish all build/security gates.
- PR #95 run #191 and PR #104 run #195 have Secret scanning and CodeQL/APK build passed, but their emulator smoke jobs remain in progress at this snapshot.


## 33. Russian TTS lifecycle work isolated into PR #106 (2026-11-07)

- Source review of PR #101 confirmed a useful isolated improvement: Android `TextToSpeech` initializes asynchronously, so speech requested before `onInit` must be queued; the TTS engine also needs deterministic shutdown.
- Did not transplant PR #101 wholesale: its `JarvisRuntimeController.prepare()` auto-downloads ASR and would regress the explicit consent gate; it also includes an unrelated Qwen self-diagnostic change.
- Created PR #106 stacked on PR #105. It transplants only the TTS lifecycle behavior and strengthens it with an injectable `SpeechEngineFactory`, handling for callbacks that fire before factory return, explicit failure when Russian voice data is unavailable, latest-utterance queue semantics, and idempotent shutdown.
- Added JVM tests for queued-message replacement, early/synchronous initialization callback, unsupported Russian locale, initialization failure and close/late-callback behavior.
- PR #106 exact head: `c39d3d4b7134019aa82884be8b99109529065075`; run #236 (`42388241382`) is queued. No CI verification yet. Physical Russian speech output on OPPO remains a separate gate.
- PR #101 remains open for its Qwen self-diagnostic slice; the TTS portion is now represented by #106 and must not be cherry-picked twice.


PR #106 compile-safety refinement: the Android `TextToSpeech.OnInitListener` is now passed as an explicit SAM lambda rather than a Kotlin function-type variable. Commit `2a9a0aef3c887433a46cfe9660ecacca18372e6f`; a new exact-head CI run is required.


## 34. PR #105 JVM test report verified directly (2026-11-07)

- Run #233 (`42388091023`) exact head `46013ce1ddd09dd6281ef72248a6288a478c12e2` completed the JVM unit-test step successfully.
- Downloaded and inspected the JUnit XML artifact `jarvis-unit-test-results-14c82f7f439e511f016c08a5f70b9211e2aaf56a`: **45 tests, 0 failures, 0 errors, 0 skipped** across 9 test suites. The downloader suite contributed 11 passing tests, including cancellation cleanup, unknown-length byte cap and storage preflight.
- Secret scanning also passed. At this snapshot the full Debug APK build was still running; CodeQL analysis and emulator smoke had not yet reached terminal state. Therefore this is a verified JVM-suite pass, not a full CI/release pass.


## 35. PR #107 — secure optional Silero VAD model delivery (2026-11-07)

- Static review confirmed the repository already contains `SileroVadEngine` and a basic `SileroVadModelManager`, but the manager used automatic redirects, had no streaming byte cap, wrote a temporary file without atomic install, and did not require an explicit consent argument. The runtime still uses a fixed 4.5-second capture window; the VAD path is not yet wired into the active voice loop.
- Created PR #107, stacked on PR #105, to harden this prerequisite before voice-loop integration. It requires `userConsent=true` before any request; pins SHA-256; caps the file at 32 MiB; validates HTTPS and every redirect hop; limits redirects; preflights free space; writes/fsyncs a .part file; atomically installs only after digest verification; disconnects blocking reads on cancellation; and checks cancellation before interpreting EOF or committing the file.
- Added six JVM tests covering no-consent/no-network, successful verified install, untrusted redirect, unknown-length streaming cap, checksum mismatch cleanup, and cancellation cleanup.
- PR #107 exact head: `fdc736481f59da9c5439698caf13c2a63657a1aa`; run #244 (`42388625778`) is queued/running. PR #107 remains Draft; no test/build pass claimed yet.
- Integration remains a separate follow-up: disclose the optional Silero model and its network/storage implications in the consent UI, then load it and feed audio frames through VAD to detect endpoint silence. Do not silently add this second download to the existing ASR consent flow.

## 36. Latest CI snapshot (2026-11-07)

- PR #105 exact head `46013ce1ddd09dd6281ef72248a6288a478c12e2`: CodeQL job completed successfully, including Kotlin compilation, 45/45 JVM tests and full debug APK build. Secret scanning passed. The separate Android emulator smoke job was still running; therefore full CI is not yet green.
- PR #106 exact head `2a9a0aef3c887433a46cfe9660ecacca18372e6f`: Secret scanning passed; CodeQL job was still building the full debug APK. No emulator or full CI pass yet.
- Older emulator smoke runs #191 and #195 were still active without terminal test counts at this snapshot. Do not treat an active job as success; inspect timeout/runner behavior before release.


## 37. PR #107 regression suite expanded after review (2026-11-07)

- Expanded the Silero downloader suite to 9 cases: added successful redirect to the approved GitHub release-asset host, declared oversized Content-Length rejection before opening the body, and insufficient-storage rejection before opening the body.
- Added a body-open callback to the fake HTTP connection so tests can prove preflight happens before any body read.
- Latest PR #107 exact head is `cb840f2b84382e92268c9b6c3eb0516a6212c910`. Run #241 was for the earlier head; run #243 was cancelled/superseded after the follow-up test commits. A fresh exact-head run is required before treating this slice as verified.
- PR #105 CodeQL and full debug APK build have now completed successfully, along with secret scanning. Its emulator smoke job is still active and has not supplied a final instrumentation count.
- PR #106 secret scanning is green; CodeQL is still in the full APK build step. No complete CI or release status is claimed for #106.


## 38. Silero VAD model provenance and tighter cap (2026-11-07)

- Cross-checked the model source against official sherpa-onnx documentation: the documented VAD download URL is the pinned GitHub release asset, and the exported `silero_vad.onnx` is the 16 kHz model (documented size approximately 629 KiB). The Android engine is configured for 16 kHz, so this is the correct model family for the current audio pipeline.
- Tightened the downloader's maximum permitted body from 32 MiB to 4 MiB, leaving headroom above the documented asset size without a needlessly wide limit.
- Added a truncated-response test that asserts neither the final model nor the .part file is left behind. PR #107 now has 10 deterministic JVM regression cases.
- Latest PR #107 head: `1627987fe43897ed6d33dc43122a1f8ee563bddb`; run #247 (`42388826653`) is pending. Previous run #244 was superseded by these changes and is not an exact-head result.


## 39. PR #106 exact-head verification completed (2026-11-07)

- PR #106 exact head `2a9a0aef3c887433a46cfe9660ecacca18372e6f`, run #238 (`42388285102`): inspected the uploaded JUnit XML artifact directly. **50 tests, 0 failures, 0 errors, 0 skipped** across 10 suites. The five `JarvisSpeakerTest` lifecycle cases all passed.
- The same exact-head CodeQL job completed successfully after Kotlin source build, JVM unit tests, full `assembleDebug` and CodeQL analysis. Secret scanning passed. A debug APK artifact was uploaded.
- The separate Android emulator smoke job has started but has not produced a terminal result/test count. PR #106 remains Draft; no physical OPPO test has occurred.
- PR #105 has the same gates completed (45/45 JVM tests, APK build, CodeQL, secret scan), but its emulator smoke job is also still running.


## 40. VAD voice-loop implementation brief saved

- Added `docs/JARVIS_VAD_VOICE_LOOP_INTEGRATION_BRIEF_2026-11-07.md` with the current fixed-window limitation, two-model consent flow, privacy disclosure, proposed local endpointing targets, cancellation/resource lifecycle, test matrix and OPPO device acceptance scenarios.
- The brief explicitly defers VAD runtime wiring until PR #107's secure downloader passes exact-head CI. It keeps always-on hotword out of this slice and preserves the existing guarded tool-confirmation policy.
- Initial tuning targets: 0.55 s trailing silence, 8 s no-speech timeout, 25 s max segment and 30 s hard capture ceiling. These are starting values, not validated device measurements; adjust only from repeatable fixtures and OPPO testing.


PR #107 hardening follow-up: added constructor validation for the injected size cap and SHA-256 manifest, and delete a stale/corrupt final target before beginning a fresh consented download. Commit `735196a692f8755a26f6e2327119008772ea2b5d`; the previous CI run must be superseded and a new exact-head run inspected.


## 41. Silero VAD third-party attribution and manifest regression tests (2026-11-07)

- Verified the exact upstream asset identity/checksum against an immutable model mirror: `silero_vad.onnx`, 643,854 bytes, SHA-256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`; the upstream VAD model is MIT licensed. Official sherpa-onnx docs identify the download URL. See upstream references: https://github.com/k2-fsa/sherpa-onnx/blob/master/docs/source/onnx/pretrained_models/index.rst and https://huggingface.co/akm9191/silero-vad-onnx.
- Updated repository `NOTICE` with the model's source, license, size and checksum, clarifying it is optional, consent-gated, stored app-privately and not bundled/relicensed.
- Added tests rejecting malformed SHA-256 and a configured cap above the production cap, and replacing a stale/corrupt existing final model with a verified download. The Silero downloader suite now contains 12 cases.
- Latest PR #107 head `54a69a4c2f1dbf139007453a2fa7253c423e434f`; prior run #252 is stale (it predates the notice and tests). Fresh exact-head CI must finish before claiming any test/build success for this state.


## 42. Correct Sherpa Silero maxSpeechDuration semantics

- Reviewed the upstream Kotlin `Vad.kt` API and C++ configuration docs. `Vad.flush()` is available and must be used at the hard capture ceiling to emit buffered trailing speech before draining the queue.
- Important correction to the initial VAD brief: Silero `maxSpeechDuration` is **not** a hard segment-length cap. Upstream describes it as raising the detector threshold to 0.9 after the configured duration. Therefore the proposed 25 s value is only a detector sensitivity parameter; enforce the 30 s command ceiling independently in Kotlin and concatenate VAD segments until endpoint/ceiling.
- Updated `JARVIS_VAD_VOICE_LOOP_INTEGRATION_BRIEF_2026-11-07.md` accordingly. Reference: https://github.com/k2-fsa/sherpa-onnx/blob/master/kotlin-api/Vad.kt and https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/silero-vad-model-config.cc.


## 43. PR #108 — local VAD endpointing implementation opened (2026-11-07)

- Created draft PR #108, stacked on PR #107: https://github.com/sokfrut-tech/Gemeni/pull/108.
- Replaced the active `JarvisRuntimeController.listenOnce()` fixed 4.5-second collector with local Silero VAD endpointing. It collects completed VAD segments, exits after endpoint, an 8 s no-speech timeout, or an independent 30 s hard capture ceiling. At the hard ceiling it calls Sherpa `flush()` and drains the final queued segment before local ASR.
- Added a pure Kotlin `VadCapturePolicy` with six deterministic JVM tests. Configured 550 ms trailing silence, 200 ms minimum speech and 25 s Sherpa `maxSpeechDuration` (detector sensitivity parameter, not app hard limit).
- Updated model readiness so microphone capture requires both verified ASR and VAD. Updated explicit consent dialog to disclose both downloads, their licenses/approximate sizes, private storage and connection metadata. Neither model is downloaded by startup or `prepare()`; both are downloaded only after the user approves the combined dialog.
- VAD model lifecycle is closed with the runtime. Existing guarded tool confirmation flow is unchanged.
- PR #108 exact head `a54382e7f5802a376d3c30d728cd5549787f53c2`; run #259 (`42389442448`) is active. Secret scanning passed; CodeQL job is still running. No JVM/ APK / emulator result claimed yet; no OPPO hardware test has been performed.
- PR #107 exact head `54a69a4c2f1dbf139007453a2fa7253c423e434f`; run #255 (`42389152047`) is active. Secret scanning passed; CodeQL job is still building the full Debug APK. The previous run #252 predates latest notice/tests and is not accepted as exact-head evidence.


PR #108 static review follow-up: `SileroVadEngine.load()` now clears the reference to the released native VAD object before attempting to construct a replacement. This prevents a failed native reinitialization from leaving a stale released pointer that a later `close()` could release again. Latest PR #108 head `e8da5277b68d17f760368e850beabf5bcdbb84a4`; exact-head run #261 is pending. PR #107 latest run #255 has secret scanning passed; CodeQL is still at full Debug APK build. PR #105/#106 emulator jobs remain active without a terminal instrumentation count.


PR #108 responsiveness hardening: changed `AudioRecordPipeline` from blocking reads to `READ_NON_BLOCKING`. When the HAL returns no samples, the flow emits an empty heartbeat every 20 ms after a short delay. This allows the existing 8 s no-speech and 30 s hard-capture policy to advance even during temporary silent/stalled input, and keeps coroutine cancellation responsive instead of waiting on a blocking native read. `SileroVadEngine.accept()` treats empty arrays as a no-op. Commit `1011ec4248277a08a8c9193c506e09bf59a1ea6e`; this supersedes PR #108's earlier exact-head CI run and requires a fresh run.


## 44. Sherpa API pin and audio-HAL stall handling (2026-11-07)

- Verified against the exact dependency tag used by the app, Sherpa-ONNX `v1.13.8`: `Vad.kt` exposes `SpeechSegment.samples`, `acceptWaveform`, `empty/front/pop`, `isSpeechDetected`, `reset`, `flush`, and `release`. The current `SileroVadEngine` calls APIs present in that pinned source. Source: https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v1.13.8/sherpa-onnx/kotlin-api/Vad.kt.
- Exact v1.13.8 native config docs describe `maxSpeechDuration` as raising the detector threshold to 0.9 after the configured duration; it is not the app's hard capture timer. Kotlin independently enforces 30 s and flushes/drains at that deadline. Source: https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v1.13.8/sherpa-onnx/csrc/silero-vad-model-config.cc.
- Replaced blocking `AudioRecord.READ_BLOCKING` with `READ_NON_BLOCKING`; a zero-sample read yields a 20 ms empty heartbeat so the policy can still enforce 8 s/30 s deadlines when the audio HAL temporarily stalls. Cancellation no longer waits for a blocking native read to return.
- PR #108 latest head `1011ec4248277a08a8c9193c506e09bf59a1ea6e`; exact-head run #263 (`42389745430`) is pending. Prior run #261 is stale. PR #107 run #255 now has secret scanning and CodeQL passed; Android emulator smoke is still running, with no terminal test count yet.


PR #108 test refinement: added a seventh deterministic policy test proving that reaching Sherpa's 25 s `maxSpeechDuration` tuning point does not terminate capture; only the app's own 30 s deadline does so (unless a VAD endpoint arrives first). Latest PR #108 head `82eab794842be36942fa8cc934001a86516db7f4`; exact-head run #266 (`42389826211`) is pending. Run #263 is stale.


## 45. PR #107 exact-head CI evidence (2026-11-07)

- PR #107 head `54a69a4c2f1dbf139007453a2fa7253c423e434f`, run #255 (`42389152047`): Secret scanning SUCCESS; CodeQL static analysis SUCCESS; Kotlin compile SUCCESS; JVM unit tests SUCCESS; full Debug APK build SUCCESS; APK verification/hash SUCCESS; CodeQL analyze/SARIF SUCCESS.
- Downloaded and parsed JUnit XML artifact `13580692876`: **57 tests, 0 failures, 0 errors, 0 skipped**, across 10 suites. The Silero VAD downloader suite specifically reports 12/12 passing.
- Debug APK artifact `13580633488`, 86,954,012 bytes (~82.9 MiB), uploaded successfully.
- Android emulator smoke job `128454449250` remains in progress; no instrumentation result/count is yet available. This does not satisfy emulator release gate. PR #107 remains draft; physical OPPO testing is not performed.
- Evidence comment posted to PR #107. Run #255 is exact-head for PR #107 and remains valid; emulator status is still pending.


PR #108 exact-head run #266 (`42389826211`) for `82eab794842be36942fa8cc934001a86516db7f4`: Secret scanning SUCCESS; Kotlin compile SUCCESS; JVM unit tests SUCCESS. Downloaded/parsed JUnit XML artifact `13580524835`: **64 tests, 0 failures, 0 errors, 0 skipped**, 11 suites; `VadCapturePolicyTest` 7/7. At this evidence update, full Debug APK build is in progress; APK verification and CodeQL analyze not yet complete. Emulator job has not yet started. PR #108 remains draft; run #266 is exact-head.


## 46. Exact-head CI evidence for PR #105–#108 (2026-11-07)

- PR #105, head `46013ce1ddd09dd6281ef72248a6288a478c12e2`, run #233 (`42388091023`): secret scanning, Kotlin compile, JVM tests, full Debug APK build/verification, and CodeQL all SUCCESS. JUnit artifact `13580511925`: **45 tests, 0 failures, 0 errors, 0 skipped**, 9 suites. APK artifact `13580716262` uploaded (86,948,286 bytes).
- PR #106, head `2a9a0aef3c887433a46cfe9660ecacca18372e6f`, run #238 (`42388285102`): same CI stages SUCCESS. JUnit artifact `13580233903`: **50 tests, 0 failures, 0 errors, 0 skipped**, 10 suites; includes 5 speaker lifecycle tests. APK artifact `13580786536` uploaded (86,953,604 bytes).
- PR #107, head `54a69a4c2f1dbf139007453a2fa7253c423e434f`, run #255 (`42389152047`): **57/57** JVM tests; secret scan, CodeQL, Kotlin compile, full APK build/verification all SUCCESS. APK artifact `13580633488` uploaded (86,954,012 bytes).
- PR #108, head `82eab794842be36942fa8cc934001a86516db7f4`, run #266 (`42389826211`): **64/64** JVM tests; secret scan, Kotlin compile, full APK build/verification, CodeQL all SUCCESS. Emulator job `128456229885` is queued.
- Emulator jobs for PR #105/#106/#107 are still in progress at latest check. No terminal instrumentation XML/count has been confirmed for any of these branches. Therefore emulator release gate remains open and PRs stay draft. Physical OPPO Find X6 Pro test remains outstanding.
- Exact evidence comments posted on PRs #105, #106, #107 and #108. Do not interpret a successful JVM/APK/CodeQL run as emulator or device acceptance.


## 47. Emulator queue update (2026-11-07)

- At the latest poll, emulator smoke jobs are all actively running (not queued) for PR #105 run #233 (job `128451096498`), PR #106 run #238 (job `128451735829`), PR #107 run #255 (job `128454449250`), and PR #108 run #266 (job `128456229885`).
- Their current workflow step is `Run Android emulator smoke tests`; diagnostics and evidence-upload steps have not started. No instrumentation XML or non-zero test count has been published yet. This remains pending, not passed.
- PR #108 exact-head CodeQL job and secret scan are both SUCCESS; APK build, verify/hash and upload are successful. Its Android emulator job has moved from queued to in progress.


## 48. Stacked-branch integration hazard: TTS lifecycle vs VAD (2026-11-07)

Read-only source comparison of PR #106 branch `fix/jarvis-tts-lifecycle-queue-2026-11-07` against PR #108 branch `feat/jarvis-vad-endpointed-push-to-talk-2026-11-07` found a real integration gap:

- PR #106 contains the lifecycle-safe `JarvisSpeaker` adapter with asynchronous initialization handling, Russian locale validation, newest-utterance queue, failure handling, and idempotent `close()`.
- PR #106's `JarvisRuntimeController.close()` calls `speaker.close()`, but its branch does not include the new VAD controller work.
- PR #108 contains VAD lifecycle cleanup (`vad.close()`) and the new endpointing pipeline, but its branch still has the old fire-and-forget `JarvisSpeaker` implementation and its controller `close()` does **not** call `speaker.close()`.
- This is expected from the current DAG: PR #106 is stacked on PR #105, while PR #107 is also stacked on PR #105 and PR #108 is stacked on PR #107. Therefore PR #108 does not inherit PR #106 automatically.

**Integration requirement:** before marking TTS+VAD integrated, carry PR #106's lifecycle-safe `JarvisSpeaker` and tests into the VAD integration branch (or rebase/stack in an equivalent dependency-safe order), and preserve both `speaker.close()` and `vad.close()` in the final controller cleanup. Run the full JVM + instrumentation suite on the combined exact head. Do not merge PR #106 and #108 as if one already contains the other.

No source files were changed in this review step; this is a verified integration finding and release-plan update.


## 49. Carry TTS lifecycle into the VAD integration branch (2026-11-07)

Implemented directly on PR #108 branch `feat/jarvis-vad-endpointed-push-to-talk-2026-11-07` to close the sibling-branch integration gap identified in section 48:

- Copied the complete lifecycle-safe `JarvisSpeaker.kt` implementation from PR #106 into the VAD branch (commit `bbbd1d37856053f2d33132d243891825bac126fb`).
- Added all five `JarvisSpeakerTest` lifecycle tests from PR #106 (commit `952349f409d6215a910b325a783f299ff0e981e8`).
- Updated `JarvisRuntimeController.close()` to clear pending confirmation and close the speaker, Qwen runtime, ASR and VAD (commit `603ff6894d60dc7cba7a4f6b9b691315f15717ed`).
- Verified the resulting branch file content: all five lifecycle test methods are present and final controller cleanup calls both `speaker.close()` and `vad.close()`.
- This changes PR #108 head and triggers a new exact-head CI run. Previous 64/64 result belongs to the previous head and must not be reused as evidence for the new head.

## 50. Android compatibility and instrumentation scope audit

- Current PR #108 `app/build.gradle.kts` declares `minSdk = 29` (Android 10). This conflicts with the original product requirement recorded for Jarvis: Android 7+ compatibility (API 24+). Treat this as an unresolved product compatibility blocker; do not claim Android 7+ support. Audit dependencies/native ABI/API calls and either restore API 24 compatibility with tests or explicitly document and approve a revised minimum supported Android version.
- The current checked-in `app/src/androidTest` now contains two UI Automator test classes: `JarvisLaunchSmokeTest.mainScreenLaunchesAndShowsReadyState` and `JarvisRuntimeSmokeTest` (four tests). The runtime tests check key UI controls, rejection of model-download consent without network setup, runtime preparation failing closed when the ASR model is absent/consent is denied, and opening the local model/settings panel. This is broader than a launch-only smoke test, but it still does not validate consent acceptance/download, runtime microphone permission grant, live AudioRecord capture, actual Silero endpointing against audio, ASR transcription, audible TTS playback, or guarded tool confirmation. The emulator workflow requires a non-zero instrumentation count; passing these UI/runtime checks is not full voice-flow or physical-device acceptance.


## 51. Integrated PR #108 exact-head JVM evidence (2026-11-07)

For integrated head `603ff6894d60dc7cba7a4f6b9b691315f15717ed`, workflow run `42390830165` uploaded JUnit artifact `13581915627`. The archive was downloaded and all XML suites parsed: **12 suites, 69 tests, 0 failures, 0 errors, 0 skipped**. This includes `JarvisSpeakerTest` 5/5 and `VadCapturePolicyTest` 7/7. Evidence comment posted on PR #108 (comment ID `6709784485`). At the time of recording, the workflow had advanced to full Debug APK build; APK verification/upload, CodeQL analysis and emulator instrumentation remained pending. Do not treat the JVM pass as full CI or release approval.


## 52. Integrated PR #108 build and static-analysis evidence (2026-11-07)

- Exact integrated head `603ff6894d60dc7cba7a4f6b9b691315f15717ed`, workflow run `42390830165`: Secret scanning SUCCESS; CodeQL static analysis SUCCESS; Kotlin source compile SUCCESS; JVM unit tests SUCCESS; full Debug APK build SUCCESS; APK verification/hash SUCCESS; APK upload SUCCESS; CodeQL analyze/SARIF archive SUCCESS.
- Exact-head JUnit artifact `13581915627`: **12 suites, 69 tests, 0 failures, 0 errors, 0 skipped**. Includes `JarvisSpeakerTest` 5/5 and `VadCapturePolicyTest` 7/7.
- Debug APK artifact `13581088149`, 86,961,134 bytes (~82.9 MiB), uploaded successfully.
- Android emulator smoke job `128459323496` was still `in_progress` at the latest poll. No instrumentation XML/count has yet been verified, so emulator acceptance remains pending. PR #108 remains draft; no physical OPPO Find X6 Pro test has been performed.
- The compatibility issue in section 50 remains open: `minSdk = 29` conflicts with the original API 24+ goal. Do not lower/raise the minimum or claim Android 7+ support without a dependency/API/ABI audit and explicit product decision.


## 53. AudioRecord native-resource failure-path fix (2026-11-07)

- Static review of the integrated VAD branch found that AudioRecordPipeline.frames() checked recorder.state before entering its try/finally. If the native recorder object was created but reported an uninitialized state, the check threw before release(), leaking the native resource on that failure path.
- Moved the Kotlin sample-buffer allocation before native recorder creation and moved the initialization-state check inside the release-protected try/finally. All operations after AudioRecord object creation are now covered by cleanup.
- Commit: 97010e0ae1c3e77befdead8d844fd0b79e0ed401, on PR #108. Fresh exact-head CI run 42391861171 was queued at the time of recording. Do not reuse run #42390830165 as exact-head evidence for this newer commit.
- This is a resource-lifecycle hardening change only; it does not establish microphone/HAL behavior on OPPO hardware. Keep PR #108 draft pending fresh CI, emulator evidence, and physical-device validation.


## 54. Superseded emulator run and fresh exact-head validation (2026-11-07)

- When commit 97010e0ae1c3e77befdead8d844fd0b79e0ed401 triggered the new PR run, workflow concurrency cancelled the previous run 42390830165 while its Android emulator smoke job was still running. That job ended with conclusion CANCELLED, not SUCCESS; no emulator pass or instrumentation count may be inferred from it.
- Fresh run 42391861171 is for the current exact head 97010e0ae1c3e77befdead8d844fd0b79e0ed401. Secret scanning SUCCESS; CodeQL job is in progress (Android build components installation at latest poll). Emulator job is gated behind CodeQL and has not started yet.
- The prior 69/69 JVM result and APK belong to parent head 603ff6894d60dc7cba7a4f6b9b691315f15717ed. They remain useful historical evidence for that parent only and must not be attributed to the new head until its fresh run completes.
- PR #108 remains draft. Release gates still include fresh exact-head JVM/CodeQL/APK, non-zero emulator instrumentation evidence, Android API compatibility decision, and physical OPPO Find X6 Pro voice-path validation.


## 55. Microphone permission denial UX and emulator coverage (2026-11-07)

- Static UI review found that the Android runtime permission callback silently discarded a denied microphone request. The UI now reports that microphone access was denied and gives the user a recovery path through app settings; no capture is started after denial.
- Added an emulator instrumentation test that revokes RECORD_AUDIO, opens Jarvis, requests microphone access, selects the Android permission dialog's deny action, checks the user-facing denial message, and verifies the permission remains denied.
- Commits on PR #108: `e2f01847c174ceec672ddfaf648cb3b726e94c39` (permission UX), `46a9289da768885c30b63f0d281085455ac07050` (instrumentation), `432964ed8d5c4bf7e03b8d5ec953346eec7829da` (correct Package Manager permission query argument order).
- This is a permission-denial UI test only. It does not prove permission grant, AudioRecord/HAL capture, VAD endpointing with microphone samples, ASR transcription, TTS playback, or OPPO behavior.
- These commits supersede run `42391861171` for current-head evidence. Its 69/69 JVM result belongs to parent `97010e0ae1c3e77befdead8d844fd0b79e0ed401`; do not attribute it to the new head until fresh CI completes.


## 56. Voice setup ordering and instrumentation test drift correction (2026-11-07)

A deeper review of the new permission test found that the original UI flow checked model readiness before requesting RECORD_AUDIO. On a clean install, the model-consent dialog therefore appeared first and the microphone-denial path could not be exercised. The flow now requests microphone permission first; only after a grant does it inspect local model readiness and, if needed, show the combined ASR+VAD consent dialog. Denial exits without model/network setup.

The same review found stale instrumentation assumptions from the pre-VAD flow:
- Consent dialog title still expected the former ASR-only wording.
- A runtime test called the removed ASR-only download method.
- A prepare() assertion expected an obsolete error phrase.
These now match the current combined voice-model API and dialog. The model-consent test explicitly grants RECORD_AUDIO so it tests model consent independently of Android's microphone prompt.

Commits on PR #108:
- `5f6e4dd85c6c6a21c99565b62dee6cb86f49a857` — permission-first flow.
- `9bfedf6116309973e12808d0b1d666e5e22f5778` — align stale instrumentation API/text.
- `6e551a25fe4f9db4d9beb6386b138c5fdc4ad92d` — isolate model-consent test.

This review exposed why emulator instrumentation is a separate release gate: JVM-only success does not compile or execute androidTest sources. The previous CI run `42392341968` is now superseded; new exact-head evidence is required.


## 57. PR #108 exact-head CI rerun after emulator-readiness gate (2026-11-07)

- PR #108 current head: `78c0c98ececc9fd4d843ee25160ce295bee2ad49`; exact-head workflow run #289 / `42395610346`: https://github.com/sokfrut-tech/Gemeni/actions/runs/42395610346.
- Secret scanning SUCCESS. CodeQL/static analysis SUCCESS, including Kotlin compilation, JVM unit tests, full Debug APK build, APK verification/hash and SARIF upload.
- Debug APK artifact `13583034365`: 86,961,305 bytes; SHA-256 `acaf0b469c80065fdf6e611612aa0791fd0bbc3f83e03af971646bb8049dee3c`.
- JUnit report artifact `13582769743` uploaded successfully. The JVM test job is green; the exact test count should be taken from the XML artifact, not inferred from the job badge.
- The Android emulator smoke job `128473342706` started after CodeQL and reached the workflow step `Run Android emulator smoke tests`. At the time of this update it remains `in_progress`; diagnostic capture and emulator evidence upload have not run. Therefore no emulator instrumentation test count or pass is established.
- This run includes the CI-only readiness change from commit `78c0c98`: wait up to 180 seconds for ADB, boot completion, PackageManager and ActivityManager before installing/running instrumentation; on timeout collect bounded device/framework/logcat diagnostics and fail. This is a diagnostic gate, not a relaxation of test assertions.
- The prior run `42392521775` on head `6e551a25fe4f9db4d9beb6386b138c5fdc4ad92d` had a failed emulator job with **zero tests executed** because the emulator was offline and Android package/activity services were unavailable. That run is superseded for current-head evidence.
- PR #108 remains Draft. Keep the emulator non-zero-test requirement, Android API compatibility decision (current `minSdk=29` vs original API 24+ target), and physical OPPO Find X6 Pro microphone/VAD/ASR/TTS validation as open release gates. Do not merge or publish an APK as a qualified release based only on this build artifact.


## 58. PR #108 emulator failure root cause and CI harness correction (2026-11-07)

The terminal result of exact-head run `42395610346` (head `78c0c98ececc9fd4d843ee25160ce295bee2ad49`) is now inspected from the Android emulator job log:

- The emulator did boot: the runner reported `sys.boot_completed=1` and `Emulator booted`; its boot took approximately 609,900 ms (about 10 minutes).
- The failure was **not** a PackageManager/ActivityManager readiness timeout and did not show `Can't find service: package`. The action invoked each line of its multiline `script:` as a separate `/usr/bin/sh -c` command. The line `for attempt in $(seq 1 90); do` was therefore executed without the following loop body and failed with `Syntax error: end of file unexpected (expecting "done")`.
- This is a CI harness/script-boundary bug introduced by the multiline shell control flow, not evidence of an Android `system_server` crash. Zero instrumentation tests ran. The emulator diagnostics folder was not uploaded because the script failed before creating its files.
- Corrective change: move the complete readiness, diagnostics, Gradle instrumentation and non-zero JUnit XML-count logic into a checked-in Bash script `.github/scripts/run-android-emulator-smoke.sh`; invoke it as a **single command** from the emulator-runner `script:` input. The script explicitly restarts host ADB (`adb kill-server` / `adb start-server`), waits for device/framework services, and retains the hard non-zero instrumentation test requirement.
- Infrastructure experiment, limited to the emulator job: pin runner to `ubuntu-22.04`, switch API 35 image target from `google_apis` to `aosp_atd`, and use cold boot / no snapshot save / wipe-data options. The CodeQL and secret-scan jobs remain on their existing runner configuration.
- These are new CI changes and require a fresh exact-head workflow. They are not evidence that emulator tests now pass. PR #108 stays Draft pending non-zero instrumentation results and physical OPPO validation.


## 59. ATD system-image compatibility correction (2026-11-07)

Checked the current `reactivecircus/android-emulator-runner` configuration documentation before the next run. Its README lists `aosp_atd` / `google_atd` as requiring API 30+, `arch: x86` or `arm64-v8`, and `channel: canary`. Therefore the first infrastructure commit's inherited `x86_64` / stable settings would not be a valid ATD configuration. Corrected PR #108 workflow to use API 35, `aosp_atd`, `arch: x86`, and `channel: canary`. Keep this as an isolated CI experiment; exact-head run must confirm SDK image availability and boot before judging test outcomes. Reference: https://github.com/ReactiveCircus/android-emulator-runner/blob/main/README.md#configurations.


## 60. Select the compatible lightweight emulator image (2026-11-07)

A final ABI compatibility check changed the initial ATD experiment before it could be treated as a valid run configuration:

- The app's `defaultConfig.ndk.abiFilters` currently includes only `arm64-v8a` and `x86_64`.
- The current `reactivecircus/android-emulator-runner` README documents `aosp_atd` as requiring `arch: x86` or `arm64-v8` and `channel: canary`; its documented ATD configuration does not match the app's available `x86_64` ABI.
- To avoid turning an emulator startup experiment into an APK ABI/install failure, the workflow now selects the lightweight `default` API 35 image with `arch: x86_64` on `ubuntu-22.04`. This removes the Google APIs image while retaining an ABI supported by the app. The app's native ABI configuration is not changed.
- A future ATD migration would require deliberate addition and verification of the x86 native ABI (or an appropriate arm64 emulator strategy); it is not silently assumed here.
- The workflow also runs the checked-in Bash harness as one command, restarts ADB, performs a cold boot with wiped data/no snapshot save, and requires a non-zero instrumentation XML count. Fresh exact-head CI is the acceptance evidence.

## 61. Current voice-loop integration and emulator CI continuation (2026-11-07)

- Rechecked the current stacked source at PR #109 head `38c6f18a2b316a47ab540b41b2b616949d0ec74c`. The active runtime contains both Silero VAD lifecycle (`vad.load/reset/flush/close`) and lifecycle-safe Russian TTS (`speaker.speak/close`). The five `JarvisSpeakerTest` cases and seven `VadCapturePolicyTest` cases are present in the same integration chain. An earlier concern that TTS was missing from the current branch was disproved by direct file inspection; no duplicate transplant is needed.
- Static review of `JarvisRuntimeSmokeTest` found one stale assertion after the combined ASR+VAD consent dialog was renamed: the decline test waited for the old title `Загрузить русскую модель?`, which was absent even before the click. Corrected it to wait for the current title `Загрузить голосовые модели?`.
- Test-only correction is isolated in PR #111, head `f6ee22cfcc2424fa9cb9af772971a1ffb91d5682`, stacked on PR #110. PR #111 remains Draft pending exact-head CI.
- PR #110 is the CI-only emulator stabilization experiment, head `b0874d2820e872c3d452bbd9493a24495e3dc149`, stacked on PR #109. It switches the emulator job to API 34 / x86_64 default image, removes wipe-data and ADB-server restart, checks live PackageManager/ActivityManager availability, bounds Gradle workers, captures diagnostics, and keeps a hard non-zero instrumentation-test-count requirement.
- The failure artifact from the prior run showed a `system_server` FinalizerWatchdogDaemon timeout while finalizing AssetManager, followed by system_server termination. This is evidence of emulator framework instability in that run; it is not evidence of an application crash. The API 34 workflow change is an experiment, not yet a proven fix.
- Fresh workflow run #299 (`42407501467`) is for PR #110 exact head. At this update, secret scanning is SUCCESS; Kotlin source compile has succeeded and JVM unit tests are in progress. APK build, CodeQL analyze and emulator smoke are not yet complete.
- Fresh workflow run #300 (`42407613350`) is for PR #111 exact head. At this update, secret scanning is SUCCESS; Android build component installation is in progress. No test or APK result is yet established.
- Do not merge either PR or claim voice-loop acceptance until the current exact-head pipeline completes, emulator instrumentation reports a non-zero executed test count, and physical OPPO Find X6 Pro validation is performed.
- Next: inspect terminal results for runs #299 and #300; if API 34 instrumentation still fails, use the newly uploaded in-action diagnostics to classify boot/framework/test-runner/app failure before changing production code. After CI, verify the real microphone -> VAD endpoint -> ASR transcript -> guarded tool result -> Russian TTS cycle on the physical OPPO. Keep wake-word/always-on capture out of scope until a separate consent, battery, privacy and lifecycle design is approved.

## 62. PR #110 exact-head JVM suite parsed from CI artifact (2026-11-07)

- Workflow run #299 / `42407501467` is the Factory Security Baseline associated with PR #110's API 34 emulator stabilization change. GitHub uploaded JUnit artifact `13588072200` (`jarvis-unit-test-results-8f6463a4b2982f62675eb9891fc31b45a60a0d4d`).
- Downloaded and parsed every XML file in that artifact: **12 suites, 69 tests, 0 failures, 0 errors, 0 skipped**. Includes `JarvisSpeakerTest` 5/5 and `VadCapturePolicyTest` 7/7.
- Secret scanning is SUCCESS; Kotlin source compilation is SUCCESS; JVM unit tests are SUCCESS with the parsed count above. At the latest poll, full Debug APK build was still in progress. CodeQL analysis and emulator instrumentation had not reached terminal results.
- This evidence is for the workflow's PR merge ref (artifact name contains merge SHA `8f6463a4b2982f62675eb9891fc31b45a60a0d4d`), not a standalone physical-device test. Do not claim API 34 emulator stability until the emulator job terminates with a non-zero instrumentation test count.

## 63. Prevent overlapping microphone sessions (2026-11-07)

- Source review found that the microphone FAB remains available while Jarvis is transcribing, reasoning, executing a tool, or speaking. `JarvisRuntimeController` owns shared AudioRecord, Silero VAD, ASR and TTS instances; a second tap could therefore enter `listenOnce()` concurrently and race shared native/runtime state.
- Added a non-blocking `ListenSessionGate` using `AtomicBoolean.compareAndSet`. Only one listen/voice-command lifecycle can enter at a time. A repeated tap is rejected immediately with a user-facing “already processing” status rather than queued behind the current session.
- The gate is released in a `finally` block, including cancellation and runtime exceptions.
- Added two pure JVM tests covering overlap rejection, release/reacquisition, and ensuring a failed second entry cannot release the first session.
- Implementation is isolated in Draft PR #112, head `9fafca6f72492f1c507b8d08bb2a799c710b116a`, stacked on PR #111 → #110 → #109. It changes only the voice runtime guard and its tests; no always-on/background microphone, permission, network, or tool-policy changes.
- PR #112 exact-head CI has not yet been observed in the available workflow results. Do not claim this guard is tested until its own fresh JVM/CodeQL/APK/emulator pipeline completes.
- The exact-head workflow runs #299 (PR #110) and #300 (PR #111) remain non-terminal at the latest poll: secret scanning passed in both; #299 is in CodeQL analysis after APK build, #300 is in APK build. Emulator instrumentation has not produced a terminal non-zero test count yet.
- Next: inspect terminal results for #299/#300; then inspect #112's exact-head CI. If it passes, continue with lifecycle tests for Activity recreation/backgrounding and actual microphone/VAD/ASR/TTS behavior on OPPO Find X6 Pro. Keep every stacked PR Draft until all applicable gates pass.

### PR #112 follow-up verification hardening (2026-11-07)

- Expanded `ListenSessionGateTest` from sequential-only checks to a concurrent contention test with 16 simultaneous callers. The test holds the winning lease until every contender has attempted entry, then asserts exactly one successful acquisition and verifies the gate can be acquired again after release.
- Worker-thread failures are captured and asserted on the test thread so executor exceptions cannot be silently swallowed by the test harness.
- Latest PR #112 head: `82bf95685279d95d0cd2dece2e928aa48af81e01`. Fresh Factory Security Baseline run #306 / `42408375739` is queued; secret scan and CodeQL have started. The previous run #303 was superseded/cancelled by this additional test commit and is not evidence for the current head.
- PR #110 run #299 and PR #111 run #300 have both passed CodeQL and secret scanning, but their Android emulator smoke jobs are still running. No terminal instrumentation result or non-zero test count has been established.
- Source inspection reconfirmed that AudioRecord uses non-blocking reads, emits empty heartbeat frames for silence deadlines, and releases the recorder in `finally`; VAD operations are synchronized and the voice-session gate prevents two calls from entering the shared capture/ASR pipeline concurrently.
- Open lifecycle review item: Activity/controller shutdown while a voice coroutine is active must be validated separately. The gate protects concurrent entry but does not itself cancel an in-flight coroutine; cancellation is expected from the owning lifecycle and must be proven by instrumentation/device evidence before always-on or background listening is considered.

### PR #112 expanded guard scope after UI call-path review (2026-11-07)

- A follow-up trace of `MainActivity.startRealListening()` found that it called `runtime.prepare()` **before** calling the guarded `runtime.listenOnce()`. Thus the first guard prevented concurrent recording sessions but did not prevent rapid taps from concurrently reloading the shared ASR/VAD native engines during preparation.
- Corrected the boundary: `listenOnce()` now acquires the session gate first, runs `prepare(onProgress)` while holding it, then runs the complete VAD → ASR → reasoning/tool policy → TTS lifecycle, and releases in `finally`. `MainActivity` no longer prepares outside that guarded path.
- This makes the gate cover the full microphone request rather than only the capture stage. A second tap is rejected before model preparation begins.
- The unit suite now includes a 16-thread contention test that asserts exactly one successful gate acquisition, captures worker exceptions explicitly, and verifies reacquisition after release.
- PR #112 updated scope/body; latest head after these changes is `b19cca46ecc081c9571df503abf59b191ea4fad5`. Fresh exact-head CI must be observed; older run #303 was superseded and cancelled and is not qualifying evidence.
- Separate lifecycle concern remains: `MainActivity.onDestroy()` calls `runtime.close()`; confirm lifecycle-scope cancellation closes an active AudioRecord flow before/while native ASR/VAD resources are released. Do not claim this lifecycle race is solved by the session gate.

### PR #112 shutdown-order hardening (2026-11-07)

- Further lifecycle review confirmed `MainActivity.onDestroy()` calls `runtime.close()` before `super.onDestroy()`; the Activity's `lifecycleScope` is cancelled as lifecycle destruction is dispatched, so an active IO voice coroutine may still be unwinding when native engines are closed.
- Extended `ListenSessionGate` with a closing state and one-shot deferred cleanup. Shutdown rejects new microphone sessions immediately. If a session is active, speaker/Qwen/ASR/VAD resources are released only when the active session exits its `finally` block; if idle, cleanup runs immediately.
- Added JVM coverage for deferred cleanup, immediate cleanup, idempotent close requests, and rejection of new sessions after shutdown, in addition to the 16-contender concurrency test.
- Latest PR #112 source commit: `62f02df2493c7e4fd749cc9ecc27d6a1213761ef` (test addition; PR head should be read from GitHub before attributing CI). This change is not yet qualified until exact-head checks finish.
- This is shutdown-order hardening, not proof that Android lifecycle cancellation or AudioRecord HAL release works on every OPPO/ColorOS build. The physical-device lifecycle test remains open.

### PR #111 exact-head JVM artifact parsed (2026-11-07)

- Downloaded and parsed all JUnit XML files from PR #111 workflow run #300 / `42407613350`, artifact `13588465984` (merge-ref SHA `570d297737c4cbea2f5827a1ce292ed44014948f`).
- Result: **12 suites, 69 tests, 0 failures, 0 errors, 0 skipped**. This independently confirms the same 69-test JVM baseline on PR #111's merge ref; it does not include Android instrumentation.
- The run's secret scan and CodeQL completed successfully and its Debug APK artifact was uploaded. Android emulator smoke remains in progress; no non-zero instrumentation count is established yet.

### PR #112 microphone busy-state UX (2026-11-07)

- The Compose screen now derives a busy state across LISTENING, TRANSCRIBING, UNDERSTANDING, EXECUTING, SPEAKING and WAITING_FOR_APPROVAL. The microphone action is visually muted and ignores taps during these states or while voice models are downloading.
- This is a usability layer only; `ListenSessionGate` remains the authoritative runtime guard against repeated calls and concurrent lifecycle races.
- Change is in PR #112; current source head after the UI change: `d84de8e192a495a22283dbf46d66f75a85dd08a2`. A fresh exact-head CI run is required.

### PR #112 contention-test synchronization correction (2026-11-07)

- A final review of the new 16-thread test found a test-harness ordering flaw: the winning worker was waiting on a release latch while the main test thread waited for all workers to finish, so both could wait until timeout.
- Corrected the test with a distinct `attempted` latch. Each worker records its acquisition result before decrementing that latch; the test thread waits until all attempts are complete, asserts exactly one winner, releases the winner, and only then waits for worker completion. Worker exceptions remain explicitly propagated.
- Latest PR #112 head: `192c417632d08af4fd794f191385a0476e3ca0bf`. Fresh Factory Security Baseline run #319 / `42408671067` is pending. Earlier run #315 on the previous head was cancelled when the test synchronization fix was pushed; it is not qualifying evidence.
