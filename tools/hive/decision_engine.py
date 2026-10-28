#!/usr/bin/env python3
"""Deterministic decision scoring for Hive candidates.

This module ranks candidate solutions using evidence, ColorOS fit, functional
results, reliability, security, performance, compatibility, maintainability,
and licensing. It never promotes a hypothesis without independent evidence.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class State(str, Enum):
    HYPOTHESIS = "HYPOTHESIS"
    EXPERIMENTAL = "EXPERIMENTAL"
    VALIDATED = "VALIDATED"
    PROVEN = "PROVEN"
    PREFERRED = "PREFERRED"
    SUPERSEDED = "SUPERSEDED"
    REJECTED = "REJECTED"


WEIGHTS = {
    "coloros": 25,
    "functionality": 20,
    "reliability": 15,
    "security": 15,
    "performance": 10,
    "compatibility": 5,
    "maintainability": 5,
    "license": 5,
}


@dataclass(frozen=True)
class Candidate:
    name: str
    scores: dict[str, float]
    independent_evidence: bool = False
    regression_passed: bool = False
    license_verified: bool = False

    def score(self) -> float:
        return sum(WEIGHTS[k] * max(0.0, min(1.0, self.scores.get(k, 0.0))) for k in WEIGHTS) / 100

    def state(self) -> State:
        if not self.license_verified or not self.independent_evidence:
            return State.EXPERIMENTAL
        if not self.regression_passed:
            return State.VALIDATED
        return State.PROVEN


def rank(candidates: list[Candidate]) -> list[tuple[Candidate, float, State]]:
    return sorted(((c, c.score(), c.state()) for c in candidates), key=lambda x: x[1], reverse=True)


if __name__ == "__main__":
    print("Hive decision engine ready")
