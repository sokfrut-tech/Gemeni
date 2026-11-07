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
