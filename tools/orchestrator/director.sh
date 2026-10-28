#!/usr/bin/env bash
set -euo pipefail

# Jarvis Factory director: deterministic, resumable, evidence-first.
# It never deletes prior evidence and never treats Gradle success as product readiness.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/factory-director/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT"
cd "$ROOT"

SHA="$(git rev-parse HEAD)"
printf '%s\n' "commit=$SHA" "started=$(date -u +%FT%TZ)" > "$OUT/intake.txt"

python3 tools/agent_swarm/run_swarm.py --repo "$ROOT" --out "$OUT/swarm" > "$OUT/swarm.log" 2>&1 || true

cat > "$OUT/handoff.md" <<EOF
# Jarvis Factory Director Handoff

- commit: $SHA
- started: $(date -u +%FT%TZ)
- deterministic swarm evidence: swarm/

## Resume protocol

1. Read `docs/CONTINUATION_STATE.md`.
2. Read `docs/CURRENT_STATUS.md`.
3. Read `docs/CHANGELOG_AI.md`.
4. Read `docs/AI_AGENT_PROTOCOL.md`.
5. Read this handoff and the latest swarm evidence.
6. Continue only from evidence-backed blockers; do not repeat already proven work.

## Release rule

A green Gradle build is insufficient. Release requires runtime proof of the full voice/action/UI chain and a security/native inspection.
EOF

echo "Director cycle complete: $OUT"
