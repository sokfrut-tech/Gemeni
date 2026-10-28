"""Continuous improvement coordinator for the Hive factory.

Records validated decisions and keeps repair, research, innovation and release
control as independent lanes. It never promotes a hypothesis without evidence.
"""
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List


class Lane(str, Enum):
    REPAIR = "repair"
    RESEARCH = "research"
    INNOVATION = "innovation"
    RELEASE = "release"


class Status(str, Enum):
    HYPOTHESIS = "hypothesis"
    EXPERIMENTAL = "experimental"
    VALIDATED = "validated"
    PROVEN = "proven"
    PREFERRED = "preferred"
    SUPERSEDED = "superseded"
    REJECTED = "rejected"


@dataclass(frozen=True)
class Evidence:
    test_name: str
    passed: bool
    artifact: str


@dataclass
class Decision:
    key: str
    lane: Lane
    status: Status
    score: float
    evidence: List[Evidence]


class ContinuousImprovement:
    def __init__(self) -> None:
        self.decisions: Dict[str, Decision] = {}

    def register(self, decision: Decision) -> None:
        self.decisions[decision.key] = decision

    def can_promote(self, key: str) -> bool:
        d = self.decisions[key]
        return bool(d.evidence) and all(e.passed and e.artifact for e in d.evidence)

    def promote(self, key: str, target: Status) -> bool:
        if target in {Status.VALIDATED, Status.PROVEN, Status.PREFERRED} and not self.can_promote(key):
            return False
        self.decisions[key].status = target
        return True

    def best(self, lane: Lane) -> List[Decision]:
        return sorted(
            (d for d in self.decisions.values() if d.lane == lane and d.status in {Status.PROVEN, Status.PREFERRED}),
            key=lambda d: d.score,
            reverse=True,
        )
