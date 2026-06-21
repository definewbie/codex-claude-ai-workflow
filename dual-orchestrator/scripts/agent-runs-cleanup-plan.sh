#!/usr/bin/env bash
set -euo pipefail

# 生成 agent-runs worktree 清理计划。
#
# 参数说明：
#   --repo <path>        原始业务仓库路径，例如 /path/to/cancer-clinic。
#   --agent-runs <path> 可选，显式指定 agent-runs 目录。
#
# 关键步骤：
#   1. 根据 repo 推导默认 <repo-parent>/<repo-name>-agent-runs。
#   2. 扫描每个 worktree 下的 .agent/workstreams/*/workflow/current.json。
#   3. 输出状态和安全清理命令；脚本不执行删除。

REPO=""
AGENT_RUNS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      REPO="${2:-}"
      shift 2
      ;;
    --agent-runs)
      AGENT_RUNS="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$REPO" && -z "$AGENT_RUNS" ]]; then
  echo "Usage: $0 --repo <repo> [--agent-runs <dir>]" >&2
  exit 2
fi

if [[ -z "$AGENT_RUNS" ]]; then
  repo_abs="$(cd "$REPO" && pwd)"
  repo_parent="$(dirname "$repo_abs")"
  repo_name="$(basename "$repo_abs")"
  AGENT_RUNS="$repo_parent/$repo_name-agent-runs"
fi

if [[ ! -d "$AGENT_RUNS" ]]; then
  echo "agent-runs directory not found: $AGENT_RUNS" >&2
  exit 1
fi

echo "Agent runs: $AGENT_RUNS"
echo
printf "%-42s %-12s %-28s %s\n" "WORKTREE" "STATUS" "WORKSTREAM" "CLEANUP"

find "$AGENT_RUNS" -mindepth 1 -maxdepth 1 -type d | sort | while IFS= read -r worktree; do
  status="unknown"
  workstream="-"
  current_json="$(find "$worktree/.agent/workstreams" -path '*/workflow/current.json' -type f 2>/dev/null | head -n 1 || true)"
  if [[ -n "$current_json" ]]; then
    workstream="$(basename "$(dirname "$(dirname "$current_json")")")"
    if command -v jq >/dev/null 2>&1; then
      status="$(jq -r '.status // "unknown"' "$current_json" 2>/dev/null || echo "unknown")"
    else
      status="unknown(jq-missing)"
    fi
  fi
  cleanup="git worktree remove \"$worktree\""
  printf "%-42s %-12s %-28s %s\n" "$(basename "$worktree")" "$status" "$workstream" "$cleanup"
done

echo
echo "Review terminal rows first. For branch cleanup, run: git branch -D <branch>"
