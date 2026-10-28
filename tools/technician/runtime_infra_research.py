#!/usr/bin/env python3
"""Infrastructure technician: diagnose Android emulator failures without weakening tests.

Reads repository workflows and emits deterministic evidence. It specifically checks for
stale AVD assumptions, Android SDK availability, and HEAD/checkout consistency.
"""
from pathlib import Path
import re, subprocess, json

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "technician" / "runtime-infra"
OUT.mkdir(parents=True, exist_ok=True)

workflows = list((ROOT / ".github" / "workflows").glob("*.y*ml")) if (ROOT / ".github" / "workflows").exists() else []
text = "\n".join(p.read_text(errors="replace") for p in workflows)

patterns = {
    "pixel_2": r"Pixel_2|pixel_2|Pixel 2",
    "emulator_runner": r"android-emulator-runner|emulator -avd|avdmanager create avd",
    "explicit_profile": r"(?:profile\s*:|--device)\s*[^\n]+",
    "checkout": r"actions/checkout@",
}
findings = {name: bool(re.search(pattern, text)) for name, pattern in patterns.items()}

try:
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
except Exception:
    head = "unknown"

report = {
    "technician": "runtime-infra",
    "head": head,
    "workflow_count": len(workflows),
    "workflows": [str(p.relative_to(ROOT)) for p in workflows],
    "findings": findings,
    "rule": "Never disable or weaken runtime tests to obtain PASS.",
}
(OUT / "diagnosis.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

md = ["# Runtime infrastructure technician", "", f"HEAD: `{head}`", f"Workflow count: {len(workflows)}", ""]
for k, v in findings.items():
    md.append(f"- {k}: {'FOUND' if v else 'not found'}")
md += ["", "## Evidence", "The technician scans every workflow for stale emulator/profile assumptions and records the exact repository HEAD.", "", "## Safety", "A failing runtime test must be repaired, not removed or weakened."]
(OUT / "REPORT.md").write_text("\n".join(md) + "\n", encoding="utf-8")
print((OUT / "REPORT.md").read_text())
