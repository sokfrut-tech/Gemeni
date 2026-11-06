# Jarvis Phone Control — implementation design

Date: 2026-11-06
Status: Design baseline; implementation not yet verified.
Tracking issue: https://github.com/sokfrut-tech/Gemeni/issues/80

## Product goal

Jarvis should be a capable Russian-speaking personal assistant for OPPO/ColorOS: understand requests, plan multi-step work, use phone capabilities, browse the web, manage time and information, and play media. Phone control and the core assistant path must work locally where Android permits and must not require a paid API or cloud connection.

The voice/model layer may propose typed actions, but it must never receive Android Context, unrestricted Intents, shell access, or service handles.

## Existing baseline (inspected on 2026-11-06)

The current `AndroidJarvisToolRegistry` supports app launch, web search, navigation, time, and a confirmation-gated dialer intent. `ToolProposalCodec` has a strict tool/argument allowlist. This is a useful starting boundary, not a complete assistant. Do not claim calendar, alarm, browser automation, radio, music or phone-control features exist until code and tests prove them.

## Product capability map

The capability map is a product target, not a claim that these features are already implemented. Each capability must have an explicit status: implemented-and-tested, partially available, needs permission, user-mediated, online-only, unsupported, or planned.

### 1. Planner, calendar, tasks and reminders

- Natural-language capture: "remind me tomorrow at nine", "schedule a meeting next Tuesday", "what do I have today?"
- Local task and reminder store with categories, priorities, due dates, recurrence, completion state and search.
- Day/week agenda, upcoming reminders, overdue tasks, conflict detection and free-time suggestions.
- Calendar read/write through Android Calendar Provider only after clear permission onboarding; alternatively use system Calendar insert/view intents so the user can review and save without granting broad access.
- Import/export and backup should be explicit and user-controlled. Avoid silently uploading calendar contents.
- Multi-step planning: break a goal into tasks, propose a schedule, show conflicts, and ask before creating or moving multiple events.
- Use stable time-zone-aware date/time handling; clarify ambiguous dates, time zones and recurrence rules instead of guessing.

### 2. Alarm clock, timers and routines

- Create, list, edit and delete alarms through documented AlarmClock intents and supported APIs.
- Timers, multiple named timers, countdowns, recurring weekday alarms and snooze controls where the platform exposes them.
- Exact alarms require API/permission checks and, where needed, user-granted exact-alarm access. A scheduled intent being accepted is not proof that the alarm will ring; report platform result honestly.
- Use WorkManager for deferrable reminders, not as a substitute for precise alarm delivery.
- Routines/scenes (Night, Driving, Home) are composed from individually supported actions and must preview restricted or disruptive changes.

### 3. Internet surfing and browser assistant

- Search the web, open websites, navigate pages, summarize articles, compare multiple sources, extract user-requested facts and retain source links.
- Open pages in the user's chosen browser or an app-owned browser surface; support back/forward, tabs/history only within the surface Jarvis owns.
- Page interaction must use a strict allowlist and visible user intent. Never accept arbitrary JavaScript, arbitrary URLs from untrusted model output, credential entry, payment submission or destructive form submission without explicit user review.
- Distinguish search snippets from opened and verified page content; cite sources in answers and flag uncertainty.
- Online browsing is network-dependent. Provide offline help from local knowledge and saved pages when possible; never pretend offline browsing is live.

### 4. Music, local audio, internet radio and media

- Play/pause/next/previous, seek, volume, queue and playback status using Android MediaSession/MediaController and Media3 where appropriate.
- Index and play user-authorized local audio; respect Android scoped storage and media permissions.
- Search for artists/tracks and open supported music apps or user-selected streaming services. Service-specific playback requires their supported integration and account/access; do not bypass subscriptions, DRM or access controls.
- Internet radio: browse/search public station directories or user-supplied stream URLs, show station name/source, play supported streams, save favorites and recover from network loss. Validate schemes/hosts and avoid unsafe redirects.
- Background playback must use a compliant media foreground service, notification and media session; expose headset/Bluetooth controls when supported.
- Clearly distinguish local playback, a launched third-party app, and a verified active stream.

### 5. Voice, conversation and intelligence

- Russian speech-to-text, voice activity detection, spoken responses, wake-word path and a visible text fallback.
- Local-first reasoning runtime with a provider-neutral model interface; deterministic tools remain available even when no LLM/model is installed.
- Tool orchestration: understand intent, ask for missing essentials, form a bounded plan, execute one safe step at a time, observe results, recover from errors and summarize what actually happened.
- Multi-step tasks may combine web search, calendar, reminders, navigation, media and phone actions, but each tool call is separately schema-validated and policy-checked.
- Short-term conversation context and user-approved long-term preferences; provide inspect/edit/clear controls. Do not store sensitive content by default.
- Optional online model fallback only when the user enables it; core actions must not depend on paid APIs. Never send private calendar, contacts, messages or notification content to a remote provider by default.

