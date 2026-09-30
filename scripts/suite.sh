#!/usr/bin/env bash
# Runs every autotest scenario in sequence and prints one line per scenario.
cd "$(dirname "$0")/.." || exit 1
SCENARIOS="${*:-smoke selftest combo movement hud fx anim pack shardling}"
mkdir -p run-test/suite
: > run-test/suite/summary.txt
for s in $SCENARIOS; do
  start=$(date +%s)
  scripts/autotest.sh "$s" > "run-test/suite/$s.out" 2>&1
  code=$?
  result=$(cat "run-test/autotest/$s/result.txt" 2>/dev/null || echo "NO RESULT")
  echo "$s: exit $code, $(( $(date +%s) - start ))s, $result" | tee -a run-test/suite/summary.txt
done
