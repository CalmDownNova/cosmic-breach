#!/usr/bin/env bash
# Tests for scripts/client-gate.sh, against a scratch lock and a stub powershell; the shared lock is never touched.
#
#   scripts/client-gate-test.sh [path to a client-gate.sh]     (default: the one beside this file)
#
# The gate under test is a copy whose LOCK= line points into a scratch folder. The stub powershell prints $FAKE_FREE
# (default 9000) with the CR a real one prints. Holders are real background processes (their Windows pids and start times
# come from ps -W, as the gate reads them). One line per check, then "N passed, M failed"; exits 1 on any failure.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
src="${1:-$here/client-gate.sh}"
tmp="$(mktemp -d)"
procs=()
trap 'kill "${procs[@]}" 2>/dev/null; rm -rf "$tmp"' EXIT
lock="$tmp/lock"

sed "s|^LOCK=.*|LOCK=$lock|" "$src" > "$tmp/gate.sh"
if ! grep -q "^LOCK=$lock\$" "$tmp/gate.sh"; then
  echo "$src has no LOCK= line to point at a scratch lock: not running (the shared lock must never be touched)"
  exit 2
fi
mkdir "$tmp/bin"
# The stub powershell answers the gate's two probes: the process listing (a query naming Win32_Process) prints $FAKE_PROCS,
# CSV lines as ConvertTo-Csv writes them (the test sets it; default: nothing running); the memory probe prints $FAKE_FREE.
cat > "$tmp/bin/powershell" <<'STUB'
#!/usr/bin/env bash
case "$*" in
  *Win32_Process*)
    [ -n "${FAKE_PROCS_FAIL-}" ] && exit 1
    while IFS= read -r l; do printf '%s\r\n' "$l"; done <<< "${FAKE_PROCS-}"
    ;;
  *) printf '%s\r\n' "${FAKE_FREE-9000}" ;;
esac
STUB
chmod +x "$tmp/bin/powershell"
# The stub touch is the real one, except that with TOUCH_RACE set the lock is deleted just before it runs: a release that
# lands between a script's "is the lock still there" check and its touch.
real_touch="$(command -v touch)"
cat > "$tmp/bin/touch" <<'STUB'
#!/usr/bin/env bash
[ -n "${TOUCH_RACE-}" ] && rm -f "$LOCKF"
exec "$REAL_TOUCH" "$@"
STUB
chmod +x "$tmp/bin/touch"
export PATH="$tmp/bin:$PATH" G="$tmp/gate.sh" LOCKF="$lock" TMPD="$tmp" REAL_TOUCH="$real_touch"

pass=0
fail=0
gate() { bash "$G" "$@"; }
# the same call from a shell of its own (a later call from "another shell": a different parent for the gate)
elsewhere() { bash -c 'bash "$G" "$@"; exit $?' elsewhere "$@"; }
# a command line in one shell of its own: the plan's "acquire X && run; release" one-liners
oneliner() { bash -c "$1; exit \$?"; }
cap() { out=$("$@" 2>&1); rc=$?; }
has() { case "$1" in *"$2"*) return 0 ;; *) return 1 ;; esac; }
t() {
  if eval "$2"; then
    pass=$((pass + 1))
    echo "PASS  $1"
  else
    fail=$((fail + 1))
    echo "FAIL  $1  (rc=${rc:-?} out='${out:-}')"
  fi
}
reset() {
  rm -rf "$lock" "$lock".refused.* "$lock".mutex "$tmp/ran"
  export FAKE_FREE=9000
  unset GATE_BEAT_SEC GATE_STALE_SEC GATE_SPLIT_STALE_SEC GATE_LEGACY_STALE_SEC GATE_EMPTY_STALE_SEC FAKE_PROCS FAKE_PROCS_FAIL TOUCH_RACE
  out=
  rc=
}
holder() { local h=; { read -r h _ < "$lock"; } 2>/dev/null; echo "$h"; }
field() { sed -n "s/^$1=//p" "$lock" | head -n 1; }
now() { date +%s; }

# a stand-in holder that lives: a background sleep, with its Windows pid and the start time the gate reads for it
spawn() { sleep 600 & SP=$!; procs+=("$SP"); SW="$(cat "/proc/$SP/winpid")"; SS="$(bash "$G" procstart "$SW")"; }
# one that has died, leaving its pid and the start time it had
dead() { sleep 600 & local p=$!; DW="$(cat "/proc/$p/winpid")"; DS="$(bash "$G" procstart "$DW")"; kill "$p"; wait "$p" 2>/dev/null; }
# a lock written by hand in the new format: who mode holder-pid holder-start beat-age-s held-for-s [token]
mklock() {
  printf '%s\n' "$1 client 2026-10-02 11:21:51 5748MB" "token=${7:-tok$RANDOM}" "acquired=$(( $(now) - $6 ))" "mode=$2" "holder=$3" "holder_start=$4" > "$lock"
  touch -d "@$(( $(now) - $5 ))" "$lock"
}

