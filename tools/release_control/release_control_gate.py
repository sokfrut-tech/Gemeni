#!/usr/bin/env python3
"""Final Technical Control & Security Gate for Jarvis/Gemeni.

This gate is intentionally conservative: missing evidence is a failure, not a pass.
It produces a machine-readable verdict for the factory director.
"""
from __future__ import annotations

import json
import sys
from dataclasses import dataclass
from pathlib import Path

REQUIRED_CHECKS = (
    "build",
    "runtime",
    "functional",
    "security",
    "manifest",
    "abi",
    "artifact",
)

@dataclass
class Verdict:
    passed: bool
    reasons: list[str]


def evaluate(evidence_path: Path) -> Verdict:
    if not evidence_path.exists():
        return Verdict(False, [f"evidence file missing: {evidence_path}"])
    try:
        data = json.loads(evidence_path.read_text(encoding="utf-8"))
    except Exception as exc:
        return Verdict(False, [f"invalid evidence JSON: {exc}"])

    reasons: list[str] = []
    checks = data.get("checks", {})
    for name in REQUIRED_CHECKS:
        value = checks.get(name)
        if value is not True:
            reasons.append(f"required check is not PASS: {name}")

    # Security is a hard gate. No severity above informational is acceptable.
    security = data.get("security", {})
    if security.get("status") != "PASS":
        reasons.append("security gate is not PASS")
    if security.get("critical", 0) != 0:
        reasons.append("critical security findings present")
    if security.get("high", 0) != 0:
        reasons.append("high security findings present")

    # Independent QA evidence is mandatory.
    if data.get("independent_qa") is not True:
        reasons.append("independent QA evidence missing")

    return Verdict(not reasons, reasons)


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("artifacts/release-evidence.json")
    verdict = evaluate(path)
    output = {"status": "PASS" if verdict.passed else "BLOCK", "reasons": verdict.reasons}
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0 if verdict.passed else 1

if __name__ == "__main__":
    raise SystemExit(main())
