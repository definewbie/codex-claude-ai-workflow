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

real_case_latest_jvm_input_epoch() {
  local project_root="$1"
  [[ -n "$project_root" && -d "$project_root" ]] || {
    printf ''
    return
  }
  local latest=0 path mtime
  while IFS= read -r path; do
    mtime="$(real_case_file_mtime_epoch "$path")"
    [[ -n "$mtime" && "$mtime" -gt "$latest" ]] && latest="$mtime"
  done < <(
    {
      find "$project_root" -maxdepth 2 -name pom.xml -type f 2>/dev/null
      find "$project_root" -maxdepth 3 -path '*/src/main' -type d 2>/dev/null |
        while IFS= read -r dir; do
          find "$dir" -type f 2>/dev/null
        done
    } | sort
  )
  [[ "$latest" -gt 0 ]] && printf '%s' "$latest" || printf ''
}

real_case_artifact_freshness_status() {
  local skip_build="$1"
  local jar_path="$2"
  local build_started_epoch="$3"
  local latest_input_epoch="${4:-}"
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
  if [[ "$mtime" -ge "$build_started_epoch" ]]; then
    printf 'fresh'
    return
  fi
  if [[ -n "$latest_input_epoch" && "$mtime" -ge "$latest_input_epoch" ]]; then
    printf 'fresh_existing_artifact'
    return
  fi
  printf 'jar_not_fresh'
}

real_case_artifact_freshness_reason() {
  local status="$1"
  case "$status" in
    fresh)
      printf 'jar_mtime_is_after_build_start'
      ;;
    fresh_existing_artifact)
      printf 'jar_mtime_covers_project_inputs'
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
  [[ "$1" == "fresh" || "$1" == "fresh_existing_artifact" ]]
}

real_case_artifact_freshness_invalid_reason() {
  local status="$1"
  if real_case_artifact_freshness_is_valid "$status"; then
    printf ''
  elif [[ -n "$status" ]]; then
    printf 'artifact_freshness_%s' "$status"
  else
    printf 'artifact_freshness_unknown'
  fi
}

real_case_provider_probe_status() {
  local exit_code="$1"
  local result_json="$2"

  if [[ "$exit_code" == "124" ]]; then
    printf 'timeout'
    return
  fi
  if [[ "$exit_code" != "0" ]]; then
    printf 'failed'
    return
  fi
  if [[ ! -f "$result_json" ]]; then
    printf 'failed'
    return
  fi
  if command -v jq >/dev/null 2>&1; then
    if jq -e '.is_error == true' "$result_json" >/dev/null 2>&1; then
      printf 'failed'
      return
    fi
    if jq -e '.type == "result"' "$result_json" >/dev/null 2>&1; then
      printf 'passed'
      return
    fi
    printf 'failed'
    return
  fi
  if grep -Eq '"is_error"[[:space:]]*:[[:space:]]*true' "$result_json"; then
    printf 'failed'
    return
  fi
  if grep -Eq '"type"[[:space:]]*:[[:space:]]*"result"' "$result_json"; then
    printf 'passed'
    return
  fi
  printf 'failed'
}

real_case_acpx_provider_probe_status() {
  local exit_code="$1"
  local transcript="$2"

  if [[ "$exit_code" == "124" ]]; then
    printf 'timeout'
    return
  fi
  if [[ "$exit_code" != "0" ]]; then
    printf 'failed'
    return
  fi
  if [[ ! -s "$transcript" ]]; then
    printf 'failed'
    return
  fi
  printf 'passed'
}

real_case_provider_probe_reason() {
  local status="$1"
  case "$status" in
    passed)
      printf 'provider_probe_completed'
      ;;
    skipped)
      printf 'provider_probe_disabled'
      ;;
    timeout)
      printf 'provider_probe_timed_out'
      ;;
    failed)
      printf 'provider_probe_failed'
      ;;
    *)
      printf 'provider_probe_unknown'
      ;;
  esac
}

real_case_provider_probe_is_valid() {
  [[ "$1" == "passed" ]]
}

real_case_provider_probe_invalid_reason() {
  local status="$1"
  case "$status" in
    failed|timeout|skipped)
      printf 'provider_preflight_%s' "$status"
      ;;
    *)
      printf ''
      ;;
  esac
}
