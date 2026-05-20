#!/usr/bin/env bash
set -euo pipefail

# 程序说明：
#   验证模块，负责整理 Claude 自测报告，并可选执行 runner 级测试命令。
# 参数说明：
#   本文件由 bin/dual source，不直接接收 CLI 参数；是否执行命令由 TEST_COMMAND 决定。
# 关键步骤：
#   1. 保存 Claude implementation 输出作为 developer self-test。
#   2. 没有 --test 时只记录研发自测模式。
#   3. 有 --test 时执行命令并生成 verification.json。
# 函数：run_verification
# 作用：汇总 Claude developer 自测报告，并可选执行 runner 层测试命令，形成 verification.json。
# 参数：无。依赖主流程提前设置 WORKDIR、OUTPUTS_DIR、VERIFICATION_DIR、TEST_COMMAND 等全局变量。
# 关键步骤：
#   1. 从 Claude 实现阶段输出中提取 developer-self-test.md，作为研发自测证据。
#   2. 如果没有 --test 命令，只记录 developer_self_test 模式并跳过脚本测试。
#   3. 如果存在 --test 命令，在 WORKDIR 中执行并保存 runner-test-output.log。
#   4. 写入 verification.json 和对应事件，函数返回 runner 测试命令的退出码。
run_verification() {
  mkdir -p "$VERIFICATION_DIR"

  local developer_self_test="$VERIFICATION_DIR/developer-self-test.md"
  local developer_self_test_available=false

  if [[ -s "$OUTPUTS_DIR/03-claude-implement.md" ]]; then
    cp "$OUTPUTS_DIR/03-claude-implement.md" "$developer_self_test"
    developer_self_test_available=true
    event "developer_self_test_captured" "$(jq -cn \
      --arg actor "verify" \
      --arg output_path "$developer_self_test" \
      '{actor: $actor, output_path: $output_path}')"
  else
    printf 'No Claude implementation output was captured.\n' > "$developer_self_test"
    event "developer_self_test_unavailable" "$(jq -cn \
      --arg actor "verify" \
      --arg output_path "$developer_self_test" \
      '{actor: $actor, output_path: $output_path, data: {reason: "missing Claude implementation output"}}')"
  fi

  if [[ -z "${TEST_COMMAND:-}" ]]; then
    jq -cn \
      --arg developer_self_test "$developer_self_test" \
      --argjson developer_self_test_available "$developer_self_test_available" \
      '{
        verification_mode: "developer_self_test",
        developer_self_test_report: $developer_self_test,
        developer_self_test_available: $developer_self_test_available,
        runner_test_command: null,
        runner_test_exit_code: null,
        runner_verification_skipped: true,
        runner_test_output_file: null,
        test_command: null,
        test_exit_code: null,
        skipped: true,
        output_file: null
      }' > "$VERIFICATION_DIR/verification.json"
    event "runner_verification_skipped" "$(jq -cn \
      --arg actor "verify" \
      --arg output_path "$VERIFICATION_DIR/verification.json" \
      '{actor: $actor, output_path: $output_path, data: {reason: "no runner test command provided"}}')"
    return 0
  fi

  printf '%s\n' "$TEST_COMMAND" > "$VERIFICATION_DIR/runner-test-command.txt"
  printf '%s\n' "$TEST_COMMAND" > "$VERIFICATION_DIR/test-command.txt"

  event "runner_verification_started" "$(jq -cn \
    --arg actor "verify" \
    --arg test_command "$TEST_COMMAND" \
    --arg output_path "$VERIFICATION_DIR/runner-test-output.log" \
    '{actor: $actor, output_path: $output_path, data: {test_command: $test_command}}')"

  local test_exit_code=0
  set +e
  (cd "$WORKDIR" && bash -lc "$TEST_COMMAND") > "$VERIFICATION_DIR/runner-test-output.log" 2>&1
  test_exit_code=$?
  set -e
  cp "$VERIFICATION_DIR/runner-test-output.log" "$VERIFICATION_DIR/test-output.log"

  jq -cn \
    --arg developer_self_test "$developer_self_test" \
    --argjson developer_self_test_available "$developer_self_test_available" \
    --arg test_command "$TEST_COMMAND" \
    --arg output_file "$VERIFICATION_DIR/runner-test-output.log" \
    --argjson test_exit_code "$test_exit_code" \
    --argjson skipped false \
    '{
      verification_mode: "developer_self_test_plus_runner_command",
      developer_self_test_report: $developer_self_test,
      developer_self_test_available: $developer_self_test_available,
      runner_test_command: $test_command,
      runner_test_exit_code: $test_exit_code,
      runner_verification_skipped: $skipped,
      runner_test_output_file: $output_file,
      test_command: $test_command,
      test_exit_code: $test_exit_code,
      skipped: $skipped,
      output_file: $output_file
    }' \
    > "$VERIFICATION_DIR/verification.json"

  event "runner_verification_finished" "$(jq -cn \
    --arg actor "verify" \
    --arg test_command "$TEST_COMMAND" \
    --arg output_file "$VERIFICATION_DIR/runner-test-output.log" \
    --argjson test_exit_code "$test_exit_code" \
    '{actor: $actor, output_path: $output_file, exit_code: $test_exit_code, data: {test_command: $test_command}}')"

  return "$test_exit_code"
}
