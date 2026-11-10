#!/usr/bin/env bash
set +e

gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace
test_exit=$?

mkdir -p emulator-diagnostics/ui-evidence
adb pull /sdcard/Download/jarvis-test emulator-diagnostics/ui-evidence
pull_exit=$?

printf 'gradle_test_exit=%s\n' "$test_exit" > emulator-diagnostics/ui-evidence/pull-status.txt
printf 'ui_evidence_pull_exit=%s\n' "$pull_exit" >> emulator-diagnostics/ui-evidence/pull-status.txt

# The Gradle instrumentation result is authoritative. Evidence collection is best-effort.
exit "$test_exit"
