# OPPO AndesGPT integration for Jarvis

Status: provider boundary committed; live inference is blocked on the official OPPO developer contract/authorization.

## What is confirmed

- OPPO's developer portal lists an "Andes large model" service under its large-model capabilities.
- OPPO describes AndesGPT as a family with model, data, and infrastructure tiers, and describes device/cloud collaboration.
- Publicly indexed information describes an on-device compact Tiny tier, but this does not prove that a third-party APK can load its weights or invoke it through a public SDK.
- The public portal exposes a developer application/permission workflow. The detailed Andes model documentation is not readable without the required portal access.

## Integration design

Jarvis uses `AndesGptProvider` as a provider boundary:
- `status()` reports whether the authorized provider is available.
- `generate()` accepts role-tagged conversation messages.
- replies identify the model and whether inference ran on-device.

`UnavailableAndesGptProvider` is the safe default until the official contract is supplied. It deliberately does not call private Breeno services, inspect hidden system APIs, scrape app traffic, or upload conversation text to an undocumented endpoint.

## Required to enable live AndesGPT

1. OPPO developer account and app registration for the Jarvis package.
2. Permission to use the Andes model service for a third-party application.
3. Official Android SDK/AAR or documented HTTPS API, including authentication, supported ColorOS/device versions, model IDs, quotas, and data-retention terms.
4. If the API is cloud-hosted, a small backend proxy must hold credentials; never embed a provider secret in the APK.
5. User-visible setting for AndesGPT, network use, and explicit consent before sending conversation content off-device.

## Runtime policy

- Offline ASR/TTS and local device commands continue to work without AndesGPT.
- AndesGPT is an optional reasoning provider, not a dependency for launching navigation, phone, browser, or other local actions.
- Tool execution remains behind Jarvis's allow-listed intent/tool registry and confirmation rules.
- If AndesGPT is unavailable, Jarvis falls back to local intent handling; it must not pretend that a cloud answer was generated.

## Official starting points

- OPPO Open Platform: https://open.oppomobile.com/
- OPPO AI smartphone white paper: https://www.oppo.com/content/dam/oppo/common/mkt/footer/OPPO-AI-white-paper-EN.pdf
