# Jarvis Interactive Orb + SaveVideo — Product & Engineering Brief
**Date:** 2026-11-07  
**Status:** Approved product direction; implementation must be split into testable PRs.  
**Target:** `sokfrut-tech/Gemeni` (Jarvis Android app; OPPO Find X6 Pro / ColorOS first).

## User decision
The user explicitly approved both capabilities shown in the supplied SaveVideo/Jarvis reference:
1. A highly interactive, animated AI orb / digital brain as the central Jarvis identity and live status surface.
2. A SaveVideo-style media link intake and download manager, alongside the existing offline video editing tools.

The orb's interactivity is a core product requirement, not optional decoration. The orb must reflect real application and engine state, not a canned animation pretending that work is happening.

## Existing repository context (do not duplicate)
- The repository already contains a native OpenGL ES visual engine (`alphavideo_gl_engine.cpp`) and an earlier orbital renderer effort.
- PR #55, **feat(jarvis): add animated holographic orbital core**, records the layered cyan/amber orbital core, moving light points, radial filaments, breathing center, and shader diagnostics. Its own stated limitation is important: the Activity only supplies a listening boolean; full IDLE/LISTENING/PROCESSING/SPEAKING/ERROR mapping and measured microphone/TTS amplitude remain follow-up work. Physical OPPO non-black-frame validation is still required.
- PR #89, **feat(video): add offline trim and MP4 export studio**, is an offline editing slice: local video picker, preview, trim range, MP4 export to Movies/Jarvis, cancel/share, and tests. This is not the SaveVideo link-downloader.
- PR #92, **feat(jarvis): add pinned llama.cpp JNI inference runtime spike**, is a draft runtime integration. Exact-head emulator/device qualification remains pending; do not imply the local neural runtime is already qualified.
- Preserve the repository's existing local-first, privacy, safety-gateway, explicit-consent and exact-head CI policies. Do not replace or bypass existing modules.

## Product architecture

### A. Interactive Orb / Digital Brain (P0)
Make the orb the central, live visual state of Jarvis.

#### State contract
Use a typed, single-source-of-truth state model shared by voice controller, task coordinator, download manager and renderer:
- `IDLE`: slow breathing, calm core.
- `WAKE_WORD`: short recognition ripple / wake pulse.
- `LISTENING`: microphone-level-reactive rings and particles.
- `PROCESSING`: visibly changing neural filaments / rotating orbit; show the current operation label.
- `SPEAKING`: react to measured TTS output amplitude, not an invented waveform.
- `SEARCHING`: outbound scanning / data-flow animation with source/query label.
- `DOWNLOADING`: segmented progress ring tied to real byte progress.
- `WORKING`: task/agent activity; show task count and current task.
- `WAITING_FOR_APPROVAL`: pause/hold visual state and expose the exact action awaiting approval.
- `SUCCESS`: short completion pulse, then return to idle.
- `ERROR`: clear error state with retry/details action; never hide failure.
- `OFFLINE`: distinct local-only state, not an error.

Transitions must be driven by real events. Avoid rapid flicker; debounce transient state changes and keep the current operation label readable.

#### Interaction
- Touch/tap orb: open/close the compact command panel or focus voice input.
- Long press: expose orb controls (visual intensity, motion reduction, sound/haptics opt-in).
- Drag/gesture: optional rotation/orbit interaction, with no interference to Android navigation.
- Provide accessible labels, keyboard/switch access and a reduced-motion mode.
- Voice wake/listening and TTS amplitude must be sourced from the actual speech pipeline, with privacy-preserving in-process event delivery.
- Do not keep the microphone active merely to animate the orb. Respect the existing explicit microphone permission/consent and voice lifecycle.
- Orb must remain useful with sound off, offline, low battery and on lower-end hardware.

#### Rendering and performance
- Reuse the existing GLES2 engine and shader diagnostics where stable; do not create a parallel renderer without evidence.
- Compose UI owns controls, status text, accessibility and lifecycle; renderer receives immutable state snapshots/events.
- Ensure EGL/context loss, Activity pause/resume, surface recreation and renderer failure degrade safely to a static Compose orb/status rather than a black screen.
- Target smooth animation on OPPO Find X6 Pro, but cap frame rate / effects under thermal or battery pressure.
- Keep allocations low in the render loop; no network or disk I/O on render thread.
- Add deterministic state-transition tests, renderer lifecycle tests, screenshot/frame smoke checks where CI supports them, and physical-device validation.

#### Acceptance criteria
1. Every state above can be entered by a real test event and exits correctly.
2. Listening and speaking visuals track real audio amplitude within a defined smoothing window.
3. Download/task progress matches actual underlying progress.
4. No false activity when idle; no hidden microphone capture.
5. No black screen on GL failure/context loss; visible fallback is tested.
6. Reduced-motion/accessibility controls work and persist locally.
7. Exact-head CI passes; physical OPPO/ColorOS evidence is attached before release.

