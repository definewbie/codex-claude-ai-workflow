#!/usr/bin/env bash
#
# Structured INVALID classification for real-case summaries.

real_case_invalid_reason_from_runs_dir() {
  local runs_dir="$1"
  [[ -n "$runs_dir" && -d "$runs_dir" ]] || {
    printf ''
    return
  }

  if command -v jq >/dev/null 2>&1; then
    while IFS= read -r file; do
      if jq -e '
        .. | objects | select(
          .failureReason? == "RECOVERABLE_PROVIDER_CONNECTION_FAILED"
          or .failureSignal? == "PROVIDER_CONNECTION_FAILED"
          or .unavailableReason? == "PROVIDER_CONNECTION_FAILED"
          or .type? == "PROVIDER_CONNECTION_FAILED"
        )
      ' "$file" >/dev/null 2>&1; then
        printf 'provider_connection_failed'
        return
      elif jq -e '
        .. | objects | select(
          .failureReason? == "RECOVERABLE_AGENT_OVERLOADED"
          or .failureSignal? == "AGENT_OVERLOADED"
          or .unavailableReason? == "AGENT_OVERLOADED"
          or .type? == "AGENT_OVERLOADED"
          or .type? == "RECOVERABLE_AGENT_OVERLOADED"
        )
      ' "$file" >/dev/null 2>&1; then
        printf 'provider_overloaded'
        return
      fi
    done < <(find "$runs_dir" -name run.json -type f | sort)
    printf ''
    return
  fi

  # Fallback keeps older environments usable but only matches typed JSON fields,
  # never free-form provider text.
  if grep -R -m 1 -E \
    '"failureReason"[[:space:]]*:[[:space:]]*"RECOVERABLE_PROVIDER_CONNECTION_FAILED"|"type"[[:space:]]*:[[:space:]]*"PROVIDER_CONNECTION_FAILED"|"failureSignal"[[:space:]]*:[[:space:]]*"PROVIDER_CONNECTION_FAILED"|"unavailableReason"[[:space:]]*:[[:space:]]*"PROVIDER_CONNECTION_FAILED"' \
    "$runs_dir" >/dev/null 2>&1; then
    printf 'provider_connection_failed'
    return
  fi
  if grep -R -m 1 -E \
    '"failureReason"[[:space:]]*:[[:space:]]*"RECOVERABLE_AGENT_OVERLOADED"|"type"[[:space:]]*:[[:space:]]*"AGENT_OVERLOADED"|"type"[[:space:]]*:[[:space:]]*"RECOVERABLE_AGENT_OVERLOADED"|"failureSignal"[[:space:]]*:[[:space:]]*"AGENT_OVERLOADED"|"unavailableReason"[[:space:]]*:[[:space:]]*"AGENT_OVERLOADED"' \
    "$runs_dir" >/dev/null 2>&1; then
    printf 'provider_overloaded'
    return
  fi
  printf ''
}
