# Agent brief — Jarvis multi-provider AI foundation

Date: 2026-11-04
Branch: feat/jarvis-multi-ai-foundation-2026-11-04
Read first: docs/CONTINUATION_STATE.md and docs/OPEN_SOURCE_AI_INTEGRATION_REVIEW_2026-11-04.md

## Goal
Implement Phase A only: provider-neutral reasoning seam and a strict typed tool-proposal boundary, preserving local-first operation. Do not add live provider networking or require API keys in this phase.

## Repository facts
- Existing pure parser: app/src/main/java/com/jarvis/breeno/assistant/core/JarvisCommandParser.kt
- Existing Android execution gateway: app/src/main/java/com/jarvis/breeno/assistant/core/AndroidJarvisToolRegistry.kt
- Gson is already a dependency in app/build.gradle.kts.
- Existing ToolResult includes NeedsConfirmation, but no complete approval workflow is implemented.
- Existing speech stack is Sherpa-ONNX; do not replace it with large Python/PyTorch models.

## Required implementation
1. Define a provider-neutral request/response contract with cancellation and explicit local-only provider.
2. Define a closed set of typed tool proposals; do not accept arbitrary class names, intents, URLs, shell commands, file paths or Android API calls from model output.
3. Validate JSON shape, exact tool names, required/allowed fields, string lengths and numeric bounds.
4. Classify each proposal SAFE / CONFIRM / BLOCK. Dial must map only to ACTION_DIAL. Never expose ACTION_CALL.
5. Ensure the only executor remains AndroidJarvisToolRegistry. Model/provider code must never receive Context or PackageManager.
6. Offline/provider-disabled path must make zero network calls and preserve current local parser behavior.

## Tests required
- Valid proposal per supported tool.
- Malformed JSON, missing/extra fields, unknown tool, oversized arguments and injection-like values are rejected.
- Sensitive action produces NeedsConfirmation; blocked action has no side effect.
- Provider disabled/offline path makes no request.
- Cancellation and timeout are propagated.
- Existing parser and tool gateway tests remain green.

## Out of scope
- No API keys, provider endpoints or live cloud requests.
- No microphone/audio upload.
- No always-on microphone, no raw audio persistence.
- No MCP server execution, shell, unrestricted filesystem, payments, message sending or automatic calls.
- No merge, release or physical device claims.

## Verification and handoff
Run focused JVM tests, full unit tests, Gradle debug APK build and applicable CI. Record exact commands, exit codes and workflow URLs. Update CONTINUATION_STATE.md, CURRENT_STATUS.md and CHANGELOG_AI.md. Create an agent report under docs/agents/ with files, tests, evidence, risks and commit SHA. Do not claim complete until exact-head CI is green.