echo "== what callers already rely on"
reset; cap gate status
t "status says free when nothing is held" '[ "$out" = free ] && [ "$rc" -eq 0 ]'
reset; cap gate
t "no arguments is status" '[ "$out" = free ]'
reset; cap gate acquire laneA client
t "acquire takes the lock and says so" 'has "$out" "acquired: laneA client " && [ "$rc" -eq 0 ] && [ "$(holder)" = laneA ]'
t "the lock line keeps its format: who kind date time MB" 'grep -Eq "^laneA client [0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} 9000MB\$" "$lock"'
t "and it is the first line, so a gate that reads only the first word still finds the holder" '[ "$(head -n 1 "$lock" | cut -d" " -f1)" = laneA ]'
cap gate status
t "status shows who holds it" 'has "$out" "held: laneA client "'
cap gate acquire laneB client
t "a second acquire is refused as busy and the holder keeps the lock" 'has "$out" "busy: laneA client " && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
reset; FAKE_FREE=4000; cap gate acquire laneA
t "no kind means client: 4500 MB" 'has "$out" "short of memory: 4000 MB free, 4500 MB needed" && [ "$rc" -eq 1 ] && [ ! -e "$lock" ]'
reset; FAKE_FREE=2400; cap gate acquire laneA blender
t "blender needs 2500 MB" 'has "$out" "short of memory: 2400 MB free, 2500 MB needed" && [ "$rc" -eq 1 ] && [ ! -e "$lock" ]'
reset; FAKE_FREE=2600; cap gate acquire laneA blender
t "blender is acquired with 2600 MB" 'has "$out" "acquired: laneA blender " && [ "$rc" -eq 0 ]'
reset; FAKE_FREE=6400; cap gate acquire laneA server
t "server needs 6500 MB" 'has "$out" "short of memory: 6400 MB free, 6500 MB needed" && [ "$rc" -eq 1 ] && [ ! -e "$lock" ]'
reset; elsewhere acquire laneA client >/dev/null; cap elsewhere release
t "release from another shell drops the lock (acquire and release in separate calls)" '[ "$out" = released ] && [ "$rc" -eq 0 ] && [ ! -e "$lock" ]'
reset; cap gate release
t "release with nothing held still says released" '[ "$out" = released ] && [ "$rc" -eq 0 ]'
reset; cap gate frobnicate
t "an unknown verb prints the usage and exits 2" 'has "$out" "usage:" && [ "$rc" -eq 2 ]'
reset; cap gate acquire
t "acquire without a name prints the usage and fails" 'has "$out" "usage:" && [ "$rc" -ne 0 ] && [ ! -e "$lock" ]'

echo "== a release only drops its own lock"
reset; gate acquire laneA client >/dev/null; cap gate release laneA
t "release <who> by the holder drops it" '[ "$out" = released ] && [ "$rc" -eq 0 ] && [ ! -e "$lock" ]'
reset; gate acquire laneA client >/dev/null; cap gate release laneB
t "release <who> by someone else is refused and the lock stays" 'has "$out" "not yours: laneA client " && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
reset; cap gate release laneB
t "release <who> with nothing held says released" '[ "$out" = released ] && [ "$rc" -eq 0 ]'
reset; gate acquire laneA client >/dev/null; tok="$(field token)"; cap gate release laneA wrong-token
t "release <who> <token> with another token is refused" 'has "$out" "not yours" && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
cap gate release laneA "$tok"
t "release <who> <token> with the lock's own token drops it" '[ "$out" = released ] && [ ! -e "$lock" ]'

echo "== a refused acquire never lets its shell's release drop another lane's lock"
reset; elsewhere acquire laneA client >/dev/null; cap oneliner 'bash "$G" acquire laneB client && echo RAN; bash "$G" release'
t "the plan's one-liner while another lane holds the lock: busy, no run, lock kept" 'has "$out" "busy: laneA client " && ! has "$out" RAN && has "$out" "not released" && [ "$rc" -ne 0 ] && [ "$(holder)" = laneA ]'
reset; elsewhere acquire laneA client >/dev/null; cap oneliner 'bash "$G" acquire laneB client; bash "$G" release; bash "$G" release'
t "and a second release from that shell is guarded too" '[ "$(holder)" = laneA ]'
reset; cap oneliner 'bash "$G" acquire laneB client && echo RAN; bash "$G" release'
t "the same one-liner with the lock free runs and releases" 'has "$out" RAN && has "$out" "released" && [ "$rc" -eq 0 ] && [ ! -e "$lock" ]'
reset; FAKE_FREE=4000; cap oneliner 'bash "$G" acquire laneB client && echo RAN; bash "$G" release'
t "refused for memory: no run, nothing held, release harmless" 'has "$out" "short of memory" && ! has "$out" RAN && [ ! -e "$lock" ]'
reset; elsewhere acquire laneA client >/dev/null; cap oneliner 'bash "$G" acquire laneB client; bash "$G" release laneA; bash "$G" acquire laneB client; bash "$G" release'
t "a later successful acquire in that shell clears the guard, so its release works" 'has "$out" "acquired: laneB client " && [ ! -e "$lock" ]'
reset; elsewhere acquire laneA client >/dev/null; elsewhere acquire laneB client >/dev/null; cap elsewhere release
t "a release from another shell than the refused acquire behaves as it always did" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; elsewhere acquire laneA client >/dev/null; cap oneliner 'echo 1 > "$LOCKF.refused.$$"; bash "$G" release'
t "a stale guard (older than ten minutes) is ignored" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; elsewhere acquire laneA client >/dev/null; cap oneliner 'bash "$G" acquire laneB client; bash "$G" release laneA'
t "release <who> naming the holder works from any shell, guarded or not" 'has "$out" "released" && [ ! -e "$lock" ]'

