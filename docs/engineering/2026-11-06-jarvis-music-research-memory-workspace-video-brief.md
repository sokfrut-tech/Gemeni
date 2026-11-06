# Jarvis multimodal capabilities: music, research, memory, workspace, Alice-style assistance and video

Date: 2026-11-06
Status: Product requirements approved for planning; implementation not yet delivered.
Target: Jarvis on OPPO Find X6 Pro / Android, local-first Qwen core.

## Product decision

Add the requested capability families to Jarvis as first-class native modules behind one Russian-language assistant interface. Use Suno, Perplexity, Superpower, OPPO Breeno Memory and Yandex Alice AI as capability references, not as dependencies or code/assets to copy. The goal is to build equivalent useful workflows where feasible, with local/offline operation as the default.

Do not claim proprietary model parity. Do not require paid subscriptions or external accounts for the local core. Optional external providers may be connected only as explicit, separately disclosed user choices. No prompts, voice, video, photos, private memory, project files or generated assets may be transmitted without the user's explicit per-task approval.

## 1. Music studio — Suno-inspired

A dedicated music workspace, distinct from speech/TTS:
- Generate original songs from Russian or other-language text descriptions; support genre, mood, tempo/BPM, key, instrumentation, structure, lyrics and duration controls.
- Lyrics workspace: write, rhyme, syllable/rhythm assistance, verse/chorus/bridge structure, alternate lines and translation.
- Create instrumental tracks, songs with vocals, short loops, sound effects and ambience using specialist audio-generation models when a suitable local runtime exists.
- Import audio/voice memo, trim and arrange clips, loop, normalize, fade, adjust gain, pitch and tempo; waveform editing.
- Separate vocals/instruments into stems when local model/runtime supports it; preview, mute/solo, reorder, remix and export stems.
- Cover/remix workflow: use user-owned or licensed source audio, preserve a melody only when rights allow; transform arrangement/genre and create variations.
- Reusable local project presets/personas for a consistent fictional/original artist voice and sound. Voice cloning must be limited to the user's own voice or voices with documented consent; never imitate a real person without consent.
- Export WAV and MP3 (encoder/license permitting), project metadata and editable stems; all outputs stored locally.
- Provide a truthful runtime label: local model ready / download needed / too slow for device / optional online provider. Do not silently send audio to Suno or any service.

Implementation stages:
1. Native audio project model, waveform, trim/mix/export and lyrics editor.
2. Benchmark compact open audio/music models on arm64 Snapdragon 8 Gen 2; record RAM, thermal, generation speed, quality, license and commercial-use constraints.
3. Integrate one model only after end-to-end offline test; add stems and advanced generation as separate model capabilities.
4. Optional Suno connector/browser handoff only if user explicitly chooses it; no credentials stored in source or model context.

## 2. Research and answer engine — Perplexity-inspired

Add a Research mode that differs from ordinary chat:
- Search current public web sources, gather multiple independent sources, deduplicate, compare claims and produce cited answers.
- Research plans with visible stages: question decomposition, source discovery, evidence extraction, contradiction checks, synthesis and report.
- Modes: quick cited answer, multi-step Pro-style research, long Deep Research report, technical/library research and current-news monitoring.
- Source cards with title, publisher, date, URL, accessed time and exact claim support; distinguish primary sources, official documentation, academic work and commentary.
- Citation verification: every factual claim that relies on web results links to a source; mark unsupported or conflicting claims rather than inventing certainty.
- File research: analyze user-selected PDFs, office docs, source archives, audio/video transcripts locally. Never upload files to web search.
- Research Spaces: local project-specific source collections, instructions, notes, files and report history.
- Generate deliverables: Markdown, PDF, DOCX, CSV/XLSX and presentations where supported; preserve citations and source list.
- Search network boundary: only send the explicit search query and necessary public context. Never append chat history, memory, private files, location, credentials or project source automatically.
- Online search is opt-in per task; show what query leaves the device. Offline research can search local documents and cached sources only.

## 3. Memory — Breeno Memory / Alice My Memory-inspired

A user-owned local memory system, not hidden surveillance:
- Capture a note, reminder, task, preference or fact by text or voice, after the user sees what will be saved.
- Convert natural-language requests into typed records: note, task, reminder, event, recurring routine, contact reference, project fact or preference.
- Ask for confirmation before saving sensitive or ambiguous details; allow "do not remember this" and "delete this".
- Search and recall by natural language, semantic similarity and exact filters; cite which local note/fact informed an answer.
- Time-aware reminders with clear due time/time zone, repeat rules, snooze, completion and notification permission.
- Contextual project memory: separate spaces for Jarvis, other projects, household tasks and general preferences; prevent cross-project leakage unless user authorizes it.
- Timeline and history: what was saved, edited, used, and when; show provenance and confidence.
- Import/export human-readable JSON/Markdown locally; encrypted export optional; app-private storage by default.
- Retention controls: pause memory, inspect, edit, export, clear selected category or clear all. Android backup and cloud sync remain off by default.
- Local retrieval augmentation can improve answers without updating model weights. Neural fine-tuning is a separate opt-in research path with evaluation and rollback; never claim retrieval is weight training.
- No microphone always-on recording or ambient surveillance. Voice capture is explicit or a clearly visible user-controlled wake-word feature.

