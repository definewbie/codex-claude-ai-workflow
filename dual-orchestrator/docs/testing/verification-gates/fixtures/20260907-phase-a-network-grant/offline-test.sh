#!/usr/bin/env bash
set -euo pipefail
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR=${1:?usage: offline-test.sh <reviewed jar>}
bash -n "$DIR/run-once.sh" "$DIR/probe.sh" "$DIR/acpx-once.sh"
RESULT="/private/tmp/phase-a-prepare-$(date -u +%Y%m%dT%H%M%SZ)-$$"
# prepare never constructs transport, invokes acpx, or probes a host.
DUAL_ACPX_EXECUTABLE=/nonexistent/DO-NOT-CALL bash "$DIR/run-once.sh" prepare "$RESULT" "$JAR"
grep -q '^PREPARE_PASS:' "$RESULT/launcher.stdout"
grep -qx 0 "$RESULT/launcher-exit-code.txt"
[[ ! -e "$RESULT/stream.jsonl" && ! -e "$RESULT/prompt-dispatched" ]]
mkdir "$RESULT/wrapper-check"
set +e
PHASE_A_REAL_ACPX=/usr/bin/false PHASE_A_RESULT="$RESULT/wrapper-check" \
  bash "$DIR/acpx-once.sh" -f unused
first=$?
PHASE_A_REAL_ACPX=/usr/bin/true PHASE_A_RESULT="$RESULT/wrapper-check" \
  bash "$DIR/acpx-once.sh" -f unused
second=$?
set -e
[[ "$first" == 1 && "$second" == 91 ]]
grep -qx SECOND_PROMPT_BLOCKED "$RESULT/wrapper-check/prompt-guard.txt"
mkdir "$RESULT/wrapper-output-check"
PHASE_A_REAL_ACPX=/bin/echo PHASE_A_RESULT="$RESULT/wrapper-output-check" \
  bash "$DIR/acpx-once.sh" -f OFFLINE_OUTPUT
grep -qx -- '--format json -f OFFLINE_OUTPUT' "$RESULT/wrapper-output-check/first-prompt.stream"
printf 'OFFLINE_PASS evidence=%s\n' "$RESULT"