echo "== run: acquire, run the command, release"
reset; cap gate run laneB client bash -c 'test -e "$LOCKF" && echo lockheld'
t "run holds the lock while the command runs and drops it after" 'has "$out" "acquired: laneB client " && has "$out" lockheld && has "$out" released && [ "$rc" -eq 0 ] && [ ! -e "$lock" ]'
reset; cap gate run laneB client bash -c 'exit 7'
t "run exits with the command's status and still releases" '[ "$rc" -eq 7 ] && [ ! -e "$lock" ]'
reset; cap gate run laneB client no-such-command-here
t "run releases when the command cannot start" '[ "$rc" -eq 127 ] && [ ! -e "$lock" ]'
reset; elsewhere acquire laneA client >/dev/null; cap gate run laneB client touch "$tmp/ran"
t "run while another lane holds the lock: busy, the command never runs, the lock is kept" 'has "$out" "busy: laneA client " && [ "$rc" -eq 1 ] && [ ! -e "$tmp/ran" ] && [ "$(holder)" = laneA ]'
reset; FAKE_FREE=4000; cap gate run laneB client touch "$tmp/ran"
t "run short of memory: refused, the command never runs" 'has "$out" "short of memory" && [ "$rc" -eq 1 ] && [ ! -e "$tmp/ran" ] && [ ! -e "$lock" ]'
reset; cap gate run laneB client
t "run without a command prints the usage" 'has "$out" "usage:" && [ "$rc" -ne 0 ] && [ ! -e "$lock" ]'
reset; cap gate run laneB client bash -c 'echo "laneC client taken over 9000MB" > "$LOCKF"'
t "run never drops a lock that is no longer its own" '[ "$(holder)" = laneC ] && [ "$rc" -eq 0 ]'
reset; elsewhere acquire laneA client >/dev/null; oneliner 'bash "$G" run laneB client true; bash "$G" release' >/dev/null 2>&1
t "a refused run also guards its shell's release" '[ "$(holder)" = laneA ]'

echo "== numbers, kinds and names"
reset; FAKE_FREE='N/A'; cap gate acquire laneA client
t "a memory reading that is not a number refuses" 'has "$out" "cannot read free memory" && [ "$rc" -eq 1 ] && [ ! -e "$lock" ]'
reset; FAKE_FREE=; cap gate acquire laneA client
t "an empty memory reading refuses" 'has "$out" "cannot read free memory" && [ "$rc" -eq 1 ] && [ ! -e "$lock" ]'
reset; cap gate acquire laneA clinet
t "an unknown kind is refused, not given the client threshold" 'has "$out" "unknown kind" && [ "$rc" -eq 2 ] && [ ! -e "$lock" ]'
reset; cap gate acquire "lane A" client
t "a name with a space is refused (the lock's first field is its owner)" '[ "$rc" -eq 2 ] && [ ! -e "$lock" ]'

echo "== how long it has been held"
reset; gate acquire laneA client >/dev/null; sed -i "s/^acquired=.*/acquired=$(( $(now) - 7200 ))/" "$lock"; cap gate acquire laneB client
t "busy says how long the lock has been held (since it was taken, not since its last beat)" 'has "$out" "busy: laneA client " && { has "$out" "(held 120 min)" || has "$out" "(held 121 min)"; }'
cap gate status
t "status says how long too" 'has "$out" "held: laneA client " && { has "$out" "(held 120 min)" || has "$out" "(held 121 min)"; }'
reset; echo "laneA client 2026-10-02 01:20:34 4885MB" > "$lock"; touch -d '2 hours ago' "$lock"; cap gate status
t "a lock written before tokens existed is read by its file's age" 'has "$out" "held: laneA client " && { has "$out" "(held 120 min)" || has "$out" "(held 121 min)"; }'

