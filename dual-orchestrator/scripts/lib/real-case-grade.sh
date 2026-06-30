#!/usr/bin/env bash
#
# Shared real-case benchmark grading helpers.
#
# The grading is intentionally small and deterministic. It summarizes lifecycle
# health for benchmark comparison; it does not replace reading the detailed run
# report when a case fails or returns PASS-WARN.

real_case_grade() {
  local status="$1"
  local repo_clean="$2"
  local missing_artifacts="$3"
  local acceptance_decisions="$4"
  local rejects="$5"
  local invalid_reason="${6:-}"

  if [[ -n "$invalid_reason" ]]; then
    printf 'INVALID'
  elif [[ "$status" != "COMPLETED" ]]; then
    printf 'FAIL'
  elif [[ "$repo_clean" != "true" ]]; then
    printf 'FAIL'
  elif [[ -n "$missing_artifacts" ]]; then
    printf 'FAIL'
  elif [[ "$acceptance_decisions" == *"ACCEPT_WITH_NOTES"* ]]; then
    printf 'PASS-WARN'
  elif [[ "$rejects" -gt 0 ]]; then
    printf 'PASS-WARN'
  else
    printf 'PASS'
  fi
}

real_case_grade_reason() {
  local status="$1"
  local repo_clean="$2"
  local missing_artifacts="$3"
  local acceptance_decisions="$4"
  local rejects="$5"
  local invalid_reason="${6:-}"

  if [[ -n "$invalid_reason" ]]; then
    printf 'invalid=%s' "$invalid_reason"
  elif [[ "$status" != "COMPLETED" ]]; then
    printf 'status=%s' "$status"
  elif [[ "$repo_clean" != "true" ]]; then
    printf 'original_repo_dirty'
  elif [[ -n "$missing_artifacts" ]]; then
    printf 'missing_artifacts=%s' "$missing_artifacts"
  elif [[ "$acceptance_decisions" == *"ACCEPT_WITH_NOTES"* ]]; then
    printf 'accepted_with_notes'
  elif [[ "$rejects" -gt 0 ]]; then
    printf 'repaired_rejects=%s' "$rejects"
  else
    printf 'ok'
  fi
}
