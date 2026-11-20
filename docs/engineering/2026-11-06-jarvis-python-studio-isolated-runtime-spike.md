# Jarvis Python Studio — isolated runtime feasibility spike

**Status:** design brief only; no Python runtime is integrated yet.  
**Date:** 2026-11-06  
**Parent design PR:** #81  
**Target:** Android / OPPO Find X6 Pro (hardware still must be measured on-device)

## Product outcome

Jarvis should let the user create, read, edit, run, test and package Python projects directly on the phone. The first milestone is a useful offline Python workspace, not an unrestricted shell and not a remote code-execution service.

The runtime must remain local. User scripts must not inherit Jarvis's private memory, model files, credentials, Android permissions, accessibility access, contacts, messages, notifications or tool gateway.

## Non-negotiable privacy contract

- No cloud execution, remote build, source upload, telemetry, crash upload or analytics.
- No network access for the script runtime by default. Do not add INTERNET permission to a worker package/process.
- Never pass model prompts, private memory, app preferences, API keys, user accounts or Android Context objects into script execution.
- A script receives only the explicit project files and input data selected by the user.
- No automatic import of contacts, SMS, call history, notifications, calendar, photos or downloads.
- User must explicitly approve any later export/share action.
- Learning from scripts and corrections stays on-device; execution output must not silently become training data.
- Script execution cannot grant permissions, bypass Android sandboxing, alter Jarvis policy, or weaken confirmation gates.

## Runtime options to evaluate

### Option A — Chaquopy embedded in the main app process

Chaquopy is a candidate for a fast Python 3 integration, but it is **not a security sandbox**. A script running in the app process has the app UID and can potentially reach app-private files and any process capabilities. It must not be treated as an acceptable boundary for untrusted scripts.

Use only for trusted built-in Python components if a separate sandbox cannot be proven.

### Option B — Chaquopy inside an Android isolatedProcess service

Preferred feasibility experiment, not yet proven.

- Declare a dedicated service with `android:isolatedProcess="true"`, no exported component, no intent filters, and a narrowly scoped Binder contract.
- Confirm at runtime that the worker has a distinct isolated UID and no inherited app permissions.
- Do not expose the app's filesystem paths or pass a normal `Context`/provider handle to scripts.
- Transfer only bounded project/input bytes through pipes or explicitly granted, short-lived file descriptors.
- Return bounded stdout/stderr, exit status and explicitly selected output files through the same narrow IPC channel.
- Kill the worker on timeout, cancellation, memory pressure or protocol violation.
- Treat Chaquopy initialization, native-library loading, asset access and package installation inside isolatedProcess as **unknown until a device/emulator spike proves it**.
- If Chaquopy cannot operate in isolatedProcess without reintroducing app privileges, reject this option rather than quietly moving scripts into the main process.

### Option C — separate Android app/UID worker

Fallback if Option B fails. A separately installed local worker APK can provide a real UID boundary, with an explicit, authenticated IPC protocol. It adds install/update friction and must be assessed for free/offline operation. No worker is to be installed silently.

## First milestone: offline Python workspace

1. Project browser: create/open/rename/delete project folders inside a dedicated workspace.
2. Editor: syntax-highlighted text editing, save/undo, file tabs, search and replace.
3. Runner: run one selected script with explicit input files, cancellation, bounded output and clear exit code.
4. Tests: run local tests and show pass/fail output; no automatic network package resolution.
5. Package catalog: initially only bundled, audited pure-Python standard-library support. Any third-party package must be pinned, license-reviewed, reproducibly bundled and checked for Android/native-wheel compatibility.
6. Export: user-initiated ZIP/share only, with a preview of included files and no hidden metadata.
7. Android APK building is a separate later spike. Python execution does not imply Gradle/Android SDK builds can run locally.

## Resource and execution limits (initial conservative defaults)

These are starting guardrails to validate, not device performance claims:

