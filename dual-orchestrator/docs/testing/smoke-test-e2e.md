# Smoke Test E2E 使用说明

## 脚本用途

`scripts/smoke-test.sh` 是 dual-orchestrator 的端到端验证脚本，用于验证 agent lifecycle 的 workflow routing、run ledger、artifact 产出、domain event 发射、report 生成等端到端行为。

脚本现在分两类 case：

- `lifecycle`：controlled smoke。脚本临时注入一个 fake `acpx`，只模拟 happy path 的 Claude/Codex 输出，用来确定性验证完整生命周期主链路。
- `thin-basic` / `p5-contract-happy-path` / `p6-reject-classify-path`：真实 acpx E2E。依赖真实 Claude/Codex cowork，验证真实 agent 调用能否跑通。

真实 acpx case 不是确定性 fixture test framework。agent 输出和 decision 结果是非确定性的；脚本验证的是"流程能跑通 + 关键产物存在"，而非"输出与预期完全一致"。

真实复杂仓库验证则用 `scripts/run-real-case.sh`。它不替代 smoke，而是验证长链路：真实 worktree、真实 acpx、真实项目上下文、真实 Agent 工具调用、真实 rejection repair loop。

真实 benchmark case 定义和报告入口：

```text
dual-orchestrator/docs/testing/real-case-benchmarks/
```

## 前置条件

- Java 17+, Maven
- `lifecycle` case 不需要真实 acpx；脚本会临时注入 controlled fake acpx
- 真实 acpx case 需要 acpx (Claude/Codex CLI) 已安装并认证
- gh CLI（仅 `--with-github` 时需要）

## 支持的 Case

### lifecycle

| 项 | 值 |
|----|----|
| Workflow variant | thin（默认，无 `--workflow-variant`） |
| Agent transport | controlled fake acpx（脚本临时注入，只作用于本次 smoke） |
| 任务 | 给 main.py 添加 goodbye 函数 |
| 关键断言 | workflow 完成、28 个节点完成、5 个 gate 被处理、final report / delivery / next-stage prompt / handoff.json 存在、Git evidence 捕获 goodbye diff |
| 预期结果 | PASS（COMPLETED） |

这个 case 的目的不是评估 Claude/Codex 输出质量，而是快速验证 orchestration 主链路是否完整：start --run、gate loop、implementation evidence、acceptance report、delivery prepare、stage handoff、next-stage prompt、workflow complete。

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

## Real Repo Long Case

当改动触及 workflow 主链路、failure classification、context package、artifact contract 或 acpx adapter 时，建议至少跑一次真实复杂仓库 case。fixture smoke 可以证明 routing 便宜、可重复；真实 repo case 才能暴露上下文装配、Agent 工具调用、项目复杂度和 semantic repair 的问题。

公开可复用真实 case：

```bash
./scripts/run-public-benchmark.sh
```

这条路径会准备 `examples/issue-flow-app` 的一次性 Git repo，并用真实
`acpx` 跑 full lifecycle。最近一次公开路径通过记录：

| 字段 | 值 |
|------|----|
| Time | 2026-06-08 |
| Case | `public-issue-flow-review-state` |
| Variant | `full` |
| Final status | `COMPLETED` |
| Grade | `PASS-WARN` |
| Final acceptance | `ACCEPT_WITH_NOTES` |
| Report | `dual-orchestrator/docs/testing/real-case-reports/<timestamp>/report.md` |
| Repair loop | 本公开样例未强制触发 repair loop；内部长 case 已验证过 `REJECT -> classify -> repair` |
| Original repo | 保持干净 |
| Worktree diff | 只发生在 disposable benchmark repo |

不要把这个 case 当成每次小改都必须跑的快速测试。它消耗时间和 Agent quota，适合主功能变更、长链路变更、或者准备宣布“主线已验证”之前运行。

### all

顺序运行 `lifecycle`、`thin-basic`、`p5-contract-happy-path`、`p6-reject-classify-path`，每个 case 使用独立的 fixture 目录。共享一次 build。最终输出 per-case 结果汇总。

