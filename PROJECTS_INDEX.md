# Project Hub — index for continuing work

Last updated: 2026-10-31

This file is the starting point for resuming the user's active and supporting projects. Each project repository remains the source of truth for its code. Read its `docs/CONTINUATION.md` before changing anything.

## Active / recent projects

| Project | Repository | Continuation branch | Known next checkpoint |
|---|---|---|---|
| Jarvis / multi-agent Factory | [Gemeni](https://github.com/sokfrut-tech/Gemeni) | `docs/continuity-2026-10-31` | Verify workflow variables and real model calls; diagnose Android black screen |
| AVTOY KOREA website + Telegram bot | [avtoy-korea-auto](https://github.com/sokfrut-tech/avtoy-korea-auto) | `docs/continuity-2026-10-31` | Verify Worker deployment, website, webhook, bot replies and admin-only inquiry delivery |
| Happ-compatible Android client | [happ-proxy-pool](https://github.com/sokfrut-tech/happ-proxy-pool) | `docs/continuity-2026-10-31` | Inspect Actions run `41004085041`; obtain/verify `libXray.aar` SHA-256; complete APK build |
| Russian OPPO assistant/search | [RussSearchOPPO](https://github.com/sokfrut-tech/RussSearchOPPO) | `docs/continuity-2026-10-31` | Fresh APK build/install test; validate English transcription and Chinese/English→Russian translation |
| Offline translation model assets | [russ-translation-models](https://github.com/sokfrut-tech/russ-translation-models) | `docs/continuity-2026-10-31` | Verify model release `translation-models-v1` and archive checksums |
| Earlier Russian voice assistant prototype | [voice-assistant](https://github.com/sokfrut-tech/voice-assistant) | `docs/continuity-2026-10-31` | Decide whether maintained separately or superseded by RussSearchOPPO/Jarvis |

## Additional repositories found

- [breeno-apks](https://github.com/sokfrut-tech/breeno-apks) — repository was empty when checked on 2026-10-31; no project files could be recorded.
- [sleep-button](https://github.com/sokfrut-tech/sleep-button) — repository was empty when checked on 2026-10-31; no project files could be recorded.

## How to resume a project in a new chat or with another agent

1. Open this Project Hub.
2. Open the relevant repository's `docs/CONTINUATION.md`.
3. Inspect the current branch head and latest GitHub Actions run; the notes here are checkpoints, not proof that an old state is still current.
4. Continue only on the documented feature or documentation branch unless the owner approves another target.
5. Before reporting success, provide fresh evidence: commit SHA, workflow run URL, test output, artifact URL and/or live test result as appropriate.
6. Update the project's continuation document after every material change, including date, branch, commit, tests, artifacts, blockers and next action.

## Global working rules

- Never place passwords, API keys, bot tokens, webhook secrets, private subscription URLs or personal data in Git.
- Store credentials only in the appropriate GitHub Actions Secrets / Cloudflare secrets store. Document secret *names* only, never values.
- Do not merge to production branches, publish a Worker, or change live services without the owner's explicit approval.
- Do not claim an app, bot, website or model is working based only on a successful compile or green workflow. Perform the relevant functional test.
- Preserve dated versions and record exact artifact checksums when applicable.
- The user prefers plain-language, phone-friendly instructions and visible progress updates. State clearly what has been done, what remains, and what action (if any) is needed from the user.

## Scope note

This hub stores project continuity information and verified technical checkpoints, not a verbatim dump of every chat message. The project repositories and Git history remain authoritative for source code and changes. Where an item is marked unverified or unknown, the next agent must inspect it rather than guess.
