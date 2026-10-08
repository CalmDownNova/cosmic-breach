#!/usr/bin/env bash
# Runs every autotest scenario in sequence and prints one line per scenario.
#
#   bash scripts/client-gate.sh run <who> client scripts/suite.sh [scenarios...]     (how to start it)
#
# One hidden test client at a time across every lane, and only with memory free: the whole suite runs under one lock of the
# client gate, so no other lane's client can start between two scenarios. Started with no lock held it takes one itself (it
# runs itself again through the command above, as GATE_WHO, "suite" by default). Started while a lock is held it runs only
# if that lock is its caller's own, and otherwise exits 1 with the gate's line and runs nothing:
#   - inside the gate's run command, which hands the command its lock's token as GATE_HELD_TOKEN: the token must be the
#     lock's current one;
#   - after the older "acquire <who> client && scripts/suite.sh ...; release": GATE_WHO must name the lock's holder.
# A refused lock (busy, short of memory) means nothing ran, and the gate's line says why.
# For the gate's own checks (scripts/client-gate-test.sh): GATE_SCRIPT, SUITE_AUTOTEST and SUITE_DIR stand in for the real gate,
# autotest.sh and run-test/suite.
cd "$(dirname "$0")/.." || exit 1
GATE="${GATE_SCRIPT:-scripts/client-gate.sh}"
AUTOTEST="${SUITE_AUTOTEST:-scripts/autotest.sh}"
OUT="${SUITE_DIR:-run-test/suite}"
if [ -f "$GATE" ]; then
  held="$(bash "$GATE" status)"
  if [ "$held" = free ]; then
    exec bash "$GATE" run "${GATE_WHO:-suite}" client scripts/suite.sh "$@"
  fi
  own=no
  case "$held" in
    *"token ${GATE_HELD_TOKEN:-no-token}]"*) own=yes ;;
  esac
  if [ "$own" = no ] && [ -n "${GATE_WHO:-}" ]; then
    case "$held" in
      "held: $GATE_WHO "*) own=yes ;;
    esac
  fi
  if [ "$own" = no ]; then
    echo "suite: not running, the client gate's lock is not this suite's ($held)"
    exit 1
  fi
  echo "suite: running under the client gate's lock ($held)"
fi
SCENARIOS="${*:-smoke selftest combo movement hud fx anim pack shardling}"
mkdir -p "$OUT"
: > "$OUT/summary.txt"
for s in $SCENARIOS; do
  start=$(date +%s)
  "$AUTOTEST" "$s" > "$OUT/$s.out" 2>&1
  code=$?
  result=$(cat "run-test/autotest/$s/result.txt" 2>/dev/null || echo "NO RESULT")
  echo "$s: exit $code, $(( $(date +%s) - start ))s, $result" | tee -a "$OUT/summary.txt"
done
