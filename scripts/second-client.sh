#!/usr/bin/env bash
# Starts a second hidden test client with plain java, for multiplayer runs where the first client (scripts/autotest.sh)
# holds this project's Gradle build, and waits for its result. The window poller watches it like autotest.sh does.
#
# First write its launch command (same -P options as runTestClient; its own game folder, its own name):
#   ./gradlew writeTestClientCommand -PtestGameDir=run-test-b -Pautotest=<scenario> -Pjoin=127.0.0.1:25565 \
#       -Pusername=<name> -PnoCompat -PclientHeap=3G
# Then:
#   scripts/second-client.sh run-test-b <scenario> [timeout seconds, default 600]
#
# Results: <game folder>/autotest/<scenario>/ (result.txt, log.txt), <scenario>-client.log and <scenario>-windows.log
# beside it. Exits 0 only when result.txt says PASS and no window incident was logged.
set -u
dir="${1:?usage: scripts/second-client.sh <game folder> <scenario> [timeout seconds]}"
scenario="${2:?usage: scripts/second-client.sh <game folder> <scenario> [timeout seconds]}"
limit="${3:-600}"
root="$(cd "$(dirname "$0")/.." && pwd)"
game="$root/$dir"
launch="$game/launch"
if [ ! -f "$launch/command.txt" ]; then
    echo "no $launch/command.txt: run ./gradlew writeTestClientCommand -PtestGameDir=$dir ... first"
    exit 2
fi
if ! cat "$launch/command.txt" "$launch"/*VmArgs.txt 2>/dev/null | tr -d '\r' | grep -q -- "-Dcosmicbreach.autotest=$scenario\$"; then
    echo "the launch command in $launch was written for another scenario (write it again with -Pautotest=$scenario)"
    exit 2
fi
out="$game/autotest"
mkdir -p "$out"
poll_log="$out/$scenario-windows.log"
stop="$out/$scenario-windows.stop"
rm -f "$poll_log" "$stop" "$out/$scenario/result.txt"

powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$root/scripts/poll-windows.ps1")" \
    -LogFile "$(cygpath -w "$poll_log")" -StopFile "$(cygpath -w "$stop")" -MaxSeconds $((limit + 60)) &
poller=$!
sleep 3 # let the poller start before the game can

mapfile -t args < <(tr -d '\r' < "$launch/command.txt" | sed '/^$/d')
if [ -f "$launch/environment.txt" ]; then
    while IFS='=' read -r key value; do
        [ -n "$key" ] && export "$key=$value"
    done < <(tr -d '\r' < "$launch/environment.txt")
fi
started=$(date +%s)
(cd "$game" && MSYS_NO_PATHCONV=1 timeout "$limit" "${args[@]}" > "$out/$scenario-client.log" 2>&1)
client_exit=$?
elapsed=$(( $(date +%s) - started ))
touch "$stop"
wait "$poller"
rm -f "$stop"

result="$(cat "$out/$scenario/result.txt" 2>/dev/null || echo 'FAIL no result.txt was written')"
polls=$(grep -c 'titled java windows' "$poll_log")
incidents=$(grep -c 'INCIDENT' "$poll_log")
echo "scenario:  $scenario (second client, $dir)"
echo "result:    $result"
echo "time:      ${elapsed}s, client exit code $client_exit (124 = the ${limit}s timeout hit)"
echo "windows:   $polls polls, $incidents incident(s), log $poll_log"
grep 'INCIDENT' "$poll_log"
[ "$incidents" -eq 0 ] && [ "${result%% *}" = "PASS" ]
