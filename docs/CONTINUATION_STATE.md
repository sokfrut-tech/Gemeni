

## 18. Checkpoint — 2026-11-04: free-first Factory Hive direction

User's explicit priority: Jarvis and the connected projects must remain free for essential use, functional, feature-rich and able to improve its intelligence over time. The intended product is a local-first Jarvis with a coordinated hive of specialist factories.

Canonical architecture and roadmap: `docs/JARVIS_FACTORY_HIVE_FREE_FIRST_ROADMAP_2026-11-04.md`.

Decisions:
- No mandatory paid subscription, hosted backend, API key or cloud model for core functions.
- Local deterministic commands, Sherpa-ONNX Russian ASR, compatible local TTS, local memory and offline skills remain the baseline.
- Optional local reasoning model must be benchmarked on the actual OPPO device for RAM, latency, Russian quality, thermal/battery impact and license before selection.
- Gemini/Qwen/Kimi/GLM are optional adapters only. Never promise permanent free tiers; verify current official pricing/terms before enabling. No surprise paid requests or silent cloud fallback.
- Factory Hive roles: coordinator, research, Android/ColorOS, voice, model, tools, UI, security, QA/release and memory/knowledge. A role is not active until a real executor is connected and returns traceable evidence.
- Coordinator creates structured task briefs, routes bounded work, reconciles evidence, tracks QUEUED/ASSIGNED/RUNNING/WAITING_FOR_USER/BLOCKED/REVIEW/VERIFYING/DONE/FAILED/CANCELLED and stores durable handoffs.
- "Self-improving" means a controlled loop of local memory, candidate skills/patches, independent review, tests, user approval, versioning and rollback. No silent self-modification or permission escalation.
- Only AndroidJarvisToolRegistry executes device actions. Provider/model outputs are untrusted typed proposals; risk policy is SAFE/CONFIRM/BLOCK.
- MCP remains disabled until explicit trusted-server allowlisting and scoped permissions exist.

Immediate next slice: Phase A safe local coordinator core only. Do not add live provider networking, API keys, microphone upload, unrestricted shell, MCP execution or generated-code execution in Phase A. Recheck PR #59/#60 exact heads and CI before choosing an integration base. No merge/release/device-validation claim is made by this documentation checkpoint.
