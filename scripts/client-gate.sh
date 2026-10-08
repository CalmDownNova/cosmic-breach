#!/usr/bin/env bash
# One hidden test client (or Blender) at a time across every lane (Aetheria 1.1 plan), and only with memory free.
#
#   scripts/client-gate.sh acquire <who> [client|blender|server]   prints "acquired ..." and exits 0, or "busy: ..." /
#                                                                  "short of memory: ..." and exits 1
#   scripts/client-gate.sh release [<who> [<token>]]               drops the lock (see Releasing)
#   scripts/client-gate.sh status                                  prints "free" or "held: ..."
#   scripts/client-gate.sh run <who> <kind> <command...>           acquire, run the command, release; exits with its status
#   scripts/client-gate.sh break <who> [<token>]                   removes a lock that is dead and nothing else (see Staleness)
#   scripts/client-gate.sh pulse <pid> [<token>]                   touches the lock every 30 s while process <pid> lives
#   scripts/client-gate.sh running                                 lists the test client and Blender processes running now
#   scripts/client-gate.sh settle [<token>]                        a client's run is over: the lock's last beat stops holding off a bare release
#   scripts/client-gate.sh procstart <windows pid>                 prints the start time the gate records for a process
#
# Every test client launch goes through `run` (the plans, briefs and scripts/suite.sh all do):
#   bash scripts/client-gate.sh run <who> client scripts/autotest.sh <scenario>
# It exits 1 with "busy: ..." or "short of memory: ..." when the client may not start (nothing ran), otherwise with the
# command's own status, and its first line is "acquired: ..." exactly when the command did run.
#
# Needs: client 4500 MB, blender 2500 MB, server 6500 MB (a local dedicated test server plus one client).
# The lock lives outside every worktree so all lanes share it. Free memory is \Memory\Available MBytes. A busy or held
# line ends with how long the lock has been held, in minutes (since it was taken), and what the gate knows of its holder.
#
# The lock. Its first line is what it always was, "who kind date time MB", so a gate that reads only that (an older
# copy) still finds the holder. The lines after it are for this gate:
#   token=<ns>-<pid>      unique to each acquire; break and release can name the exact lock they looked at
#   acquired=<epoch>      when it was taken (the "held N min" figure)
#   mode=run|split        run: the gate's own process holds it for the length of its command; split: a plain acquire
#   holder=<windows pid>  the process that holds it (run: the gate itself, split: the shell that called acquire; 0 if unknown)
#   holder_start=<time>   that process's start time as ps -W shows it, so a reused pid is not taken for the holder
# The file's modification time is the heartbeat: `run` touches it every 30 s while its command runs (and stops if the gate
# is killed), and scripts/autotest.sh pulses it for as long as a client is up.
#
# Staleness. A lock is stale, and only then can break or acquire take it, when its holder process is gone (or reused, or
# was never recorded) AND its last beat is older than: 3 minutes for a run lock; 2 hours for a split acquire (nobody can
# beat for a shell that has ended, and an agent may think a while between calls); 3 hours for a lock written by a gate that
# did not record any of this (judged by its file's age alone); 10 seconds for an empty lock file (nothing in it names a
# holder: a late heartbeat or a crash leaves one, and no gate writes its lock in pieces). Nothing else is stale: a holder
# that is alive keeps its lock however old, a beat that is fresh keeps it however dead the holder looks. break reads the
# lock at the moment it removes it, under the gate's own mutex, and `break <who> <token>` refuses unless that lock still
# carries the token you looked at, so a lock released and taken again between your look and your break is left alone. To
# take a live lock, stop its holder (status gives the pid). Prefer `run` to the split form in plans and briefs: the lock
# then lives exactly as long as the command, and a lane cut off in the middle of one is detected without anyone guessing.
#
# A dead lock is still not taken while its client may be running. Before acquire reclaims a stale lock or break removes
# one, the gate looks at the running processes: a test client (a java process whose command line carries the test client's
# own marks, its cosmicbreach.autotest property or the Gradle task runTestClient) or Blender (blender.exe, not the Blender
# MCP server) refuses it, naming the pid ("busy: ..." / "refused: ..."), and so does a process list that cannot be read.
# That covers a client that outlives a gate killed under it, and a split Blender session nothing beats. `running` prints
# what the gate sees. A free lock is acquired without this look (the memory check is its guard), and a live holder's lock
# is not stale at all.
#
# Beats never make a lock. Every touch of the lock (the beat of a run, a pulse) is `touch -c`: if a release landed between
# the check that the lock is still there and the touch, nothing is created. A pulse also stops, without a last touch, as
# soon as the lock it began on is gone or another lane's (the token changes), and scripts/autotest.sh waits for its pulse
# to end before it returns.
#
# Releasing. `run` releases only the lock it took, so a caller that uses `run` never writes a release at all.
# `release <who>` drops the lock only if <who> holds it ("not yours: ..." and exit 1 otherwise; with a token, only that
# lock; an empty lock file is nobody's, so anyone drops it). A bare `release` (the older form, and the plan's
# "acquire X client && run; release" lines) drops whatever lock there is, except: in a shell whose own acquire was just
# refused (that lock belongs to someone else, so it stays: "not released: ..." and exit 1; the refusal leaves a note beside
# the lock named after the calling shell, which expires after ten minutes or when that shell acquires); while the lock's
# holder is a live process that is not the shell calling it; and while a client is beating the lock, that is, the lock was
# written to after it was taken (a split lock's holder is the shell that called acquire, which for an agent ended with that
# call, so the holder check alone cannot see its running client) and its last write is younger than two beats (60 s). A bare
# release from another shell after the acquiring shell has ended and its client has stopped (acquire and release in separate
# calls) is unchanged: when scripts/autotest.sh ends a run it stops its pulse with TERM, and a pulse stopped that way settles
# the lock (the verb above), so the release that follows a run is never held off by the run's own last beat. `release <who>`
# always names the lane that means it.
#
# Settings (environment, for tests): GATE_BEAT_SEC (30), GATE_STALE_SEC (180), GATE_SPLIT_STALE_SEC (7200),
# GATE_LEGACY_STALE_SEC (10800), GATE_EMPTY_STALE_SEC (10).
#
# Tests: scripts/client-gate-test.sh (against a scratch lock, never this one).
set -u
LOCK=/c/Users/puppy/.cosmic-breach-client.lock
USAGE="usage: scripts/client-gate.sh acquire <who> [client|blender|server] | release [<who> [<token>]] | status | run <who> <kind> <command...> | break <who> [<token>] | pulse <pid> [<token>] | running | settle [<token>]"
BEAT_SEC="${GATE_BEAT_SEC:-30}"
STALE_SEC="${GATE_STALE_SEC:-180}"
SPLIT_STALE_SEC="${GATE_SPLIT_STALE_SEC:-7200}"
LEGACY_STALE_SEC="${GATE_LEGACY_STALE_SEC:-10800}"
EMPTY_STALE_SEC="${GATE_EMPTY_STALE_SEC:-10}"
# a lock written to within this long is being beaten by a client that is up (a bare release leaves it alone)
FRESH_SEC=$(( 2 * BEAT_SEC ))

