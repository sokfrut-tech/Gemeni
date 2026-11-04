# Factory brief — Jarvis Phase A local coordinator core

Date: 2026-11-04
Priority: P0 foundation
Target repository: `sokfrut-tech/Gemeni`
Read first:
- `docs/CONTINUATION_STATE.md`
- `docs/CURRENT_STATUS.md`
- `docs/JARVIS_FACTORY_HIVE_FREE_FIRST_ROADMAP_2026-11-04.md`
- `docs/agents/BRIEF_JARVIS_MULTI_PROVIDER_FOUNDATION_2026-11-04.md`

## Mission

Implement the smallest real vertical slice of the Jarvis Factory Hive that works with no internet, no API key, no subscription and no remote AI provider. The result must be testable and must not break the existing Russian parser or Android tool gateway.

## Scope for this task

1. Inspect current repository instructions, active PR bases/heads and exact-head CI before editing. Do not merge or rebase shared branches without authorization.
2. Define a provider-neutral reasoning interface and a deterministic local-only implementation. Disabled/offline mode must make zero network calls.
3. Define closed typed proposals for the existing safe tool set. No arbitrary intents, class names, shell commands, URLs, filesystem paths or Android API calls.
4. Validate exact tool names, allowed/required fields, string lengths, numeric bounds and malformed input.
5. Add a policy result with SAFE / CONFIRM / BLOCK. Preserve ACTION_DIAL only; no ACTION_CALL.
6. Add a small coordinator/task state model with QUEUED, ASSIGNED, RUNNING, WAITING_FOR_USER, BLOCKED, REVIEW, VERIFYING, DONE, FAILED and CANCELLED; cancellation and bounded retries.
7. Route all executable actions through the existing `AndroidJarvisToolRegistry`. Model/provider classes must not receive Android `Context` or `PackageManager`.
8. Add local persistence only if the current architecture has an appropriate existing persistence layer; otherwise define an interface and document the next migration. Do not add a database dependency speculatively.
9. Add unit tests for valid proposals, malformed/unknown/extra fields, oversized values, injection-like inputs, risk classification, no side effects for BLOCK, confirmation path, offline zero-network behavior, cancellation and existing parser compatibility.
10. Add an agent report with exact files, commands, exit codes, CI URLs, commit SHA, limitations and next handoff.

## Strictly forbidden in this phase

- Any paid API, live provider endpoint, API key, cloud request or silent cloud fallback.
- Audio/microphone upload or always-on listening changes.
- MCP server execution.
- Shell, arbitrary filesystem, unrestricted Accessibility or arbitrary Android intents.
- Automatic calls, SMS, purchases, payments, deletion, permission changes or code installation.
- Self-modifying APK/code or autonomous merging.
- Weakening/deleting existing tests to get a green result.

## File ownership and collaboration

One implementation owner controls shared interfaces and runtime files. Other factories may perform read-only independent reviews:
- Security factory: threat model and adversarial proposal test cases.
- QA factory: independent test plan and exact-head verification.
- Android factory: compatibility review of gateway boundary.
- Research/model factory: read-only runtime/license benchmark options; no dependency changes.

Do not allow concurrent writers to edit the same files. If Claude/Gemini are not actually reachable through an authorized channel, record them as unavailable and continue sequentially; do not fabricate their participation.

## Acceptance criteria

- Existing parser and gateway behavior remains compatible.
- Disabled/offline mode demonstrably makes zero network calls.
- Invalid, unknown, oversized and unsafe model proposals are rejected.
- BLOCK has no side effects; CONFIRM requires explicit approval for the exact proposed action.
- No model/provider receives Android context or executes actions directly.
- Tests pass on the exact branch head; debug APK build succeeds.
- No secrets are added.
- Status, continuation state, changelog and agent report are updated.
- PR remains draft until review and user approval; no merge/release.

## Handoff format

Return a concise summary plus evidence:
- changed files;
- commands and exit codes;
- CI workflow/run URLs and exact tested SHA;
- test counts;
- known gaps and device limitations;
- commit SHA;
- next single action.
