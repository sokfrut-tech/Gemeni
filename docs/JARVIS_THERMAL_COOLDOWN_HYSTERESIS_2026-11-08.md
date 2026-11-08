# Jarvis thermal cooldown hysteresis — 2026-11-08

## Purpose

Prevent Jarvis from immediately restoring its highest local-LLM workload when Android thermal status briefly drops. A transient cooler sample must not cause repeated full-load / throttled oscillation.

## Policy

- Worsening Android thermal status applies immediately.
- Improving status is accepted only after it remains at the improved level for 30 seconds.
- A renewed heating signal immediately tightens the budget and restarts the recovery window.
- Unknown Android status values remain fail-closed as CRITICAL.
- The policy uses monotonic `SystemClock.elapsedRealtime()`; wall-clock changes cannot accelerate cooldown.
- This guard only budgets Jarvis-owned inference. It does not control CPU/GPU frequencies or replace Android/ColorOS thermal management.

## Implementation

- `ThermalBudgetHysteresis` is a pure Kotlin state holder with injected time.
- `AndroidThermalGuard` samples the latest platform status and passes monotonic time to the hysteresis policy.
- Existing token budgets remain unchanged: NORMAL 160, WARM 96, HOT 48, CRITICAL 0 / heavy inference blocked.

## Tests added

- Immediate escalation.
- Recovery only after the full 30-second stable interval.
- Renewed heating restarts the cooldown window.
- Monotonic-clock rollback restarts the window conservatively.
- Existing Android status mapping remains covered.

## Remaining validation

CI must compile the Android module and run JVM tests on this exact branch head. Emulator tests cannot validate physical device temperature behavior. OPPO Find X6 Pro validation is still required with repeated inference/voice sessions, ambient conditions, battery/charging state, and observed Android thermal-status transitions.