echo "== a lock records the process that holds it"
reset; gate acquire laneA client >/dev/null
t "acquire writes a token, its mode, the time it was taken and the process that holds it" 'grep -Eq "^token=[0-9]+-[0-9]+\$" "$lock" && [ "$(field mode)" = split ] && [ "$(field acquired)" -le "$(now)" ] && [ "$(field holder)" -gt 0 ] && [ -n "$(field holder_start)" ]'
tok1="$(field token)"; gate release laneA >/dev/null; gate acquire laneA client >/dev/null
t "every acquire has a token of its own" '[ -n "$tok1" ] && [ "$(field token)" != "$tok1" ]'
cap gate status
t "status shows the token and says the holder is alive" 'has "$out" "$(field token)" && has "$out" "holder $(field holder) alive"'
reset; cap gate run laneB client bash -c 'p=$(cat /proc/$PPID/winpid); grep -q "^mode=run\$" "$LOCKF" && grep -q "^holder=$p\$" "$LOCKF" && echo OWNPID'
t "run records mode=run and the gate's own process as the holder" 'has "$out" OWNPID'
reset; spawn; cap gate procstart "$SW"
t "procstart prints the start time the gate records for a live process" '[ -n "$out" ] && [ "$rc" -eq 0 ] && [ "$out" = "$SS" ]'
reset; dead; cap gate procstart "$DW"
t "and nothing for one that has ended" '[ -z "$out" ]'

echo "== a heartbeat while the command runs"
reset; export GATE_BEAT_SEC=1; cap gate run laneB client bash -c 'a=$(stat -c %Y "$LOCKF"); sleep 3; b=$(stat -c %Y "$LOCKF"); [ "$b" -gt "$a" ] && echo BEAT'
t "run touches the lock while the command runs" 'has "$out" BEAT && [ ! -e "$lock" ]'
reset; export GATE_BEAT_SEC=1 GATE_STALE_SEC=2
bash "$G" run laneB client bash -c 'echo $$ > "$TMPD/cmdpid"; sleep 60' > /dev/null 2>&1 &
gp=$!
sleep 2
kill -9 "$gp" 2>/dev/null
sleep 1
m1="$(stat -c %Y "$lock" 2>/dev/null)"; sleep 3; m2="$(stat -c %Y "$lock" 2>/dev/null)"
t "when the gate is killed mid run its heartbeat stops (the lock is left, no longer touched)" '[ -n "$m1" ] && [ "$m1" = "$m2" ]'
cap gate break orchestrator
t "and a lane cut off like that can be broken once its beat is stale" 'has "$out" "broke: laneB client " && [ ! -e "$lock" ]'
[ -s "$tmp/cmdpid" ] && kill "$(cat "$tmp/cmdpid")" 2>/dev/null
reset; gate acquire laneA client >/dev/null; spawn; touch -d '1 hour ago' "$lock"; export GATE_BEAT_SEC=1
bash "$G" pulse "$SP" > /dev/null 2>&1 &
pp=$!
sleep 2
t "pulse touches the lock while the process it watches lives" '[ $(( $(now) - $(stat -c %Y "$lock") )) -le 3 ]'
kill "$SP" 2>/dev/null
sleep 3
t "and ends when that process does" '! kill -0 "$pp" 2>/dev/null'

echo "== break only takes a lock that is dead, and only the one it looked at"
reset; spawn; mklock laneA run "$SW" "$SS" 10000 10000
cap gate break orchestrator
t "a lock whose holder is alive is refused, however old its last beat" 'has "$out" "refused:" && has "$out" alive && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
reset; dead; mklock laneA run "$DW" "$DS" 5 600
cap gate break orchestrator
t "a fresh beat is refused even when the holder is gone" 'has "$out" "refused:" && has "$out" beat && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
reset; dead; mklock laneA run "$DW" "$DS" 500 600
cap gate break orchestrator
t "a holder that is gone and a beat older than three minutes can be broken" 'has "$out" "broke: laneA client " && has "$out" orchestrator && [ "$rc" -eq 0 ] && [ ! -e "$lock" ]'
reset; spawn; mklock laneA run "$SW" "not the start time this pid had" 500 600
cap gate break orchestrator
t "a live pid with another start time is a reused pid, so the holder is gone" 'has "$out" "broke: laneA client " && [ ! -e "$lock" ]'
reset; dead; mklock laneA run "$DW" "$DS" 500 600 tokA
cap gate break orchestrator tokB
t "break <who> <token> refuses a token that is not the lock's, stale or not" 'has "$out" "refused:" && has "$out" tokA && [ "$rc" -eq 1 ] && [ -e "$lock" ]'
cap gate break orchestrator tokA
t "and takes the lock when the token is its own" 'has "$out" "broke: laneA client " && [ ! -e "$lock" ]'
reset; dead; mklock laneA run "$DW" "$DS" 36000 36000 tokOld
cap gate status
t "status calls a dead lock stale and names its token" 'has "$out" STALE && has "$out" tokOld'
rm -f "$lock"; gate acquire laneB client >/dev/null
cap gate break orchestrator tokOld
t "the incident: a break aimed at the old lock leaves the fresh lock its lane took meanwhile" 'has "$out" "refused:" && [ "$rc" -eq 1 ] && [ "$(holder)" = laneB ]'
cap gate break orchestrator
t "and a break without a token refuses that fresh lock too while its holder lives" 'has "$out" "refused:" && [ "$(holder)" = laneB ]'
reset; dead; mklock laneA split "$DW" "$DS" 3000 3000
cap gate break orchestrator
t "a split acquire whose shell is gone keeps a two hour grace" 'has "$out" "refused:" && [ "$rc" -eq 1 ] && [ -e "$lock" ]'
mklock laneA split "$DW" "$DS" 8000 8000
cap gate break orchestrator
t "and can be broken after it" 'has "$out" "broke: laneA client " && [ ! -e "$lock" ]'
reset; mklock laneA split 0 0 3000 3000
cap gate break orchestrator
t "a lock with no holder process recorded gets the same grace" 'has "$out" "refused:" && [ -e "$lock" ]'
reset; echo "laneA client 2026-10-02 01:20:34 4885MB" > "$lock"; touch -d '10 minutes ago' "$lock"
cap gate status
t "a lock from before tokens says so" 'has "$out" "held: laneA client " && has "$out" legacy'
cap gate break orchestrator
t "a young one of those is refused" 'has "$out" "refused:" && [ -e "$lock" ]'
touch -d '4 hours ago' "$lock"
cap gate break orchestrator
t "and one older than three hours can be broken" 'has "$out" "broke: laneA client " && [ ! -e "$lock" ]'
reset; cap gate break orchestrator
t "break with nothing held says so" 'has "$out" "nothing to break" && [ "$rc" -eq 0 ]'
reset; gate acquire laneA client >/dev/null; cap gate break
t "break needs a name" 'has "$out" "usage:" && [ "$rc" -ne 0 ] && [ "$(holder)" = laneA ]'

