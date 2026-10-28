#!/usr/bin/env python3
"""Minimal research ledger schema and validation for Hive memory."""
from __future__ import annotations

REQUIRED = {
    "source",
    "component",
    "version",
    "license",
    "evidence",
    "tests",
    "result",
}


def validate(entry: dict) -> tuple[bool, list[str]]:
    missing = sorted(REQUIRED - entry.keys())
    if missing:
        return False, missing
    if entry["result"] not in {"unknown", "failed", "validated", "proven", "superseded"}:
        return False, ["invalid result"]
    if entry["result"] in {"proven", "validated"} and not entry["evidence"]:
        return False, ["evidence required"]
    return True, []


if __name__ == "__main__":
    print("Hive research ledger ready")
