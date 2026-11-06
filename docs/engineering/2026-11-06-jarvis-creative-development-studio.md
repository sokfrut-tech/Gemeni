# Jarvis Creative & Development Studio — product and engineering brief

Date: 2026-11-06
Status: Approved product direction; architecture/implementation plan, not a shipped capability.
Owner: Jarvis / Gemeni
Related: PR #81 (phone-control foundation), PR #82 (local learning foundation), issue #80.

## Product decision

Jarvis is to become a phone-first, local-first AI assistant AND a creative/development studio. The Qwen family is the primary reasoning and coding brain, not the only model or a magical all-in-one engine. The system must combine specialist on-device runtimes behind one coherent Russian-language interface.

The target user experience is: describe an outcome in Russian, inspect the proposed plan and files, then have Jarvis create, run, test, revise and package the result on the OPPO phone wherever Android/device resources permit.

No paid API or subscription may be required for the core workflow. Model downloads and optional online features must be explicit, resumable, integrity-checked and user-controlled. No user content, local project, source code, image, prompt history or learned memory may leave the phone by default.

## Capability groups and expected behavior

### A. Qwen reasoning core and agent orchestration

- Use a provider-neutral local model interface. Qwen is the preferred first model family for Russian conversation, planning, code generation, explanation, tool selection and multimodal understanding where a suitable local checkpoint exists.
- Separate the roles: conversation/planning, code generation/review, vision understanding, image generation, speech recognition/synthesis and audio/music generation. Do not claim one Qwen checkpoint performs every role.
- Implement bounded tool calling: model proposes typed actions; deterministic validators and policy authorize them; adapters execute; observed results return to the model.
- Support multi-step jobs with visible plan, progress, checkpoints, cancellation, resumable state, logs and artifact list.
- Use model-size tiers selected by measured RAM, thermal and latency budget. Small model for fast/offline tasks; larger model is optional download and must be benchmarked on the actual phone before recommendation.
- Model files are never bundled blindly into the APK. Download to app-private storage with license/source metadata, SHA-256 verification, disk-space check, cancellation, resume, delete and rollback.
- Keep a deterministic no-LLM fallback for core phone tools and safe failure messages.

### B. Programming workshop

Jarvis must be able to:
- Create, read, explain, search, compare, refactor, debug and document project files.
- Write Python scripts and modules, tests, CLI tools, data processing utilities and small local automation tasks.
- Read and write Kotlin/Java, C/C++, JavaScript/TypeScript, HTML/CSS, Gradle, XML, JSON, SQL and other supported text/code formats as runtimes mature.
- Open imported ZIP/project folders, map the structure, inspect build configuration and dependencies, explain unknown code, identify risks and prepare a change plan before edits.
- Work on Git repositories: branch/patch/diff, commit only with configured authorization, preserve user changes, create a reviewable change summary and never force-push or overwrite user work.
- Run syntax checks, unit tests, linters and builds in a sandboxed workspace with resource/time limits; show exact command, exit status and output summary.
- Explain errors in Russian and attempt bounded fixes; never hide failing tests or claim success without fresh evidence.
- Generate complete source projects, README, tests, assets manifest and reproducible build instructions.

### C. Python runtime on Android

- Provide a managed local Python workspace with interpreter/version information, project folders, package/environment isolation and a simple editor/console.
- Evaluate embedded CPython/JNI, Chaquopy and other Android-compatible runtimes by license, ABI, maintenance, wheel availability, offline operation and APK size before choosing. Do not assume every desktop pip package or native wheel works on Android.
- Prefer pure-Python packages where practical; native extensions must be pinned, ABI-verified and tested on arm64-v8a.
- No arbitrary untrusted script runs with unrestricted app/device permissions. Run jobs in a dedicated process/sandbox with file allowlists, no implicit network, execution timeout, memory/output limits and cancellation.
- Network access from a Python job is denied by default; a user-approved task may enable a narrow, visible network capability.
- Support export/share of source and generated outputs only after user action.

### D. APK and Android application factory

- Create Android app projects from templates and natural-language specifications.
- Generate Kotlin/Compose UI, resources, manifests, Gradle configuration, tests and app icons/assets.
- Build debug APKs locally on the phone using a managed/pinned Android SDK, Gradle and required build tools where device storage/RAM/thermal constraints allow.
- Provide a clear build pipeline: requirements -> project scaffold -> code -> static checks -> unit tests -> APK build -> APK signature/hash -> install prompt -> smoke test -> release artifact.
- Debug APK and release APK must be labelled distinctly. Release signing keys remain local, encrypted where feasible, never uploaded, never embedded in source or logs.
- Explain Android permission prompts, target/min SDK constraints, package IDs, signing and install-source restrictions. Do not silently install, grant permissions or publish.
- If a build is too large for the phone, report the exact blocker and offer a user-approved local/remote build route; never silently upload the source code.

