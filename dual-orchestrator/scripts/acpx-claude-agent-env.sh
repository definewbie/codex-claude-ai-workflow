#!/usr/bin/env bash
#
# acpx-claude-agent-env.sh — 为 Claude ACP adapter 注入 Claude Code 环境变量。
#
# 背景：
# - Claude Code CLI 会读取 ~/.claude/settings.json 里的 env 配置。
# - claude-agent-acp 的 ACP query 路径目前只继承进程 env，不会自动把
#   settings.json 的 env 传给 SDK 子进程。
# - 使用 GLM / DeepSeek / one-api 等 Anthropic-compatible 后端时，
#   ANTHROPIC_BASE_URL 和 token 往往只配置在 Claude Code settings.env 中。
#
# 作用：
# 1. 读取 ${CLAUDE_CONFIG_DIR:-$HOME/.claude}/settings.json 的 env 字段。
# 2. 只导出固定的 provider 连接 allowlist，不转发 proxy 或任意用户变量。
# 3. exec 传入的 pinned Claude ACP adapter 命令。
#
# 参数：
#   $@  可选。要执行的 Claude ACP adapter 命令。
#       如果为空，使用脚本内置的 pinned 默认命令。
#
# 关键边界：
# - 不打印 env 值，避免泄露 token。
# - 不把 token 写入 ~/.acpx/config.json。
# - 这里只解决 adapter runtime env，不承担 workflow 状态或 session 持久化。

set -euo pipefail

DEFAULT_CLAUDE_ACP_COMMAND=(npx -y @agentclientprotocol/claude-agent-acp@0.70.0)
CLAUDE_SETTINGS_PATH="${CLAUDE_CONFIG_DIR:-$HOME/.claude}/settings.json"

fail() {
  printf '[ACPX-CLAUDE-ENV][FAIL] %s\n' "$*" >&2
  exit 1
}

load_claude_settings_env() {
  [[ -f "$CLAUDE_SETTINGS_PATH" ]] || return 0
  command -v node >/dev/null 2>&1 || fail "node is required to read $CLAUDE_SETTINGS_PATH"

  # 固定 allowlist 是 adapter 连接输入，不是通用 settings passthrough。
  while IFS= read -r -d '' key && IFS= read -r -d '' value; do
    export "$key=$value"
  done < <(node - "$CLAUDE_SETTINGS_PATH" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
let root = {};
try {
  root = JSON.parse(fs.readFileSync(path, "utf8"));
} catch {
  process.exit(0);
}
const env = root && typeof root.env === "object" && !Array.isArray(root.env) ? root.env : {};
const allowed = new Set([
  "ANTHROPIC_BASE_URL",
  "ANTHROPIC_AUTH_TOKEN",
  "ANTHROPIC_API_KEY",
  "ANTHROPIC_MODEL",
  "ANTHROPIC_DEFAULT_SONNET_MODEL",
  "ANTHROPIC_DEFAULT_OPUS_MODEL",
  "ANTHROPIC_DEFAULT_HAIKU_MODEL",
  "CLAUDE_CODE_MODEL",
]);
for (const [key, value] of Object.entries(env)) {
  if (!allowed.has(key)) {
    continue;
  }
  if (value === null || value === undefined) {
    continue;
  }
  process.stdout.write(`${key}\0${String(value)}\0`);
}
NODE
  )
}

main() {
  unset ALL_PROXY HTTP_PROXY HTTPS_PROXY NO_PROXY all_proxy http_proxy https_proxy no_proxy
  load_claude_settings_env
  if [[ "$#" -gt 0 ]]; then
    exec "$@"
  fi
  exec "${DEFAULT_CLAUDE_ACP_COMMAND[@]}"
}

main "$@"
