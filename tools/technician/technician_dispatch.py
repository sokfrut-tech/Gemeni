#!/usr/bin/env python3
"""Jarvis Factory repair dispatcher.

Classifies evidence into independent technician lanes. It never hides a failure and
never disables a test to make the pipeline green. Each lane produces a repair ticket
for the next pipeline attempt.
"""
import json, pathlib, re, sys

TECHNICIANS = {
    "infra": [r"Pixel_2", r"No device found", r"AVD", r"sdkmanager", r"gradle", r"NDK", r"emulator"],
    "android": [r"Manifest", r"permission", r"Activity", r"Service", r"VoiceInteraction", r"SecurityException"],
    "speech": [r"AudioRecord", r"Sherpa", r"Silero", r"KWS", r"VAD", r"ASR", r"Piper", r"TTS"],
    "native": [r"JNI", r"UnsatisfiedLinkError", r"SIGSEGV", r"EGL", r"GLES", r"lib.*\.so", r"ABI"],
    "ui": [r"black screen", r"Compose", r"render", r"Surface", r"timeout", r"screenshot"],
    "functional": [r"Intent", r"Navigator", r"search", r"phone", r"AIDL", r"not found", r"does not open"],
    "security": [r"secret", r"token", r"exported", r"permission", r"cleartext", r"credential"],
    "qa": [r"test failed", r"assert", r"runtime", r"e2e", r"smoke", r"instrumentation"],
}

def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    evidence = root / "build" / "factory"
    text = ""
    if evidence.exists():
        for p in evidence.rglob("*"):
            if p.is_file():
                try: text += "\n" + p.read_text(errors="replace")
                except Exception: pass
    tickets = []
    for tech, patterns in TECHNICIANS.items():
        hits = [p for p in patterns if re.search(p, text, re.I)]
        if hits:
            tickets.append({"technician": tech, "priority": "P0" if tech in ("infra","android","speech","native") else "P1", "signals": hits, "action": "diagnose -> minimal fix -> test -> evidence"})
    out = evidence / "technician-dispatch.json"
    evidence.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"tickets": tickets, "policy": "Never disable a failing test to obtain green status."}, ensure_ascii=False, indent=2))
    print(json.dumps({"tickets": tickets}, ensure_ascii=False))

if __name__ == "__main__": main()
