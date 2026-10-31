# Jarvis / Gemeni — continuity handoff

Last updated: 2026-10-31

## Purpose
Jarvis / ColorOS assistant and the multi-agent “Jarvis Factory” orchestration project. Repository: https://github.com/sokfrut-tech/Gemeni

## Known state
- The repository README identifies this as an open Russian-language assistant stack for OPPO/ColorOS.
- A previous configuration commit was recorded as `e8b4b925...` (full SHA should be checked in Git history).
- “Jarvis Factory — Director #52” / run `40742925402` was reported successful, but the run was only about 28 seconds and did not execute a real multi-model review because endpoint variables were empty.
- Reported missing configuration at that time: `QWEN_URL`, `DEEPSEEK_URL`, `GENERIC_URL`. The likely Qwen connection variables to verify are `JARVIS_QWEN_BASE_URL` and `JARVIS_QWEN_API_KEY`; confirm actual workflow references before changing names.
- The user has been configuring OpenRouter/Qwen and asked where to set `QWEN_URL`. Do not assume a value was saved successfully.
- Jarvis Android architecture described by the user: Kotlin + Compose/coroutines, NDK r26b C++17/JNI/EGL/OpenGL ES, VoiceInteractionService/AccessibilityService, Sherpa-ONNX ASR, Silero VAD, Piper TTS, hotword “Джарвис”, Gemini fallback, Protobuf CardWidget.
- A reported app symptom was a black screen / non-functional launch. This must be reproduced and diagnosed from the current code; do not claim it is fixed without a fresh build/device test.

## Immediate next steps
1. Inspect current default branch, active branches, latest commits, workflow YAML and run logs.
2. Trace exact variable/secret names referenced by the workflow; compare with configured GitHub Variables/Secrets without exposing values.
3. Run a real factory workflow and verify that Qwen and any configured second/third reviewer are actually called (not merely that the workflow job is green).
4. For the Android app, reproduce the black screen, capture Logcat/build output, identify the root cause, fix in a feature branch, and run tests/build.
5. Keep a dated record of each run, commit SHA, artifact URL and test evidence.

## Working rules
- Never commit API keys, tokens, passwords or secret values. Use GitHub Actions Secrets for credentials and Variables for non-secret endpoints/model names.
- Do not report a workflow as functionally successful solely because its status is green; verify the intended calls and outputs.
- Preserve the user's preference for visible progress reports and plain-language instructions.
- Use feature branches and pull requests for code changes; do not silently overwrite working code.

## Evidence still needed
- Current workflow run URL and complete logs.
- Exact current branch/commit.
- Confirmation of actual Qwen endpoint/model and whether the API key is present as a secret (never print it).
- Fresh Android build and reproduction evidence for the black screen.
