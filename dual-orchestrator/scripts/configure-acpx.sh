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
#   DUAL_CLAUDE_ACP_COMMAND='npx -y @agentclientprotocol/claude-agent-acp@0.46.0' ./scripts/configure-acpx.sh
#   DUAL_CODEX_ACP_COMMAND='npx -y @zed-industries/codex-acp-darwin-arm64@0.16.0' ./scripts/configure-acpx.sh
#

set -euo pipefail

EXPECTED_ACPX_VERSION="${DUAL_ACPX_VERSION:-0.11.0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLAUDE_ACP_COMMAND="${DUAL_CLAUDE_ACP_COMMAND:-$SCRIPT_DIR/acpx-claude-agent-env.sh npx -y @agentclientprotocol/claude-agent-acp@0.46.0}"
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

codex_platform_package() {
  local os arch suffix
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  arch="$(uname -m)"
  case "$os:$arch" in
    darwin:arm64) suffix="darwin-arm64" ;;
    darwin:x86_64) suffix="darwin-x64" ;;
    linux:arm64|linux:aarch64) suffix="linux-arm64" ;;
    linux:x86_64) suffix="linux-x64" ;;
    *)
      fail "Unsupported platform for @zed-industries/codex-acp binary: $os/$arch"
      ;;
  esac
  printf '@zed-industries/codex-acp-%s@0.16.0' "$suffix"
}

check_acpx_version() {
  local actual
  actual="$(acpx --version 2>/dev/null | head -1 | tr -d '[:space:]')"
  [[ "$actual" == "$EXPECTED_ACPX_VERSION" ]] || fail "Expected acpx $EXPECTED_ACPX_VERSION, found ${actual:-unknown}. Install with: npm install -g acpx@$EXPECTED_ACPX_VERSION"
  log "acpx version pinned: $actual"
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
  require_command acpx
  CODEX_ACP_COMMAND="${DUAL_CODEX_ACP_COMMAND:-npx -y $(codex_platform_package)}"
  check_acpx_version
  write_config
  acpx config show >/dev/null
  log "acpx config is readable"
}

main "$@"