# The lock's own lines in one pass with builtins only (a fork costs tens of milliseconds here): F_first is its first
# line, the rest are the key=value lines this gate writes. Returns 1 if there is no lock.
read_lock() {
  local line n=0
  F_first=; F_token=; F_acquired=; F_mode=; F_holder=; F_holder_start=
  [ -e "$LOCK" ] || return 1
  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%$'\r'}
    n=$((n + 1))
    if [ "$n" -eq 1 ]; then
      F_first=$line
      continue
    fi
    case "$line" in
      token=*) F_token=${line#token=} ;;
      acquired=*) F_acquired=${line#acquired=} ;;
      mode=*) F_mode=${line#mode=} ;;
      holder=*) F_holder=${line#holder=} ;;
      holder_start=*) F_holder_start=${line#holder_start=} ;;
    esac
  done < "$LOCK" 2>/dev/null
  return 0
}
lock_token() { read_lock; printf '%s' "$F_token"; }
mtime() { stat -c %Y "$LOCK" 2>/dev/null; }

# The start time ps -W shows for a Windows process (HH:MM:SS today, "Mon DD" older); nothing if there is no such process.
proc_stime() {
  ps -W 2>/dev/null | awk -v p="$1" '$4 == p { if ($7 ~ /^[0-9][0-9]:[0-9][0-9]:[0-9][0-9]$/) print $7; else print $7 " " $8; exit }'
}

