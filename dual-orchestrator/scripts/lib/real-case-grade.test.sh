#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# shellcheck source=scripts/lib/real-case-grade.sh
source "$ROOT/scripts/lib/real-case-grade.sh"

assert_eq() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$expected" != "$actual" ]]; then
    printf '[FAIL] %s: expected %s, got %s\n' "$label" "$expected" "$actual" >&2
    exit 1
  fi
}

assert_eq "PASS" "$(real_case_grade COMPLETED true "" "ACCEPT" 0)" "completed accept"
assert_eq "FAIL" "$(real_case_grade START_FAILED true "" "" 0)" "start failed"
assert_eq "INVALID" "$(real_case_grade START_FAILED true "" "" 0 provider_connection_failed)" "invalid precondition"
assert_eq "invalid=provider_connection_failed" "$(real_case_grade_reason START_FAILED true "" "" 0 provider_connection_failed)" "invalid reason"

