#!/usr/bin/env python3
"""Closed-loop defect router for the Jarvis factory.

Consumes test evidence, classifies failures, emits a deterministic repair
queue, and records a handoff artifact. It never suppresses or weakens tests.
"""
from __future__ import annotations
import json, os, re, sys
from pathlib import Path

RULES = [
    ("infra", r"pixel_2|avd|emulator|sdkmanager|avdmanager|runner|gradle|ndk"),
    ("android", r"manifest|permission|service|activity|lifecycle|foreground"),
    ("coloros", r"coloros|oppo|voiceinteraction|breeno|assistant"),
    ("speech", r"asr|vad|kws|audio|microphone|sherpa|tts|piper"),
    ("native", r"jni|native|\.so|egl|gles|abi|ndk|segmentation fault"),
    ("ui", r"compose|ui|black screen|render|animation|surface"),
    ("functional", r"intent|navigation|yandex|search|phone|aidl|accessibility"),
    ("security", r"security|secret|permission|exported|credential|token"),
]

def classify(text: str) -> str:
    low = text.lower()
    scores = [(len(re.findall(p, low)), team) for team, p in RULES]
    scores.sort(reverse=True)
    return scores[0][1] if scores and scores[0][0] else "integration"

def main() -> int:
    src = Path(os.getenv("EVIDENCE_FILE", "test-evidence.json"))
    out = Path(os.getenv("QUEUE_FILE", "repair-queue.json"))
    if not src.exists():
        print(f"No evidence file: {src}")
        return 0
    data = json.loads(src.read_text(encoding="utf-8"))
    failures = data.get("failures", data if isinstance(data, list) else [])
    queue = []
    for item in failures:
        text = json.dumps(item, ensure_ascii=False)
        team = classify(text)
        queue.append({
            "status": "open",
            "team": team,
            "failure": item,
            "rule": "fix_then_retest",
            "must_preserve_tests": True,
        })
    out.write_text(json.dumps({"version": 1, "items": queue}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"repair queue: {len(queue)} item(s)")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
