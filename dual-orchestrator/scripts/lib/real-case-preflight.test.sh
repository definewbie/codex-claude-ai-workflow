#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# shellcheck source=scripts/lib/real-case-preflight.sh
source "$ROOT/scripts/lib/real-case-preflight.sh"

assert_eq() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$expected" != "$actual" ]]; then
    printf '[FAIL] %s: expected %s, got %s\n' "$label" "$expected" "$actual" >&2
    exit 1
  fi
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

jar="$tmp/dual-cli.jar"
printf 'jar\n' >"$jar"
now="$(date '+%s')"
past="$((now - 10))"
future="$((now + 10))"

assert_eq "fresh" \
  "$(real_case_artifact_freshness_status false "$jar" "$past")" \
  "fresh jar after build start"

assert_eq "jar_not_fresh" \
  "$(real_case_artifact_freshness_status false "$jar" "$future")" \
  "stale jar before build start"

assert_eq "fresh_existing_artifact" \
  "$(real_case_artifact_freshness_status false "$jar" "$future" "$past")" \
  "existing jar covers project inputs"

assert_eq "jar_not_fresh" \
  "$(real_case_artifact_freshness_status false "$jar" "$future" "$future")" \
  "existing jar before project inputs is stale"

assert_eq "unproven_skip_build" \
  "$(real_case_artifact_freshness_status true "$jar" "$past")" \
  "skip build is unproven"

assert_eq "jar_missing" \
  "$(real_case_artifact_freshness_status false "$tmp/missing.jar" "$past")" \
  "missing jar"

assert_eq "jar_mtime_is_after_build_start" \
  "$(real_case_artifact_freshness_reason fresh)" \
  "fresh reason"

assert_eq "jar_mtime_covers_project_inputs" \
  "$(real_case_artifact_freshness_reason fresh_existing_artifact)" \
  "existing artifact reason"

if ! real_case_artifact_freshness_is_valid fresh; then
  printf '[FAIL] fresh should be valid\n' >&2
  exit 1
fi

if ! real_case_artifact_freshness_is_valid fresh_existing_artifact; then
  printf '[FAIL] existing artifact that covers inputs should be valid\n' >&2
  exit 1
fi

if real_case_artifact_freshness_is_valid unproven_skip_build; then
  printf '[FAIL] skip build should not be valid\n' >&2
  exit 1
fi

assert_eq "artifact_freshness_unproven_skip_build" \
  "$(real_case_artifact_freshness_invalid_reason unproven_skip_build)" \
  "skip build invalid reason"

assert_eq "" \
  "$(real_case_artifact_freshness_invalid_reason fresh)" \
  "fresh artifact has no invalid reason"

probe_json="$tmp/provider-result.json"
printf '{"type":"result","is_error":false,"result":"OK"}\n' >"$probe_json"
assert_eq "passed" \
  "$(real_case_provider_probe_status 0 "$probe_json")" \
  "provider probe passed on successful result json"

printf '{"type":"result","is_error":true,"result":"API Error"}\n' >"$probe_json"
assert_eq "failed" \
  "$(real_case_provider_probe_status 0 "$probe_json")" \
  "provider probe failed on error result json"

assert_eq "failed" \
  "$(real_case_provider_probe_status 1 "$probe_json")" \
  "provider probe failed on nonzero exit"

assert_eq "timeout" \
  "$(real_case_provider_probe_status 124 "$probe_json")" \
  "provider probe timeout"

assert_eq "provider_probe_failed" \
  "$(real_case_provider_probe_reason failed)" \
  "provider probe failed reason"

if ! real_case_provider_probe_is_valid passed; then
  printf '[FAIL] passed provider probe should be valid\n' >&2
  exit 1
fi

if real_case_provider_probe_is_valid skipped; then
  printf '[FAIL] skipped provider probe should not be valid\n' >&2
  exit 1
fi

if real_case_provider_probe_is_valid failed; then
  printf '[FAIL] failed provider probe should not be valid\n' >&2
  exit 1
fi

assert_eq "provider_preflight_failed" \
  "$(real_case_provider_probe_invalid_reason failed)" \
  "provider probe failed invalid reason"

assert_eq "provider_preflight_timeout" \
  "$(real_case_provider_probe_invalid_reason timeout)" \
  "provider probe timeout invalid reason"

assert_eq "provider_preflight_skipped" \
  "$(real_case_provider_probe_invalid_reason skipped)" \
  "provider probe skipped invalid reason"

assert_eq "" \
  "$(real_case_provider_probe_invalid_reason passed)" \
  "provider probe passed has no invalid reason"

probe_transcript="$tmp/acpx-probe.log"
printf 'OK\n' >"$probe_transcript"
assert_eq "passed" \
  "$(real_case_acpx_provider_probe_status 0 "$probe_transcript")" \
  "acpx provider probe passed on successful transcript"

printf 'API Error: unavailable\n' >"$probe_transcript"
assert_eq "passed" \
  "$(real_case_acpx_provider_probe_status 0 "$probe_transcript")" \
  "acpx provider probe does not parse transcript prose"

printf 'OK\n' >"$probe_transcript"
assert_eq "failed" \
  "$(real_case_acpx_provider_probe_status 1 "$probe_transcript")" \
  "acpx provider probe failed on nonzero exit"

assert_eq "timeout" \
  "$(real_case_acpx_provider_probe_status 124 "$probe_transcript")" \
  "acpx provider probe timeout"

: >"$probe_transcript"
assert_eq "failed" \
  "$(real_case_acpx_provider_probe_status 0 "$probe_transcript")" \
  "acpx provider probe failed on empty transcript"