# The Windows pid of the shell that called the gate (nothing when it is not a shell the gate can see).
parent_winpid() {
  local p="$PPID" w=
  [ "$p" -gt 1 ] 2>/dev/null || return 0
  w=$(tr -d '\r' < "/proc/$p/winpid" 2>/dev/null)
  [ -n "$w" ] && echo "$w"
  return 0
}

# Reads the lock into ST_*: its token and mode, its holder and whether that process lives, how old its last beat is, whether
# something has beaten it since it was taken (ST_BEATEN: its last write is later than the one that took it; a lock that
# does not say when it was taken counts as beaten), and whether it is stale. One read, so everything said about a lock
# comes from the same moment. An empty lock file (no first line, no token) is mode "empty".
lock_state() {
  local threshold cur m
  read_lock
  ST_TOKEN=$F_token
  ST_MODE=$F_mode
  ST_HOLDER=$F_holder
  ST_HSTART=$F_holder_start
  m=$(mtime) || m=$EPOCHSECONDS
  ST_BEAT=$(( EPOCHSECONDS - m ))
  [ "$ST_BEAT" -lt 0 ] && ST_BEAT=0
  ST_ALIVE=none
  ST_BEATEN=no
  if [ -z "$ST_TOKEN" ]; then
    if [ -z "$F_first" ]; then
      ST_MODE=empty
      threshold=$EMPTY_STALE_SEC
    else
      ST_MODE=legacy
      threshold=$LEGACY_STALE_SEC
      ST_BEATEN=yes
    fi
  else
    if [ -n "$ST_HOLDER" ] && [ "$ST_HOLDER" != 0 ]; then
      cur=$(proc_stime "$ST_HOLDER")
      if [ -n "$cur" ] && [ "$cur" = "$ST_HSTART" ]; then ST_ALIVE=yes; else ST_ALIVE=no; fi
    fi
    if [ "$ST_MODE" = run ]; then threshold=$STALE_SEC; else threshold=$SPLIT_STALE_SEC; fi
    case "$F_acquired" in
      ''|*[!0-9]*) ;;
      *) [ $(( m - F_acquired )) -gt 2 ] && ST_BEATEN=yes ;;
    esac
  fi
  ST_THRESHOLD=$threshold
  if [ "$ST_ALIVE" != yes ] && [ "$ST_BEAT" -gt "$threshold" ]; then ST_STALE=yes; else ST_STALE=no; fi
}

# After lock_state: what the gate knows of the holder and the beat, for status, busy and refusal lines.
lock_info() {
  local h stale=
  case "$ST_ALIVE" in
    yes) h="holder $ST_HOLDER alive" ;;
    no) h="holder $ST_HOLDER gone" ;;
    *) case "$ST_MODE" in
         legacy) h="legacy lock, no holder or beat recorded" ;;
         empty) h="empty lock file, nothing in it names a holder (a late heartbeat or a crash leaves one)" ;;
         *) h="no holder process recorded" ;;
       esac ;;
  esac
  [ "$ST_STALE" = yes ] && stale=", STALE"
  if [ -z "$ST_TOKEN" ]; then
    echo "[$h, last write $ST_BEAT s ago$stale]"
  else
    echo "[$h, last beat $ST_BEAT s ago$stale, token $ST_TOKEN]"
  fi
}

