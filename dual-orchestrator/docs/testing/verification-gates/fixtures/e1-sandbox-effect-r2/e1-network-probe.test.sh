#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

PROXY_NAMES=(
  HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY
  http_proxy https_proxy all_proxy no_proxy
)
CREDENTIAL_NAMES=(ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN ANTHROPIC_BASE_URL)
ORIGINAL_PATH="$PATH"

run_clean() {
  local workdir="$1"
  shift
  mkdir -p "$workdir/tmp"
  (
    cd "$workdir"
    env \
      -u "${CREDENTIAL_NAMES[0]}" -u "${CREDENTIAL_NAMES[1]}" -u "${CREDENTIAL_NAMES[2]}" \
      -u "${PROXY_NAMES[0]}" -u "${PROXY_NAMES[1]}" -u "${PROXY_NAMES[2]}" -u "${PROXY_NAMES[3]}" \
      -u "${PROXY_NAMES[4]}" -u "${PROXY_NAMES[5]}" -u "${PROXY_NAMES[6]}" -u "${PROXY_NAMES[7]}" \
      TMPDIR="$workdir/tmp" "$@" "$SCRIPT_DIR/e1-network-probe.sh" --classify-env-only
  )
}

run_clean "$TMP_ROOT/absent" > "$TMP_ROOT/absent.out"
grep -q 'E1_PROXY_CLASSIFICATION_COMPLETE' "$TMP_ROOT/absent.out"
test "$(grep -c $'\tabsent$' "$TMP_ROOT/absent/e1-probe-output/environment.tsv")" -eq 11

run_clean "$TMP_ROOT/controlled" \
  HTTP_PROXY=http://127.0.0.1:41001 \
  HTTPS_PROXY=http://localhost:41001 \
  ALL_PROXY= \
  NO_PROXY=localhost,127.0.0.1 \
  http_proxy=http://127.0.0.1:41001 \
  https_proxy=http://localhost:41001 \
  all_proxy=socks5h://127.0.0.1:41002 \
  no_proxy= > "$TMP_ROOT/controlled.out"
test "$(grep -c $'\tcontrolled_loopback$' "$TMP_ROOT/controlled/e1-probe-output/environment.tsv")" -eq 5
grep -q $'NO_PROXY\tcontrolled_local_only$' "$TMP_ROOT/controlled/e1-probe-output/environment.tsv"
grep -q $'ALL_PROXY\tempty$' "$TMP_ROOT/controlled/e1-probe-output/environment.tsv"
grep -q $'no_proxy\tempty$' "$TMP_ROOT/controlled/e1-probe-output/environment.tsv"
! grep -q '4100' "$TMP_ROOT/controlled/e1-probe-output/environment.tsv"

run_clean "$TMP_ROOT/loopback-range" \
  HTTP_PROXY=http://127.0.0.2:41001 \
  NO_PROXY=localhost,127.0.0.1, \
  > "$TMP_ROOT/loopback-range.out"
grep -q $'HTTP_PROXY\tcontrolled_loopback$' "$TMP_ROOT/loopback-range/e1-probe-output/environment.tsv"
grep -q $'NO_PROXY\tcontrolled_local_only$' "$TMP_ROOT/loopback-range/e1-probe-output/environment.tsv"

set +e
run_clean "$TMP_ROOT/loopback-lookalike" HTTP_PROXY=http://127.evil:41001 \
  > "$TMP_ROOT/loopback-lookalike.out" 2> "$TMP_ROOT/loopback-lookalike.err"
lookalike_exit=$?
set -e
test "$lookalike_exit" -eq 74
grep -q $'HTTP_PROXY\texternal$' "$TMP_ROOT/loopback-lookalike/e1-probe-output/environment.tsv"
! grep -q '127.evil' "$TMP_ROOT/loopback-lookalike/e1-probe-output/environment.tsv"
! grep -q '127.evil' "$TMP_ROOT/loopback-lookalike.err"

for classifier_exit in 126 127; do
  fake_bin="$TMP_ROOT/node-exit-${classifier_exit}"
  mkdir -p "$fake_bin"
  printf '#!/usr/bin/env bash\nexit %s\n' "$classifier_exit" > "$fake_bin/node"
  chmod +x "$fake_bin/node"
  set +e
  run_clean "$TMP_ROOT/classifier-${classifier_exit}" \
    PATH="$fake_bin:$ORIGINAL_PATH" \
    HTTP_PROXY=http://127.0.0.1:41001 \
    > "$TMP_ROOT/classifier-${classifier_exit}.out" \
    2> "$TMP_ROOT/classifier-${classifier_exit}.err"
  actual_exit=$?
  set -e
  test "$actual_exit" -eq 76
  grep -q $'E1_PROXY_CLASSIFIER_UNAVAILABLE\tself_test\texit_'"$classifier_exit" \
    "$TMP_ROOT/classifier-${classifier_exit}.err"
  ! grep -q 'E1_UNSAFE_PROXY_PROVENANCE' "$TMP_ROOT/classifier-${classifier_exit}.err"
done

set +e
run_clean "$TMP_ROOT/external" HTTP_PROXY=http://proxy.example:8080 \
  > "$TMP_ROOT/external.out" 2> "$TMP_ROOT/external.err"
external_exit=$?
set -e
test "$external_exit" -eq 74
grep -q $'HTTP_PROXY\texternal$' "$TMP_ROOT/external/e1-probe-output/environment.tsv"
! grep -q 'proxy.example' "$TMP_ROOT/external/e1-probe-output/environment.tsv"
! grep -q 'proxy.example' "$TMP_ROOT/external.err"

set +e
run_clean "$TMP_ROOT/userinfo-bypass" HTTP_PROXY=http://localhost:41001@proxy.example:8080 \
  > "$TMP_ROOT/userinfo-bypass.out" 2> "$TMP_ROOT/userinfo-bypass.err"
userinfo_exit=$?
set -e
test "$userinfo_exit" -eq 74
grep -q $'HTTP_PROXY\texternal$' "$TMP_ROOT/userinfo-bypass/e1-probe-output/environment.tsv"
! grep -q 'proxy.example' "$TMP_ROOT/userinfo-bypass/e1-probe-output/environment.tsv"
! grep -q 'proxy.example' "$TMP_ROOT/userinfo-bypass.err"

set +e
run_clean "$TMP_ROOT/credential" ANTHROPIC_AUTH_TOKEN=secret-canary \
  > "$TMP_ROOT/credential.out" 2> "$TMP_ROOT/credential.err"
credential_exit=$?
set -e
test "$credential_exit" -eq 73
grep -q $'ANTHROPIC_AUTH_TOKEN\tpresent$' "$TMP_ROOT/credential/e1-probe-output/environment.tsv"
! grep -q 'secret-canary' "$TMP_ROOT/credential/e1-probe-output/environment.tsv"
! grep -q 'secret-canary' "$TMP_ROOT/credential.err"

set +e
run_clean "$TMP_ROOT/no-proxy-wildcard" NO_PROXY='*' \
  > "$TMP_ROOT/no-proxy-wildcard.out" 2> "$TMP_ROOT/no-proxy-wildcard.err"
wildcard_exit=$?
set -e
test "$wildcard_exit" -eq 74
grep -q $'NO_PROXY\texternal_bypass$' "$TMP_ROOT/no-proxy-wildcard/e1-probe-output/environment.tsv"

printf '[PASS] E1 proxy provenance classification is value-safe and fail-closed\n'