### 6. Everyday assistant tools

- Weather, news, time, unit/currency conversion, maps/navigation, notes, lists, contacts, calls and message composition.
- Device diagnostics: battery/charging, storage, memory, connectivity and supported thermal information.
- Notifications only after explicit notification-listener permission, with clear explanation and revocation.
- Contacts, calls and messages use Android permission and confirmation boundaries; no silent calls or sending messages.

## Architecture

### Typed contracts and tool orchestration

Introduce a closed set of domain-level contracts, independent of Android framework classes:

- `JarvisCapability`: stable ID, display name, read/write mode, minimum API, permission requirements, network requirement, risk tier, availability reason.
- `JarvisToolRequest`: exact tool ID and typed/validated arguments; never arbitrary class names, component names, shell strings, raw Intent data or executable code from a model.
- `JarvisToolResult`: success, needs-user-action, denied, unsupported, cancelled, failed, plus optional observed postcondition and source references.
- `ApprovalRequirement`: none, user-mediated system UI, explicit in-app confirmation, unavailable.
- `JarvisPlanStep`: bounded step, dependencies, timeout, retry policy and cancellation behavior.
- `JarvisStateSnapshot`: only safe, explicitly permitted state fields.

Keep provider -> intent/plan validation -> policy -> orchestrator -> capability gateway -> adapter. A model response is untrusted input and must pass exact schema and policy checks. Tools return structured results; the model cannot mark its own action successful.

### Capability adapter boundaries

1. Planner/calendar: local task repository; Calendar Provider adapter or system Calendar intents; time-zone-safe scheduler.
2. Alarm/timer: AlarmClock intents and exact-alarm permission-aware adapter.
3. Web: search provider boundary, safe URL policy, browser surface/intent adapter and source extraction.
4. Media: Media3 playback service, MediaSession integration, local media adapter and internet-radio stream adapter.
5. Voice: ASR/VAD/TTS and wake-word components behind replaceable interfaces.
6. Device diagnostics, audio, display, torch, app launch, settings and connectivity adapters.
7. Notifications, telephony/messaging and accessibility only with explicit user-granted special access.
8. Maps/navigation and everyday utilities through supported intents or provider interfaces.

## Phone Control adapter details

Implement adapters independently so unsupported actions can be reported without blocking other capabilities:

1. Device diagnostics: battery/charging, storage, memory and connectivity snapshot; temperature only where a supported public API provides meaningful data.
2. Audio: read/set stream volume and ringer mode only where API and permission allow.
3. Display: brightness read/write with required settings permission; use user-facing settings flow when permission is absent. Do not promise lock/unlock or wake behavior outside documented APIs.
4. Flashlight: CameraManager torch API with availability/error handling and observable state where possible.
5. App navigation: launch an installed app by a disambiguated user-visible label; open a specific settings screen via allowlisted settings actions.
6. Connectivity: inspect state where permitted; for restricted Wi-Fi/Bluetooth toggles open supported system panels/settings and return NEEDS_USER_ACTION. Never claim a toggle succeeded merely because a panel opened.
7. Media and alarms: use public media-session/MediaController or documented intents and AlarmClock intents; do not inspect or control unrelated apps beyond granted platform access.
8. Notifications: only after the user explicitly enables notification-listener access; expose a clear revocation path and limit actions to supported notification actions.
9. Telephony/messaging: ACTION_DIAL and ACTION_SENDTO/SMS compose flows with preview and user confirmation. Never silently place calls or send messages.
10. Accessibility: optional explicit user-enabled service; narrowly scoped UI actions, visible feedback, no credential capture, hidden surveillance or security bypass.

## Risk policy

- READ_ONLY: diagnostics, calendar/task reads with permission, web search and media status.
- REVERSIBLE: volume, brightness, torch, playback controls and edits to local tasks.
- USER_MEDIATED: system panels, permissions, settings screens, app selection, opening a website or third-party media app.
- EXPLICIT_APPROVAL: creating/updating/deleting calendar events, sending messages, notification replies, sharing data, purchases, destructive operations or privacy/security-sensitive actions.
- DENY: silent communication, arbitrary shell/root commands, bypassing platform restrictions, hidden monitoring, credential collection, DRM/access-control circumvention.

Every result must distinguish ACTION_STARTED from STATE_VERIFIED. Only return verified success after the adapter observes the expected postcondition; otherwise say that the user must finish the action or that the state could not be verified.

## Android and ColorOS compatibility

