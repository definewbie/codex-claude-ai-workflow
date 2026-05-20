#!/usr/bin/env bash
set -euo pipefail

# 程序说明：
#   Git 证据采集模块，负责记录修改前后的仓库状态和 diff。
# 参数说明：
#   本文件由 bin/dual source，不直接接收 CLI 参数；函数依赖 WORKDIR、SNAPSHOTS_DIR 等全局变量。
# 关键步骤：
#   1. 读取 base commit 和当前分支。
#   2. 在 Agent 执行前后分别保存 git status。
#   3. 保存 diff.stat 和 diff.patch；非 Git 目录写 unavailable 占位。
# 函数：git_base_commit
# 作用：读取当前有效工作目录的 HEAD commit，作为本次 run 的基准提交。
# 参数：无。依赖 WORKDIR。
# 关键步骤：调用 git rev-parse HEAD 并输出结果。
git_base_commit() {
  git -C "$WORKDIR" rev-parse HEAD
}

# 函数：git_branch
# 作用：读取当前有效工作目录所在分支名。
# 参数：无。依赖 WORKDIR。
# 关键步骤：调用 git rev-parse --abbrev-ref HEAD 并输出结果。
git_branch() {
  git -C "$WORKDIR" rev-parse --abbrev-ref HEAD
}

# 函数：write_git_unavailable_snapshot
# 作用：在非 Git 目录或 Git 不可用时，生成占位 Git 证据，避免报告阶段缺文件。
# 参数：
#   $1 - phase，采集阶段，取值 before 或 after。
#   $2 - reason，不可用原因，例如 not a git repository。
# 关键步骤：
#   1. 确保 snapshots 目录存在。
#   2. 写入 base-commit.txt 的 unavailable 占位。
#   3. 按 before/after 阶段分别写入 status、diff.stat、diff.patch 占位文件。
write_git_unavailable_snapshot() {
  local phase="$1"
  local reason="$2"

  mkdir -p "$SNAPSHOTS_DIR"

  if [[ ! -f "$SNAPSHOTS_DIR/base-commit.txt" ]]; then
    printf 'unavailable\n' > "$SNAPSHOTS_DIR/base-commit.txt"
  fi

  case "$phase" in
    before)
      printf 'unavailable: %s\n' "$reason" > "$SNAPSHOTS_DIR/git-status-before.txt"
      ;;
    after)
      printf 'unavailable: %s\n' "$reason" > "$SNAPSHOTS_DIR/git-status-after.txt"
      printf 'unavailable: %s\n' "$reason" > "$SNAPSHOTS_DIR/diff.stat"
      printf 'unavailable: %s\n' "$reason" > "$SNAPSHOTS_DIR/diff.patch"
      ;;
  esac
}

# 函数：capture_git_before
# 作用：在 Agent 修改代码前采集 Git 基线证据。
# 参数：无。依赖 WORKDIR、SNAPSHOTS_DIR、GIT_AVAILABLE。
# 关键步骤：
#   1. 如果 Git 不可用，写 unavailable 占位并记录事件。
#   2. 保存 base-commit.txt。
#   3. 保存 git-status-before.txt 并记录 git_snapshot_before 事件。
capture_git_before() {
  mkdir -p "$SNAPSHOTS_DIR"

  if [[ "${GIT_AVAILABLE:-false}" != "true" ]]; then
    write_git_unavailable_snapshot "before" "${GIT_UNAVAILABLE_REASON:-git unavailable}"
    event "git_snapshot_before" "$(jq -cn \
      --arg actor "git" \
      --arg output_path "$SNAPSHOTS_DIR/git-status-before.txt" \
      --arg reason "${GIT_UNAVAILABLE_REASON:-git unavailable}" \
      '{actor: $actor, output_path: $output_path, data: {status: "unavailable", reason: $reason}}')"
    return 0
  fi

  git_base_commit > "$SNAPSHOTS_DIR/base-commit.txt"
  git -C "$WORKDIR" status --short > "$SNAPSHOTS_DIR/git-status-before.txt"

  event "git_snapshot_before" "$(jq -cn \
    --arg actor "git" \
    --arg output_path "$SNAPSHOTS_DIR/git-status-before.txt" \
    --arg base_commit "$(cat "$SNAPSHOTS_DIR/base-commit.txt")" \
    '{actor: $actor, output_path: $output_path, data: {base_commit: $base_commit}}')"
}

# 函数：capture_git_after
# 作用：在实现和验证之后采集 Git 结果证据。
# 参数：无。依赖 WORKDIR、SNAPSHOTS_DIR、GIT_AVAILABLE。
# 关键步骤：
#   1. 如果 Git 不可用，写 unavailable 占位并记录事件。
#   2. 保存 git-status-after.txt。
#   3. 保存 diff.stat 和 diff.patch，供最终验收和报告使用。
capture_git_after() {
  mkdir -p "$SNAPSHOTS_DIR"

  if [[ "${GIT_AVAILABLE:-false}" != "true" ]]; then
    write_git_unavailable_snapshot "after" "${GIT_UNAVAILABLE_REASON:-git unavailable}"
    event "git_snapshot_after" "$(jq -cn \
      --arg actor "git" \
      --arg output_path "$SNAPSHOTS_DIR/diff.patch" \
      --arg reason "${GIT_UNAVAILABLE_REASON:-git unavailable}" \
      '{actor: $actor, output_path: $output_path, data: {status: "unavailable", reason: $reason}}')"
    return 0
  fi

  git -C "$WORKDIR" status --short > "$SNAPSHOTS_DIR/git-status-after.txt"
  git -C "$WORKDIR" diff --stat > "$SNAPSHOTS_DIR/diff.stat"
  git -C "$WORKDIR" diff > "$SNAPSHOTS_DIR/diff.patch"

  event "git_snapshot_after" "$(jq -cn \
    --arg actor "git" \
    --arg diff_stat_file "$SNAPSHOTS_DIR/diff.stat" \
    --arg diff_patch_file "$SNAPSHOTS_DIR/diff.patch" \
    '{actor: $actor, output_path: $diff_patch_file, data: {diff_stat_file: $diff_stat_file, diff_patch_file: $diff_patch_file}}')"
}
