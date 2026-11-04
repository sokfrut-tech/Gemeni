# Jarvis — open-source capability research and integration roadmap

Date: 2026-11-04
Repository: `sokfrut-tech/Gemeni`
Target: Android / OPPO Find X6 Pro / Chinese ColorOS
Status: research and architecture proposal; this document does not claim the proposed capabilities are implemented.

## Executive decision

Do not transplant a desktop assistant wholesale. Build an Android-native, local-first assistant around the existing Kotlin/Compose + Sherpa-ONNX + Piper/VITS + JNI/GLES2 direction. Reuse ideas and compatible libraries only after checking each upstream license, maintenance status, Android support, dependency footprint, and security model.

## Repositories reviewed

| Project | Useful ideas | Reuse decision |
|---|---|---|
| [Personal-Jarvis](https://github.com/MaurizioCasciano/Personal-Jarvis) | Persistent agents, per-agent tools/memory/schedules, MCP/plugin catalog, task traces, risk checks, local-first provider selection | Adopt architecture patterns for agent/task orchestration and approval/audit. Desktop UI/runtime is not an Android drop-in. Verify Apache-2.0 and each dependency before copying code. |
| [isair/jarvis](https://github.com/isair/jarvis) | Local speech/model choices, conversational context, memory viewer, MCP tools, task centre, privacy boundaries | Adopt offline-first and context/memory patterns. Upstream states there is no mobile app; do not port its desktop stack wholesale. |
| [open-jarvis/OpenJarvis](https://github.com/open-jarvis/OpenJarvis) | Local models by default, cloud optional, local API boundary, model management | Adopt provider abstraction and offline-first routing. Validate Android-compatible runtime and model RAM before selecting any model. |
| [Kirosoft/Thoth](https://github.com/Kirosoft/Thoth) | Typed knowledge graph, workflows, scheduled triggers, approval steps, sandboxed plugins/MCP, multi-channel adapters | Adopt workflow and capability-permission concepts; Python/desktop stack is not suitable for direct Android embedding. |
| [hasibjaan/jarvis](https://github.com/hasibjaan/jarvis) | Lightweight orb, explicit assistant states, audio-reactive visualization | Adopt the visual-state contract and low-cost rendering principles; no Python/PySide port. |
| [Dodaios/Dodaios](https://github.com/Dodaios/Dodaios) | Central tool supervisor, risk tiers, exact-call approvals, audit traces | Adopt as security architecture pattern, not as a bundled runtime. |

## What Jarvis should gain

### P0 — reliable core (must precede feature expansion)

1. **One voice lifecycle/state machine:** IDLE, WAKE_WORD, LISTENING, TRANSCRIBING, UNDERSTANDING, EXECUTING, SPEAKING, WAITING_FOR_APPROVAL, ERROR. The UI/orb, notification, logs and tests consume the same state source.
2. **Real audio pipeline:** KWS (where supported) → VAD → AudioRecord → Sherpa-ONNX ASR → intent router → tool registry → action result → Piper/VITS TTS. Every stage exposes timeout, cancellation, error and diagnostic events.
3. **Foreground microphone service:** explicit Android foreground-service type/notification, runtime permissions, lifecycle recovery, battery policy and ColorOS autostart/background restrictions documented. No covert always-on recording.
4. **Tool registry with typed contracts:** stable tool ID, schema/argument validation, capability requirements, risk class, timeout, cancellation, result schema and audit record.
5. **Action safety gate:** SAFE / MONITOR / ASK / BLOCK. Opening an app or public search can be low risk; sending messages, making calls, changing settings, deleting files, purchases or sharing location require a clear confirmation or must be blocked. Approval is for the exact action and arguments, expires, and cannot be reused.
6. **Offline-first model router:** local ASR/TTS and local intent/command handling remain available without internet. Cloud Gemini/OpenAI/other provider fallback is opt-in, visibly indicated, bounded by cost/time limits, and never silently sends private content.
7. **Truthful UI:** the orb only reflects actual states and measured mic/TTS level. Errors and offline mode are visible; no fake waveform or fake “working” state.

### P1 — useful Android tools

Implement as separate, testable adapters behind the registry:

- App launcher: resolve installed apps and open by package/label; safe allowlist and disambiguation.
- Navigation/search: Android intents to a chosen map/search app; show destination/query before launch.
- Phone/SMS: ACTION_DIAL and draft SMS first; direct call/send requires explicit permission and confirmation.
- Alarms/timers/calendar: use supported Android intents/Calendar APIs; confirm writes and respect exact timezone.
- Device controls: flashlight, volume, brightness, Wi-Fi/Bluetooth settings via supported public APIs/intents. Do not promise privileged toggles that Android/ColorOS does not expose.
- Files: scoped-storage picker and user-selected directories only; no broad hidden filesystem access.
- Notifications: read/respond only through explicit Notification Listener permission, with per-app allowlist and clear privacy explanation.
- Clipboard/dictation: user-initiated session, preview before paste, no background scraping.
- Web lookup: separate online tool with source URLs, timeout and offline message.
- Translation: local model first where practical; online provider optional.
- Status panel: network/battery/storage/microphone/service/model readiness with diagnostics.

### P2 — memory, agents and routines

- Local encrypted conversation store with user-visible history, retention controls, export and delete.
- Searchable personal knowledge index; distinguish user facts, inferred context and temporary session context. Never store credentials.
- WorkManager routines for reminders and bounded scheduled jobs; Android background limits respected.
- Multi-agent task delegation only after task contracts and tool permissions exist. Each agent receives only required context and tools.
- Optional MCP client behind per-server/per-tool permissions. Never auto-trust a server or install arbitrary plugins.
- Optional Telegram/other remote channel only after pairing, strong authentication, allowlisted users, replay protection and confirmation routing are designed.

### P3 — optional integrations

- Cloud TTS such as ElevenLabs only as an optional provider; local Piper/VITS remains default. Keys stored in Android Keystore-backed storage and never in Git.
- Provider adapters for Gemini/OpenAI/local OpenAI-compatible endpoints; per-task provider selection and transparent data egress.
- Smart-home/Home Assistant adapter after network trust and pairing design.
- Browser automation is not a default Android capability; use explicit, narrow Android intents or a separately permissioned accessibility workflow.

## Proposed Android architecture

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

Keep UI, speech, orchestration, tool adapters, permissions and native renderer in separate modules/packages. Do not let an LLM call Android APIs directly: all actions must pass through typed tools and the safety gate.

## First implementation sequence

1. Audit actual current source and tests against the P0 list; mark each item IMPLEMENTED / PARTIAL / PLACEHOLDER / NOT PRESENT with file and test evidence.
2. Make the state machine the shared contract for Activity, foreground service, orb and diagnostics.
3. Build a minimal typed ToolRegistry and SafetyPolicyEngine; port only two real tools first (open app, web/search intent) with tests.
4. Connect speech pipeline to that registry and verify real captured Russian audio end-to-end.
5. Add confirmation-gated dial/SMS/navigation adapters.
6. Add local memory and WorkManager routines after data retention/security review.
7. Add MCP/provider plugins only after sandboxing, permissions, redaction and audit are tested.

## Acceptance tests

- Airplane mode: installed local ASR/TTS and basic tools still work; online tools report offline without hanging.
- Russian speech: captured audio is transcribed and routed; a loading label alone never counts as success.
- Tool safety: dangerous tool calls cannot execute without exact-call approval; denied and expired approvals fail closed.
- Cancellation: stop/interrupt cancels ASR, model requests and tool work where possible.
- Service lifecycle: screen off, process recreation, permission revocation, app upgrade and ColorOS background restrictions are tested.
- Privacy: cloud fallback is off until enabled; user sees what data leaves device; secrets never appear in logs or repository.
- Memory: data is local, searchable, exportable and deletable; retention policy is tested.
- Visual: orb states match real pipeline state; measured audio amplitude only; rendering does not starve audio.
- Physical device: install and test on the user's OPPO Find X6 Pro / Chinese ColorOS before calling the project ready.

## Security and licensing rules

- Treat upstream repositories as references, not trusted code.
- Before copying any code, record the exact upstream commit, license, notices, dependencies and modifications.
- Prefer small clean-room adapters or protocol-compatible implementations where licenses or platform compatibility are unclear.
- No API keys, tokens, private user data or model credentials in source, issues, logs or CI artifacts.
- No silent calls, hidden recording, unapproved messaging/calls, or unrestricted AccessibilityService actions.
- No Play Store publication is in scope.

## Current verified boundary

The current open PR #55 changes the GLES2 orb renderer and adds shader compile/link logging. Its own brief explicitly says the Activity still exposes only a listening boolean and does not yet connect the full IDLE/LISTENING/PROCESSING/SPEAKING/ERROR lifecycle or measured audio amplitude. This PR is visual work, not implementation of the capabilities above. Physical OPPO validation remains outstanding.

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
