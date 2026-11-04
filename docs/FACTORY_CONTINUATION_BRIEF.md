# Factory continuation brief — Gemeni / Jarvis

**Updated:** 2026-11-04 | **Status:** active Android runtime and CI work  
**Canonical portfolio:** Universal-AI-Factory/docs/PROJECT_PORTFOLIO_AND_CONTINUATION_PLAN_2026-11-04.md  
**Branch:** factory/continuation-brief-2026-11-04  
**Open PRs:** #53 security, #51 emulator smoke tests, #35 official Gemini cloud client  
**Correlation:** gemeni-jarvis-continuation-20261104

## Mission and owner requirements
Create an installable Android assistant for Chinese OPPO/ColorOS (Breeno 13.3.2 context) that supports Russian speech/search and responses, English speech recognition and English-to-Russian translation, and Chinese-to-Russian translation. Local/offline capability is preferred with online updates/fallback. Target Android 7+ where feasible. User wants an actual APK, not just setup instructions, and wants visible progress and security checks.

## Current evidence and limits
- Prior security rollout records PR #50 merged and Android debug build/CodeQL success on exact historical head. Re-check current main before relying on that evidence.
- PR #51 adds Android API 35 emulator instrumentation for launcher UI, microphone permission/runtime state and accessibility settings; refresh exact CI.
- PR #35 implements official Gemini cloud client; inspect privacy, key handling, fallback and tests.
- Historical deterministic Director/specialist output explicitly stated LLMs were not configured; do not label it independent Claude/Gemini review.
- A prior user-reported build launched to a black screen / non-functional UI. This remains a priority until reproduced and fixed with logs/runtime evidence.
- No physical OPPO/ColorOS validation is established by emulator success.

## Next actions
1. Inspect README/AGENTS, latest main, PR #51/#35/#53 and workflow runs.
2. Reproduce black screen with emulator instrumentation and capture logcat/crash details.
3. Verify Russian + English ASR, language identification, Chinese/English translation to Russian, TTS, microphone permission and lifecycle.
4. Keep model assets/licences/checksums and ABI packaging explicit; protect API keys server-side/securely.
5. Validate offline path separately from online Gemini fallback.
6. Produce a sideloadable APK with exact commit SHA, artifact and SHA-256.
7. Record physical OPPO/ColorOS checks as NOT_TESTED until performed.

## Prohibited
No secret in Git/APK/logs, no permission bypass, no Play publishing or destructive changes without owner approval.

## Resume
Refresh PR #51/#35/#53 and CI first. If Gemini/Claude are unavailable, ChatGPT continues direct implementation; do not fake model review or cloud availability.


## Documentation handoff PR
- Continuation brief PR: [#54](https://github.com/sokfrut-tech/Gemeni/pull/54) (draft; not merged).
- Review this brief against current main before code changes.