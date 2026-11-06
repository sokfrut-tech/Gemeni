# Jarvis Phone Control — implementation design

Date: 2026-11-06
Status: Design baseline; implementation not yet verified.
Tracking issue: https://github.com/sokfrut-tech/Gemeni/issues/80

## Product goal

Jarvis should control supported phone functions and system services on OPPO/ColorOS through a single auditable Android capability gateway. The voice/model layer may propose typed actions, but it must never receive Android Context, unrestricted Intents, shell access, or service handles.

The core phone-control path must work locally and must not require a paid API or cloud connection.

## Existing baseline (inspected on 2026-11-06)

The current `AndroidJarvisToolRegistry` supports app launch, web search, navigation, time, and a confirmation-gated dialer intent. `ToolProposalCodec` has a strict tool/argument allowlist. This is a useful starting boundary, not a complete phone-control implementation. Do not claim Wi-Fi, Bluetooth, flashlight, volume, brightness, notification or system-service control exists until code and tests prove it.

## Architecture

### Typed contracts

Introduce a closed set of domain-level contracts, independent of Android framework classes:

- `PhoneCapability`: stable ID, display name, read/write mode, minimum API, permission requirements, OEM caveats, risk tier, availability reason.
- `PhoneAction`: sealed action types with validated arguments; never arbitrary class names, component names, URIs, shell strings or raw Intent data from the model.
- `PhoneActionResult`: success, needs-user-action, denied, unsupported, cancelled, failed, plus optional observed postcondition.
- `ApprovalRequirement`: none, user-mediated system UI, explicit in-app confirmation, unavailable.
- `PhoneStateSnapshot`: only safe, explicitly permitted state fields.

Keep provider -> proposal validation -> policy -> Android gateway -> capability adapter. A model response is untrusted input and must pass exact schema and policy checks.

### Adapter boundaries

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

### Risk policy

- READ_ONLY: diagnostics and state queries.
- REVERSIBLE: volume, brightness and torch, with capability and permission checks.
- USER_MEDIATED: system panels, permissions, settings screens, app selection.
- EXPLICIT_APPROVAL: dial/SMS composition, notification replies, destructive or privacy/security-sensitive operations.
- DENY: silent communication, arbitrary shell/root commands, bypassing platform restrictions, hidden monitoring, credential collection.

Every result must distinguish ACTION_STARTED from STATE_VERIFIED. Only return verified success after the adapter observes the expected postcondition; otherwise say that the user must finish the action or that the state could not be verified.

## Android and ColorOS compatibility

- Verified on `main` on 2026-11-06: `app/build.gradle.kts` declares `minSdk = 29` and `targetSdk = 35`. This build does NOT support Android 7/API 24. Do not claim Android 7 support unless minSdk is deliberately lowered and all dependencies/native ABIs are proven compatible.
- Android 10+ restricts ordinary apps from directly toggling Wi-Fi; use supported system UI such as the Wi-Fi panel where appropriate.
- Android 13+ restricts ordinary apps from enabling/disabling Bluetooth; use supported system UI.
- Special access (write settings, notification listener, accessibility, role/default-handler status) must be requested through explicit user-facing setup screens and checked again at execution time.
- ColorOS background execution, autostart, battery optimization and voice-assistant role behavior require physical-device verification on the target OPPO Find X6 Pro and exact ColorOS build.
- Never request device administrator, root or broad package visibility unless a concrete feature requires it and a separate security review approves it.

## UI and user experience

- Add a Phone Control capability page showing available, needs-permission, user-mediated and unsupported states.
- Before enabling a special permission, explain what it grants and how to revoke it.
- For each command show: what Jarvis understood, what will happen, whether Android will show a system screen, and whether success can be verified.
- Provide Cancel and Settings/Grant access actions. Do not silently retry denied actions.
- Add a local action history with timestamp, requested action, result category and verification outcome; avoid storing sensitive message content, phone numbers or notification text by default.

## Delivery phases

### P0 — foundation
- Audit Gradle minSdk/targetSdk, manifest, existing service classes and current gateway.
- Add typed capability/action/result contracts and a deterministic capability registry.
- Add unit tests for allowlist rejection, missing permissions, unsupported API, user cancellation, approval rejection and false-success regression.
- Add a capability matrix document with API-level behavior.

### P1 — first real controls
- Implement read-only battery/storage/connectivity diagnostics.
- Implement volume and torch adapters with state verification.
- Implement app launch and allowlisted settings navigation.
- Add UI capability status and permission explanation.
- Test on emulator and physical OPPO.

### P2 — user-mediated controls
- Brightness with WRITE_SETTINGS flow.
- Wi-Fi/Bluetooth panels with post-action state re-check.
- AlarmClock and media actions.
- Validate ColorOS-specific behavior.

### P3 — advanced integrations
- Optional notification listener with explicit setup/revoke.
- Accessibility actions with narrow scope and visible operation.
- Dial/SMS composition flows with preview and explicit confirmation.
- User-defined scenes (Night, Driving, Home) composed only of individually supported actions.

## Acceptance gates

1. Every action has an exact schema and policy entry.
2. No arbitrary Intent, URI, component, command string or code is accepted from a model.
3. Permission and API-level checks happen at execution time, not only setup.
4. Unsupported and restricted actions return honest, actionable explanations.
5. Success is not reported until the expected state is observed; if observation is impossible, return an unverified outcome.
6. Tests cover allowlist, policy, permission denial, cancellation, API-level restrictions and false success.
7. CI builds and relevant tests pass on the exact PR head.
8. Physical OPPO Find X6 Pro acceptance is documented separately from emulator results.
9. No paid/cloud dependency is required for core control.

## Source audit findings (2026-11-06)\n\n- `app/build.gradle.kts`: `minSdk = 29`, `targetSdk = 35`, compileSdk 35. Android 7/API 24 is currently out of support. Any backward-compatibility effort is a separate engineering task with dependency and native-runtime verification.\n- `JarvisAccessibilityService` is currently an empty service. Its XML config requests all accessibility event types and window-content retrieval; this is broader than needed for an empty implementation and should be reduced before adding behavior.\n- `JarvisVoiceInteractionService` currently only overrides lifecycle callbacks; it does not yet implement a full assistant session workflow.\n\n## Immediate next implementation step

After PR #78's currently running CI finishes, create a focused implementation PR from the reconciled integration base (do not merge the open PR dependency chain blindly). Start with P0 typed contracts and P1 diagnostics/volume/torch. Keep native LLM/JNI work independent so phone-control progress does not wait on model inference.
