# HIVE Global Evolution Engine

## Mission
The factory continuously improves the product while keeping release safety independent.

## Global evidence sources
Research may use public technical documentation, source repositories, issue trackers, standards, academic material, and community reports from any country/language. Country is metadata, not a quality score.

## Continuous loop
1. Observe failures, regressions, performance and user-visible defects.
2. Research multiple independent solutions.
3. Record source, license, version, applicability and evidence.
4. Generate competing implementation hypotheses.
5. Run isolated experiments.
6. Score results on correctness, ColorOS compatibility, Android compatibility, security, performance, maintainability and license compliance.
7. Promote only independently validated solutions.
8. Preserve superseded solutions as rollback knowledge.
9. Add regression tests before promotion.
10. Re-evaluate preferred solutions when new evidence appears.

## ColorOS-first constraint
Chinese OPPO/ColorOS is the primary target. A generic Android solution cannot replace a ColorOS validation. Generic compatibility is developed after the ColorOS path is functional.

## Agent roles
Research, architecture, implementation, runtime/infra, Android, ColorOS, speech, native/GLES, UI, functional integration, security, QA, integration and release control work in parallel where dependencies allow.

## Learning safety
Agents must not silently promote guesses. Every promoted decision requires reproducible evidence. Agents may not weaken, delete, skip or rewrite tests solely to obtain PASS.

## Knowledge states
HYPOTHESIS -> EXPERIMENTAL -> VALIDATED -> PROVEN -> PREFERRED.
Failed approaches remain recorded as REJECTED or SUPERSEDED with evidence.

## Release rule
Only independent technical-control and security gates can authorize release. Critical/high security findings or failed mandatory ColorOS runtime tests block release.
