# Jarvis open-source implementation research — 2026-11-08

## Scope and honesty

This is a first comparative research pass, not a claim that all GitHub repositories have been reviewed. GitHub is too large and changes continuously. We searched targeted repository categories and inspected selected project READMEs. Before copying code, inspect source files, dependency versions, issue history, license compatibility, security posture, and device fit.

Target product: Russian-first, local/offline-capable Android assistant for OPPO Find X6 Pro / ColorOS. Priorities: phone/emergency calls and system audio always win; one microphone owner; thermal and battery limits; explicit user consent; visible controls; deterministic tool execution with verification; no untrusted autonomous actions.

## Repositories inspected in this pass

### 1. tokenarc/open-jarvis — Android-native agent (MIT per repository README)
https://github.com/tokenarc/open-jarvis

README claims Android 8+, Kotlin, AccessibilityService, Compose UI, multiple LLM providers, OCR/screen vision, voice I/O, local model, Graphify memory, app intelligence/chaining, skills, MCP, screen watch, scheduler, notification intelligence, risky-action confirmation, and self-healing executor.

Potentially valuable patterns to inspect in source:
- task router + atomic JSON action plan rather than unconstrained free-form commands;
- Accessibility executor followed by screen-state verification;
- app capability/trust scoring and multi-app workflows;
- graph-shaped memory and pattern learning;
- skill/MCP extension contracts;
- explicit confirmation for risky actions;
- self-healing/retry bounded by evidence.

Caution: README feature-status claims are not proof of reliability or security. Audit implementation, permissions, prompt injection defenses, action allowlists, tests, and actual offline behavior before adoption.

### 2. open-jarvis/OpenJarvis — Stanford local-first AI framework (Apache-2.0)
https://github.com/open-jarvis/OpenJarvis

README describes shared primitives for on-device agents, multiple local inference engines (including llama.cpp/Ollama), optional cloud routing, MCP, web search, code interpreter, retrieval, and evaluation that treats energy, FLOPs, latency, and cost as first-class metrics.

Potentially valuable:
- local-first routing with explicit cloud fallback;
- measure latency, energy/thermal cost, and accuracy together;
- use trace/evaluation loops to improve model selection;
- separate reusable agent primitives from device-specific integration.

Not a drop-in Android app; adapt the evaluation and routing principles, not the Python stack wholesale.

### 3. leon-ai/leon — modular assistant and agent execution (MIT)
https://github.com/leon-ai/leon

README describes smart/controlled/agent modes; native skills and SKILL.md agent skills; layered memory; provider abstraction; context grounding; and a Skills → Actions → Tools → Functions → Binaries hierarchy. It also explicitly warns that its develop branch is a 2.0 developer preview and legacy docs may not match current code.

Potentially valuable:
- separate deterministic controlled mode from agent planning mode;
- layered memory (durable preferences, day-to-day context, recent conversation);
- skills/actions/tools as distinct layers;
- bounded proactive pulse rather than uncontrolled background activity.

### 4. openclaw/openclaw — gateway and channel architecture (MIT)
https://github.com/openclaw/openclaw

README describes a local Gateway/control plane for sessions, tools, events, and channels; pluggable model/agent harnesses; state and credentials kept on the user's hardware; native clients and many messaging channels.

Potentially valuable:
- one local control plane and explicit session lifecycle;
- provider adapters decoupled from assistant state;
- channel adapters separated from tools and agent runtime;
- credentials and workspace scoped locally.

Caution: desktop/server gateway architecture must be adapted to Android process/lifecycle and battery constraints.

### 5. zeroclaw-labs/zeroclaw — compact local agent runtime (MIT OR Apache-2.0)
https://github.com/zeroclaw-labs/zeroclaw

README describes a Rust single-binary runtime, many LLM providers, channels, tools, MCP, local service mode, and hardware integrations.

Potentially valuable:
- small, auditable runtime core;
- provider/channel/tool adapters;
- explicit configuration and service lifecycle;
- local-first architecture.

Not an Android-native dependency candidate without a separate portability/build/security review.

### 6. Edw590/VISOR---Android-Version-Assistant — Android phone control
https://github.com/Edw590/VISOR---Android-Version-Assistant

README describes an Android assistant designed to operate a phone with limited/no screen/touch input, with local-first intent and modular command handling. The project notes older Android 8.1 testing and legacy compatibility choices.

Potentially valuable:
- robust accessibility and hardware-button interaction concepts;
- voice-driven phone control for accessibility use cases;
- command modules and device-context actions.

Caution: old target Android version and privileged/legacy behavior are not suitable to copy blindly into modern ColorOS. Review current maintenance and security implications.