# minutes since the lock was taken (a lock without a time of its own: since its file was written), from what read_lock has read
held_min() {
  local acq="$F_acquired" age
  case "$acq" in
    ''|*[!0-9]*) acq=$(mtime) || { echo "?"; return 0; } ;;
  esac
  age=$(( (EPOCHSECONDS - acq) / 60 ))
  [ "$age" -lt 0 ] && age=0
  echo "$age"
}

# The lock as one line, from what lock_state has already read (so a decision and the words about it agree) ...
lock_text_now() { echo "$F_first (held $(held_min) min) $(lock_info)"; }
# ... or after reading it afresh.
lock_text() {
  lock_state
  lock_text_now
}

# The heavy processes a lock stands for, looked up in one process listing: a test client (a java process whose command line
# carries the test client's own marks: its cosmicbreach.autotest system property, the Gradle task runTestClient that starts
# it, or testClientRun, as scripts/poll-windows.ps1 knows it) or Blender (blender.exe itself: the Blender MCP server is a
# different program). Sets HEAVY_LIST (one entry per process, "test client (pid N)" or "Blender (pid N)") and HEAVY (the
# entries joined). Returns 1 when the list could not be read, with HEAVY saying so: not seeing a process is not the same as
# there being none.
heavy_running() {
  local list rc line pid rest name
  HEAVY_LIST=()
  HEAVY=
  list=$(powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object Name -Match '^(javaw?|blender)\.exe\$' | Select-Object ProcessId,Name,CommandLine | ConvertTo-Csv -NoTypeInformation" 2>/dev/null)
  rc=$?
  if [ "$rc" -ne 0 ]; then
    HEAVY="the process list could not be read (powershell exit $rc), so a running client or Blender cannot be ruled out"
    return 1
  fi
  list=${list//$'\r'/}
  while IFS= read -r line; do
    case "$line" in
      \"[0-9]*) ;;
      *) continue ;;
    esac
    pid=${line#\"}
    pid=${pid%%\"*}
    rest=${line#*\",\"}
    name=${rest%%\"*}
    case "$name" in
      blender.exe) HEAVY_LIST+=("Blender (pid $pid)") ;;
      java.exe|javaw.exe)
        case "$line" in
          *cosmicbreach.autotest*|*runTestClient*|*testClientRun*) HEAVY_LIST+=("test client (pid $pid)") ;;
        esac ;;
    esac
  done <<< "$list"
  local item
  for item in "${HEAVY_LIST[@]+"${HEAVY_LIST[@]}"}"; do
    HEAVY="${HEAVY:+$HEAVY, }$item"
  done
  return 0
}

# Runs the arguments with the gate's mutex held, so two gates never read and rewrite the lock at the same moment.
# A mutex left behind by a shell that died is taken over after 30 s.
with_mutex() {
  local m="$LOCK.mutex" tries=0 rc mt
  until mkdir "$m" 2>/dev/null; do
    if mt=$(stat -c %Y "$m" 2>/dev/null); then
      if [ $(( EPOCHSECONDS - mt )) -gt 30 ]; then
        rmdir "$m" 2>/dev/null
        continue
      fi
      tries=$((tries + 1))
      if [ "$tries" -gt 150 ]; then
        echo "the gate's own mutex stayed taken for 15 s ($m): is another gate stuck?"
        return 1
      fi
      sleep 0.1
    fi
  done
  "$@"
  rc=$?
  rmdir "$m" 2>/dev/null
  return $rc
}

# Notes of refused acquires, named after the shell that asked (the gate's parent; 1 means no known shell: no notes).
note_refusal() { [ "$PPID" -gt 1 ] && echo "$EPOCHSECONDS" > "$LOCK.refused.$PPID" 2>/dev/null; return 0; }
clear_refusal() { rm -f "$LOCK.refused.$PPID"; }
refused_here() { [ "$PPID" -gt 1 ] && [ -e "$LOCK.refused.$PPID" ]; }
prune_notes() {
  local f t now=$EPOCHSECONDS
  for f in "$LOCK".refused.*; do
    [ -e "$f" ] || continue
    t=$(cat "$f" 2>/dev/null)
    case "$t" in
      ''|*[!0-9]*) rm -f "$f"; continue ;;
    esac
    [ $((now - t)) -gt 600 ] && rm -f "$f"
  done
  return 0
}

# acquire_locked <who> <need MB> <kind> <mode>: with the mutex held. Takes a free lock, or one that is stale (and says so);
# refuses a live one and a lack of memory.
acquire_locked() {
  local who="$1" need="$2" kind="$3" mode="$4" free holder hstart token stamp
  if [ -e "$LOCK" ]; then
    lock_state
    if [ "$ST_STALE" = yes ]; then
      # dead, but not while the client or Blender it stood for may still be running
      if ! heavy_running; then
        echo "busy: $(lock_text_now); dead, but not reclaimed: $HEAVY"
        return 1
      fi
      if [ -n "$HEAVY" ]; then
        echo "busy: $(lock_text_now); dead, but not reclaimed while still running: $HEAVY"
        return 1
      fi
      echo "reclaimed a stale lock: $(lock_text_now)"
      rm -f "$LOCK"
    else
      echo "busy: $(lock_text)"
      return 1
    fi
  fi
  free=$(powershell -NoProfile -Command "[int](Get-Counter '\Memory\Available MBytes').CounterSamples[0].CookedValue" | tr -d '\r')
  case "$free" in
    ''|*[!0-9]*) echo "short of memory: cannot read free memory ('$free'), $need MB needed"; return 1 ;;
  esac
  if [ "$free" -lt "$need" ]; then
    echo "short of memory: $free MB free, $need MB needed"
    return 1
  fi
  if [ "$mode" = run ]; then holder=$(tr -d '\r' < "/proc/$$/winpid" 2>/dev/null); else holder=$(parent_winpid); fi
  hstart=
  [ -n "$holder" ] && hstart=$(proc_stime "$holder")
  if [ -z "$holder" ] || [ -z "$hstart" ]; then holder=0; hstart=; fi
  token="$(date +%s%N)-$$"
  printf -v stamp '%(%Y-%m-%d %H:%M:%S)T' -1
  if ! ( set -o noclobber; printf '%s\n' "$who $kind $stamp ${free}MB" "token=$token" "acquired=$EPOCHSECONDS" \
        "mode=$mode" "holder=$holder" "holder_start=$hstart" > "$LOCK" ) 2>/dev/null; then
    echo "busy: $(lock_text)"
    return 1
  fi
  echo "acquired: $(read_lock; echo "$F_first") token=$token"
}