### B. SaveVideo link intake and download manager (P1)
Add a separate `MediaLink` / `DownloadManager` capability. Keep it distinct from the offline editor (PR #89), but expose both from one Jarvis Media workspace.

#### User flow
1. Accept a pasted URL, Android share-sheet intent, or explicit clipboard import initiated by the user.
2. Parse and validate the URL; display the normalized host and destination before any network request.
3. Resolve metadata only through a permitted source adapter or official API.
4. Show title, thumbnail (when legally/publicly available), duration, creator/source and available formats/qualities.
5. Let the user choose quality/format where the source permits it.
6. Start only after a clear user action; show real progress, speed, size, pause/resume/cancel and failure reason.
7. Verify downloaded file (size/checksum where available), save through MediaStore to `Movies/Jarvis/Downloads`, and provide open/share actions.
8. Maintain a local history with retry, delete and duplicate detection.

#### Source support and legal boundaries
- Build a provider-adapter architecture; never assume all platforms expose the same download method.
- Support direct media URLs and platforms/sources whose official APIs or terms explicitly permit downloading.
- For platforms such as YouTube, Instagram and TikTok, do not bypass DRM, access controls, private content, paywalls, login restrictions or technical download restrictions. Do not scrape protected streams or extract private session cookies. Use official/authorized mechanisms where available; otherwise clearly explain that direct download is unavailable and offer a permitted alternative (for example opening the original source or saving a user-owned local file).
- Do not claim universal platform support. Maintain a visible capability matrix with source, supported action, limitation, last verification and test fixture.
- Respect copyright: downloads are for content the user owns, has permission to save, or is otherwise legally downloadable. Display a concise reminder at the point of download.
- No hidden background downloads and no automatic clipboard surveillance.

#### Reliability and privacy
- Download network activity begins only after the user explicitly starts a download.
- No uploaded prompts, voice, personal files or local memory. No telemetry by default.
- Use HTTPS, host allowlists per provider, redirect validation, content-type and file-size checks, safe filenames, path traversal protection and temporary-file + atomic rename.
- Avoid arbitrary URL fetching that could enable SSRF; resolve only approved schemes/hosts and reject local/private-network destinations.
- Bound concurrency, retries and storage use. Support cancellation and cleanup after failure.
- Use Android DownloadManager only if its lifecycle/security behavior meets the app's needs; otherwise implement a bounded foreground worker with user-visible notification and correct Android background execution constraints.
- Keep downloads separate from media editing. A completed download can be opened in the existing offline trim/export studio.

#### Acceptance criteria
1. Share-sheet and manual URL intake work; clipboard is never read silently.
2. Invalid, unsupported, private-network and restricted URLs are rejected safely.
3. No request occurs before explicit download confirmation.
4. Real progress, pause/resume/cancel, retry and cleanup are tested.
5. Files land in the expected MediaStore location with safe names and correct MIME type.
6. No bypass of DRM, access controls, authentication or platform restrictions.
7. Unit/instrumentation tests cover redirects, malformed URLs, large files, interruption, low storage, duplicate files and cleanup.

### C. Unified Media & Activity workspace (P2)
- One Media area with tabs/cards: **Скачать**, **Мои видео**, **Обрезать**, **История**.
- Orb reflects the active media state (resolving, downloading, exporting, completed, failed).
- Global Jarvis activity panel shows real task/agent states, timestamps, progress and approval requests.
- Keep analytics local and explain what each metric means; do not fabricate engagement or productivity statistics.

## Delivery sequence
1. **State contract and UI integration:** define typed orb states/events and connect existing voice/task lifecycle. Keep renderer unchanged unless a specific defect is evidenced.
2. **Orb behavior:** actual mic/TTS amplitude, task/search/approval/download signals, accessibility, reduced motion and GL fallback.
3. **SaveVideo foundation:** URL parser, provider registry, capability matrix, security validation and tests; no platform-specific downloader until terms/API capability is verified.
4. **First permitted provider:** implement one lawful direct/official source end-to-end with consent, progress, cancellation and MediaStore tests.
5. **More providers:** add only after per-provider legal/technical review and reproducible tests.
6. **Unified workspace:** connect download history and existing offline editor.
7. **Release qualification:** exact-head CI, CodeQL, secret scanning, Android instrumentation, low-storage/interruption cases and physical OPPO Find X6 Pro / ColorOS validation.

## Non-negotiable project rules
- Local-first and free/open-source core; no mandatory paid API or account.
- No secrets in source, APK, logs or screenshots.
- No hidden uploads, telemetry, microphone capture or clipboard polling.
- All risky Android actions continue through the central risk policy / tool gateway and require confirmation when classified.
- Keep feature PRs draft until exact-head checks are reviewed. Red CI means no merge/release.
- Do not report a feature as implemented until code exists and tests/evidence support the claim.
- Update this brief with actual implementation PRs, decisions, limitations and test evidence; preserve continuity for other agents.