- One active script per worker.
- Wall-clock timeout: 10 seconds initially; user can explicitly request a longer bounded run after UI and cancellation tests exist.
- Output cap: 256 KiB combined stdout/stderr.
- Input/project transfer cap: 10 MiB per run for the first prototype.
- No child processes, shell, dynamic native library loading or arbitrary package installation.
- No network sockets, HTTP clients or DNS.
- No background or scheduled script execution.
- No access to Android services or system intents.
- Explicit cancellation and worker process termination must be tested.
- Enforce quotas in the worker, not only in UI; reject malformed IPC frames and oversized payloads.

The final limits should be adjusted only from measurements and threat-model review, not to make a failing test pass.

## Required proof tests before enabling Run

### Isolation and privacy
- Assert worker UID differs from app UID.
- Assert worker has no INTERNET, RECORD_AUDIO, contacts, SMS, storage or accessibility permissions.
- Verify scripts cannot read app-private preferences, local-learning memory, model files, tokens, databases or other projects unless those files were explicitly staged.
- Verify a script attempting DNS/HTTP/socket access fails.
- Verify no network traffic occurs during run, cancellation, crash and restart.
- Verify no crash or execution logs leave the device.
- Verify worker cannot call Jarvis's Android tool gateway or VoiceInteraction/Accessibility services.

### Robustness
- Infinite loop times out and worker is terminated.
- Large stdout/stderr is truncated safely and reported.
- Oversized input/output, malformed messages, path traversal, symlink tricks and zip bombs are rejected.
- Cancellation returns promptly and does not leave a background process.
- Process death does not corrupt the project or learning store.
- Repeated runs do not leak file descriptors or memory.
- UI remains responsive while scripts run.

### Correctness
- Standard-library smoke tests.
- File read/write only within the staged workspace.
- Deterministic test output for the same input.
- Russian UTF-8 filenames and text.
- Save/reopen project persistence.
- Device and emulator tests on arm64; do not infer OPPO performance from emulator.

## Implementation sequence

**P0 — spike only**
- Add a throwaway isolated-service experiment on a dedicated branch.
- Prove distinct UID, no permissions and Binder file-descriptor exchange.
- Test whether the selected Python runtime can initialize in that isolated process.
- Record APK size, startup latency, peak native RSS, CPU time, cancellation latency and thermal behavior.
- Do not expose it in the production UI.

**P1 — secure runner prototype**
- Only after P0 passes, implement typed request/response IPC, quotas, worker lifecycle, project staging and adversarial tests.
- Add a disabled-by-default developer screen for local testing.
- Run secret scanning, CodeQL, JVM tests, APK build and emulator tests.

**P2 — Python editor**
- Add project browser/editor and connect only to the isolated runner.
- Keep network package installation disabled.
- Add visible execution status, elapsed time, cancel button and exit code.

**P3 — APK build feasibility**
- Separately assess Gradle/Android SDK footprint, disk requirements, memory, process spawning, build time and Android licensing.
- No cloud build fallback; no source upload.
- If on-device build is impractical on this phone, provide a transparent local project export and explain the limitation rather than silently using a server.

## Acceptance criteria

This feature is not complete until:
- The worker has a distinct OS-level UID and demonstrably lacks app permissions.
- Network-egress tests pass with no outbound traffic.
- Adversarial file/path/resource tests pass.
- The editor, runner, cancel, output and project persistence work on the target OPPO device.
- The user can inspect, export and delete projects.
- A regression suite proves Python cannot access Jarvis memory, model files, credentials or phone-control tools.

## Explicitly out of scope for this spike

- Qwen/LLM integration or model training.
- Internet-based pip installs.
- Cloud execution or cloud APK builds.
- Unrestricted shell/terminal.
- Arbitrary access to phone files.
- Automatic background jobs.
- Claiming that Chaquopy itself is a sandbox.

## Handoff

Next engineer/agent: inspect current Gradle plugin versions and Android service architecture, create the isolatedProcess proof branch, and return evidence for each P0 item. If isolated runtime fails, stop and report the blocker; do not downgrade to main-process execution.
