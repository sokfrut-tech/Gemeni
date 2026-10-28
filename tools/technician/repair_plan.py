#!/usr/bin/env python3
"""Deterministic repair-plan generator for the Jarvis factory.

Consumes a test failure summary and emits a bounded repair plan. It never
weakens/deletes tests; every repair must preserve the failing assertion and
produce evidence before being accepted.
"""
from __future__ import annotations
import json, sys
from pathlib import Path

ROUTES = {
    "infra": "Infra Technician",
    "emulator": "Infra Technician",
    "gradle": "Infra Technician",
    "android": "Android/ColorOS Technician",
    "coloros": "Android/ColorOS Technician",
    "speech": "Speech Technician",
    "asr": "Speech Technician",
    "vad": "Speech Technician",
    "tts": "Speech Technician",
    "native": "Native/GLES Technician",
    "gles": "Native/GLES Technician",
    "jni": "Native/GLES Technician",
    "ui": "UI Technician",
    "compose": "UI Technician",
    "intent": "Functional Technician",
    "functional": "Functional Technician",
    "security": "Security Technician",
    "manifest": "Security Technician",
}

def route(text: str) -> str:
    low = text.lower()
    for key, worker in ROUTES.items():
        if key in low:
            return worker
    return "Foreman / Integration Technician"

def main() -> int:
    src = Path(sys.argv[1] if len(sys.argv) > 1 else "test-results/failures.json")
    data = json.loads(src.read_text(encoding="utf-8"))
    failures = data.get("failures", [])
    plans = []
    for failure in failures:
        summary = str(failure.get("summary", failure))
        plans.append({
            "owner": route(summary),
            "summary": summary,
            "required_evidence": [
                "minimal diff",
                "reproduction before fix",
                "same test passing after fix",
                "no test deletion/weakening",
                "build/runtime evidence",
            ],
            "status": "queued",
        })
    out = {"schema": 1, "failures": plans, "auto_merge": False}
    Path("test-results").mkdir(exist_ok=True)
    Path("test-results/repair-plan.json").write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(out, indent=2, ensure_ascii=False))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