echo "== acquire takes over a dead lock and never a live one"
reset; dead; mklock laneA run "$DW" "$DS" 500 600
cap gate acquire laneB client
t "a dead lock is reclaimed, and the output says what it was" 'has "$out" "reclaimed a stale lock: laneA client " && has "$out" "acquired: laneB client " && [ "$rc" -eq 0 ] && [ "$(holder)" = laneB ]'
reset; spawn; mklock laneA run "$SW" "$SS" 500 600
cap gate acquire laneB client
t "a live one is busy" 'has "$out" "busy: laneA client " && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
reset; dead; mklock laneA run "$DW" "$DS" 5 600
cap gate acquire laneB client
t "so is one whose holder is gone but whose beat is fresh" 'has "$out" "busy: laneA client " && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
reset; echo "laneA client 2026-10-02 01:20:34 4885MB" > "$lock"; touch -d '4 hours ago' "$lock"
cap gate acquire laneB client
t "a lock from before tokens that is hours old is reclaimed too" 'has "$out" "reclaimed a stale lock: laneA client " && [ "$(holder)" = laneB ]'

echo "== a bare release leaves another process's live lock alone"
reset; spawn; mklock laneA run "$SW" "$SS" 5 5
cap gate release
t "a bare release is refused while the lock's holder is a live process that is not this shell's parent" 'has "$out" "not released" && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
reset; dead; mklock laneA split "$DW" "$DS" 5 5
cap gate release
t "and still drops a lock whose holder is gone (acquire and release in separate calls)" '[ "$out" = released ] && [ ! -e "$lock" ]'

