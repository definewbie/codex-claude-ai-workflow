# Smoke Test E2E 使用说明

## 脚本用途

`scripts/smoke-test.sh` 是 dual-orchestrator 的端到端验证脚本，用于在真实 Codex + Claude cowork 环境中运行完整的 agent lifecycle，验证 workflow variant、artifact 产出、domain event 发射、report 生成等端到端行为。

**这不是确定性 fixture test framework。** 脚本依赖真实的 acpx agent 执行，agent 输出和 decision 结果是非确定性的。脚本验证的是"流程能跑通 + 关键产物存在"，而非"输出与预期完全一致"。

## 前置条件

- Java 17+, Maven
- acpx (Claude/Codex CLI) 已安装并认证
- gh CLI（仅 `--with-github` 时需要）

## 支持的 Case

### thin-basic

| 项 | 值 |
|----|----|
| Workflow variant | thin（默认，无 `--workflow-variant`） |
| 任务 | 给 main.py 添加 goodbye 函数 |
| 关键断言 | `reports/latest-final-report.md` 存在、delivery artifact 存在、next-stage prompt 存在 |
| 预期结果 | PASS（COMPLETED） |

### p5-contract-happy-path

| 项 | 值 |
|----|----|
| Workflow variant | p5 |
| 任务 | 给 main.py 添加 calculate 函数 |
| 关键断言 | `artifacts/task-contract.json` 存在、`artifacts/task-contract.md` 存在、`contract_compilation_completed` 事件存在、report 包含 TaskContract section |
| 预期结果 | PASS（COMPLETED） |

### p6-reject-classify-path

| 项 | 值 |
|----|----|
| Workflow variant | p6 |
| 任务 | 给 main.py 添加 format_output 函数（多格式 + README API 文档） |
| 关键断言（必须） | `artifacts/task-contract.json` 存在 |
| 关键断言（best-effort） | `artifacts/failure-classification.json` 存在、`failure_classified` 事件存在、report 包含 Failure Classification section |
| 预期结果 | PASS 或 PASS-WARN（见下方说明） |

#### p6 best-effort 特性

p6-reject-classify-path 的 classify_failure 路径依赖 Codex agent 在 acceptance review 中产出 REJECT 或 ACCEPT_WITH_NOTES。由于 agent 输出不确定，脚本采用 best-effort 策略：

- **如果 rejection 路径触发**：验证 `failure-classification.json`、`failure_classified` 事件、report section，结果为 PASS
- **如果 acceptance 产出 ACCEPT**：跳过分类断言，输出 warn 日志，结果为 PASS-WARN（不视为失败）
- **如果 workflow 出错或必需 artifact 缺失**：结果为 FAIL

### all

顺序运行上述 3 个 case，每个 case 使用独立的 fixture 目录。共享一次 build。最终输出 per-case 结果汇总。

## 结果语义

| 结果 | 含义 |
|------|------|
| PASS | 所有关键断言通过，workflow 正常完成 |
| PASS-WARN | 核心流程正常，但某些可选断言未覆盖（如 p6 classify 路径未触发） |
| FAIL | workflow 错误、必需 artifact 缺失、或关键断言失败 |

`--case all` 模式下，只有 FAIL 会导致脚本 exit 1。PASS-WARN 不影响退出码。

## 常用命令

```bash
# 默认 case（thin-basic）
./scripts/smoke-test.sh --cleanup

# 指定 case
./scripts/smoke-test.sh --case p5-contract-happy-path --cleanup
./scripts/smoke-test.sh --case p6-reject-classify-path --cleanup

# 运行全部 case
./scripts/smoke-test.sh --case all --cleanup

# 跳过 build（开发中反复跑时）
./scripts/smoke-test.sh --case thin-basic --skip-build --cleanup

# 交互模式（每个 gate 暂停审阅）
./scripts/smoke-test.sh --interactive --cleanup

# 列出可用 case
./scripts/smoke-test.sh --list

# 帮助
./scripts/smoke-test.sh --help
```