## 4. AI workspace productivity — Superpower-inspired

Bring the useful workspace tools into Jarvis itself:
- Project folders/subfolders, tags, favorites, pinning, archive and bulk operations.
- Global search across conversations, notes, prompts, project files, image/audio/video assets, generated outputs and saved sources.
- Conversation map/tree with branches, bookmarks, highlights and annotations.
- Prompt library with variables, reusable instruction profiles, task templates and multi-step prompt chains.
- Message/task queue: queue, reorder, pause, edit and cancel requests while a long task is running.
- Reference prior conversations and project notes through explicit local selection; show exactly which context is attached.
- Image/audio/video gallery with filters, prompts, source project and provenance.
- Export selected messages, entire projects, assets and reports to Markdown, JSON, PDF, DOCX, images and audio where supported.
- Project snapshots, version history, diffs, restore and archive; no automatic destructive cleanup.
- All workspace indexes and history remain local; no extension or third-party cloud dependency.

## 5. General assistant and agent workflows — Alice AI-inspired

- Russian-first natural conversation with robust task understanding and follow-up context.
- Multimodal answers: text, generated/selected images, short video clips, maps/links, tables, charts and downloadable files where a real capability exists.
- Practical task agents: research, compare products, prepare a report, create reminders, organize files, draft messages, create a project, run tests and build APKs.
- Turn voice/text streams into structured task lists, shopping lists, plans, notes and reminders; user confirms consequential actions.
- Tool orchestration: break task into typed steps, choose specialist local model/tool, show progress, allow pause/cancel, verify output and summarize.
- Android actions must use the safe Jarvis tool gateway and Android system permission flows. No silent calls, purchases, messages, file sharing or settings changes.
- Optional Yandex Alice connector is not required; do not scrape private APIs or reuse Yandex account credentials.

## 6. Video studio — full first-class capability

Video is not just an image generator. Build a real editing and generation workflow:
- Import video from user-selected Android media/document picker; inspect metadata, codec, duration, resolution, frame rate, orientation, audio tracks and file size.
- Timeline editor with multiple video/audio/text/image tracks, trim, split, crop, rotate, reorder, speed, transitions, overlays, filters, volume, fade, keyframe basics and undo/redo.
- Storyboard and shot list; turn a script into scenes, shot descriptions, captions, narration and asset checklist.
- AI clip analysis: local speech transcription, scene/shot boundary detection, keyframe extraction, object/action summaries where supported, chaptering and searchable transcript with timestamps.
- Automatic subtitles: Russian and multilingual ASR, editable text/timing, SRT/VTT export, burned-in subtitle option.
- Smart editing: silence/filler detection, highlight extraction, beat alignment, reframing for 9:16/16:9/1:1, scene ordering and rough-cut suggestions; always keep human-editable timeline.
- Audio processing: denoise, normalize, ducking, voiceover, music bed and sound effects, all with preview and separate tracks.
- Text-to-video and image-to-video generation using specialist video models; animate still photos, generate short clips, extend or restyle clips only when a compatible model exists.
- Frame-level image editing and optional inpainting/outpainting; distinguish generated frames from original footage.
- Export MP4/H.264 baseline where device codecs support it, plus other formats only when tested; selectable resolution, FPS, bitrate and audio quality. Preserve source originals and export a new file.
- Mobile performance: proxy/low-resolution previews, background render worker, progress, cancel/resume where possible, thermal-aware pause, storage estimates and cleanup of temporary files.
- Local-first guarantee: video/audio/images stay on device. Cloud video generation or translation is a separately approved exception that discloses exact media and provider.
- Rights/provenance: use user-owned/authorized footage, music, voices and likenesses; label synthetic media and preserve model/license metadata.

## Architecture additions

Add specialist capability interfaces to the existing Jarvis task graph:
- `MusicGenerationProvider`, `AudioEditEngine`, `StemSeparationProvider`
- `WebResearchProvider`, `CitationVerifier`, `LocalDocumentSearchProvider`
- `LocalMemoryRepository`, `MemoryConsentPolicy`, `ReminderScheduler`
- `WorkspaceIndex`, `PromptLibrary`, `ConversationGraphRepository`, `TaskQueue`
- `VideoImportPipeline`, `VideoTimelineProject`, `VideoAnalysisProvider`, `SubtitleEngine`, `VideoRenderEngine`, `VideoGenerationProvider`

