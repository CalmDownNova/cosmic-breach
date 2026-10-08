#!/usr/bin/env bash
# Tests for scripts/autotest.sh's exit status: a stub gradlew and a stub window poller stand in for the game, so a run takes a
# few seconds. What matters is that a scenario's PASS followed by a game that does not exit is reported as a stall and fails.
#
#   scripts/autotest-test.sh [path to an autotest.sh]     (default: the one beside this file)
#
# One line per check, then "N passed, M failed"; exits 1 on any failure.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
src="${1:-$here/autotest.sh}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

root="$tmp/root"
mkdir -p "$root/scripts" "$tmp/bin"
cp "$src" "$root/scripts/autotest.sh"
[ -f "$here/autotest-verdict.sh" ] && cp "$here/autotest-verdict.sh" "$root/scripts/autotest-verdict.sh"
# the client gate beside it, pointed at a scratch lock (the shared one is never touched)
lock="$tmp/gate.lock"
if [ -f "$here/client-gate.sh" ]; then
  sed "s|^LOCK=.*|LOCK=$lock|" "$here/client-gate.sh" > "$root/scripts/client-gate.sh"
fi
: > "$root/scripts/poll-windows.ps1"

# the window poller: writes the log file it was given and exits
cat > "$tmp/bin/powershell" <<'STUB'
#!/usr/bin/env bash
log=
while [ $# -gt 0 ]; do
  case "$1" in -LogFile) shift; log="$1" ;; esac
  shift
done
if [ -n "$log" ]; then
  log="$(cygpath -u "$log")"
  mkdir -p "$(dirname "$log")"
  echo "polled: 0 titled java windows" > "$log"
fi
STUB
chmod +x "$tmp/bin/powershell"

# the game: writes a result as the scenario would, then exits the way STUB_MODE says
cat > "$root/gradlew" <<'STUB'
#!/usr/bin/env bash
scenario=
for a in "$@"; do case "$a" in -Pautotest=*) scenario="${a#-Pautotest=}" ;; esac; done
dir="run-test/autotest/$scenario"
mkdir -p "$dir"
case "${STUB_MODE:-clean}" in
  clean) echo PASS > "$dir/result.txt"; echo "[00:00:01] stopped"; exit 0 ;;
  slow) sleep 4; echo PASS > "$dir/result.txt"; echo "[00:00:05] stopped"; exit 0 ;;
  stall-old) echo PASS > "$dir/result.txt"; echo "[00:01:01] [watchdog/ERROR] [autotest] the game did not exit 60 s after the result; halting"; exit 1 ;;
  stall-new) echo PASS > "$dir/result.txt"; echo "[00:01:01] [watchdog/ERROR] [autotest] the game did not exit 60 s after the result; halting"
             echo "FAIL the game did not exit 60 s after the result (was: PASS)" > "$dir/result.txt"; exit 1 ;;
  crash) echo PASS > "$dir/result.txt"; echo "[00:00:01] exception while stopping"; exit 1 ;;
  fail) echo "FAIL a check failed: the cap holds" > "$dir/result.txt"; exit 0 ;;
esac
STUB
chmod +x "$root/gradlew"
export PATH="$tmp/bin:$PATH" ROOT="$root"

pass=0
fail=0
out=
rc=
t() {
  if eval "$2"; then
    pass=$((pass + 1))
    echo "PASS  $1"
  else
    fail=$((fail + 1))
    echo "FAIL  $1  (rc=$rc)"
    printf '%s\n' "$out" | sed 's/^/        | /'
  fi
}
has() { case "$1" in *"$2"*) return 0 ;; *) return 1 ;; esac; }
run() { # <stub mode> <scenario>
  out=$(STUB_MODE="$1" bash "$root/scripts/autotest.sh" "$2" 30 2>&1)
  rc=$?
}

echo "== a clean run"
run clean quiet
t "PASS and a clean exit: exit 0, no stall line" '[ "$rc" -eq 0 ] && has "$out" "result:    PASS" && ! has "$out" STALL'