echo "== a bare release leaves a lock alone while a client is beating it (A3 lock cleanup, review item R1)"
reset; dead; mklock laneA split "$DW" "$DS" 5 600
cap gate release
t "a split lock whose shell has ended but whose client beat it seconds ago is not another lane's to drop" 'has "$out" "not released" && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
t "and the refusal says how to drop it if it is yours" 'has "$out" "release <who>"'
cap gate release laneA
t "release <who> by the lane that holds it drops it whatever its beat" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; dead; mklock laneA split "$DW" "$DS" 100 600
cap gate release
t "a bare release goes through once the beat is older than two beats (60 s)" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; dead; mklock laneA split "$DW" "$DS" 5 5
cap gate release
t "a lock nobody has beaten since it was taken is dropped as before (acquire, think, release in separate calls)" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; dead; mklock laneA run "$DW" "$DS" 5 600
cap gate release
t "a run lock whose gate has died but whose client still beats it is kept too" 'has "$out" "not released" && [ "$(holder)" = laneA ]'
reset; echo "laneA client 2026-10-02 01:20:34 4885MB" > "$lock"
cap gate release
t "so is a lock from before tokens that was written seconds ago" 'has "$out" "not released" && [ "$(holder)" = laneA ]'
touch -d '2 minutes ago' "$lock"
cap gate release
t "and that one goes once it is two minutes old" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; export GATE_BEAT_SEC=1; elsewhere acquire laneA client >/dev/null; spawn
bash "$G" pulse "$SP" > /dev/null 2>&1 &
pp=$!
sleep 5
cap elsewhere release
t "the real chain: a client pulsing a split lock keeps a bare release from another shell out" 'has "$out" "not released" && [ "$(holder)" = laneA ]'
kill "$SP" 2>/dev/null
sleep 4
cap elsewhere release
t "and once the client is gone and two beats have passed the same release drops it" '[ "$out" = released ] && [ ! -e "$lock" ]'
kill "$pp" 2>/dev/null
reset; export GATE_BEAT_SEC=5; elsewhere acquire laneA client >/dev/null; spawn
bash "$G" pulse "$SP" > /dev/null 2>&1 &
pp=$!
sleep 7
kill "$pp" 2>/dev/null
wait "$pp" 2>/dev/null
cap elsewhere release
t "a run that ends cleanly (autotest.sh stops its pulse) takes its beats back, so the release that follows it goes through at once" '[ "$out" = released ] && [ ! -e "$lock" ]'
kill "$SP" 2>/dev/null
reset; dead; mklock laneA split "$DW" "$DS" 5 600
cap gate release
t "(a split lock with a client beating it is refused)" 'has "$out" "not released" && [ -e "$lock" ]'
cap gate settle
t "settle moves the lock's last write just outside the window a bare release respects (60 s), and no further" '[ "$rc" -eq 0 ] && [ $(( $(now) - $(stat -c %Y "$lock") )) -ge 60 ] && [ $(( $(now) - $(stat -c %Y "$lock") )) -le 63 ]'
cap gate release
t "and then a bare release drops it" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; dead; mklock laneA split "$DW" "$DS" 5 600 tokA
cap gate settle tokB
t "settle <token> leaves another lock alone" '[ $(( $(now) - $(stat -c %Y "$lock") )) -le 8 ]'
reset; dead; mklock laneA split "$DW" "$DS" 500 600
before="$(stat -c %Y "$lock")"
cap gate settle
t "a lock whose last write is already outside the window is not touched" '[ "$(stat -c %Y "$lock")" = "$before" ]'
reset; echo "laneA client 2026-10-02 01:20:34 4885MB" > "$lock"; before="$(stat -c %Y "$lock")"
cap gate settle
t "a lock from before tokens says nothing of when it was taken, so settle leaves it as it is" '[ "$(stat -c %Y "$lock")" = "$before" ] && [ "$rc" -eq 0 ]'
reset; cap gate settle
t "settle with nothing held is harmless" '[ "$rc" -eq 0 ] && [ ! -e "$lock" ]'

echo "== two acquires at once: exactly one wins"
bad=0
for i in 1 2 3 4 5 6; do
  reset
  bash "$G" acquire laneA client > "$tmp/c1" 2>&1 &
  p1=$!
  bash "$G" acquire laneB client > "$tmp/c2" 2>&1 &
  p2=$!
  wait "$p1" "$p2"
  n=$(cat "$tmp/c1" "$tmp/c2" | grep -c '^acquired:')
  [ "$n" -eq 1 ] || bad=$((bad + 1))
done
out="runs with other than one winner: $bad"
t "six rounds of two simultaneous acquires" '[ "$bad" -eq 0 ]'
reset; mkdir "$lock.mutex"; touch -d '2 minutes ago' "$lock.mutex"; cap gate acquire laneA client
t "a mutex left behind by a shell that died does not block the gate" 'has "$out" "acquired: laneA client " && [ "$rc" -eq 0 ]'

echo "== a late touch never leaves an empty lock behind (review item R2)"
reset; gate acquire laneA client >/dev/null; spawn; export GATE_BEAT_SEC=1
TOUCH_RACE=1 bash "$G" pulse "$SP" > /dev/null 2>&1 &
pp=$!
sleep 3
t "a release landing between the pulse's check and its touch does not make an empty lock" '[ ! -e "$lock" ]'
kill "$pp" "$SP" 2>/dev/null
reset; export GATE_BEAT_SEC=1
TOUCH_RACE=1 bash "$G" run laneB client bash -c 'sleep 3' > /dev/null 2>&1
t "nor does one landing between a run's beat check and its touch" '[ ! -e "$lock" ]'
reset; gate acquire laneA client >/dev/null; spawn; export GATE_BEAT_SEC=1
bash "$G" pulse "$SP" > /dev/null 2>&1 &
pp=$!
sleep 2
mklock laneB run 0 0 3600 3600 tokOther   # another lane's lock takes its place at once (the file is never absent)
sleep 3
t "a pulse never beats a lock other than the one it began on" '[ $(( $(now) - $(stat -c %Y "$lock") )) -ge 3000 ]'
t "and it ends" '! kill -0 "$pp" 2>/dev/null'
kill "$pp" "$SP" 2>/dev/null
reset; : > "$lock"
cap gate status
t "an empty lock file is called what it is, not a legacy lock" 'has "$out" "empty lock file" && ! has "$out" legacy'
cap gate acquire laneB client
t "for its first ten seconds it is busy (a writer may be mid write)" 'has "$out" "busy:" && [ "$rc" -eq 1 ] && [ -e "$lock" ]'
touch -d '30 seconds ago' "$lock"
cap gate acquire laneB client
t "after that acquire takes it over and says so (one used to block every lane for three hours)" 'has "$out" "reclaimed a stale lock" && has "$out" "acquired: laneB client " && [ "$(holder)" = laneB ]'
reset; : > "$lock"; touch -d '30 seconds ago' "$lock"
cap gate break orchestrator
t "break removes an empty lock file once it has sat there ten seconds" 'has "$out" "broke:" && [ "$rc" -eq 0 ] && [ ! -e "$lock" ]'
reset; : > "$lock"
cap gate break orchestrator
t "but not one written just now" 'has "$out" "refused:" && [ "$rc" -eq 1 ] && [ -e "$lock" ]'
reset; : > "$lock"
cap gate release laneA
t "release <who> drops an empty lock file (it is nobody's)" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; : > "$lock"
cap gate release
t "and so does a bare release" '[ "$out" = released ] && [ ! -e "$lock" ]'
reset; echo > "$lock"; touch -d '30 seconds ago' "$lock"
cap gate acquire laneB client
t "a lock file holding only a blank line counts as empty" 'has "$out" "reclaimed a stale lock" && [ "$(holder)" = laneB ]'
reset; export GATE_EMPTY_STALE_SEC=1; : > "$lock"; sleep 2
cap gate acquire laneB client
t "the ten seconds can be set (GATE_EMPTY_STALE_SEC)" 'has "$out" "reclaimed a stale lock" && [ "$(holder)" = laneB ]'

