#!/usr/bin/env bash
#
# configure-acpx.sh — Pin acpx and its Codex ACP adapter for reproducible runs.
#
# The orchestrator calls `acpx codex ...`; acpx itself resolves the Codex ACP
# adapter from its config. Leaving that adapter on a floating npm range makes
# real runs non-reproducible. This script writes an explicit command into the
# user's acpx config.
#
# Usage:
#   ./scripts/configure-acpx.sh
#   DUAL_CODEX_ACP_COMMAND='npx -y @zed-industries/codex-acp@0.12.0' ./scripts/configure-acpx.sh
#

set -euo pipefail

EXPECTED_ACPX_VERSION="${DUAL_ACPX_VERSION:-0.8.0}"
CODEX_ACP_COMMAND="${DUAL_CODEX_ACP_COMMAND:-npx -y @zed-industries/codex-acp@0.12.0}"
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
  actual="$(acpx --version 2>/dev/null | head -1 | tr -d '[:space:]')"
  [[ "$actual" == "$EXPECTED_ACPX_VERSION" ]] || fail "Expected acpx $EXPECTED_ACPX_VERSION, found ${actual:-unknown}. Install with: npm install -g acpx@$EXPECTED_ACPX_VERSION"
  log "acpx version pinned: $actual"
}

write_config() {
  mkdir -p "$(dirname "$CONFIG_PATH")"
  node - "$CONFIG_PATH" "$CODEX_ACP_COMMAND" <<'NODE'
const fs = require("fs");
const [configPath, codexCommand] = process.argv.slice(2);
let config = {};
if (fs.existsSync(configPath)) {
  config = JSON.parse(fs.readFileSync(configPath, "utf8"));
}
config.agents = config.agents || {};
config.agents.codex = { command: codexCommand };
fs.writeFileSync(configPath, JSON.stringify(config, null, 2) + "\n");
NODE
  log "wrote $CONFIG_PATH"
  log "agents.codex.command = $CODEX_ACP_COMMAND"
}

main() {
  require_command node
  require_command acpx
  check_acpx_version
  write_config
  acpx config show >/dev/null
  log "acpx config is readable"
}

main "$@"