### 7. RECTDEV/serenity — persistent local voice/vision agent
https://github.com/RECTDEV/serenity

README claims local inference, wake-word → STT → TTS, cross-domain semantic memory, screen/camera vision, skills, scheduling, proactive work, and MCP memory integration.

Potentially valuable:
- memory consolidation and retrieval;
- optional multimodal perception;
- skills as small extensions;
- autonomous work separated from immediate user interaction.

Caution: autonomy, camera/screen capture, and proactive behavior require strong consent, privacy controls, resource budgets, and bounded execution.

### 8. stevensaint/jarvis — pluggable voice assistant
https://github.com/stevensaint/jarvis

README describes plugin groups for brain, STT/TTS, wake-word, realtime, harness, tools, and channels; stream-based interfaces and contract tests; plain-file Markdown knowledge.

Potentially valuable:
- stable plugin interfaces and contract tests;
- streaming as a common interface;
- knowledge base in inspectable files;
- add providers without modifying the core.

Desktop/Python project; use architectural patterns rather than transplanting code.

## Cross-project feature candidates for Jarvis

Priority A — foundational, implement and test first:
1. Single-owner microphone controller: one AudioRecord flow, one capture Job, idempotent cancellation and exactly-once cleanup.
2. Interruption supervisor: telephony/emergency-call priority, competing mic capture, privacy toggle, permission revocation, audio-recording failures. Suspend and release; never feed call audio into ASR/KWS.
3. Explicit listening consent and persistent visible state; no auto-start after reboot/crash; no hidden capture.
4. Thermal/battery-aware scheduler: pause or reduce KWS/VAD/ASR/TTS/inference as temperature and battery conditions worsen; bounded cooldown; do not fight Android thermal management.
5. Deterministic tool executor: typed schemas, allowlists, permission checks, action risk levels, confirmation for destructive/sensitive actions, post-action verification, audit log, bounded retries.
6. Resource/quality evaluation: latency, CPU/RAM, energy, thermal state, ASR accuracy, wake false accepts/rejects, tool success, crash rate.

Priority B — capabilities:
7. Skill registry with versioned manifests, input/output schemas, capability permissions, and isolated execution boundaries.
8. Layered local memory: explicit user-approved durable facts, project state, episodic summaries, searchable notes; deletion/retention controls.
9. App intelligence: installed-app capabilities, deep links/intents, Accessibility only when necessary; verify UI outcome rather than assuming taps worked.
10. Screen/OCR/vision mode, activated only on request or explicit policy, with sensitive-screen exclusions and no silent screenshots.
11. Multi-provider model router: local-first; cloud fallback only when user has configured/consented; thermal/network/cost-aware selection.
12. MCP and remote agent bridges behind scoped permissions and signed/validated tool manifests.
13. Scheduled reminders and proactive suggestions, all user-configured and resource-bounded.

Priority C — future research:
14. Personalization/pattern learning from user-approved local traces, with opt-out and inspectable state.
15. Multi-agent specialist routing for research, coding, radio/electronics, maps, gardening, construction, science and other knowledge domains.
16. Memory consolidation/"dream" process only while idle, charging/thermal-safe, with visible controls and measurable benefit.

## Do not copy blindly

- Any README's "feature complete" claims without source/test verification.
- Root, hidden accessibility, notification, call-log, or broad storage permissions without a strict need.
- Continuous camera/screen/microphone capture without explicit consent and clear UI.
- Unbounded self-healing retries, autonomous shell/Termux execution, or direct destructive actions.
- Cloud API dependencies as a default; offline/local operation remains a core goal.
- Model download or large background inference without explicit consent, thermal/battery checks, and storage planning.

## Recommended implementation order

1. Finish thermal guard integration across KWS/VAD/ASR/TTS/inference.
2. Build the single-owner session controller with interruption injection and deterministic cleanup.
3. Add Android platform interruption adapter after reviewing supported APIs and permissions; test on OPPO.
4. Route one audio stream sequentially through KWS → VAD → ASR → reasoning/tool execution → TTS.
5. Add schema-driven tool registry, risk tiers, action verification, and audit events.
6. Add skill registry and layered memory.
7. Add OCR/vision and app intelligence as explicit, permission-scoped capabilities.
8. Evaluate local/cloud routing using latency, energy, thermal, accuracy, and privacy metrics.

## Evidence and limitations

- This pass used targeted GitHub repository search and README inspection, not a complete crawl of GitHub.
- No third-party source code has been copied into Jarvis by this research document.
- License labels are taken from repository README/badges where visible; verify the actual LICENSE file and all bundled model/data licenses before reuse.
- Next audit should inspect source modules and test suites of the top Android candidate (tokenarc/open-jarvis), then compare against our current Gemeni architecture file-by-file.
