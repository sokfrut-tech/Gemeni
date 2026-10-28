# HIVE Evolution Protocol

## Purpose
Gemeni is operated as a continuously improving engineering hive. Agents do not merely execute tickets: they observe failures, research alternatives, test hypotheses, preserve evidence, and promote proven improvements into reusable engineering knowledge.

## Operating loop
1. Observe: collect build, runtime, QA, security and compatibility evidence.
2. Diagnose: identify root cause and affected subsystem.
3. Research: search official documentation, source repositories, issue trackers and technical communities across regions and languages.
4. Compare: score candidate solutions for correctness, license compatibility, ColorOS compatibility, Android compatibility, security, performance and maintainability.
5. Experiment: implement the smallest reversible change on an isolated branch.
6. Verify: run independent tests and regression checks.
7. Promote: only independently verified solutions may become `PROVEN` knowledge or default implementation patterns.
8. Preserve: retain superseded approaches as historical evidence when useful for rollback or future comparison.

## Global research rule
Research is not geographically constrained. Chinese, Japanese, Russian, Indian, English, European and other technical sources may be considered. External claims are hypotheses until reproduced or independently validated in this project.

## Reuse rule
Prefer mature, proven open-source implementations over reinvention when their licenses permit use. Every external component must be inventoried with source, version/commit, license, notices and redistribution conditions. Third-party code is never silently relicensed as project code.

## ColorOS-first rule
Chinese OPPO/ColorOS is the reference platform. A solution must work on the reference ColorOS target before being treated as the primary implementation. AOSP/other Android compatibility is developed as an adaptation layer afterward, without weakening the ColorOS path.

## Knowledge states
- `HYPOTHESIS`: unverified idea.
- `EXPERIMENTAL`: implemented on an isolated branch.
- `VALIDATED`: passes defined tests once.
- `PROVEN`: independently reproduced and regression-tested.
- `PREFERRED`: proven solution currently selected as the factory default.
- `SUPERSEDED`: previously proven but replaced by a better solution.
- `REJECTED`: disproven or unsafe.

## Safety gates
No agent may obtain PASS by deleting, disabling, weakening or bypassing a failing test. Security findings marked critical/high block release. Automated repair must remain reversible and auditable.

## Continuous improvement
At each production cycle the Director reviews recurring failures, successful repairs, test coverage gaps, dependency changes and research discoveries. The Director may propose updates to agent skills, routing rules, test matrices and preferred implementations, but promotion requires evidence.

## Parallelism
Research, implementation, testing and security review should proceed concurrently where dependencies allow. A failed lane creates a repair task; it must not unnecessarily stop unrelated lanes.
