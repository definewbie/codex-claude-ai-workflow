#!/usr/bin/env bash
#
# configure-acpx.sh — Pin acpx and ACP adapters for reproducible runs.
#
# The orchestrator calls `acpx claude ...` and `acpx codex ...`; acpx itself
# resolves those ACP adapters from its config. Leaving either adapter on a
# floating npm range makes real runs non-reproducible. This script writes
# explicit commands into the user's acpx config.
#
# Usage:
#   ./scripts/configure-acpx.sh
#   DUAL_CLAUDE_ACP_COMMAND='npx -y @agentclientprotocol/claude-agent-acp@0.70.0' ./scripts/configure-acpx.sh
#   DUAL_CODEX_ACP_COMMAND='npx -y @agentclientprotocol/codex-acp@1.10.0' ./scripts/configure-acpx.sh
#

set -euo pipefail

EXPECTED_ACPX_VERSION="0.13.2"
EXPECTED_UPSTREAM_COMMIT="fd173f04aa1b56f9e3f5ca5190c034ddcae28792"
EXPECTED_PATCH_SHA256="2313fe2d53e573dc0227a453fd1c4cbe6107373472a2027bb7ed3e2952038b63"
EXPECTED_POLICY_SCHEMA="claude_execution_policy.v2"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ACPX_EXECUTABLE="${DUAL_ACPX_EXECUTABLE:-$REPO_ROOT/.tools/acpx-e1/bin/acpx}"
CLAUDE_ACP_COMMAND="${DUAL_CLAUDE_ACP_COMMAND:-$SCRIPT_DIR/acpx-claude-agent-env.sh npx -y @agentclientprotocol/claude-agent-acp@0.70.0}"
CONFIG_PATH="${ACPX_CONFIG_PATH:-$HOME/.acpx/config.json}"

fail() {
  printf '[ACPX][FAIL] %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[ACPX] %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

check_acpx_version() {
  local actual
  actual="$("$ACPX_EXECUTABLE" --version 2>/dev/null | head -1 | tr -d '[:space:]')"
  [[ "$actual" == "$EXPECTED_ACPX_VERSION" ]] || fail "Expected patched acpx $EXPECTED_ACPX_VERSION, found ${actual:-unknown}. Run ./scripts/install-acpx-e1.sh"
  log "acpx version pinned: $actual"
}

check_acpx_build_identity() {
  local build_info
  build_info="$("$ACPX_EXECUTABLE" build-info --json 2>/dev/null)" || fail "NETWORK_AUTHORITY_NOT_CONFIGURED: patched acpx build-info is unavailable"
  if ! node - "$build_info" "$EXPECTED_UPSTREAM_COMMIT" "$EXPECTED_PATCH_SHA256" "$EXPECTED_POLICY_SCHEMA" <<'NODE'
const [raw, upstream, patch, schema] = process.argv.slice(2);
const value = JSON.parse(raw);
if (value.upstream_commit !== upstream || value.patch_sha256 !== patch || value.policy_schema !== schema) {
  process.exit(1);
}
NODE
  then
    fail "NETWORK_AUTHORITY_NOT_CONFIGURED: patched acpx build identity mismatch"
  fi
  log "E1 policy transport build identity verified"
}

write_config() {
  mkdir -p "$(dirname "$CONFIG_PATH")"
  node - "$CONFIG_PATH" "$CLAUDE_ACP_COMMAND" "$CODEX_ACP_COMMAND" <<'NODE'
const fs = require("fs");
const [configPath, claudeCommand, codexCommand] = process.argv.slice(2);
let config = {};
if (fs.existsSync(configPath)) {
  config = JSON.parse(fs.readFileSync(configPath, "utf8"));
}
config.agents = config.agents || {};
config.agents.claude = { command: claudeCommand };
config.agents.codex = { command: codexCommand };
fs.writeFileSync(configPath, JSON.stringify(config, null, 2) + "\n");
NODE
  log "wrote $CONFIG_PATH"
  log "agents.claude.command = $CLAUDE_ACP_COMMAND"
  log "agents.codex.command = $CODEX_ACP_COMMAND"
}

main() {
  require_command node
  [[ -x "$ACPX_EXECUTABLE" ]] || fail "Patched acpx is missing: $ACPX_EXECUTABLE. Run ./scripts/install-acpx-e1.sh"
  CODEX_ACP_COMMAND="${DUAL_CODEX_ACP_COMMAND:-npx -y @agentclientprotocol/codex-acp@1.10.0}"
  check_acpx_version
  check_acpx_build_identity
  write_config
  "$ACPX_EXECUTABLE" config show >/dev/null
  log "acpx config is readable"
  log "export DUAL_ACPX_EXECUTABLE=$ACPX_EXECUTABLE before running Dual"
}

main "$@"