- Verified on `main` on 2026-11-06: `app/build.gradle.kts` declares `minSdk = 29` and `targetSdk = 35`. This build does NOT support Android 7/API 24. Do not claim Android 7 support unless minSdk is deliberately lowered and all dependencies/native ABIs are proven compatible.
- Android 10+ restricts ordinary apps from directly toggling Wi-Fi; use supported system UI such as the Wi-Fi panel where appropriate.
- Android 13+ restricts ordinary apps from enabling/disabling Bluetooth; use supported system UI.
- Exact alarms, calendar, media, notification listener, write-settings, accessibility and role/default-handler status require explicit user-facing setup and execution-time checks.
- ColorOS background execution, autostart, battery optimization and voice-assistant role behavior require physical-device verification on the target OPPO Find X6 Pro and exact ColorOS build.
- Never request device administrator, root or broad package visibility unless a concrete feature requires it and a separate security review approves it.

## UI and user experience

- A polished Russian assistant home screen with clear voice/text entry, current task/plan, active media and next reminder.
- Capability pages for Phone, Planner, Calendar, Alarms, Browser, Music/Radio, Voice and Memory showing available, permission-needed, user-mediated, online-only and unsupported states.
- Before enabling special access, explain what it grants and how to revoke it.
- For each command show: what Jarvis understood, the proposed plan, what will happen, whether Android/network/user interaction is required, and how success will be verified.
- Provide Cancel, pause/resume and Settings/Grant access actions. Do not silently retry denied or failed actions.
- Add a local action history with timestamp, requested action, result category and verification outcome; avoid storing sensitive message content, phone numbers, calendar descriptions or notification text by default.

## Delivery phases

### P0 — foundation and capability truth
- Audit Gradle minSdk/targetSdk, manifest, existing service classes and current gateway.
- Add typed capability/action/result/plan contracts and a deterministic capability registry.
- Add capability matrix with API, permission, network and verification requirements.
- Add tests for allowlist rejection, missing permissions, unsupported API, user cancellation, approval rejection and false-success regression.

### P1 — useful offline core
- Implement read-only device diagnostics, volume, torch, app launch and allowlisted settings navigation.
- Implement local notes, tasks, reminders and timer/alarm creation using supported Android APIs.
- Add capability status UI, Russian voice/text interaction and permission explanations.
- Verify on emulator and physical OPPO; exact alarm behavior must be tested on device.

### P2 — calendar, media and web
- Calendar read/write with explicit permission or system Calendar intents, event conflict checks and review-before-save.
- Media3 local audio playback, MediaSession controls and public internet-radio playback/favorites.
- Web search, safe browser opening/navigation and source-backed page summaries.
- Add offline/online state awareness and graceful network-loss recovery.

### P3 — advanced intelligence and integrations
- Bounded multi-step planner with tool orchestration, cancellation, retries and observed postconditions.
- Optional notification listener and narrowly scoped accessibility actions.
- Dial/SMS composition with preview and explicit confirmation.
- User-defined scenes (Night, Driving, Home) composed only of individually supported actions.
- Optional online model fallback only with user opt-in; no paid dependency in core.

### P4 — refinement and expansion
- Better Russian conversational understanding, memory controls, personalized routines and proactive suggestions with user-configurable limits.
- Additional utility integrations and compatibility improvements based on measured OPPO device testing.
- Performance, battery, privacy and reliability tuning; no autonomous background actions that surprise the user.

## Acceptance gates

1. Every tool has an exact schema, capability entry, risk policy and permission/network declaration.
2. No arbitrary Intent, URI, component, command string, JavaScript or code is accepted from a model.
3. Permission and API-level checks happen at execution time, not only setup.
4. Unsupported, offline and restricted actions return honest, actionable explanations.
5. Success is not reported until the expected state is observed; if observation is impossible, return an unverified outcome.
6. Tests cover allowlist, policy, permission denial, cancellation, API-level restrictions, time-zone/recurrence behavior, network loss and false success.
7. CI builds and relevant tests pass on the exact PR head.
8. Physical OPPO Find X6 Pro acceptance is documented separately from emulator results.
9. No paid/cloud dependency is required for core offline features.
10. Calendar, alarms, browser, local music and internet radio each have an end-to-end acceptance test before being marked implemented.

## Source audit findings (2026-11-06)

- `app/build.gradle.kts`: `minSdk = 29`, `targetSdk = 35`, compileSdk 35. Android 7/API 24 is currently out of support. Any backward-compatibility effort is a separate engineering task with dependency and native-runtime verification.
- `JarvisAccessibilityService` is currently an empty service. Its XML config requests all accessibility event types and window-content retrieval; this is broader than needed for an empty implementation and should be reduced before adding behavior.
- `JarvisVoiceInteractionService` currently only overrides lifecycle callbacks; it does not yet implement a full assistant session workflow.

## Immediate next implementation step

After PR #78's currently running CI finishes, create a focused implementation PR from the reconciled integration base (do not merge the open PR dependency chain blindly). Start with P0 typed contracts and a thin P1 offline vertical slice. Keep native LLM/JNI work independent so phone-control progress does not wait on model inference.