### E. Games and interactive applications

- Generate small Android games and interactive apps from a brief: concept, rules, levels, UI, assets, sound, save system and tests.
- Start with native Compose/Canvas for simple 2D experiences; evaluate libGDX or Godot Android export for richer 2D/3D projects. Toolchain/license/storage/RAM compatibility must be verified before committing.
- Support iterative preview, playtest checklist, bug reports, balancing and packaging.
- Keep game assets and project source editable; no flattened mockup presented as a working game.
- Generate procedural 2D art and UI assets locally where feasible; larger 3D/animation tasks may require a separate specialist pipeline.

### F. Image understanding, generation and editing

- Accept user images and project assets; describe, inspect, OCR, classify layout, identify visual defects and answer questions grounded in the supplied image.
- Provide prompt-to-image generation, image variations, inpainting/outpainting, background removal, upscaling and controlled edits through specialist image models.
- Target high visual quality, but expose model, resolution, estimated memory/time and whether the task is local or online before execution.
- Treat Qwen-VL or another vision-language checkpoint as image understanding, not image synthesis. Image generation requires a diffusion/flow model or other dedicated generator.
- Evaluate local diffusion options by model license, mobile runtime, quantization, memory footprint, latency, output quality and arm64 compatibility. A desktop SDXL pipeline must not be assumed to run well on a phone.
- Use adaptive presets: fast draft, balanced, high-quality; allow queued/offline generation and cancel/resume where runtime supports it.
- Generated images should be saved as real image files with metadata and user-selected destination; provide editable prompts/settings where practical.

### G. Audio, speech, music and writing

- Russian ASR/VAD/TTS for hands-free interaction, with visible text fallback and local processing.
- Compose poetry, stories, scripts, lyrics, descriptions, documentation and other text in requested style; distinguish factual claims from fiction.
- Support audio transcription, summarization, sound generation and music composition through specialist local models when available. Music generation is separate from speech synthesis and must not be implied by Piper TTS.
- Provide audio export (for example WAV/MP3 where encoder/license permits), playback, metadata and project source where possible.
- Internet radio and streaming are network features; no local content is uploaded to make them work.

## Creative freedom and safety boundary

- The product should support a broad range of lawful adult creative themes, including mature romance and non-explicit erotic art/writing, with user control over style and tone.
- It must not create sexual content involving minors or ambiguous-age subjects, non-consensual intimate imagery of real people, sexual deepfakes, or other unlawful abusive material. Do not use a real person's likeness in intimate content without clear consent.
- Respect applicable law, model licenses, platform rules and third-party rights. No claim of "no restrictions whatsoever".
- The local product must make content handling transparent. Safety checks should be narrow, predictable and documented, not a hidden upload to a moderation service.

## Architecture

Use a modular studio pipeline:

1. **Conversation & planner** — local Qwen reasoning; converts request into a typed task graph and clarifies only essential missing details.
2. **Policy & capability broker** — checks permissions, data classification, network policy, resource budget and user approvals.
3. **Specialist model router** — selects local LLM, vision model, diffusion model, ASR/TTS, audio model or deterministic tool.
4. **Workspace manager** — app-private project directory, versioned files, snapshots, diffs and artifact registry.
5. **Execution workers** — Python process, Gradle/Android build worker, test runner, image worker, audio worker; each sandboxed and cancellable.
6. **Artifact manager** — APK, source archive, images, audio, reports and logs with hashes and provenance.
7. **Learning/memory** — user-approved local corrections and project preferences; separate from model weights; opt-in, inspectable, editable and deletable.
8. **UI** — Studio home, project explorer, code editor, terminal/task log, image canvas/gallery, build page, model manager and privacy controls.

Every task must declare: inputs, outputs, required model/runtime, offline/online status, permissions, estimated resource use, file/network access, timeout, cancellation and acceptance tests.

## Privacy and security invariants

- Default is local-only. No telemetry, analytics, crash uploads, remote memory sync, prompt uploads or source-code uploads.
- Online search is opt-in per task and sends only the minimum explicit query. Never attach private project files, conversation memory or images automatically.
- No cloud inference, remote image generation or remote build unless the user explicitly chooses that route after seeing exactly what data leaves the phone.
- Python/code execution uses sandboxed workers and scoped file access; no arbitrary shell/root, hidden persistence, credential extraction or unrestricted Intent execution.
- Model output is untrusted. Validate code and tool requests; require approval for file deletion, external sharing, installation, signing, purchases and destructive operations.
- Project snapshots and diffs before automated edits; rollback on failed tests or explicit user request.
- Pin model/runtime/dependency versions, verify hashes and licenses, audit SDKs for hidden network egress.
- Keep secrets, signing keys and tokens out of model context, source files, logs and generated archives.
- Memory and project history stay app-private; Android cloud backup/device transfer remains disabled unless a future explicit encrypted local-export flow is implemented.