echo "== a dead lock is not taken from under a running client or Blender (review item R3)"
CSV_HEAD='"ProcessId","Name","CommandLine"'
P_CLIENT='"4242","java.exe","""C:\Program Files\Java\jdk-21\bin\java.exe"" -Dcosmicbreach.hiddenWindow=true -Dcosmicbreach.autotest=gyre-dive cpw.mods.bootstraplauncher.BootstrapLauncher --gameDir run-test"'
P_WRAPPER='"4300","java.exe","""C:\Program Files\Java\jdk-21\bin\java.exe"" -classpath C:\work\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runTestClient -Pautotest=gyre-dive"'
P_GRADLE='"3100","java.exe","""C:\Program Files\Java\jdk-21\bin\java.exe"" -classpath C:\work\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build --no-daemon"'
P_DAEMON='"3200","java.exe","""C:\Program Files\Java\jdk-21\bin\java.exe"" --add-opens=java.base/java.lang=ALL-UNNAMED org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.10"'
P_MCP='"2500","blender-mcp.exe","""C:\Users\x\Scripts\blender-mcp.exe"""'
P_BLENDER='"5151","blender.exe","""C:\Program Files\Blender Foundation\Blender 5.2\blender.exe"" --background --python gen.py"'
procs() { local l="$CSV_HEAD"; for p in "$@"; do l="$l"$'\n'"${!p}"; done; export FAKE_PROCS="$l"; }
reset; dead; mklock laneA run "$DW" "$DS" 500 600; procs P_GRADLE P_DAEMON P_MCP
cap gate acquire laneB client
t "a build, a Gradle daemon and the Blender MCP server are not a client: the dead lock is reclaimed" 'has "$out" "reclaimed a stale lock" && [ "$(holder)" = laneB ]'
reset; dead; mklock laneA run "$DW" "$DS" 500 600; procs P_CLIENT
cap gate acquire laneB client
t "a running test client keeps a dead lock from being reclaimed, and its pid is named" 'has "$out" "busy:" && has "$out" "test client" && has "$out" 4242 && [ "$rc" -eq 1 ] && [ "$(holder)" = laneA ]'
cap gate break orchestrator
t "break refuses it too" 'has "$out" "refused:" && has "$out" 4242 && [ "$rc" -eq 1 ] && [ -e "$lock" ]'
procs P_WRAPPER
cap gate break orchestrator
t "the Gradle wrapper of a client run counts as a client" 'has "$out" "refused:" && has "$out" 4300 && [ -e "$lock" ]'
procs P_BLENDER
cap gate acquire laneB client
t "Blender running keeps it as well" 'has "$out" "busy:" && has "$out" Blender && has "$out" 5151 && [ "$(holder)" = laneA ]'
procs P_GRADLE P_MCP
cap gate break orchestrator
t "and once nothing of the kind runs the same dead lock can be broken" 'has "$out" "broke: laneA client " && [ ! -e "$lock" ]'
reset; dead; mklock laneA split "$DW" "$DS" 8000 8000; procs P_BLENDER
cap gate acquire laneB blender
t "a split Blender session's lock, two hours old, is not reclaimed while Blender runs (nothing beats it)" 'has "$out" "busy:" && has "$out" 5151 && [ "$(holder)" = laneA ]'
reset; echo "laneA client 2026-10-02 01:20:34 4885MB" > "$lock"; touch -d '4 hours ago' "$lock"; procs P_CLIENT
cap gate acquire laneB client
t "a lock from before tokens, hours old, is held to the same rule" 'has "$out" "busy:" && has "$out" 4242 && [ "$(holder)" = laneA ]'
reset; : > "$lock"; touch -d '30 seconds ago' "$lock"; procs P_CLIENT
cap gate acquire laneB client
t "an empty lock file is no exception" 'has "$out" "busy:" && has "$out" 4242 && [ -e "$lock" ]'
reset; dead; mklock laneA run "$DW" "$DS" 500 600; export FAKE_PROCS_FAIL=1
cap gate acquire laneB client
t "when the process list cannot be read the lock is not reclaimed (cannot tell is not none)" 'has "$out" "busy:" && has "$out" "process" && [ "$(holder)" = laneA ]'
reset; procs P_CLIENT P_BLENDER
cap gate acquire laneB client
t "a free lock is acquired as before: the check guards taking a dead lock, nothing else" 'has "$out" "acquired: laneB client " && [ "$rc" -eq 0 ]'
reset; procs P_CLIENT P_BLENDER P_MCP P_GRADLE
cap gate running
t "running lists the test client and Blender with their pids, and nothing else" 'has "$out" "test client (pid 4242)" && has "$out" "Blender (pid 5151)" && ! has "$out" 2500 && ! has "$out" 3100 && [ "$rc" -eq 0 ]'
reset; procs P_GRADLE P_MCP P_DAEMON
cap gate running
t "running says nothing when nothing of the kind runs" '[ -z "$out" ] && [ "$rc" -eq 0 ]'

