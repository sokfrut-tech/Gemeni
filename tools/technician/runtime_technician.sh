#!/usr/bin/env bash
set -euo pipefail

# Jarvis Factory Technician: deterministic, evidence-driven repair loop.
# This agent only applies narrowly-scoped, known-safe CI/runtime repairs.
REPO_ROOT="${1:-.}"
cd "$REPO_ROOT"

mkdir -p build/technician
REPORT=build/technician/TECHNICIAN_REPORT.md

{
  echo "# Jarvis Runtime Technician"
  echo
  echo "- commit: ${GITHUB_SHA:-local}"
  echo "- time: $(date -u +%FT%TZ)"
  echo
  echo "## Checks"
} > "$REPORT"

changed=0

# Known CI defect: emulator-runner rejects the Pixel_2 profile on some current
# runner images. Let the runner choose/create a compatible API-35 AVD instead.
if grep -q "^[[:space:]]*profile: Pixel_2[[:space:]]*$" .github/workflows/build.yml; then
  sed -i '/^[[:space:]]*profile: Pixel_2[[:space:]]*$/d' .github/workflows/build.yml
  changed=1
  echo "- fixed: removed obsolete Pixel_2 emulator profile; API-35 runner now selects the compatible AVD" >> "$REPORT"
fi

# Never silently weaken runtime verification.
if ! grep -q "connectedDebugAndroidTest" .github/workflows/build.yml; then
  echo "- BLOCKED: connectedDebugAndroidTest is missing; refusing to weaken the gate" >> "$REPORT"
  exit 2
fi

git diff --check

git status --short >> "$REPORT"

echo "changed=$changed" >> "$REPORT"
cat "$REPORT"

if [ "$changed" -eq 0 ]; then
  exit 0
fi

if [ "${JARVIS_TECHNICIAN_COMMIT:-false}" != "true" ]; then
  echo "repair prepared; commit disabled in this invocation"
  exit 0
fi

git config user.name "jarvis-factory-technician"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add .github/workflows/build.yml tools/technician/runtime_technician.sh build/technician/TECHNICIAN_REPORT.md
git commit -m "Factory technician: repair Android emulator runtime"
git push origin HEAD:main
