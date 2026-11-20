# Jarvis — open-source capability research and integration roadmap

Date: 2026-11-04
Repository: `sokfrut-tech/Gemeni`
Target: Android / OPPO Find X6 Pro / Chinese ColorOS
Status: research and architecture proposal; proposed capabilities are not claimed as implemented.

## Executive decision

Do not transplant a desktop assistant wholesale. Build an Android-native, local-first assistant around the existing Kotlin/Compose + Sherpa-ONNX + Piper/VITS + JNI/GLES2 direction. Reuse ideas and compatible libraries only after checking each upstream license, maintenance status, Android support, dependency footprint and security model.

## Repositories reviewed

| Project | Useful ideas | Reuse decision |
|---|---|---|
| [Personal-Jarvis](https://github.com/MaurizioCasciano/Personal-Jarvis) | Persistent agents, per-agent tools/memory/schedules, MCP/plugins, task traces, risk checks, local-first provider selection | Adopt agent/task orchestration and approval/audit patterns. Desktop runtime is not an Android drop-in. Check license and dependencies before copying code. |
| [isair/jarvis](https://github.com/isair/jarvis) | Local speech/model choices, conversational context, memory viewer, MCP tools, task centre, privacy boundaries | Adopt offline-first and memory patterns. Upstream is desktop-first and says no mobile app; do not port wholesale. |
| [open-jarvis/OpenJarvis](https://github.com/open-jarvis/OpenJarvis) | Local models by default, cloud optional, local API boundary, model management | Adopt provider abstraction and offline-first routing. Validate Android runtime and RAM before selecting models. |
| [Kirosoft/Thoth](https://github.com/Kirosoft/Thoth) | Typed knowledge graph, workflows, scheduled triggers, approval steps, sandboxed plugins/MCP, multi-channel adapters | Adopt workflow and capability-permission concepts; Python/desktop stack is not suitable for direct Android embedding. |
| [hasibjaan/jarvis](https://github.com/hasibjaan/jarvis) | Lightweight orb, explicit assistant states, audio-reactive visualization | Adopt visual-state contract and low-cost rendering principles; no Python/PySide port. |
| [Dodaios/Dodaios](https://github.com/Dodaios/Dodaios) | Central tool supervisor, risk tiers, exact-call approvals, audit traces | Adopt as a security architecture pattern, not as a bundled runtime. |

## Target capabilities

### P0 — reliable core

1. **One voice lifecycle/state machine:** IDLE, WAKE_WORD, LISTENING, TRANSCRIBING, UNDERSTANDING, EXECUTING, SPEAKING, WAITING_FOR_APPROVAL, ERROR. UI/orb, notification, logs and tests consume the same state.
2. **Real audio pipeline:** KWS (where supported) → VAD → AudioRecord → Sherpa-ONNX ASR → intent router → tool registry → action result → Piper/VITS TTS. Every stage exposes timeout, cancellation, error and diagnostics.
3. **Foreground microphone service:** correct Android foreground-service type/notification, runtime permissions, lifecycle recovery, battery policy and ColorOS background restrictions. No covert always-on recording.
4. **Typed tool registry:** stable tool ID, argument schema/validation, capability requirements, risk class, timeout, cancellation, result schema and audit record.
5. **Action safety gate:** SAFE / MONITOR / ASK / BLOCK. Sending messages, calls, changing settings, deleting files, purchases or sharing location require clear confirmation or are blocked. Approval is bound to exact action + arguments, expires, and cannot be reused.
6. **Offline-first model router:** local ASR/TTS and basic commands work without internet. Cloud Gemini/OpenAI fallback is opt-in, visible, bounded by cost/time limits, and never silently sends private content.
7. **Truthful UI:** orb reflects actual states and measured mic/TTS levels. Errors and offline mode are visible; no fake waveform or fake working state.

### P1 — useful Android tools

Build separate, testable adapters behind the registry:

- App launcher: resolve installed apps and open by package/label; allowlist and disambiguation.
- Navigation/search: Android intents to the chosen maps/search app; show destination/query before launch.
- Phone/SMS: ACTION_DIAL and draft SMS first; direct call/send requires explicit permission and confirmation.
- Alarms/timers/calendar: supported intents/Calendar APIs; confirm writes and respect timezone.
- Device controls: flashlight, volume, brightness, Wi-Fi/Bluetooth settings only through public supported APIs/intents. Do not promise privileged toggles Android/ColorOS does not expose.
- Files: scoped-storage picker and user-selected directories only.
- Notifications: read/respond only with explicit Notification Listener permission, per-app allowlist and privacy explanation.
- Clipboard/dictation: user-initiated session, preview before paste, no background scraping.
- Web lookup and translation: separate online tools with source URLs, timeout and offline messaging; local translation where practical.
- Status panel: network, battery, storage, microphone, service and model readiness with diagnostics.

### P2 — memory, agents and routines

- Local encrypted conversation store with visible history, retention controls, export and delete.
- Searchable personal knowledge index; distinguish user facts, inferred context and temporary session context. Never store credentials.
- WorkManager routines for reminders and bounded scheduled jobs; respect Android background limits.
- Multi-agent delegation only after task contracts and tool permissions exist. Each agent receives only required context and tools.
- Optional MCP client behind per-server/per-tool permissions. Never auto-trust servers or install arbitrary plugins.
- Optional Telegram/other remote channel only after pairing, strong authentication, allowlisted users, replay protection and confirmation routing.

### P3 — optional integrations

- ElevenLabs or other cloud TTS only as an optional provider; local Piper/VITS remains default. Keys stay in Android Keystore-backed storage, never Git.
- Gemini/OpenAI/local OpenAI-compatible provider adapters; per-task selection and transparent data egress.
- Smart-home/Home Assistant adapter after network trust and pairing design.
- Browser automation is not a default Android capability; prefer narrow intents or separately permissioned accessibility workflows.

## Proposed architecture

```
Compose UI + Orb + notification
          |
JarvisStateStore (single source of truth)
          |
VoiceForegroundService ---- AudioRecord / VAD / KWS
          |                         |
          +---- SpeechPipeline ------+---- Sherpa ASR
          |                                Piper/VITS TTS
          |
ConversationOrchestrator
          |
LocalIntentRouter -> ModelRouter (local first, cloud opt-in)
          |
ToolRegistry -> SafetyPolicyEngine -> Approval UI
          |
Android adapters / Web adapter / MCP adapters
          |
Result + AuditLog + LocalMemory
```

Keep UI, speech, orchestration, tool adapters, permissions and native renderer separated. Never let an LLM call Android APIs directly: all actions pass through typed tools and the safety gate.

## First implementation sequence

1. Audit actual source/tests against P0; label each item IMPLEMENTED / PARTIAL / PLACEHOLDER / NOT PRESENT with file and test evidence.
2. Make the state machine the shared contract for Activity, foreground service, orb and diagnostics.
3. Build minimal typed ToolRegistry and SafetyPolicyEngine; implement two real tools first (open app, web/search intent) with tests.
4. Connect the speech pipeline and verify real captured Russian audio end-to-end.
5. Add confirmation-gated dial/SMS/navigation adapters.
6. Add local memory and WorkManager routines after retention/security review.
7. Add MCP/provider plugins only after sandboxing, permissions, redaction and audit are tested.

## Acceptance tests

- Airplane mode: installed local ASR/TTS and basic tools work; online tools report offline without hanging.
- Russian speech: captured audio is transcribed and routed; a loading label alone never counts as success.
- Tool safety: dangerous calls cannot execute without exact-call approval; denied/expired approvals fail closed.
- Cancellation: stop/interrupt cancels ASR, model requests and tool work where possible.
- Service lifecycle: screen off, process recreation, permission revocation, app upgrade and ColorOS background restrictions are tested.
- Privacy: cloud fallback is off until enabled; user sees what data leaves device; secrets never appear in logs or repository.
- Memory: local, searchable, exportable and deletable; retention policy tested.
- Visual: orb states match real pipeline state; measured audio amplitude only; rendering does not starve audio.
- Physical device: install and test on OPPO Find X6 Pro / Chinese ColorOS before calling the project ready.

## Security and licensing rules

- Treat upstream repositories as references, not trusted code.
- Before copying code, record exact upstream commit, license, notices, dependencies and modifications.
- Prefer small adapters or clean-room protocol-compatible implementations where licenses/platform compatibility are unclear.
- No API keys, tokens, private data or credentials in source, issues, logs or CI artifacts.
- No silent calls, hidden recording, unapproved messaging/calls or unrestricted AccessibilityService actions.
- Google Play publication is out of scope.

## Current verified boundary

Open PR #55 changes the GLES2 orb renderer and adds shader compile/link logging. Its own brief says full IDLE/LISTENING/PROCESSING/SPEAKING/ERROR mapping and measured audio amplitude are not yet connected. PR #55 is visual work, not implementation of the capabilities above. Physical OPPO validation remains outstanding.

## Sources

- https://github.com/MaurizioCasciano/Personal-Jarvis
- https://github.com/isair/jarvis
- https://github.com/open-jarvis/OpenJarvis
- https://github.com/Kirosoft/Thoth
- https://github.com/hasibjaan/jarvis
- https://github.com/Dodaios/Dodaios
- https://developer.android.com/develop/background-work/services/foreground-services
- https://developer.android.com/develop/ui/views/notifications/notification-listener
- https://developer.android.com/privacy-and-security/keystore
