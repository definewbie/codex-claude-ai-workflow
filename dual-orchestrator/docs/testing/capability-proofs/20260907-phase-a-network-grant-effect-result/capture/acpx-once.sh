#!/usr/bin/env bash
set -euo pipefail
# Transport may retry a missing/reconnect session. Never dispatch a second prompt.
for arg in "$@"; do
  if [[ "$arg" == -f ]]; then
    if ! mkdir "$PHASE_A_RESULT/prompt-dispatched" 2>/dev/null; then
      printf 'SECOND_PROMPT_BLOCKED\n' >>"$PHASE_A_RESULT/prompt-guard.txt"
      exit 91
    fi
    printf '%s\n' --format json "$@" >"$PHASE_A_RESULT/prompt-arguments.txt"
    set +e
    { "$PHASE_A_REAL_ACPX" --format json "$@" 2>&1 1>&3 |
        tee "$PHASE_A_RESULT/first-prompt.stderr" >&2;
    } 3>&1 | tee "$PHASE_A_RESULT/first-prompt.stream"
    exit "$?"
  fi
done
exec "$PHASE_A_REAL_ACPX" "$@"
