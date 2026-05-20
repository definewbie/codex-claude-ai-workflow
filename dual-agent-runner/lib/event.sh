#!/usr/bin/env bash
set -euo pipefail

# 程序说明：
#   事件写入模块，负责把运行过程中的关键动作记录到 events.jsonl。
# 参数说明：
#   本文件由 bin/dual source，不直接接收 CLI 参数；函数参数见 event 注释。
# 关键步骤：
#   1. 使用固定字段生成 JSONL 事件。
#   2. 将扩展信息放入 data 字段。
#   3. 让 final-report.md 可以根据事件还原运行时间线。
# 函数：event
# 作用：向当前 run ledger 的 events.jsonl 追加一条事件，保持事件 schema 稳定，方便后续复盘。
# 参数：
#   $1 - 事件名称，例如 agent_step_started、git_snapshot_after。
#   $2 - 可选 JSON 对象，用于补充 actor、step、session、prompt_path、output_path、exit_code、duration_ms、data 等字段。
# 关键步骤：
#   1. 校验 EVENTS_FILE 是否已经由主流程初始化。
#   2. 使用 jq 组装固定字段，缺失字段统一写成 null。
#   3. 以 JSONL 形式追加写入 events.jsonl。
event() {
  local event_type="$1"
  local payload="{}"

  if [[ $# -ge 2 ]]; then
    payload="$2"
  fi

  if [[ -z "${EVENTS_FILE:-}" ]]; then
    echo "EVENTS_FILE is not set" >&2
    return 1
  fi

  mkdir -p "$(dirname "$EVENTS_FILE")"

  jq -cn \
    --arg ts "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
    --arg run_id "${RUN_ID:-}" \
    --arg event_type "$event_type" \
    --argjson payload "$payload" \
    '
    def empty_to_null:
      if . == null or . == "" then null else . end;

    {
      ts: $ts,
      run_id: ($payload.run_id // $run_id | empty_to_null),
      actor: ($payload.actor // null | empty_to_null),
      event: $event_type,
      step: ($payload.step // null | empty_to_null),
      session: ($payload.session // null | empty_to_null),
      prompt_path: ($payload.prompt_path // null | empty_to_null),
      output_path: ($payload.output_path // null | empty_to_null),
      exit_code: ($payload.exit_code // null),
      duration_ms: ($payload.duration_ms // null),
      data: ($payload.data // null)
    }' >> "$EVENTS_FILE"
}
