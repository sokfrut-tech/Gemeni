# Jarvis — consent-gated Qwen model installer

Date: 2026-11-06

## Added in this implementation slice

- Dedicated QwenModelInstaller for the exact pinned Qwen3.5 0.8B Q4_0 artifact.
- Installation refuses to start unless the caller explicitly passes user consent.
- Model is stored under app-private filesDir/models/; no shared storage or cloud sync.
- Checks at least 650,000,000 bytes of available storage before starting.
- Downloads to a temporary .part file, reports byte progress, checks exact expected byte count and SHA-256, then performs an atomic rename.
- Manual redirect handling: HTTPS only, no userinfo or non-standard ports, at most five redirects, and exact allowlisted Hugging Face/CDN hosts.
- Cancellation is checked during transfer; failed or cancelled transfers remove the partial file.
- Explicit user-invoked model removal is provided.
- A previously installed file is reused only after size and SHA-256 verification.

## Security and privacy boundaries

- This class performs no work in its constructor and never schedules background downloads.
- The UI/caller must present the model, its size and license, and request consent before invoking install with consent=true.
- No prompt, microphone data, local memory, or generated content is uploaded.
- A download requires internet access; after installation, inference is intended to run locally. Offline inference still requires separate device verification.
- Redirect allowlist may need adjustment only if Hugging Face's documented delivery host changes; do not replace it with unrestricted redirect following.

## Verification still required

- Exact-head Kotlin compile, unit tests, native APK build, CodeQL and secret scan.
- Exercise cancellation during a real download, low-storage rejection, truncated response, wrong digest, redirect rejection, atomic install, verified reinstall and removal.
- Confirm the final GGUF loads through LlamaCppRuntime and generate Russian text on emulator and physical OPPO Find X6 Pro.
- No claim of model readiness or successful on-device inference is made by this installer alone.
