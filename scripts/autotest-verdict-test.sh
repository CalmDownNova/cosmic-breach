#!/usr/bin/env bash
# Tests for scripts/autotest-verdict.sh: what a run's result is once its exit is taken into account.
#
#   scripts/autotest-verdict-test.sh
#
# One line per check, then "N passed, M failed"; exits 1 on any failure.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if [ ! -f "$here/autotest-verdict.sh" ]; then
  echo "no $here/autotest-verdict.sh"
  exit 2
fi
# shellcheck source=autotest-verdict.sh
. "$here/autotest-verdict.sh"

pass=0
fail=0
out=
t() {
  if eval "$2"; then
    pass=$((pass + 1))
    echo "PASS  $1"
  else
    fail=$((fail + 1))
    echo "FAIL  $1  (out='${out:-}')"
  fi
}
has() { case "$1" in *"$2"*) return 0 ;; *) return 1 ;; esac; }

clean="$tmp/clean.log"
stalled="$tmp/stalled.log"
printf '[12:51:46] [Render thread/INFO] [autotest] RESULT PASS\n[12:51:47] [Server thread/INFO] ThreadedAnvilChunkStorage: All dimensions are saved\n' > "$clean"
printf '[00:56:23] [Render thread/INFO] [autotest] RESULT PASS\n[00:57:23] [watchdog/ERROR] [autotest] the game did not exit 60 s after the result; halting\n' > "$stalled"

echo "== a clean run stays what it was"
out=$(autotest_verdict "PASS" 0 "$clean" 400)
t "PASS, exit 0, a clean log: PASS" '[ "$out" = PASS ]'
out=$(autotest_verdict "FAIL a check failed: the cap holds" 0 "$clean" 400)
t "a FAIL stays that FAIL, word for word" '[ "$out" = "FAIL a check failed: the cap holds" ]'
out=$(autotest_verdict "FAIL no result.txt was written" 1 "$clean" 400)
t "a FAIL with a nonzero exit is not rewritten either" '[ "$out" = "FAIL no result.txt was written" ]'
out=$(autotest_verdict "PASS" 0 "$tmp/no-such-log" 400)
t "a missing log is not a stall" '[ "$out" = PASS ]'

echo "== a PASS that did not exit is a FAIL"
out=$(autotest_verdict "PASS" 0 "$stalled" 400)
t "the watchdog's line in the log turns PASS into FAIL even if gradle said 0" '[ "${out%% *}" = FAIL ] && has "$out" "did not exit" && has "$out" "(was: PASS)"'
out=$(autotest_verdict "PASS" 1 "$stalled" 400)
t "PASS, gradle exit 1, the watchdog line: FAIL naming the stalled exit" '[ "${out%% *}" = FAIL ] && has "$out" "did not exit" && has "$out" "(was: PASS)"'
out=$(autotest_verdict "PASS" 1 "$clean" 400)
t "PASS with a nonzero gradle exit and no watchdog line (a crash at exit): FAIL naming the exit code" '[ "${out%% *}" = FAIL ] && has "$out" "gradle exit 1" && has "$out" "(was: PASS)"'
out=$(autotest_verdict "PASS" 124 "$clean" 400)
t "gradle exit 124 means the time limit hit, and says so" '[ "${out%% *}" = FAIL ] && has "$out" "gradle exit 124" && has "$out" "400 s"'
out=$(autotest_verdict "PASS 12 steps" 3 "$clean" 400)
t "a PASS with text after it keeps that text in what it was" '[ "${out%% *}" = FAIL ] && has "$out" "(was: PASS 12 steps)"'

echo "== what the harness already rewrote is left alone"
out=$(autotest_verdict "FAIL the game did not exit 60 s after the result (was: PASS)" 1 "$stalled" 400)
t "a result the watchdog already rewrote stays as it is" '[ "$out" = "FAIL the game did not exit 60 s after the result (was: PASS)" ]'

echo "== one line, plain ASCII"
out=$(autotest_verdict "PASS" 1 "$stalled" 400)
t "the verdict is a single line" '[ "$(printf "%s\n" "$out" | wc -l)" -eq 1 ]'
t "and plain ASCII with no dashes" '! printf "%s" "$out" | LC_ALL=C grep -q "[^ -~]"'

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
