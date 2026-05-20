#!/usr/bin/env bash
set -euo pipefail

# 程序说明：
#   报告生成模块，负责把 run ledger 中的元数据和证据汇总成 final-report.md。
# 参数说明：
#   本文件由 bin/dual source，不直接接收 CLI 参数；函数依赖 RUN_DIR、EVENTS_FILE、STEPS_DIR 等全局变量。
# 关键步骤：
#   1. 读取 run.json、events.jsonl、steps/*.json 和 verification.json。
#   2. 输出执行环境、时间线、Agent 摘要、验证、PR、通知和重放命令。
#   3. 生成可供人工审阅和 PR 引用的最终报告。
# 函数：append_file_or_placeholder
# 作用：把指定文件内容追加到报告；文件为空或不存在时输出占位文本。
# 参数：
#   $1 - file，要读取的文件路径。
#   $2 - placeholder，文件不可用时显示的文本。
# 关键步骤：优先 cat 文件内容，否则 printf 占位文本。
append_file_or_placeholder() {
  local file="$1"
  local placeholder="$2"

  if [[ -s "$file" ]]; then
    cat "$file"
  else
    printf '%s\n' "$placeholder"
  fi
}

# 函数：artifact_list
# 作用：列出当前 run ledger 下所有产物，生成报告中的 Artifact List。
# 参数：无。依赖 RUN_DIR。
# 关键步骤：find 所有文件，排序后去掉 RUN_DIR 前缀。
artifact_list() {
  find "$RUN_DIR" -type f | sort | sed "s#^$RUN_DIR/##"
}

# 函数：markdown_cell
# 作用：转义 Markdown 表格单元格中的竖线，避免表格被内容打断。
# 参数：
#   $1 - 原始单元格文本。
# 关键步骤：将 | 替换为 \|。
markdown_cell() {
  printf '%s' "$1" | sed 's/|/\\|/g'
}