## Device feasibility and honest capability labels

The target device is OPPO Find X6 Pro, but actual free RAM, storage, ColorOS restrictions, thermal throttling and Android SDK compatibility must be measured on-device. Do not infer capacity from marketing specifications.

Every capability must show one of:
- Available offline on this phone
- Available after downloading a local model/toolchain
- Available but resource-limited / slow
- Requires user permission or system-mediated action
- Online-only, with explicit data disclosure and approval
- Not supported on this device/runtime yet

A feature is not "implemented" merely because its screen or mockup exists. Mark it implemented only after a real end-to-end test and artifact/output verification.

## Phased delivery

### Studio P0 — architecture and device benchmark
- Inventory device/API/ABI/storage/RAM and existing project build pipeline.
- Add task graph, workspace, artifact manifest, model/runtime registry and resource budget contracts.
- Establish no-egress test harness and local-only network policy.
- Benchmark one small Qwen checkpoint and one code task; record RAM, latency, thermal behavior and Russian quality.
- Produce runtime/license matrix for Python, Android SDK/Gradle, image generation and game engines.

### Studio P1 — local coding and Python vertical slice
- Project explorer, safe file reader/editor, diff/snapshot/rollback.
- Embedded Python runtime with isolated execution, timeout, tests and export.
- Qwen code assistant for explanation, generation, debugging and tests.
- Demonstrate a real Python project created, run, tested and exported fully offline.

### Studio P2 — Android APK factory
- Android template generator, Kotlin/Compose generation, SDK/Gradle manager and local APK build worker.
- Build and install a sample APK on OPPO with explicit user action.
- Produce reproducible artifact/hash and smoke-test report.

### Studio P3 — image and creative studio
- Local vision understanding and OCR.
- Evaluate and integrate the best feasible local image generation/editing runtime.
- Image gallery, prompt presets, inpainting/upscaling if supported, export and quality benchmark.
- Poetry/story/writing workspace; audio tools separated by actual runtime capability.

### Studio P4 — game factory
- Compose a simple playable 2D game end-to-end, preview, test and package.
- Add richer engine support only after device/toolchain benchmark.

### Studio P5 — learning and advanced orchestration
- Connect explicit local correction memory to the planner/code workflow with user controls.
- Add locally evaluated skill improvements and regression/rollback.
- Investigate local model fine-tuning only if device compute, storage and thermal budget genuinely support it; otherwise explain limits and retain retrieval/rule learning without pretending it trains model weights.

## Acceptance tests (minimum)

1. Airplane-mode: local conversation, project reading, Python execution, poetry writing and supported local tools work without network.
2. Python: create a script, run it, test success and failure paths, enforce timeout and file/network sandbox.
3. Code: inspect a supplied project, propose a diff, apply it to a snapshot, run tests and rollback a deliberate failure.
4. APK: generate a minimal app, run Gradle build locally, verify APK exists/hash, install only after user confirmation, launch and smoke-test.
5. Image: generate an actual image file with a local model, inspect dimensions and verify no network egress; distinguish image understanding from image generation.
6. Game: create a genuinely playable build with working input, state changes and restart; not a static mockup.
7. Privacy: packet/network inspection confirms no prompts, project files, memory, images, audio or code leave the phone in local mode.
8. Online exception: explicit user-approved web query sends only the disclosed query and no private context.
9. Resource controls: model download cancellation/resume, disk-space checks, worker timeout/cancellation, thermal-aware pause and clear failure reporting.
10. License/provenance: every bundled/downloaded model, runtime and engine has recorded source, version, license and checksum.
11. Learning: default-off, local-only, inspect/edit/delete, regression tests and no autonomous safety-policy changes.
12. UX: user can understand progress, errors, required permissions, output files and next step without opening GitHub or a terminal.

## Non-goals and explicit cautions

- Do not promise desktop-class image generation, large-model reasoning or rapid Android builds until measured on the actual phone.
- Do not bundle a multi-gigabyte model into the APK.
- Do not use an online API as a silent fallback.
- Do not equate text/code generation with successful execution or a playable app.
- Do not equate vision-language understanding with image generation.
- Do not claim a Python package works on Android until its wheel/native dependencies are tested for the target ABI.
- Do not claim a game or app is complete until it runs on device and passes its acceptance tests.
