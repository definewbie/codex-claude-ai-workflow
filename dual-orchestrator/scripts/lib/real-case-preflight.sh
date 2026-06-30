#!/usr/bin/env bash
#
# Deterministic preflight helpers for real provider case runners.

real_case_file_mtime_epoch() {
  local file="$1"
  [[ -f "$file" ]] || {
    printf ''
    return
  }
  if stat -f '%m' "$file" >/dev/null 2>&1; then
    stat -f '%m' "$file"
    return
  fi
  stat -c '%Y' "$file" 2>/dev/null || printf ''
}

real_case_artifact_freshness_status() {
  local skip_build="$1"
  local jar_path="$2"
  local build_started_epoch="$3"
  local mtime

  if [[ "$skip_build" == "true" ]]; then
    printf 'unproven_skip_build'
    return
  fi
  if [[ ! -f "$jar_path" ]]; then
    printf 'jar_missing'
    return
  fi
  mtime="$(real_case_file_mtime_epoch "$jar_path")"
  if [[ -z "$mtime" || -z "$build_started_epoch" ]]; then
    printf 'jar_mtime_unavailable'
    return
  fi
  if [[ "$mtime" -lt "$build_started_epoch" ]]; then
    printf 'jar_not_fresh'
    return
  fi
  printf 'fresh'
}

real_case_artifact_freshness_reason() {
  local status="$1"
  case "$status" in
    fresh)
      printf 'jar_mtime_is_after_build_start'
      ;;
    unproven_skip_build)
      printf 'skip_build_cannot_prove_current_code_is_in_the_executed_jar'
      ;;
    jar_missing)
      printf 'dualctl_jar_missing_after_build'
      ;;
    jar_mtime_unavailable)
      printf 'dualctl_jar_mtime_could_not_be_read'
      ;;
    jar_not_fresh)
      printf 'dualctl_jar_mtime_is_older_than_build_start'
      ;;
    *)
      printf 'unknown_freshness_status'
      ;;
  esac
}

real_case_artifact_freshness_is_valid() {
  [[ "$1" == "fresh" ]]
}