Each provider declares: local/online, model/runtime/license, supported input/output formats, ABI, memory/storage budget, latency estimate, permissions, network endpoints, cancellation and test coverage. Capability broker enforces the no-egress default. UI must never show a capability as available until an actual end-to-end acceptance test passes.

## Delivery order

P0 — data contracts and UX architecture:
- Add modules to capability registry and task graph; local workspace, memory and media project schemas.
- Define privacy boundaries, user consent and audit events; no new network endpoints.
- Add test fixtures for local memory, citation provenance, media metadata and project serialization.

P1 — local memory + workspace:
- Notes/tasks/reminders with user confirmation, search, project scopes, edit/delete/export.
- Folders, global search, saved prompts, task queue, conversation references and export.
- Demonstrate airplane-mode end-to-end.

P2 — video editing foundations:
- Media picker, metadata inspection, thumbnail/keyframe extraction, single/multitrack timeline, trim/split, audio, subtitles and MP4 export.
- Demonstrate editing a user-selected local clip completely offline on the target OPPO.

P3 — research:
- Local document search and citations first; optional explicit public web search adapter second.
- Multi-step report generation with verified citations and export.
- Packet-level tests prove private context/files are not sent in online mode.

P4 — music/audio:
- Lyrics, waveform, trim/mix/export and audio projects; benchmark local generation and stem models.
- Integrate only models that pass device, license and offline tests.

P5 — AI video generation and advanced editing:
- Benchmark image-to-video and text-to-video models for Snapdragon 8 Gen 2. Do not assume high-end desktop models run on phone.
- Add generation only after real clip output, playback, export and thermal/resource tests.
- Add AI rough-cut, reframing and enhancement iteratively.

P6 — multimodal agent orchestration:
- One task planner routes to memory, research, code, music, image, video and phone tools.
- Multi-step jobs have visible plan, checkpoints, progress, cancellation and artifact verification.

## Acceptance tests

1. Airplane mode: local memory search, project workspace, prompt queue, local video import/edit/subtitles/export and audio project editing function without network.
2. Memory: user explicitly saves a note, retrieves it, sees provenance, edits and deletes it; no network traffic or Android cloud backup.
3. Privacy: packet capture verifies no prompt, media, file, memory or source code egress in local mode.
4. Research: every source-based statement in a report has a working source reference; fabricated citations fail tests; online search sends only approved query.
5. Workspace: folder/search/pin/queue/branch/export works with a large local test dataset and survives process restart.
6. Video: import, metadata, thumbnail, trim/split, multi-track audio, subtitle edit and MP4 export; verify playable output and hash.
7. AI video: produce an actual video file from a text/image prompt with documented model, device metrics, license and no egress in local mode.
8. Music: generate or edit a real audio file; verify duration, codec, waveform and export; do not count TTS as music generation.
9. Resource safety: large file/storage warnings, cancellation, no ANR, background render, thermal pause and cleanup.
10. Provenance: each generated or transformed media item records model, version, prompt, source assets, license and export settings.

## Reference products and feature inspiration

These are research references for feature discovery, not permission to copy proprietary implementations or send user content to them:
- Suno product features: https://suno.com/products — Voices, stem splitting, covers, mashups, samples, Personas and Studio workflow.
- Suno Studio 2.0: https://help.suno.com/en/articles/13670529 — DAW-style editing, MIDI, effects, automation and generative sound.
- Perplexity Pro help: https://www.perplexity.ai/help-center/en/articles/10352901-what-is-perplexity-pro — Pro Search, Research, file analysis, model routing and generated deliverables.
- Perplexity overview: https://www.perplexity.ai/hub — web-grounded answers, citations and research workflows.
- Superpower ChatGPT: https://spchatgpt.com/ — folders, search, notes, highlights, prompt chains, queue, tree maps and export.
- Yandex Alice AI announcement: https://www.yandex.com/company/news/2025-10-28-01 — Russian-language task assistance, multimodal answers and My Memory direction.
- Yandex Alice photo animation: https://yandex.ru/company/news/16-09-2025-02 — image-to-video animation concept.
- CapCut AI video editing guide: https://www.capcut.com/resource/ai-video-editor-guide — timeline editing, AI captions, clip assistance and export.
- Android local video generation feasibility reference: https://github.com/AbrahamPaulJ/nightmare-mobile — example of on-device image/video diffusion on selected Snapdragon devices; compatibility, performance and licenses must be independently verified.

## Honest limitations

This is a requirements/architecture brief, not an implementation claim. No Suno/Perplexity/Alice/Superpower account or paid API is assumed. Some reference capabilities are cloud-backed or subscription-gated in their original products; Jarvis must provide local alternatives or clearly label a feature as unavailable rather than silently depending on those services. High-quality generative music and video may exceed this phone's practical memory/thermal budget; benchmark before committing.