# 函数：print_timeline_table
# 作用：根据 events.jsonl 输出运行时间线表格。
# 参数：无。依赖 EVENTS_FILE。
# 关键步骤：
#   1. 打印 Markdown 表头。
#   2. events.jsonl 为空时输出占位行。
#   3. 用 jq 提取事件字段并逐行渲染为表格。
print_timeline_table() {
  printf '| Time | Actor | Event | Step | Session | Exit | Duration |\n'
  printf '| --- | --- | --- | --- | --- | --- | --- |\n'

  if [[ ! -s "$EVENTS_FILE" ]]; then
    printf '|  |  | No events captured |  |  |  |  |\n'
    return 0
  fi

  jq -r '
    [
      (.ts // ""),
      (.actor // ""),
      (.event // ""),
      (.step // ""),
      (.session // ""),
      ((.exit_code // "") | tostring),
      ((.duration_ms // "") | tostring)
    ] | join("\u001f")
  ' "$EVENTS_FILE" | while IFS="$(printf '\037')" read -r ts actor event_name step session exit_code duration_ms; do
    printf '| %s | %s | %s | %s | %s | %s | %s |\n' \
      "$(markdown_cell "$ts")" \
      "$(markdown_cell "$actor")" \
      "$(markdown_cell "$event_name")" \
      "$(markdown_cell "$step")" \
      "$(markdown_cell "$session")" \
      "$(markdown_cell "$exit_code")" \
      "$(markdown_cell "$duration_ms")"
  done
}

# 函数：print_agent_step_summary_table
# 作用：根据 steps/*.json 输出 Agent 阶段摘要表格。
# 参数：无。依赖 STEPS_DIR。
# 关键步骤：
#   1. 打印 Markdown 表头。
#   2. 如果没有步骤元数据，输出占位行。
#   3. 汇总 step、agent、session、exit_code、duration_ms、output_path。
print_agent_step_summary_table() {
  printf '| Step | Agent | Session | Exit | Duration | Output |\n'
  printf '| --- | --- | --- | --- | --- | --- |\n'

  if ! find "$STEPS_DIR" -name '*.json' -type f | grep -q .; then
    printf '|  |  |  |  |  | No agent steps captured |\n'
    return 0
  fi

  find "$STEPS_DIR" -name '*.json' -type f | sort | while IFS= read -r step_file; do
    jq -r '
      [
        (.step // ""),
        (.agent // ""),
        (.session // ""),
        ((.exit_code // "") | tostring),
        ((.duration_ms // "") | tostring),
        (.output_path // "")
      ] | join("\u001f")
    ' "$step_file" | while IFS="$(printf '\037')" read -r step agent session exit_code duration_ms output_path; do
      printf '| %s | %s | %s | %s | %s | `%s` |\n' \
        "$(markdown_cell "$step")" \
        "$(markdown_cell "$agent")" \
        "$(markdown_cell "$session")" \
        "$(markdown_cell "$exit_code")" \
        "$(markdown_cell "$duration_ms")" \
        "$(markdown_cell "$output_path")"
    done
  done
}

# 函数：print_replay_command
# 作用：生成尽量等价的 dual 重放命令，方便后续复现实验。
# 参数：无。依赖主流程中的选项全局变量。
# 关键步骤：
#   1. 从 ROOT_DIR/bin/dual 和 ORIGINAL_WORKDIR 开始拼命令。
#   2. 按执行模式、PR、通知、质量门禁、测试命令逐项补参数。
#   3. 使用 printf %q 转义路径和用户输入，降低复制执行时的歧义。
print_replay_command() {
  printf '%q' "$ROOT_DIR/bin/dual"
  printf ' --workdir '
  printf '%q' "${ORIGINAL_WORKDIR:-$WORKDIR}"

  if [[ "${IN_PLACE:-false}" == "true" || "${EXECUTION_MODE:-}" == "in-place" ]]; then
    printf ' --in-place'
  elif [[ "${WORKTREE_ENABLED:-false}" == "true" ]]; then
    if [[ "${ATTACH_EXISTING:-false}" == "true" ]]; then
      printf ' --attach-existing'
    fi
    if [[ "${WORKTREE_DIR_SET:-false}" == "true" || "${ATTACH_EXISTING:-false}" == "true" ]]; then
      printf ' --worktree-dir '
      printf '%q' "$WORKTREE_DIR"
    fi
  fi

  if [[ "${BRANCH_OVERRIDE_SET:-false}" == "true" && -n "${BRANCH_OVERRIDE:-}" ]]; then
    printf ' --branch '
    printf '%q' "$BRANCH_OVERRIDE"
  fi

  if [[ "${CREATE_PR:-false}" == "true" ]]; then
    printf ' --create-pr'
    printf ' --pr-base '
    printf '%q' "$PR_BASE"
    if [[ -n "${PR_TITLE:-}" ]]; then
      printf ' --pr-title '
      printf '%q' "$PR_TITLE"
    fi
    if [[ "${PR_DRAFT:-false}" == "true" ]]; then
      printf ' --pr-draft'
    fi
  fi

  if [[ "${NOTIFY_ENABLED:-false}" == "true" && -n "${NOTIFY_COMMAND:-}" ]]; then
    printf ' --notify-command '
    printf '%q' "$NOTIFY_COMMAND"
    printf ' --notify-on '
    printf '%q' "$NOTIFY_ON"
  fi

  if [[ "${ALLOW_TEST_FAILURE:-false}" == "true" ]]; then
    printf ' --allow-test-failure'
  fi

  if [[ "${SKIP_FINAL_ACCEPTANCE:-false}" == "true" ]]; then
    printf ' --skip-final-acceptance'
  fi

  if [[ "${DRY_RUN:-false}" == "true" ]]; then
    printf ' --dry-run'
  fi

  if [[ -n "${TEST_COMMAND:-}" ]]; then
    printf ' --test '
    printf '%q' "$TEST_COMMAND"
  fi

  printf ' '
  printf '%q' "$USER_REQUEST"
  printf '\n'
}

# 函数：generate_final_report
# 作用：生成 final-report.md，把 run 元数据、执行环境、证据、验收和通知状态汇总到一个文件。
# 参数：无。依赖 RUN_DIR、OUTPUTS_DIR、SNAPSHOTS_DIR、VERIFICATION_DIR、EVENTS_FILE 等全局变量。
# 关键步骤：
#   1. 读取 verification.json 和 run.json，整理报告所需状态。
#   2. 输出 Execution Environment、Review Commands、Cleanup Commands。
#   3. 输出 timeline、Agent summary、diff、verification、quality gates、PR、notification。
#   4. 追加 Codex final acceptance 摘要，并记录 final_report_generated 事件。
generate_final_report() {
  local report_file="$RUN_DIR/final-report.md"
  local verification_status="not run"
  local verification_mode="unknown"
  local developer_self_test_report="null"
  local developer_self_test_available="false"
  local runner_test_command="null"
  local runner_test_exit_code="null"
  local runner_verification_skipped="null"
  local runner_test_output="null"
  local run_status="unknown"
  local finished_at="unknown"
  local design_decision="null"
  local final_decision="null"
  local allow_test_failure="false"
  local skip_final_acceptance="false"
  local dry_run="false"
  local original_workdir="${ORIGINAL_WORKDIR:-$WORKDIR}"
  local effective_workdir="$WORKDIR"
  local execution_mode="${EXECUTION_MODE:-unknown}"
  local worktree_enabled="${WORKTREE_ENABLED:-false}"
  local worktree_dir="${WORKTREE_DIR:-null}"
  local branch="${BRANCH:-unavailable}"
  local base_branch="${BASE_BRANCH:-unavailable}"
  local base_commit="${BASE_COMMIT:-unavailable}"
  local cleanup_command="$(cleanup_command_text)"
  local pr_enabled="false"
  local pr_created="false"
  local pr_status="not_requested"
  local pr_url="null"
  local pr_error_tail="null"
  local pr_base="${PR_BASE:-main}"
  local pr_branch="${BRANCH:-unavailable}"
  local notification_enabled="false"
  local notification_status="not_requested"
  local notification_exit_code="null"

  if [[ -f "$VERIFICATION_DIR/verification.json" ]]; then
    verification_mode="$(jq -r '.verification_mode // "legacy_runner_command"' "$VERIFICATION_DIR/verification.json")"
    developer_self_test_report="$(jq -r '.developer_self_test_report // "null"' "$VERIFICATION_DIR/verification.json")"
    developer_self_test_available="$(jq -r '.developer_self_test_available // false' "$VERIFICATION_DIR/verification.json")"
    runner_test_command="$(jq -r '.runner_test_command // .test_command // "null"' "$VERIFICATION_DIR/verification.json")"
    runner_test_exit_code="$(jq -r '.runner_test_exit_code // .test_exit_code // "null"' "$VERIFICATION_DIR/verification.json")"
    runner_verification_skipped="$(jq -r 'if has("runner_verification_skipped") then .runner_verification_skipped elif has("skipped") then .skipped else "null" end' "$VERIFICATION_DIR/verification.json")"
    runner_test_output="$(jq -r '.runner_test_output_file // .output_file // "null"' "$VERIFICATION_DIR/verification.json")"
    if [[ "$runner_verification_skipped" == "true" ]]; then
      verification_status="developer self-test only"
    elif [[ "$runner_test_exit_code" == "0" ]]; then
      verification_status="passed"
    else
      verification_status="failed"
    fi
  fi

  if [[ -f "$RUN_DIR/run.json" ]]; then
    run_status="$(jq -r '.status // "unknown"' "$RUN_DIR/run.json")"
    finished_at="$(jq -r '.finished_at // "unknown"' "$RUN_DIR/run.json")"
    design_decision="$(jq -r '.quality_gates.design_decision // "null"' "$RUN_DIR/run.json")"
    final_decision="$(jq -r '.quality_gates.final_decision // "null"' "$RUN_DIR/run.json")"
    allow_test_failure="$(jq -r '.quality_gates.allow_test_failure // false' "$RUN_DIR/run.json")"
    skip_final_acceptance="$(jq -r '.quality_gates.skip_final_acceptance // false' "$RUN_DIR/run.json")"
    dry_run="$(jq -r '.quality_gates.dry_run // false' "$RUN_DIR/run.json")"
    original_workdir="$(jq -r '.original_workdir // .workdir // "unknown"' "$RUN_DIR/run.json")"
    effective_workdir="$(jq -r '.effective_workdir // .workdir // "unknown"' "$RUN_DIR/run.json")"
    execution_mode="$(jq -r '.execution_mode // "unknown"' "$RUN_DIR/run.json")"
    worktree_enabled="$(jq -r '.worktree_enabled // false' "$RUN_DIR/run.json")"
    worktree_dir="$(jq -r '.worktree_dir // "null"' "$RUN_DIR/run.json")"
    branch="$(jq -r '.branch // "unavailable"' "$RUN_DIR/run.json")"
    base_branch="$(jq -r '.base_branch // "unavailable"' "$RUN_DIR/run.json")"
    base_commit="$(jq -r '.base_commit // "unavailable"' "$RUN_DIR/run.json")"
    cleanup_command="$(jq -r '.cleanup_command // "N/A"' "$RUN_DIR/run.json")"
    pr_enabled="$(jq -r '.pr_enabled // false' "$RUN_DIR/run.json")"
    pr_created="$(jq -r '.pr_created // false' "$RUN_DIR/run.json")"
    pr_status="$(jq -r '.pr_status // "not_requested"' "$RUN_DIR/run.json")"
    pr_url="$(jq -r '.pr_url // "null"' "$RUN_DIR/run.json")"
    pr_error_tail="$(jq -r '.pr_error_tail // "null"' "$RUN_DIR/run.json")"
    pr_base="$(jq -r '.pr_base // "main"' "$RUN_DIR/run.json")"
    pr_branch="$(jq -r '.pr_branch // .branch // "unavailable"' "$RUN_DIR/run.json")"
    notification_enabled="$(jq -r '.notification.enabled // false' "$RUN_DIR/run.json")"
    notification_status="$(jq -r '.notification.status // "not_requested"' "$RUN_DIR/run.json")"
    notification_exit_code="$(jq -r '.notification.exit_code // "null"' "$RUN_DIR/run.json")"
  fi

  {
    printf '# Dual Agent Run Report\n\n'
    printf '## Run Metadata\n\n'
    printf -- '- Run ID: `%s`\n' "$RUN_ID"
    printf -- '- Tool version: `%s`\n' "$TOOL_VERSION"
    printf -- '- acpx version: `%s`\n' "$ACPX_VERSION"
    printf -- '- Original workdir: `%s`\n' "$original_workdir"
    printf -- '- Effective workdir: `%s`\n' "$effective_workdir"
    printf -- '- Worktree enabled: `%s`\n' "$worktree_enabled"
    printf -- '- Worktree dir: `%s`\n' "$worktree_dir"
    printf -- '- Run dir: `%s`\n' "$RUN_DIR"
    printf -- '- Branch: `%s`\n' "$branch"
    printf -- '- Started at: `%s`\n' "$STARTED_AT"
    printf -- '- Finished at: `%s`\n' "$finished_at"
    printf -- '- Status: `%s`\n' "$run_status"
    printf -- '- Base commit: `%s`\n\n' "$base_commit"

    printf '## Execution Environment\n\n'
    printf -- '- Mode: `%s`\n' "$execution_mode"
    printf -- '- Original Workdir: `%s`\n' "$original_workdir"
    printf -- '- Effective Workdir: `%s`\n' "$effective_workdir"
    printf -- '- Branch: `%s`\n' "$branch"
    printf -- '- Base Branch: `%s`\n' "$base_branch"
    printf -- '- Base Commit: `%s`\n' "$base_commit"
    printf -- '- Worktree Dir: `%s`\n' "$worktree_dir"
    printf -- '- Cleanup Command: `%s`\n\n' "$cleanup_command"

    printf '## Review Commands\n\n'
    printf '```bash\n'
    printf 'cd %q\n' "$effective_workdir"
    printf 'git status\n'
    if [[ "$base_branch" != "unavailable" ]]; then
      printf 'git diff %q...HEAD\n' "$base_branch"
    else
      printf '# git diff unavailable outside a git repository\n'
    fi
    printf '```\n\n'

    printf '## Cleanup Commands\n\n'
    if [[ "$worktree_enabled" == "true" && "$worktree_dir" != "null" ]]; then
      printf '```bash\n'
      printf 'git -C %q worktree remove %q\n' "$original_workdir" "$worktree_dir"
      printf 'git -C %q branch -D %q\n' "$original_workdir" "$branch"
      printf '```\n\n'
    else
      printf '```text\n'
      printf 'N/A for in-place execution.\n'
      printf '```\n\n'
    fi

    printf '## Timeline\n\n'
    print_timeline_table
    printf '\n\n'

    printf '## Agent Step Summary\n\n'
    print_agent_step_summary_table
    printf '\n\n'

    printf '## User Request\n\n'
    printf '```text\n'
    append_file_or_placeholder "$RUN_DIR/00-user-request.md" ""
    printf '```\n\n'

    printf '## Artifact List\n\n'
    artifact_list | while IFS= read -r artifact; do
      printf -- '- `%s`\n' "$artifact"
    done
    printf '\n'

    printf '## Diff Stat\n\n'
    printf '```text\n'
    append_file_or_placeholder "$SNAPSHOTS_DIR/diff.stat" "No diff stat captured."
    printf '```\n\n'

    printf '## Verification Result\n\n'
    printf -- '- Status: `%s`\n' "$verification_status"
    printf -- '- Mode: `%s`\n' "$verification_mode"
    printf -- '- Developer self-test available: `%s`\n' "$developer_self_test_available"
    printf -- '- Developer self-test report: `%s`\n' "$developer_self_test_report"
    printf -- '- Runner verification skipped: `%s`\n' "$runner_verification_skipped"
    printf -- '- Runner test exit code: `%s`\n' "$runner_test_exit_code"
    if [[ "$runner_test_command" != "null" && -n "$runner_test_command" ]]; then
      printf -- '- Runner test command: `%s`\n' "$runner_test_command"
    fi
    printf -- '- Runner test output: `%s`\n' "$runner_test_output"
    printf '\n\n'

    printf '## Quality Gates\n\n'
    printf -- '- Design decision: `%s`\n' "$design_decision"
    printf -- '- Final decision: `%s`\n' "$final_decision"
    printf -- '- Allow test failure: `%s`\n' "$allow_test_failure"
    printf -- '- Skip final acceptance: `%s`\n' "$skip_final_acceptance"
    printf -- '- Dry run: `%s`\n' "$dry_run"
    printf '\n'

    printf '## PR\n\n'
    printf -- '- Enabled: `%s`\n' "$pr_enabled"
    printf -- '- Created: `%s`\n' "$pr_created"
    printf -- '- Status: `%s`\n' "$pr_status"
    printf -- '- URL: `%s`\n' "$pr_url"
    printf -- '- Base: `%s`\n' "$pr_base"
    printf -- '- Branch: `%s`\n' "$pr_branch"
    if [[ "$pr_error_tail" != "null" ]]; then
      printf -- '- Error tail: `%s`\n' "$pr_error_tail"
    fi
    printf '\n'

    printf '## Notification\n\n'
    printf -- '- Enabled: `%s`\n' "$notification_enabled"
    printf -- '- Status: `%s`\n' "$notification_status"
    printf -- '- Exit code: `%s`\n' "$notification_exit_code"
    if [[ -f "$RUN_DIR/notification/notify.stderr.log" ]]; then
      printf -- '- Stderr: `%s`\n' "$RUN_DIR/notification/notify.stderr.log"
    fi
    printf '\n'

    printf '## Verification Summary\n\n'
    if [[ -f "$VERIFICATION_DIR/developer-self-test.md" ]]; then
      printf '### Developer Self-Test\n\n'
      sed -n '1,120p' "$VERIFICATION_DIR/developer-self-test.md"
      printf '\n\n'
    fi
    printf '### Verification Metadata\n\n'
    if [[ -f "$VERIFICATION_DIR/verification.json" ]]; then
      printf '```json\n'
      jq '.' "$VERIFICATION_DIR/verification.json"
      printf '```\n'
    else
      printf 'No verification metadata was captured.\n'
    fi
    printf '\n'

    printf '## Replay Command\n\n'
    printf '```bash\n'
    print_replay_command
    printf '```\n\n'

    printf '## Codex Final Acceptance Summary\n\n'
    if [[ -f "$OUTPUTS_DIR/04-codex-final-acceptance.md" ]]; then
      sed -n '1,80p' "$OUTPUTS_DIR/04-codex-final-acceptance.md"
    else
      printf 'No Codex final acceptance output was captured.\n'
    fi
    printf '\n'
  } > "$report_file"

  event "final_report_generated" "$(jq -cn \
    --arg actor "dual" \
    --arg output_path "$report_file" \
    '{actor: $actor, output_path: $output_path}')"
}
