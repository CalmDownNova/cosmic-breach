#!/usr/bin/env bash
# What a run's result is once its exit is taken into account. Sourced by scripts/autotest.sh; tested by
# scripts/autotest-verdict-test.sh.
#
#   autotest_verdict <result text> <gradle exit code> <gradle log> <time limit in seconds>
#
# prints the result text to report. A scenario's PASS is only a pass if the game then exited: a stop that hangs (the
# harness's watchdog writes "did not exit" and halts the game, or Gradle returns nonzero for any other reason) is a FAIL
# that says what the result was before (A2 quality review, Important 1: a hung stop used to end with "result: PASS" and
# exit status 0). Anything that is not a PASS comes back unchanged.

autotest_verdict() {
  local result="$1" code="$2" log="$3" limit="$4"
  if [ "${result%% *}" != "PASS" ]; then
    printf '%s\n' "$result"
    return 0
  fi
  if grep -q "the game did not exit" "$log" 2>/dev/null; then
    printf '%s\n' "FAIL the game did not exit after the result and the watchdog halted it (was: $result)"
  elif [ "$code" = 124 ]; then
    printf '%s\n' "FAIL the game did not exit before the time limit of $limit s, gradle exit 124 (was: $result)"
  elif [ "$code" != 0 ]; then
    printf '%s\n' "FAIL the game did not exit cleanly after the result, gradle exit $code (was: $result)"
  else
    printf '%s\n' "$result"
  fi
}