# try_acquire <who> <kind> <mode>: prints the outcome; returns 0 acquired, 1 refused (busy, short of memory), 2 bad arguments
try_acquire() {
  local who="$1" kind="$2" mode="$3" need
  case "$who" in
    ''|*[[:space:]]*) echo "bad name '$who': one word, no spaces (it is the lock's owner)"; return 2 ;;
  esac
  case "$kind" in
    client) need=4500 ;;
    blender) need=2500 ;;
    server) need=6500 ;;
    *) echo "unknown kind '$kind': client, blender or server"; return 2 ;;
  esac
  with_mutex acquire_locked "$who" "$need" "$kind" "$mode"
}

# release_locked <who> [token]: drops the lock only if <who> holds it (and, given a token, only that lock)
release_locked() {
  local who="$1" tok="${2:-}"
  if ! read_lock; then
    echo "released"
    return 0
  fi
  # an empty lock file is nobody's: anyone may drop it
  if { [ "${F_first%% *}" = "$who" ] && { [ -z "$tok" ] || [ "$F_token" = "$tok" ]; }; } || { [ -z "$F_first" ] && [ -z "$F_token" ]; }; then
    rm -f "$LOCK"
    echo "released"
    return 0
  fi
  echo "not yours: $(lock_text)"
  return 1
}
release_owned() { with_mutex release_locked "$@"; }

