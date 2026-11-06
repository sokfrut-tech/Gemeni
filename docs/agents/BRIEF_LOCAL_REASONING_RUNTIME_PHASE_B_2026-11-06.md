# Jarvis Phase B — real on-device reasoning runtime

Date: 2026-11-06  
Priority: P0 after Phase A safe execution seam  
Status: design/implementation brief; no neural inference runtime is claimed yet  
Repository: `sokfrut-tech/Gemeni`

## Mission

Replace the current deterministic `LocalOnlyJarvisModelProvider` as the primary conversational reasoning implementation with a real on-device language model, while retaining the deterministic provider as an offline fallback and safety baseline.

## Current verified baseline

- The active ASR transcript path now calls the provider-neutral `JarvisModelProvider` interface.
- The current `LocalOnlyJarvisModelProvider` delegates to `JarvisCommandParser`; it does not run a neural model, reason over arbitrary language, plan multiple steps or answer general questions.
- Provider output is treated as untrusted JSON and must pass `ToolProposalCodec`.
- Only `AndroidJarvisToolRegistry` executes Android actions.
- CONFIRM actions require explicit in-app approval; BLOCK/unknown proposals never execute.
- See `docs/engineering/2026-11-06-safe-agent-runtime-integration.md`.

## Runtime candidates to evaluate — do not select by name alone

Evaluate at least:
1. **llama.cpp Android/NDK — preferred first spike**. Evidence and constraints are recorded in `docs/engineering/2026-11-06-local-inference-runtime-decision.md`. Pin upstream commit `954de248ac6907dd05dc19f0f511ab18f2807a54`; build a small app-owned JNI adapter. Do not import the current Arm AI Chat wrapper unchanged: its library currently declares minSdk 33.
2. **MLC LLM / TVM-based mobile runtime** — evaluate model conversion, supported devices, package size, Vulkan/CPU support and maintenance state.
3. **ONNX Runtime GenAI** — evaluate Android package availability, model compatibility, token streaming, native ABI footprint and coexistence with the existing Sherpa-ONNX runtime.

No runtime is approved until the code, dependency and model licenses are reviewed. Do not vendor desktop Python frameworks into the APK.

## Model selection process

- Compare small multilingual/Russian instruction models that permit the intended redistribution and use.
- Test at least a compact profile and a higher-quality profile; do not hard-code one model before target-device measurements.
- Store model weights outside the APK in app-private storage. Downloads must be resumable where practical, SHA-256 verified, versioned, cancellable and removable by the user.
- Keep a no-model profile: deterministic parser and local skills remain functional when the LLM is absent or memory is insufficient.
- Do not assume a model is free to redistribute because its weights are downloadable. Record model card, license, attribution and any use restrictions.

## Required architecture

`ASR transcript -> context builder -> local inference engine -> structured planner output -> schema validation -> risk policy -> Android gateway -> verified result -> memory candidate`

Implement:
- `LocalInferenceEngine` with load/unload, warm-up, streaming token output, cancellation, memory-pressure handling and deterministic error results.
- `LocalModelCatalog` with model metadata, license reference, expected RAM, quantization, file size, SHA-256 and compatibility.
- Structured output constrained to the existing typed proposal schema; prefer grammar/constrained decoding where supported. Never parse arbitrary natural-language model output as executable commands.
- Bounded planner: maximum steps, wall-clock budget, retry count, context budget, cancellation and stop conditions.
- Context builder that includes only relevant user-approved memory and current task state; redact secrets and avoid raw audio retention.
- Provider router: local neural model first when installed and within resource budget; deterministic parser fallback when unavailable; optional cloud adapter only after explicit opt-in and with a visible network indicator.
- Tool-result feedback to the planner, with a bounded observe/act/verify loop. Each action must have an expected result and a verification rule.

## Target-device benchmark gate

Run on the actual OPPO Find X6 Pro before choosing default model:
- peak and steady-state RAM;
- load/warm-up time;
- time to first token and tokens/second;
- Russian instruction following and tool-selection accuracy on a fixed test set;
- hallucinated/invalid tool proposal rate;
- CPU/GPU/NPU usage where observable;
- battery drain and thermal throttling over a sustained session;
- background/foreground lifecycle and low-memory recovery.

A model that destabilizes the phone or blocks voice capture must not become the default, even if its text quality is higher.

## Security and privacy requirements

- Inference is local by default. No microphone upload, telemetry payload or cloud fallback without explicit user opt-in.
- Model output is untrusted. It cannot call Android APIs directly, access Context, filesystem, shell or network.
- Tool schema is closed; unknown tool names, extra fields, oversized arguments, malformed values and out-of-scope requests are rejected.
- Sensitive actions remain CONFIRM or BLOCK. Confirmation must display the exact action and arguments.
- Model files are data, not executable code; validate hashes and provenance.
- User can remove downloaded models and clear model context/memory.

## Acceptance criteria

1. Airplane-mode test proves no network call is made during local reasoning.
2. At least 20 Russian free-form prompts are evaluated against a versioned benchmark set.
3. The model can answer ordinary questions without inventing a tool call.
4. Multi-step requests produce a bounded plan with explicit expected outcomes.
5. Malformed/adversarial outputs are rejected by the proposal validator.
6. SAFE actions pass through the Android gateway; CONFIRM actions remain blocked until the user approves; BLOCK actions never execute.
7. Cancellation stops inference and the plan without leaving microphone/audio resources active.
8. Memory is retrieved only with provenance and can be removed by the user.
9. Benchmark report records exact device, OS/ColorOS version, model SHA, runtime commit, settings and results.
10. Existing deterministic voice commands still work with no model installed.

## Explicit non-goals for Phase B

- No silent self-modification or generated-code execution.
- No automatic paid API.
- No always-on microphone/wake word until the foreground service, battery, privacy and stop-control gates pass.
- No unrestricted Accessibility, shell, MCP or background agent privileges.
- No release claim based only on Gradle build or emulator success.

## Next action

After PR #78 has green JVM/build/security checks, implement a runtime spike behind a separate interface/feature flag using the pinned llama.cpp source and CPU-only arm64-v8a first. The generic upstream Android NDK guide's example uses Android API 28; API24 support is not yet proven. MLC is a later GPU experiment; ONNX Runtime base Android package is not by itself an LLM generation stack. See `docs/engineering/2026-11-06-local-inference-runtime-decision.md`. Keep the deterministic provider as fallback and do not replace it until the neural path passes all acceptance tests.
