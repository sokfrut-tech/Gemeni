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