# A bare release: whatever lock there is, unless this shell's acquire was refused, a live process that is not this shell
# holds it, or a client is beating it (it was written to after it was taken, within the last two beats).
release_any_locked() {
  if [ ! -e "$LOCK" ]; then
    clear_refusal
    echo "released"
    return 0
  fi
  if refused_here; then
    echo "not released: this shell's acquire was refused, so the lock is not yours: $(lock_text)"
    return 1
  fi
  lock_state
  if [ "$ST_ALIVE" = yes ] && [ "$ST_HOLDER" != "$(parent_winpid)" ]; then
    echo "not released: a live process (pid $ST_HOLDER), not this shell, holds the lock; release <who> drops it if it is yours: $(lock_text_now)"
    return 1
  fi
  if [ "$ST_ALIVE" != yes ] && [ "$ST_BEATEN" = yes ] && [ "$ST_BEAT" -lt "$FRESH_SEC" ]; then
    echo "not released: it was written to $ST_BEAT s ago, so a client or Blender session of the lane that took it may be running; release <who> drops it if it is yours, and a bare release works again once its last write is $FRESH_SEC s old: $(lock_text_now)"
    return 1
  fi
  clear_refusal
  rm -f "$LOCK"
  echo "released"
}

# settle_locked [token]: with the mutex held. A client's run is over: if the lock's last write is inside the window a bare
# release respects (FRESH_SEC), it is moved just outside it, so a bare release is no longer held off by a client that has
# stopped beating it (the staleness clocks lose a minute at most: the beat history stays). Only the lock the token names, if
# one is given; a lock from before tokens, which does not say when it was taken, is left as it is.
settle_locked() {
  local tok="${1:-}" to
  read_lock || return 0
  [ -n "$F_token" ] || return 0
  [ -z "$tok" ] || [ "$F_token" = "$tok" ] || return 0
  to=$(( EPOCHSECONDS - FRESH_SEC - 1 ))
  [ "$(mtime)" -gt "$to" ] 2>/dev/null && touch -c -d "@$to" "$LOCK" 2>/dev/null
  return 0
}

# break_locked <who> [token]: removes the lock only if it is stale, and only the one the token names (when one is given)
break_locked() {
  local who="$1" want="${2:-}" text
  if [ ! -e "$LOCK" ]; then
    echo "nothing to break: free"
    return 0
  fi
  lock_state
  text="$(lock_text_now)"
  if [ -n "$want" ] && [ "$want" != "$ST_TOKEN" ]; then
    echo "refused: that is not the lock you looked at (it now carries token ${ST_TOKEN:-none}, not $want): $text"
    return 1
  fi
  if [ "$ST_STALE" != yes ]; then
    if [ "$ST_ALIVE" = yes ]; then
      echo "refused: its holder (pid $ST_HOLDER) is alive: $text"
    else
      echo "refused: its last beat was $ST_BEAT s ago and a $ST_MODE lock is stale only after $ST_THRESHOLD s without one: $text"
    fi
    return 1
  fi
  # dead, but not while the client or Blender it stood for may still be running
  if ! heavy_running; then
    echo "refused: $HEAVY: $text"
    return 1
  fi
  if [ -n "$HEAVY" ]; then
    echo "refused: dead, but not removed while still running: $HEAVY: $text"
    return 1
  fi
  rm -f "$LOCK"
  echo "broke: $text (by $who)"
}