## 结果语义

| 结果 | 含义 |
|------|------|
| PASS | 所有关键断言通过，workflow 正常完成 |
| PASS-WARN | 核心流程正常，但某些可选断言未覆盖（如 p6 classify 路径未触发） |
| FAIL | workflow 错误、必需 artifact 缺失、或关键断言失败 |

`--case all` 模式下，只有 FAIL 会导致脚本 exit 1。PASS-WARN 不影响退出码。

## 常用命令

```bash
# controlled full lifecycle smoke：不调用真实 acpx，适合改 runtime / ledger / CLI 后快速验证主链路
./scripts/smoke-test.sh lifecycle --cleanup

# 默认 case（thin-basic，真实 acpx）
./scripts/smoke-test.sh --cleanup

# 指定真实 acpx case
./scripts/smoke-test.sh --case thin-basic --cleanup
./scripts/smoke-test.sh --case p5-contract-happy-path --cleanup
./scripts/smoke-test.sh --case p6-reject-classify-path --cleanup

# 运行全部 case：先 controlled lifecycle，再真实 thin/p5/p6
./scripts/smoke-test.sh --case all --cleanup

# 跳过 build（开发中反复跑时）
./scripts/smoke-test.sh --case thin-basic --skip-build --cleanup

# 终端交互模式（脚本在每个 gate 询问是否 approve）
./scripts/smoke-test.sh --interactive --cleanup

# UI 人审模式（脚本在每个 gate 等你去 Operator Panel 审阅和操作）
./scripts/smoke-test.sh --case p5-contract-happy-path --manual-gates

# 列出可用 case
./scripts/smoke-test.sh --list

# 帮助
./scripts/smoke-test.sh --help
```

## 用 Operator Panel 查看 Smoke Ledger

如果要用本地 UI 查看某次 smoke 的 status / timeline / gate 状态，不要使用 `--cleanup`，否则脚本结束后会删除 fixture 和 worktree：

```bash
cd dual-orchestrator

# controlled smoke，适合快速验证 UI 和主链路
./scripts/smoke-test.sh lifecycle --skip-build

# 或者真实 acpx E2E
./scripts/smoke-test.sh --case thin-basic --skip-build
```

脚本会输出：

```text
Workspace: /tmp/...-agent-runs/<workstream_id>
Workstream: <workstream_id>
```

启动 server：

```bash
cd dual-orchestrator
mvn -pl dual-server -am package
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

打开：

```text
http://localhost:8080/
```

在页面顶部 `Effective workdir` 填入脚本输出里的 `Workspace`，点击 `Refresh`。页面可以列出 workstreams，查看 status / timeline，并执行 `Resume`、`Approve + Run`、`Complete`、`Retry`、`Revise + Run`。

### UI 人审 Smoke

`--manual-gates` 会把 gate 决策交给 Operator Panel：

```bash
cd dual-orchestrator
mvn -pl dual-server -am package
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

另开一个终端：

```bash
cd dual-orchestrator
./scripts/smoke-test.sh --case p5-contract-happy-path --manual-gates
```

每次 workflow 到达 human gate，脚本会打印：

```text
Open operator panel: http://localhost:8080
Effective workdir: ...
Workstream: ...
Gate: ...
Review packet: ...
```

操作步骤：

1. 打开 `http://localhost:8080/`。
2. 把 `Effective workdir` 填到页面顶部。
3. 点击 `Refresh`，选中 workstream。
4. 阅读 `Human Review Packet`。
5. 根据 review 结果点击 `Approve + Run`、`Revise + Run` 或最后一个 gate 的 `Complete`。
6. 回到 smoke 脚本按 Enter，脚本会重新读取状态并继续。

这个模式不会自动 approve gate，适合验证 UI 和人工卡点语义。`--cleanup` 可以和 `--manual-gates` 一起使用，但调试 UI 时建议先不要加，便于保留 ledger。

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
