# Jarvis Android tool gateway — implementation checkpoint

Date: 2026-11-04
Branch: `feat/jarvis-tool-registry-safety-core-2026-11-04`
Base: orbital renderer PR #55 head `7dc7430dd2808e0bbefad2714b2be0ae77b8927c`

## Implemented in this branch

- Added `AssistantState` lifecycle values: IDLE, LISTENING, TRANSCRIBING, UNDERSTANDING, EXECUTING, SPEAKING, WAITING_FOR_APPROVAL and ERROR.
- Added a typed `ToolResult` result contract (Success / NeedsConfirmation / Failure).
- Added `AndroidJarvisToolRegistry` as a single gateway for voice commands and quick actions.
- Connected the captured Russian ASR transcript to the gateway rather than the old ad-hoc string dispatcher.
- Connected the three home-screen quick actions to the same gateway.
- Added visible state labels to the Compose assistant screen.

## Available actions now routed through the gateway

1. `открой <название приложения>` — searches launchable apps and opens a unique match; ambiguous matches are rejected.
2. `найди <запрос>` / `поищи <запрос>` — opens a browser search.
3. `навигатор` / `маршрут до <место>` — opens a navigation intent.
4. `позвони <номер>` — opens Android ACTION_DIAL with the number; it does not place a call. The user must review and confirm in the phone app.
5. `который час` / `сколько времени` — returns local device time.

## Safety boundaries

- No ACTION_CALL permission and no automatic call placement.
- No automatic SMS/message sending, deletion, purchases, settings mutation or arbitrary shell/Accessibility actions.
- Ambiguous app launch is rejected instead of choosing arbitrarily.
- All intent launches check that a handler exists and convert permission/runtime failures into explicit results.
- Online search/navigation requires an installed handler and network; core ASR preparation remains local after model download.

## Known gaps — do not mark complete

- The current capture is a fixed 4.5-second one-shot recording, not continuous wake-word/VAD streaming.
- The current UI state callback is connected, but the GLES orb still receives only a listening boolean; state-specific colors/animation and measured microphone/TTS amplitude remain pending.
- The dialer is opened for user confirmation; no direct call or contact lookup.
- App discovery depends on Android package visibility and must be checked on ColorOS.
- No foreground microphone service, persistent background listening, cancellation UI, local memory, MCP or multi-agent runtime is delivered by this checkpoint.
- Physical OPPO Find X6 Pro validation is not performed by this branch.
- This is not a release APK. Wait for exact-head CI and emulator results.

## Verification plan

- Build debug APK on this exact branch.
- Run unit and instrumentation suites; preserve the black-screen regression checks.
- Exercise launcher UI and all tool paths on emulator, including no-handler and ambiguous-app cases where feasible.
- Inspect logcat for ActivityNotFoundException, SecurityException, audio errors and native renderer errors.
- Install on OPPO Find X6 Pro / Chinese ColorOS and verify launcher, microphone, Russian recognition, browser, maps and dialer manually.

## Upstream inspiration

- Personal-Jarvis: task/agent orchestration and tool supervision concepts.
- isair/jarvis and OpenJarvis: local-first speech/model routing and optional cloud providers.
- Thoth and Dodaios: scoped tools, approval gates, auditability and workflows.
- hasibjaan/jarvis: visual state-machine/orb design.
  
Only architecture patterns are adapted here. No upstream desktop code or third-party visual assets were copied.
