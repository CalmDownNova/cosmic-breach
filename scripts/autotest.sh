#!/usr/bin/env bash
# Runs one autotest scenario in the hidden test client while scripts/poll-windows.ps1 watches
# for any visible window (and kills the test client if one ever appears).
#
#   scripts/autotest.sh <scenario> [timeout seconds, default 400]
#
# Results: run-test/autotest/<scenario>/ (result.txt, log.txt, screenshots). Next to that
# folder: <scenario>-gradle.log (full game log) and <scenario>-windows.log (window polls).
# Exits 0 only when result.txt says PASS and no window incident was logged.
# One run at a time: every run shares run-test/.
set -u
scenario="${1:?usage: scripts/autotest.sh <scenario> [timeout seconds]}"
limit="${2:-400}"
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/run-test/autotest"
mkdir -p "$out"
poll_log="$out/$scenario-windows.log"
gradle_log="$out/$scenario-gradle.log"
stop="$out/$scenario-windows.stop"
rm -f "$poll_log" "$stop"

powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$root/scripts/poll-windows.ps1")" \
    -LogFile "$(cygpath -w "$poll_log")" -StopFile "$(cygpath -w "$stop")" -MaxSeconds $((limit + 60)) &
poller=$!
sleep 3 # let the poller start before the game can

started=$(date +%s)
# AUTOTEST_ARGS: more Gradle arguments (-Pjfr=<file> to profile, -PrenderCompat=<dir> for Sodium and Iris)
(cd "$root" && timeout "$limit" ./gradlew runTestClient -Pautotest="$scenario" ${AUTOTEST_ARGS:-} > "$gradle_log" 2>&1)
gradle_exit=$?
elapsed=$(( $(date +%s) - started ))
touch "$stop"
wait "$poller"
rm -f "$stop"

result="$(cat "$out/$scenario/result.txt" 2>/dev/null || echo 'FAIL no result.txt was written')"
polls=$(grep -c 'titled java windows' "$poll_log")
incidents=$(grep -c 'INCIDENT' "$poll_log")
echo "scenario:  $scenario"
echo "result:    $result"
echo "time:      ${elapsed}s, gradle exit code $gradle_exit (124 = the ${limit}s timeout hit)"
echo "windows:   $polls polls, $incidents incident(s), log $poll_log"
grep 'INCIDENT' "$poll_log"
echo "outputs:   $(ls "$out/$scenario" 2>/dev/null | tr '\n' ' ')"
[ "$incidents" -eq 0 ] && [ "${result%% *}" = "PASS" ]