echo "== the suite wrapper runs clients only under a lock it holds itself (A3 quality review, I1)"
# suite.sh against the scratch gate, with a stub autotest that only leaves a mark: no client ever starts here
cat > "$tmp/autotest-stub.sh" <<'STUB'
#!/usr/bin/env bash
echo "$1" >> "$TMPD/suite-ran"
exit 0
STUB
chmod +x "$tmp/autotest-stub.sh"
suite() { GATE_SCRIPT="$G" SUITE_AUTOTEST="$tmp/autotest-stub.sh" SUITE_DIR="$tmp/suite-out" bash "$here/suite.sh" "$@"; }
export -f suite 2>/dev/null
reset; rm -f "$tmp/suite-ran"; elsewhere acquire laneA client >/dev/null; cap suite one two
t "another lane's live lock: the suite refuses, nothing runs, the lock is kept" 'has "$out" "not running" && has "$out" laneA && [ "$rc" -eq 1 ] && [ ! -e "$tmp/suite-ran" ] && [ "$(holder)" = laneA ]'
reset; rm -f "$tmp/suite-ran"; dead; mklock laneA run "$DW" "$DS" 500 600; cap suite one
t "a dead lock is not the suite's either" 'has "$out" "not running" && [ "$rc" -eq 1 ] && [ ! -e "$tmp/suite-ran" ]'
reset; rm -f "$tmp/suite-ran"; elsewhere acquire laneA client >/dev/null; out=$(GATE_HELD_TOKEN=tok-of-an-old-lock suite one 2>&1); rc=$?
t "a token that is not the lock's own does not pass" 'has "$out" "not running" && [ "$rc" -eq 1 ] && [ ! -e "$tmp/suite-ran" ]'
reset; rm -f "$tmp/suite-ran"; cap env GATE_SCRIPT="$G" SUITE_AUTOTEST="$tmp/autotest-stub.sh" SUITE_DIR="$tmp/suite-out" bash "$G" run laneB client bash "$here/suite.sh" one two
t "started through the gate's run command, the suite runs under that lock and the lock is dropped after" 'has "$out" "one: exit 0" && has "$out" "two: exit 0" && [ "$(cat "$tmp/suite-ran" | tr "
" " ")" = "one two " ] && [ ! -e "$lock" ]' 
reset; rm -f "$tmp/suite-ran"; cap suite one
t "started bare with the lock free, it takes a lock of its own and runs" 'has "$out" "acquired: suite client " && has "$out" "one: exit 0" && [ ! -e "$lock" ]'
reset; rm -f "$tmp/suite-ran"; gate acquire laneB client >/dev/null; out=$(GATE_WHO=laneB suite one 2>&1); rc=$?
t "the older split form: the caller named in GATE_WHO holds the lock, so the suite runs" 'has "$out" "one: exit 0" && [ "$rc" -eq 0 ] && [ "$(holder)" = laneB ]'
reset; rm -f "$tmp/suite-ran"; gate acquire laneB client >/dev/null; out=$(GATE_WHO=laneC suite one 2>&1); rc=$?
t "the split form with another lane's name refuses" 'has "$out" "not running" && [ "$rc" -eq 1 ] && [ ! -e "$tmp/suite-ran" ] && [ "$(holder)" = laneB ]'
reset; rm -f "$tmp/suite-ran"; gate acquire laneB client >/dev/null; cap suite one
t "the split form with no GATE_WHO at all refuses" 'has "$out" "not running" && [ "$rc" -eq 1 ] && [ ! -e "$tmp/suite-ran" ]'

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