echo "== a PASS that did not exit"
run stall-old hung
t "PASS written, the game halted by the watchdog: exit 1" '[ "$rc" -eq 1 ]'
t "the result line says FAIL, with what the scenario wrote" 'has "$out" "result:    FAIL " && has "$out" "(was: PASS)"'
t "and a STALL line names it" 'has "$out" "STALL:"'
run stall-new hung2
t "a result the harness already rewrote to FAIL still prints the STALL line and exits 1" '[ "$rc" -eq 1 ] && has "$out" "STALL:" && has "$out" "result:    FAIL "'
run crash crashed
t "PASS then a nonzero exit with no watchdog line (a crash at exit): exit 1 and a STALL line" '[ "$rc" -eq 1 ] && has "$out" "STALL:" && has "$out" "gradle exit 1"'

echo "== a scenario that failed"
run fail failed
t "FAIL and a clean exit: exit 1, no stall line, the scenario's own reason" '[ "$rc" -eq 1 ] && ! has "$out" STALL && has "$out" "FAIL a check failed: the cap holds"'

echo "== the gate's lock keeps beating while the game runs"
echo 'laneA client 2026-10-02 11:21:51 5748MB' > "$lock"
touch -d '1 hour ago' "$lock"
GATE_BEAT_SEC=1 run slow beating
t "autotest.sh pulses the lock for as long as the run lasts" '[ $(( $(date +%s) - $(stat -c %Y "$lock") )) -le 3 ]'
sleep 3
last=$(stat -c %Y "$lock")
sleep 3
t "and stops pulsing when the run is over" '[ "$(stat -c %Y "$lock")" = "$last" ]'
rm -f "$lock"
GATE_BEAT_SEC=1 run slow nolock
t "with no lock held there is nothing to pulse and the run is unaffected" '[ "$rc" -eq 0 ] && [ ! -e "$lock" ]'

echo "== the pulse ends with the run (a late touch once left an empty lock that blocked every lane)"
echo 'laneA client 2026-10-02 11:21:51 5748MB' > "$lock"
GATE_BEAT_SEC=1 run slow ending
rm -f "$lock"
echo 'laneB client 2026-10-02 12:00:00 5000MB' > "$lock"
touch -d '1 hour ago' "$lock"
sleep 3
t "a lock another lane takes the moment the run returns is never touched by its pulse" '[ $(( $(date +%s) - $(stat -c %Y "$lock") )) -ge 3000 ]'
rm -f "$lock"
printf '%s\n' 'laneA client 2026-10-02 11:21:51 5748MB' 'token=tokA' "acquired=$(date +%s)" 'mode=split' 'holder=0' 'holder_start=' > "$lock"
touch -d '1 hour ago' "$lock"
(sleep 2; printf '%s\n' 'laneB client 2026-10-02 12:00:00 5000MB' 'token=tokB' "acquired=$(date +%s)" 'mode=split' 'holder=0' 'holder_start=' > "$lock"; touch -d '1 hour ago' "$lock") &
swapper=$!
GATE_BEAT_SEC=1 run slow swapped
wait "$swapper"
sleep 1
t "and a run's pulse stops beating once another lane's lock replaces the one it began on" '[ $(( $(date +%s) - $(stat -c %Y "$lock") )) -ge 3000 ]'
rm -f "$lock"
printf '%s\n' 'laneA client 2026-10-02 11:21:51 5748MB' 'token=tokA' "acquired=$(( $(date +%s) - 3600 ))" 'mode=split' 'holder=0' 'holder_start=' > "$lock"
touch -d '1 hour ago' "$lock"
GATE_BEAT_SEC=1 run slow settled
t "a run that ends cleanly takes its pulse's beats back: the lock's last write is outside the window a bare release respects (2 beats)" '[ $(( $(date +%s) - $(stat -c %Y "$lock") )) -ge 3 ] && [ "$rc" -eq 0 ]'
rm -f "$lock"

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
