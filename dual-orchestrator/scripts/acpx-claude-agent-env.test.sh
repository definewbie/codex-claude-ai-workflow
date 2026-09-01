#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT
mkdir -p "$TMP_ROOT/.claude"
cat >"$TMP_ROOT/.claude/settings.json" <<'JSON'
{
  "env": {
    "ANTHROPIC_BASE_URL": "https://provider.example",
    "ANTHROPIC_AUTH_TOKEN": "secret-token",
    "ANTHROPIC_MODEL": "$(printf injected)",
    "HTTP_PROXY": "http://forbidden-proxy",
    "DUAL_UNRELATED_SETTING": "must-not-cross"
  }
}
JSON

OUTPUT="$({
  HOME="$TMP_ROOT" \
  ALL_PROXY="http://parent-all-proxy" \
  HTTP_PROXY="http://parent-proxy" \
  HTTPS_PROXY="http://parent-secure-proxy" \
  NO_PROXY="parent.example" \
  all_proxy="http://parent-all-proxy-lower" \
  http_proxy="http://parent-proxy-lower" \
  https_proxy="http://parent-secure-proxy-lower" \
  no_proxy="parent-lower.example" \
  DUAL_PARENT_ONLY="must-not-cross" \
  "$SCRIPT_DIR/acpx-claude-agent-env.sh" sh -c '
    printf "base=%s\n" "${ANTHROPIC_BASE_URL-}"
    printf "token=%s\n" "${ANTHROPIC_AUTH_TOKEN-}"
    printf "model=%s\n" "${ANTHROPIC_MODEL-}"
    printf "proxy=%s\n" "${HTTP_PROXY-}"
    printf "proxy_names=%s\n" "${ALL_PROXY-}${HTTPS_PROXY-}${NO_PROXY-}${all_proxy-}${http_proxy-}${https_proxy-}${no_proxy-}"
    printf "settings_other=%s\n" "${DUAL_UNRELATED_SETTING-}"
    printf "parent_only=%s\n" "${DUAL_PARENT_ONLY-}"
  '
} 2>&1)"

[[ "$OUTPUT" == *"base=https://provider.example"* ]]
[[ "$OUTPUT" == *"token=secret-token"* ]]
[[ "$OUTPUT" == *'model=$(printf injected)'* ]]
[[ "$OUTPUT" == *$'proxy=\n'* ]]
[[ "$OUTPUT" == *$'proxy_names=\n'* ]]
[[ "$OUTPUT" == *$'settings_other='* ]]
[[ "$OUTPUT" == *'parent_only=must-not-cross'* ]]
[[ "$OUTPUT" != *"forbidden-proxy"* ]]
printf '[PASS] Claude adapter env uses the fixed provider allowlist\n'
