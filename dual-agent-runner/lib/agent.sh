#!/usr/bin/env bash
set -euo pipefail

# 程序说明：
#   Agent 调用模块，统一封装 acpx claude / acpx codex 的执行方式。
# 参数说明：
#   本文件由 bin/dual source，不直接接收 CLI 参数；函数参数见 run_agent 注释。
# 关键步骤：
#   1. 为每个 Agent 阶段准备输出路径。
#   2. 执行 acpx 并捕获 stdout/stderr/exit_code。
#   3. 写入 step metadata 和事件，失败时把控制权交回主状态机。
# 函数：run_agent
# 作用：通过 acpx 执行一个 Agent 阶段，并把输出、错误、退出码和步骤元数据写入 run ledger。
# 参数：
#   $1 - step，阶段名，例如 01-claude-prd-architecture。
#   $2 - agent，acpx Agent 名称，例如 claude 或 codex。
#   $3 - session，acpx 会话名，例如 claude-main 或 codex-reviewer。
#   $4 - prompt_file，本阶段要传给 acpx 的 prompt 文件路径。
# 关键步骤：
#   1. 写入 agent_step_started 事件。
#   2. 在 WORKDIR 中执行 acpx，并分别保存 stdout、stderr、exit_code。
#   3. 生成 steps/<step>.json，记录开始/结束时间和耗时。
#   4. 写入 agent_step_finished；如果退出码非 0，写 run_failed 并把错误返回给主流程。
run_agent() {
  local step="$1"
  local agent="$2"
  local session="$3"
  local prompt_file="$4"

  local stdout_file="$OUTPUTS_DIR/${step}.md"
  local stderr_file="$OUTPUTS_DIR/${step}.stderr.log"
  local exit_code_file="$OUTPUTS_DIR/${step}.exit_code"
  local step_metadata_file="$STEPS_DIR/${step}.json"

  mkdir -p "$OUTPUTS_DIR" "$STEPS_DIR"

  local started_at
  local finished_at
  local start_seconds
  local end_seconds
  local duration_ms

  started_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  start_seconds="$(date -u +"%s")"

  event "agent_step_started" "$(jq -cn \
    --arg step "$step" \
    --arg actor "$agent" \
    --arg session "$session" \
    --arg prompt_path "$prompt_file" \
    --arg output_path "$stdout_file" \
    '{step: $step, actor: $actor, session: $session, prompt_path: $prompt_path, output_path: $output_path}')"

  local exit_code=0
  set +e
  (cd "$WORKDIR" && acpx "$agent" -s "$session" -f "$prompt_file") >"$stdout_file" 2>"$stderr_file"
  exit_code=$?
  set -e

  finished_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  end_seconds="$(date -u +"%s")"
  duration_ms=$(( (end_seconds - start_seconds) * 1000 ))

  printf '%s\n' "$exit_code" > "$exit_code_file"

  jq -cn \
    --arg step "$step" \
    --arg agent "$agent" \
    --arg session "$session" \
    --arg prompt_path "$prompt_file" \
    --arg output_path "$stdout_file" \
    --arg stderr_path "$stderr_file" \
    --arg started_at "$started_at" \
    --arg finished_at "$finished_at" \
    --argjson duration_ms "$duration_ms" \
    --argjson exit_code "$exit_code" \
    '{
      step: $step,
      agent: $agent,
      session: $session,
      prompt_path: $prompt_path,
      output_path: $output_path,
      stderr_path: $stderr_path,
      started_at: $started_at,
      finished_at: $finished_at,
      duration_ms: $duration_ms,
      exit_code: $exit_code
    }' > "$step_metadata_file"

  event "agent_step_finished" "$(jq -cn \
    --arg step "$step" \
    --arg actor "$agent" \
    --arg session "$session" \
    --arg prompt_path "$prompt_file" \
    --arg output_path "$stdout_file" \
    --argjson exit_code "$exit_code" \
    --argjson duration_ms "$duration_ms" \
    --arg stderr_path "$stderr_file" \
    --arg step_metadata_file "$step_metadata_file" \
    '{step: $step, actor: $actor, session: $session, prompt_path: $prompt_path, output_path: $output_path, exit_code: $exit_code, duration_ms: $duration_ms, data: {stderr_path: $stderr_path, step_metadata_path: $step_metadata_file}}')"

  if [[ "$exit_code" -ne 0 ]]; then
    event "run_failed" "$(jq -cn \
      --arg step "$step" \
      --arg actor "$agent" \
      --arg session "$session" \
      --arg prompt_path "$prompt_file" \
      --arg output_path "$stdout_file" \
      --arg reason "agent exited with non-zero status" \
      --argjson exit_code "$exit_code" \
      --argjson duration_ms "$duration_ms" \
      '{step: $step, actor: $actor, session: $session, prompt_path: $prompt_path, output_path: $output_path, reason: $reason, exit_code: $exit_code, duration_ms: $duration_ms, data: {reason: $reason}}')"
    echo "Agent step failed: $step (exit code $exit_code)" >&2
    echo "stderr: $stderr_file" >&2
    return "$exit_code"
  fi
}