# the heartbeat of a run: touch the lock every BEAT_SEC while the gate lives and the lock is still its own. touch -c: a
# release that lands between the check and the touch must not leave a lock file behind (an empty one blocked every lane)
beat_loop() {
  local gate="$1" token="$2" nap=
  trap '[ -n "$nap" ] && kill "$nap" 2>/dev/null; exit 0' TERM
  while kill -0 "$gate" 2>/dev/null; do
    sleep "$BEAT_SEC" &
    nap=$!
    wait "$nap" 2>/dev/null
    kill -0 "$gate" 2>/dev/null || break
    [ "$(lock_token)" = "$token" ] || break
    touch -c "$LOCK" 2>/dev/null
  done
}

case "${1:-status}" in
  acquire)
    who="${2:?usage: scripts/client-gate.sh acquire <who> [client|blender|server]}"
    prune_notes
    try_acquire "$who" "${3:-client}" split
    rc=$?
    if [ "$rc" -eq 0 ]; then clear_refusal; else note_refusal; fi
    exit "$rc"
    ;;
  run)
    if [ $# -lt 4 ]; then
      echo "usage: scripts/client-gate.sh run <who> <client|blender|server> <command...>"
      exit 2
    fi
    who="$2"
    kind="$3"
    shift 3
    prune_notes
    try_acquire "$who" "$kind" run
    rc=$?
    if [ "$rc" -ne 0 ]; then
      note_refusal
      exit "$rc"
    fi
    clear_refusal
    token=$(lock_token)
    beat_loop "$$" "$token" &
    beater=$!
    # the command's own lock, for a wrapper that must know the lock it runs under is this one (scripts/suite.sh)
    GATE_HELD_TOKEN="$token" GATE_HELD_WHO="$who" "$@"
    rc=$?
    kill "$beater" 2>/dev/null
    wait "$beater" 2>/dev/null
    release_owned "$who" "$token" || echo "run: the lock was no longer ours when the command ended" >&2
    exit "$rc"
    ;;
  release)
    prune_notes
    if [ $# -ge 2 ]; then
      release_owned "$2" "${3:-}"
      exit $?
    fi
    with_mutex release_any_locked
    exit $?
    ;;
  status)
    if [ -e "$LOCK" ]; then echo "held: $(lock_text)"; else echo "free"; fi
    ;;
  break)
    who="${2:?usage: scripts/client-gate.sh break <who> [<token>]  (takes a dead lock only; status shows why a live one is refused)}"
    with_mutex break_locked "$who" "${3:-}"
    exit $?
    ;;
  pulse)
    pid="${2:?usage: scripts/client-gate.sh pulse <pid> [<token>]}"
    # the lock this pulse serves: the one named, or the one held when it starts. It beats nothing else: once that lock is
    # gone or is another lane's (its token changed) or is an empty file, the pulse ends without a last touch.
    token="${3:-$(lock_token)}"
    nap=
    # TERM is how scripts/autotest.sh ends it when a run is over: the lock it served then gets its beats taken back (settle)
    trap '[ -n "$nap" ] && kill "$nap" 2>/dev/null; [ -n "$token" ] && with_mutex settle_locked "$token" > /dev/null 2>&1; exit 0' TERM
    while kill -0 "$pid" 2>/dev/null && [ -s "$LOCK" ] && [ "$(lock_token)" = "$token" ]; do
      touch -c "$LOCK" 2>/dev/null
      sleep "$BEAT_SEC" &
      nap=$!
      wait "$nap" 2>/dev/null
    done
    ;;
  settle)
    with_mutex settle_locked "${2:-}"
    exit $?
    ;;
  running)
    if ! heavy_running; then
      echo "$HEAVY"
      exit 2
    fi
    [ "${#HEAVY_LIST[@]}" -gt 0 ] && printf '%s\n' "${HEAVY_LIST[@]}"
    exit 0
    ;;
  procstart)
    proc_stime "${2:?usage: scripts/client-gate.sh procstart <windows pid>}"
    ;;
  *)
    echo "$USAGE"
    exit 2
    ;;
esac
