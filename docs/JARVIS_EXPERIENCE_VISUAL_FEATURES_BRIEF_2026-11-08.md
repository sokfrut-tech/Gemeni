# Jarvis Experience — visual and delightful features brief

Date: 2026-11-08
Status: approved direction from user; design/implementation brief only
Scope: Android app experience for OPPO Find X6 Pro / ColorOS
Baseline inspected: `MainActivity.kt`, `JarvisRuntimeController.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` on `main`.

## Product principle

Jarvis must be both dependable and delightful. Visual polish is not a substitute for real behavior: every control must represent a working capability or be clearly marked as a preview. Do not add decorative controls that imply unsupported functionality.

## Existing baseline and gaps

The current home screen already contains a dark Compose surface, an `AlphaVideoSurfaceView` sphere, mascot, microphone FAB, three quick-command labels, status text, and settings shortcut. The runtime currently supports a short one-shot recording, local ASR, a small intent dispatcher, a few Android intents, and TTS. The screen uses a fixed vertical spacer and does not yet expose a real persistent listening session, conversation history, settings model, or working device dashboard. Treat these as gaps, not implemented features.

## Experience pillars

### 1. Living Jarvis core
- Retain the existing sphere renderer as the visual centerpiece; evolve it into a state-driven animation.
- Define explicit visual states: idle, permission-needed, preparing-model, listening, recognizing, thinking, executing, speaking, interrupted-by-call, thermally-paused, error.
- Transitions should be smooth, low-power, and driven by runtime state, not guessed from status strings.
- Provide reduced-motion and low-power modes. Animation must slow or stop when backgrounded, screen-off, or thermally constrained.

### 2. Voice interaction
- Show a clear microphone state and a visible, accessible stop control whenever capture is active.
- Add a live waveform only when actual audio level data is available; never fabricate activity.
- Show recognized speech and Jarvis response as readable conversation turns.
- Offer push-to-talk as the dependable baseline. Persistent wake-word listening is a separate opt-in capability, with explicit consent and Android/ColorOS-compliant foreground visibility.
- Phone calls and emergency/system audio have priority: suspend capture, release the microphone, never inspect or transcribe call audio, and do not silently resume.

### 3. Useful visual cards
Create reusable Compose cards backed by real typed results:
- weather and forecast;
- maps/navigation and route preview;
- calculations, conversions, and charts;
- device status (battery, thermal state, network status, storage);
- reminders and task progress;
- search/research summaries with source links;
- files and recent actions.
Cards must show freshness/source where applicable, loading/empty/error states, and a clear action. Do not claim live data when offline or stale.

### 4. Personal command center
- Home: Jarvis core, conversation, microphone, a few context-aware shortcuts.
- Devices: battery/thermal/network/storage diagnostics with read-only status first.
- Skills: searchable catalog with enabled/disabled state and permissions explained.
- Memory: user-visible entries with provenance, confidence, expiry, and delete controls; sensitive data is opt-in and locally protected.
- Activity: recent commands, outcomes, and reversible actions; redact secrets and private audio.
- Settings: voice, language, appearance, animation intensity, privacy, model management, and battery policy.

### 5. Personality and accessibility
- Natural Russian voice with adjustable speech rate and selectable installed voices; no promise of voice cloning.
- Short, context-aware acknowledgements; avoid chatter during navigation, calls, or focus mode.
- Large touch targets, readable contrast, scalable text, TalkBack labels, one-hand reach, and landscape/foldable-safe layouts.
- Dark-first visual language with optional light theme and accent presets. Keep a restrained sci-fi identity rather than cluttered neon decoration.

## Visual direction

- Premium dark graphite base, restrained cyan/ice-blue highlights, soft depth, glass-like cards used sparingly.
- Strong hierarchy: core animation first, current state second, actionable content third.
- Responsive Compose layouts; no fixed spacer that pushes content off-screen on different devices.
- Motion should communicate state, not merely decorate. Keep a static fallback if GPU/rendering resources are constrained.

## Architecture and implementation order

1. **State contract:** introduce a typed UI state model (sealed states/data classes) owned by the runtime/controller. UI must not infer state by parsing Russian status strings.
2. **Responsive home screen:** replace fixed spacing with adaptive layout; extract core, status, conversation, and shortcut composables.
3. **Voice lifecycle controls:** start/stop and interruption state surfaced accurately; do not add always-on KWS until the single-owner microphone and call-priority work is integrated and tested.
4. **Card framework:** typed result models, loading/error/empty states, and source/freshness metadata.
5. **Command center tabs:** add only after each tab has a real data source or explicitly labelled empty state.
6. **Themes, motion, accessibility:** add reduced-motion/low-power modes and UI tests.

## Non-functional requirements

- Preserve local/offline-first behavior; cloud calls are optional, visible, and consent-based.
- Never download large models without explicit user approval.
- No microphone capture without clear permission and visible user intent.
- Severe/unknown thermal conditions suspend expensive animation/inference/listening according to the thermal policy.
- UI must not block the main thread; model loading and inference remain off-main.
- No secrets, raw audio, or private transcripts in logs/analytics.
- Support the repository's current minSdk 29 unless a deliberate compatibility decision is documented.

## Acceptance criteria for the first implementation slice

- Home screen adapts to short/tall screens and landscape without fixed spacer overflow.
- Sphere animation reflects typed runtime states and has a static low-power fallback.
- Microphone capture has an obvious active indicator and stop action; the UI never claims listening when capture is inactive.
- Conversation shows actual recognized text and actual spoken response, with clear failure states.
- Quick actions are either genuinely implemented and tested or not shown as active controls.
- Compose UI tests cover idle, listening, processing, error, call interruption, and thermal pause states.
- Android build, unit tests, UI smoke tests, CodeQL, and secret scanning are green before merge.
- Physical OPPO Find X6 Pro validation is required before claiming device-ready behavior.

## Explicit non-goals for this brief

- No runtime code changes.
- No always-on microphone implementation.
- No fabricated weather/network/device values.
- No unreviewed accessibility automation or automatic tapping.
- No merge, release, or APK publication.
