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

assert_eq "unproven_skip_build" \
  "$(real_case_artifact_freshness_status true "$jar" "$past")" \
  "skip build is unproven"

assert_eq "jar_missing" \
  "$(real_case_artifact_freshness_status false "$tmp/missing.jar" "$past")" \
  "missing jar"

assert_eq "jar_mtime_is_after_build_start" \
  "$(real_case_artifact_freshness_reason fresh)" \
  "fresh reason"

if ! real_case_artifact_freshness_is_valid fresh; then
  printf '[FAIL] fresh should be valid\n' >&2
  exit 1
fi

if real_case_artifact_freshness_is_valid unproven_skip_build; then
  printf '[FAIL] skip build should not be valid\n' >&2
  exit 1
fi
