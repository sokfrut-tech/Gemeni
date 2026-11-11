# Jarvis runtime integration audit — 2026-11-10

## Verified baseline

- Branch: `integration/jarvis-assistant-ci-smoke-repair-2026-11-10`
- PR: #133 (draft; do not merge before physical OPPO qualification)
- Commit under verification: `b2b27f7800a8ccdef418f19f5bbfd8da5b6ce4f9`
- Build Jarvis run: #42891048660
- Latest observed CI state: APK build, JVM unit tests, security/native checks, manifest/ABI inspection and Android emulator runtime smoke tests all completed successfully. Diagnostics/artifact upload were still running at audit time.

## Important implementation boundary

The new `JarvisFoldedRuntimePolicy` and `JarvisContextFolder` are production source classes with JVM tests. They are not yet wired into a real local LLM lifecycle.

Inspection of the current source shows:

- `LocalLlmEngine.complete(prompt)` is currently a placeholder that returns the prompt unchanged. It is not an inference engine.
- `JarvisCognitiveKernel` routes among provider abstractions and enforces explicit cloud permission and typed tool proposal filtering, but it does not currently consult `RuntimePlan`.
- `JarvisRuntimeController` currently owns ASR capture/dispatch/TTS; it does not yet route conversational turns through the cognitive kernel.
- `ModelDownloadManager` currently manages the Russian ASR model pack, not an LLM pack.
- Passing emulator smoke tests confirms the tested startup/runtime paths only. It does not prove microphone quality, wake-word reliability, model inference, background operation, ColorOS integration, battery/thermal behavior, or compatibility on the physical OPPO Find X6 Pro.

## Next implementation sequence

1. Keep PR #133 draft and preserve the green exact-head CI evidence.
2. Define a stable device-resource provider that samples Android available memory, thermal status/headroom where supported, foreground state and battery-saver/charging state. Unknown thermal data must fail conservatively.
3. Inject the resource provider and `JarvisFoldedRuntimePolicy` into the reasoning orchestration boundary. Block local inference when the plan disallows it; cap context and ensure only one heavyweight neural engine is resident.
4. Replace the echo-only `LocalLlmEngine` placeholder only after selecting and pinning a real Android-compatible inference runtime and a legally redistributable small model pack. No model weights in the APK; explicit download consent, resumable verified downloads and uninstall controls remain required.
5. Add lifecycle tests proving policy denial prevents model load/inference, HOT/CRITICAL transitions unload or suspend the LLM, and memory/thermal changes cannot leave multiple heavyweight engines resident.
6. Add deterministic tests for model download resume/Content-Range/checksum failure before shipping additional model packs.
7. Qualify on the physical OPPO Find X6 Pro: install, first-run permission flow, ASR model consent/download, Russian recognition, real commands, navigation handoff, wake-word, screen-off/background restrictions, call/audio focus preemption, thermal throttling, memory pressure and battery drain.

## Release gate

No merge, public release or claim of a functional offline conversational LLM until exact-head CI is fully green, a real inference runtime is present, and physical-device qualification has evidence.

## Addendum — 2026-11-11

The earlier audit above is a historical snapshot and its commit/run references are not the latest state. The latest baseline checked on 2026-11-11 was commit `d0f98a2cf66b444c3b4958a248c324fd681b7feb`, with Build Jarvis run #42977903093 reported completed/success. That run covered APK build, JVM tests, security/native compatibility checks, APK inspection and emulator smoke tests. It still does not prove physical OPPO behavior or real LLM inference.

A follow-on architecture decision is recorded in [JARVIS_LOCAL_INFERENCE_DECISION_2026-11-11.md](JARVIS_LOCAL_INFERENCE_DECISION_2026-11-11.md). It selects llama.cpp as the first Android local-inference backend candidate and identifies Qwen2.5-0.5B-Instruct Q4_K_M only as a profiling candidate. Neither runtime nor model has yet been integrated; the existing echo-only `LocalLlmEngine` remains a known blocker.

The PR remains draft and must not be merged until the decision record's implementation and release gates are met.