## 每次 E2E 运行建议记录的数据

以下数据用于后续改进脚本、开发 P7 calibration、以及评估 workflow variant 的实际表现。不需要自动化采集。

如果要保留 ledger 供人工摘录，本次运行不要加 `--cleanup`；记录完成后再手动删除 fixture/worktree。`--cleanup` 更适合只关心脚本 exit code 的快速验证。

### 1. Run-Level Summary

| 字段 | 来源 | 示例 |
|------|------|------|
| case | `--case` 参数 | `p6-reject-classify-path` |
| workflow variant | 脚本输出 `Variant:` 行 | `p6` |
| result | 脚本最终 `RESULT:` 行 | `PASS` / `PASS-WARN` / `FAIL` |
| final workflow status | 脚本输出 `Final status:` 行 | `COMPLETED` / `WAITING_FOR_RECOVERY` |
| duration | 手动记录开始/结束时间 | `12m 34s` |
| workstream id | 脚本输出 `Workstream:` 行 | `ws-abc123` |
| gate rounds | 脚本输出 `Gate rounds:` 行 | `6` |

### 2. Artifact Summary

| 字段 | 来源 | 说明 |
|------|------|------|
| task-contract.json | `$WS_DIR/artifacts/task-contract.json` | 是否存在；若存在记录 `contract_id`、`status`、`acceptanceCriteria` 条数 |
| failure-classification.json | `$WS_DIR/artifacts/failure-classification.json` | 是否存在；若存在记录 `category`、`matched_rule`、`review_decision` |
| final report | `$WS_DIR/reports/latest-final-report.md` | 是否存在 |
| delivery prepare artifacts | `$WS_DIR/delivery/commit-message.txt`, `$WS_DIR/delivery/pr-body.md` | 是否存在 |

### 3. Domain Event Summary

| 字段 | 来源 | 说明 |
|------|------|------|
| contract_compilation_completed | `events.jsonl` 中 grep | 是否出现 |
| failure_classified | `events.jsonl` 中 grep | 是否出现 |
| total event count | 脚本输出 `Events:` 行 | 数字 |

### 4. P6 Classify-Path Summary

| 字段 | 说明 |
|------|------|
| classify path 是否触发 | `failure-classification.json` 是否存在 |
| 未触发原因 | 如果未触发，是否因为 acceptance decision = ACCEPT |
| 是否进入 recovery path | final status 是否为 `WAITING_FOR_RECOVERY` 或类似的非 COMPLETED 状态 |

### 记录模板

```
case:               thin-basic
variant:            thin
result:             PASS
final_status:       COMPLETED
duration:           __
workstream_id:      __
gate_rounds:        __

task-contract.json: absent (thin has no contract)
failure-classification.json: absent
final_report:       present
delivery_prepare:   present

compilation_event:  absent
failure_event:      absent
event_count:        __

classify_triggered: n/a (thin variant)
```

```
case:               p6-reject-classify-path
variant:            p6
result:             PASS / PASS-WARN
final_status:       COMPLETED
duration:           __
workstream_id:      __
gate_rounds:        __

task-contract.json: present (contract_id: __, status: __, criteria: __)
failure-classification.json: present / absent
  category:        __
  matched_rule:    __
  review_decision: __
final_report:       present
delivery:           present

compilation_event:  present
failure_event:      present / absent
event_count:        __

classify_triggered: yes / no
  if no: acceptance = ACCEPT
recovery_path:      no / yes
```

## 注意事项

- `--cleanup` 会在脚本结束时删除 fixture 目录和 worktree。不加则保留，便于事后检查
- 脚本运行时间取决于 agent 执行速度，通常每个 case 5-15 分钟
- p5/p6 variant 会经过更多 gate（gate.contract_review），gate loop 轮次比 thin 多
- 环境变量和 acpx 配置影响 agent 行为，结果可能因配置不同
