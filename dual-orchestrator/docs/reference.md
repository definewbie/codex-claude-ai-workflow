# dual-orchestrator

`dual-orchestrator` 是 `codex-claude-ai-workflow` 的 Java 版 Agent 生命周期编排器原型。

Bash 版 `dual-agent-runner` 证明了 `acpx`、worktree、run ledger、报告这些基础机制可行；Java 版不再围绕固定双 Agent 脚本扩展，而是验证一个更长期的工作流模型：

```text
需求澄清 -> 设计审视 -> 人工卡点 -> 计划 -> 实现 -> 自测 -> 验收 -> 下一阶段提示词
```

当前仍是 MVP，但核心目标已经从“能不能调用两个 Agent”推进到“能不能在真实仓库里跑完整生命周期”。公开分支保留了可复用 sample app benchmark；内部真实长 case 也验证过 Codex acceptance `REJECT` 后进入 failure classification、路由回 Claude 修复、再次验收并生成 delivery / handoff / next-stage artifacts 的主修复闭环。

这说明 MVP 主生命周期已经功能性闭环。它还不是完整产品；当前最重要的缺口不是再加 UI 或边角命令，而是把研发自测、上下文证据和 Codex acceptance 的可信度继续做实，让 `PASS-WARN` 更少依赖人工复读报告。

## Documentation

建议阅读顺序：

- `docs/quickstart.md`：从安装到第一个 smoke / real-case 的最短路径。
- `docs/README.md`：当前文档入口，区分 current docs 和历史 handoff。
- `docs/whitepaper-mapping.md`：项目如何对应 AI 时代研发效能白皮书的核心观点。
- `docs/architecture-and-execution-flow.md`：总体设计和执行流程。
- `docs/design/workflow-state-machine.md`：workflow / node / gate / recovery 状态机说明。
- `docs/testing/real-case-benchmarks/README.md`：真实 case benchmark 入口和报告索引。

## Workflow Engine Model

`dualctl` 运行的是 Agent lifecycle workflow，不是单次命令脚本。

Runtime 只认识三类节点：

- `Task`：执行有副作用的工作，例如调用 Claude、调用 Codex、生成 artifact。
- `Decision`：读取 artifact 或结果，选择下一条 edge。
- `Gate`：暂停等待人或外部条件。

当前内置 workflow 覆盖：

1. Intake / clarification
2. Design review
3. Planning
4. Implementation and Claude self-test report
5. Codex acceptance review
6. Final report
7. Stage handoff and next-stage prompt

第一版仍是薄 mainline，但几个关键节点已经开始落地：artifact validation 会采集 Git evidence，final report 会把验收、研发自测和 Git evidence 收口成报告，next-stage prompt 会根据 Claude handoff 生成下一阶段提示词，`acpx.agent_run` 能通过 `acpx` 调 Claude / Codex，decision handler 能解析 Agent 输出里的 `dual-control` block。

## 当前能力

已经实现：

- Maven 多模块工程。
- `dualctl` CLI。
- 默认创建 Git worktree。
- `dual-runtime` workflow runtime。
- `Task` / `Decision` / `Gate` / `Edge` domain model。
- `WorkflowFileLedger` 文件账本。
- `events.jsonl` 审计事件流。
- `workflow/current.json` 快照。
- node / gate 文件化状态。
- handler SPI 和 registry。
- baseline lifecycle workflow definition。
- `acpx.agent_run` TaskHandler。
- resources-based Agent prompt templates。
- human review gate。
- review packet artifact：human gate 会生成 `reviews/<gate_node_instance_id>.md` / `.json`。
- review packet operator guidance：human gate 会给出推荐动作、原因和建议检查项，帮助人判断 approve / revise / complete。
- `dualctl review show`：展示当前人工卡点需要看的 review packet。
- Timeline projection：ledger 自动生成 `timeline.json`，`dualctl timeline` 可展示 workflow / node 进度。
- Trace CLI：`dualctl trace` 一屏展示当前 status、active node、open gate、acceptance、repair loops、timeline、关键 artifacts 和下一步命令。
- Workstream summary：`dualctl summary` 会聚合 timeline、recovery、delivery、calibration 和 final report，方便真实 case 复盘。
- Provider metrics MVP：`metrics/workstream-metrics.json` 按 Claude / Codex 聚合 run count、完成/失败数量、可恢复失败、总耗时和平均耗时。
- review decision handler。
- context package handler，会在关键 Agent step 前装配可读上下文。
- artifact validation handler，会落盘 Git status / diff / changed files。
- next-stage prompt handler。
- `dualctl start/status/resume/review approve/review revise` 接入 workflow ledger。
- 节点级幂等保护，`resume` 会拒绝正在执行中的节点。
- Prompt 注入防护：`dual-control` block 解析锚定行首，只认 terminal block，不回退旧文件。
- Revision loop 上限 + recovery gate。
- 单元测试覆盖 handler、ledger、control block 解析、contract registry、prompt 一致性、context governance 和 workflow 集成。
- Agent artifact contract registry（`ContractRegistry`），per-node output schema 校验。
- Prompt 模板变量加载时校验，防止引用未知变量。
- Context governance 架构：6 profile 各自拥有 **token-level** budget-controlled 渲染管线，priority-based degradation（7 种策略含 ADAPTIVE 自适应压缩），token-aware budget enforcement。per-profile config overrides（`max_snippet_chars` 等）。
- `TokenEstimator` SPI：默认 ~4 chars/token 估算，可替换为 tiktoken 等真实 tokenizer。
- `handoff.json` sidecar：从 agent handoff markdown 解析生成结构化 JSON，下游 handler 可直接消费 `completedWork`（iteration_history section）和 `openRisks`（human_feedback section）。
- Configurable retry policies：per-failure-type 策略（RATE_LIMIT / SESSION_MISSING / AGENT_RECONNECT / CONTEXT_WINDOW），指数退避 + `Thread.sleep`，node-level config override。
- `WorkflowFileLedger` 重构为 facade + 4 delegates（`RoutingEngine`、`NodeStateWriter`、`RevisionTracker`、`LedgerEventLog`）+ `LedgerUtils` 工具类。公共 API 不变。
- Delivery 管线：`delivery.prepare_pr` → `dualctl delivery commit` → `dualctl delivery create-pr`（自动 commit + push + `gh pr create`）。
- CalibrationCandidate MVP：failure classification 可生成 `calibration/candidates/<id>.json` / `.md`，并可通过 `dualctl calibration plan` 生成可审阅 apply plan。
- Failure summary MVP：节点 `FAILED` / `BLOCKED` 或分类失败时会生成 `recovery/current-failure.md`。
- Watch mode MVP：`dualctl watch` 基于 `timeline.json` 轮询展示 workflow 进度。
- Local API Server MVP：`dual-server` 提供 HTTP API，用于读取 workflow overview / status / timeline，并执行最小 action：resume、retry、review approve/revise；scheduler 支持手动 tick 和默认关闭的后台周期调度。
- 条件 edge routing：`EdgeCondition` 接口 + `ConfigEqualsCondition` 实现，edge 支持条件判断（`if config.x == y`），`EdgeConditionModule` Jackson 序列化。
- 并行节点：fan-out edge routing 激活多个后续节点 + sequential multi-node resume（单线程语义，一次 `resume` 依次执行所有 active nodes）。
- Ledger schema migration 暂不作为当前主线；schema 仍在快速演进，当前只保留最小兼容能力。

仍未完成：

- deterministic reject fixture：内部真实长 case 已覆盖 Codex `REJECT` → failure classification → Claude repair → final acceptance 的主修复闭环。为了一个边界分支引入 fake acpx 的收益暂时不高；只有当 CI 需要稳定、可重复覆盖这条分支时再补 controlled fixture。
- 实时推送：还没有 SSE / file watch / WebSocket，当前 `dualctl watch` 是轮询式 CLI。
- Scheduler：已有默认关闭的后台周期调度；仍不会自动通过 human gate、recovery 或 failure。
- Web UI / dashboard。
- Notification 集成。
- Homebrew / Docker / native image 等正式分发包。

## Modules

```text
dual-core
  domain model
  workflow model

dual-ledger-file
  WorkflowFileLedger (facade)
  RoutingEngine · NodeStateWriter · RevisionTracker · LedgerEventLog · LedgerUtils
  workflow/current/nodes/gates/events
  local lock

dual-runtime
  WorkflowRuntime
  HandlerRegistry
  built-in handlers
  context governance pipeline
  handoff.json sidecar
  lifecycle workflow definition

dual-adapter-acpx
  acpx transport
  acpx.agent_run TaskHandler
  Agent prompt templates

dual-adapter-git
  git repo and worktree support

dual-cli
  dualctl start/status/resume/review

dual-server
  local HTTP API
  status / timeline read endpoints
  resume / retry / review approve / review revise action endpoints
```

## Dependencies

本地需要：

- JDK 21+
- Maven 3.9+
- Git
- acpx
- Claude Code / Codex 对应的 acpx agent runtime 可用

检查：

```bash
java -version
mvn -version
git --version
acpx --help
```

## Build

```bash
cd dual-orchestrator
mvn test
mvn -pl dual-cli -am package
```

CLI jar：

```text
dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar
```

API server jar：

```text
dual-orchestrator/dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

## Install

本地安装脚本会构建 CLI/server jar，把它们复制到用户目录，并生成 `dualctl` / `dual-server` wrapper。默认安装到 `$HOME/.local`，不需要 root，也不会修改 shell profile。

```bash
cd dual-orchestrator
./scripts/install.sh
```

指定安装目录：

```bash
./scripts/install.sh --prefix "$HOME/.local"
```

如果已经构建过，只安装现有 jar：

```bash
./scripts/install.sh --skip-build
```

只安装 CLI：

```bash
./scripts/install.sh --no-server
```

安装后确认：

```bash
$HOME/.local/bin/dualctl --help
$HOME/.local/bin/dualctl doctor --workdir /path/to/git/repo
```

如果 `$HOME/.local/bin` 不在 `PATH`，加入 shell profile：

```bash
export PATH="$HOME/.local/bin:$PATH"
```

安装脚本是当前开源雏形的本地安装方式。Homebrew、Docker、native image 这类正式分发方式还没有做。

开发时也可以使用临时 alias：

```bash
alias dualctl='java -jar /absolute/path/to/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar'
```

建议先确认 CLI 本身能启动：

```bash
dualctl --help
dualctl start --help
dualctl resume --help
dualctl review approve --help
dualctl review show --help
dualctl trace --help
dualctl timeline --help
```

## Recommended MVP Flow

当前推荐的 MVP 主线验证方式是：先用公开 sample benchmark 验证一条可复用真实 run，再在自己的仓库里用真实 `acpx`、真实 worktree 跑一条受控需求。这个流程用于验证编排闭环，不等于最终业务验收。

```bash
cd dual-orchestrator

./scripts/run-public-benchmark.sh
```

跑完后先看三类证据：

```bash
cat docs/testing/real-case-reports/<timestamp>/report.md
jq . docs/testing/real-case-reports/<timestamp>/summary.json
cat docs/testing/real-case-benchmarks/benchmark-index.md
```

如果要直接看某个 workstream 当前跑到哪里，先用一屏 trace：

```bash
dualctl trace --workdir <effective_worktree> <workstream_id>
```

`trace` 会把当前节点、open gate、最近 timeline、final acceptance、repair loop、关键 artifact 和下一步命令放在一起。需要深挖时，再看 `status` / `timeline` / `runs` / `events`。

结果判断：

- `PASS`：workflow 完成、原始仓库干净、关键 artifact 齐全、最终 Codex acceptance 没有 notes。
- `PASS-WARN`：主生命周期跑完了，但仍有需要人工关注的不确定性，例如 `ACCEPT_WITH_NOTES`、经历过 `REJECT -> repair`，或 build/runtime 证据不足。
- `FAIL`：workflow 没完成、原始仓库被污染，或 final report / handoff / delivery artifact 缺失。

对 MVP 主线来说，最重要的字段是：

- `final_status`
- `final_acceptance_decision`
- `had_acceptance_reject`
- `repair_loop_count`
- `verification_confidence`
- `original_repo_clean`
- `artifacts`

内部真实长 case 已验证：系统能从 intake 跑到 delivery，经历 Codex `REJECT` 后自动进入 repair loop，最终完成，并保持原始仓库干净。公开可复现路径见 `./scripts/run-public-benchmark.sh` 和 `docs/mvp-mainline-validation.md`。

## Local API Server

`dual-server` 是给后续 dashboard / automation 用的本地 HTTP 入口。当前第一版读取 ledger 里的 overview / status / timeline，并提供最小 action API 来推进人工卡点和失败恢复。它也提供 scheduler：默认关闭，可以手动 tick，也可以通过配置打开后台周期调度。

构建：

```bash
cd dual-orchestrator
mvn -pl dual-server -am package
```

启动：

```bash
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

打开本地 operator panel：

```text
http://localhost:8080/
```

这个页面是一个很薄的调用 UI，不是完整 dashboard。它直接调用下面这些 HTTP API，可以做几件日常 E2E 操作：

- 输入 effective workdir，列出 workstreams。
- 首屏 overview 回答“现在跑到哪、为什么停、下一步做什么”，并展示 attention items、acceptance、repair loops、最近 timeline 和关键 artifacts。
- 查看当前 status 和 timeline 详情。
- 查看最近的 Agent artifact contract sidecars 摘要，包括 decision、summary、must fix、风险、变更文件和验证摘要。
- 当 workflow 停在 human review gate 时，展示当前 review packet；页面会先渲染推荐动作、TaskContract / Decision / Must Fix / Git Evidence 等结构化审查卡片，原始 Markdown packet 保留在折叠区。
- 当 workflow 停在 failed / blocked / waiting for recovery 状态时，展示 `recovery/current-failure.md`。
- 手动执行 scheduler tick。
- 对选中的 workstream 执行 resume、retry、approve + run、revise + run、complete。

这些按钮不是自动验收。`Approve + Run`、`Revise + Run`、`Complete` 只应该在人读完页面里的 `Human Review Packet` 后使用。Smoke 脚本为了自动化 E2E 会自动 approve gate；真实使用时，human review gate 的语义是“暂停，让人基于 packet 做决定”。

常见用法：

1. 先用 `dualctl start` 或 smoke script 创建一个 workstream。
2. 启动 `dual-server`。
3. 打开 `http://localhost:8080/`。
4. 在页面顶部填入 start 输出里的 `Workspace`，点击 `Refresh`。
5. 选中 workstream 后先看首屏 overview，再按需查看 status / timeline；遇到 human gate 时点 `Approve + Run` 或 `Complete`。

查询某个 workstream：

```bash
curl "http://localhost:8080/api/workstreams?workdir=<effective_worktree>"
curl "http://localhost:8080/api/workstreams/<workstream_id>/overview?workdir=<effective_worktree>"
curl "http://localhost:8080/api/workstreams/<workstream_id>/status?workdir=<effective_worktree>"
curl "http://localhost:8080/api/workstreams/<workstream_id>/timeline?workdir=<effective_worktree>"
curl "http://localhost:8080/api/workstreams/<workstream_id>/review/current?workdir=<effective_worktree>"
curl "http://localhost:8080/api/workstreams/<workstream_id>/recovery/current?workdir=<effective_worktree>"
```

推进 workflow：

```bash
curl -X POST "http://localhost:8080/api/workstreams/<workstream_id>/resume?workdir=<effective_worktree>&maxSteps=1"
curl -X POST "http://localhost:8080/api/workstreams/<workstream_id>/retry?workdir=<effective_worktree>" \
  -H "Content-Type: application/json" \
  -d '{"note":"fixed external cause"}'
curl -X POST "http://localhost:8080/api/workstreams/<workstream_id>/review/approve?workdir=<effective_worktree>&run=false"
curl -X POST "http://localhost:8080/api/workstreams/<workstream_id>/review/complete?workdir=<effective_worktree>" \
  -H "Content-Type: application/json" \
  -d '{"note":"completed from API"}'
curl -X POST "http://localhost:8080/api/workstreams/<workstream_id>/review/revise?workdir=<effective_worktree>&run=false" \
  -H "Content-Type: application/json" \
  -d '{"feedback":"Please address the Must Fix items before implementation."}'
```

`run=true&maxSteps=20` 可以在 approve / revise 后继续跑到下一个 gate、recovery、failure 或 completed。所有 action API 都复用 workflow ledger 和 workstream lock，和 CLI 使用同一套账本。CLI 仍然是主要操作入口；HTTP API 目前服务于后续 UI 和外部自动化。

手动触发一次 scheduler tick：

```bash
curl -X POST "http://localhost:8080/api/scheduler/tick?workdir=<effective_worktree>&maxWorkstreams=10&maxStepsPerWorkstream=1"
```

手动 tick 会列出指定 `workdir` 下的 workstreams，只推进 `RUNNING` 状态的 workflow；已经等待人工 review、等待 recovery、失败、阻塞或完成的 workstream 会被跳过。

后台 scheduler 默认关闭。打开方式：

```bash
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar \
  --dual.scheduler.enabled=true \
  --dual.scheduler.workdirs=/path/to/effective-worktree-1,/path/to/effective-worktree-2 \
  --dual.scheduler.interval-ms=30000 \
  --dual.scheduler.max-workstreams=10 \
  --dual.scheduler.max-steps-per-workstream=1
```

查看后台 scheduler 状态：

```bash
curl "http://localhost:8080/api/scheduler/background/status"
```

立即触发一次后台 scheduler run：

```bash
curl -X POST "http://localhost:8080/api/scheduler/background/run-once"
```

后台 scheduler 和手动 tick 使用同一套规则：只推进 `RUNNING` workstream，不自动 approve human gate，不自动修复 recovery，不绕过失败状态。同一时间如果已有后台 tick 在运行，新的后台 tick 会被跳过。

## acpx Sessions

Agent 调用统一走 acpx：

```bash
acpx claude -s claude-main -f <prompt_file>
acpx codex -s codex-reviewer -f <prompt_file>
```

这里的 `<prompt_file>` 不是你手写的需求文件，而是 `dualctl resume` 在 run ledger 里生成的 Agent 指令文件，例如：

```text
<worktree>/.agent/workstreams/<workstream_id>/runs/<run_id>/prompts/design-claude.md
```

如果 session 不存在，`AcpxAgentTransport` 会尝试自动创建：

```bash
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

默认 Agent 调用会加上保守的非交互边界：

```bash
acpx --timeout 600 --approve-reads --non-interactive-permissions fail ...
```

含义是：读操作可以自动批准；如果 agent 需要无法自动处理的权限，直接失败并把 stderr 留在 ledger 里，避免 CLI 无限挂住。

较小的 `baseline` / `contract` lifecycle 仍然尽量保守，只在实现和自测相关 Claude 节点启用写权限。完整真实生命周期 `full` 会让所有 acpx Agent 节点使用：

```bash
acpx --timeout 600 --approve-all ...
```

原因是 full 的 intake / design / plan / acceptance 都可能需要通过 Agent 工具读取项目、执行 `git diff`、运行测试或生成证据；在非交互模式下使用 `--approve-reads --non-interactive-permissions fail` 会让这类工具调用直接失败。安全边界主要由默认 worktree、`Execution Workspace Guard` 和 prompt contract 共同承担：Claude 是主控开发者；Codex 仍然只做外部审视和验收，不应修改业务代码。

如果 acpx runtime 返回 reconnect、额度不足、登录失效等可恢复错误，ledger 会保留 stdout/stderr。若 stdout 已经包含完整 terminal `dual-control` block，并以 `[done] end_turn` 结束，adapter 会接受该产物，同时在 result 中记录 `acpx_recoverable_stderr_ignored=true` 和原始 `exit_code`。如果 stdout 不完整，节点仍会停到 recovery；dual 不会替你修复 agent runtime。

所有 Agent prompt 都会带 `Execution Workspace Guard`，明确唯一有效工作目录，并在上下文预览中隐藏 workspace 外的绝对路径。这样做是为了避免 Agent 从历史输出里照抄原始 repo 路径，绕过 worktree 隔离。

## Prompt Templates

Agent prompt 不再写在 Java 字符串里。`AcpxAgentTaskHandler` 会根据 workflow node 的 `prompt_template` 读取 resources：

```text
dual-adapter-acpx/src/main/resources/dev/dual/adapter/acpx/prompts/
  intake-claude-brainstorm.md
  intake-codex-product-review.md
  design-claude.md
  design-codex-review.md
  plan-claude.md
  plan-codex-review.md
  implement-claude.md
  implement-claude-self-test.md
  acceptance-codex-review.md
  stage-claude-handoff.md
```

渲染时会注入：

```text
{{workstream_id}}
{{node_id}}
{{prompt_template}}
{{ledger_path}}
{{requirement}}
```

模板第一版遵守几个原则：

- Claude 是主控 developer / architect，只有 Claude 可以修改业务代码。
- Codex 是外部 reviewer / verifier，不修改业务代码。
- 每个阶段都有明确 Markdown section。
- Codex review 和 acceptance 都必须输出 `Decision`。
- 每个 prompt 末尾都有 `dual-control` block，decision handler 会从这里解析结构化结果。

`dual-control` 格式：

````text
```dual-control
decision: PASS
summary: one short sentence
```
````

当前解析策略：

- Decision node 只读取最新的 `inputRefs`，不会回退到更早文件。
- 找不到 fenced `dual-control` block，会以 `MISSING_DUAL_CONTROL` 停到 `control.repair` recovery gate。
- **Terminal block rule**：如果 markdown 中存在多个 `dual-control` block，只有最后一个（terminal block）被当作控制指令，前面的被视为 prose（例如 agent 引用的用户需求原文）。
- Terminal block 之后不能有非空白内容，否则以 `NON_TERMINAL_CONTROL_BLOCK` 停到 `control.repair` recovery gate；acpx 运行时追加的 `[done] end_turn` sentinel 会被忽略。
- Blockquote `> ```dual-control` 和嵌套 code fence 中的 `dual-control` 不会被匹配（regex 锚定行首 `^`）。
- 找到 `dual-control` 但缺少 `decision`，会以 `INVALID_DUAL_CONTROL` 停到 `control.repair` recovery gate。
- `decision` 不在当前阶段允许集合内，会以 `CONTRACT_VIOLATION`（映射为 `INVALID_DUAL_CONTROL`）停到 `control.repair` recovery gate。
- `BLOCKED` 会把 workflow 标记为 `BLOCKED`，不会继续路由。
- `NEEDS_REVISION` / `REJECT` 会沿 workflow definition 中配置的 revision edge 返回对应 Claude 节点。

合法 decision 值由 `ContractRegistry` 定义（单一真相源）：

| Decision Node | Allowed Decisions |
| --- | --- |
| `intake.decision` | `READY_FOR_PRODUCT_REVIEW`, `NEEDS_REVISION`, `BLOCKED` |
| `design.decision` | `READY_FOR_ARCH_REVIEW`, `NEEDS_REVISION`, `BLOCKED` |
| `plan.decision` | `READY_TO_IMPLEMENT`, `NEEDS_REVISION`, `BLOCKED` |
| `acceptance.decision` | `ACCEPT`, `ACCEPT_WITH_NOTES`, `REJECT`, `BLOCKED` |

每个 agent task node 也有对应的 output contract，定义其合法的 `decision` 值域和 required keys（`decision` + `summary`）。`ArtifactContractValidator` 会在 `acpx.agent_run` 完成后集中校验 Agent 输出 artifact；缺失 `dual-control`、缺少 required key、或 decision 越界都会让当前节点停到 recovery，不继续路由。校验通过后，runtime 会在 Agent 输出旁写入 `*.contract.json` sidecar，例如 `design-codex-review.md` 对应 `design-codex-review.contract.json`，记录 node、decision、summary、source output 和 contract 值域。sidecar 还会从已有 Markdown 章节中提取轻量 pipeline 字段：`must_fix`、`open_risks`、`changed_files`、`verification_summary`。这些字段用于上下文稳定、报告和 UI 展示，不是新的路由规则引擎。`ContractRegistry` 保证 decision node 的 `allowedDecisions` 与 workflow edge `on` 值和 agent prompt 模板声明的值域一致。

Context Package 会优先读取这些 sidecar，把 `decision` / `summary` 作为结构化事实放在 Markdown 原文片段之前。这样后续 Agent 不需要从长篇 review 或 handoff 里重新猜关键状态；原文仍保留为可追溯证据。

当前选择是“规则集中化”，不是“规则引擎化”：规则仍在 Java registry / validator 中，便于 IDE 导航、单元测试和快速调整。等 workflow contract 稳定、并且自定义 workflow 需要声明自己的 output contract 时，再考虑把这层迁移到 workflow JSON 配置。

Prompt 模板变量在加载时校验：如果模板引用了非 `KNOWN_VARIABLES` 集合（`workstream_id`, `node_id`, `prompt_template`, `ledger_path`, `requirement`, `context_package`）中的 `{{variable}}`，会立即报错。

## Commands

### Doctor

检查本机和 worktree 是否具备运行条件：

```bash
dualctl doctor --workdir <effective_worktree>
dualctl doctor --workdir <effective_worktree> <workstream_id>
```

`doctor` 会检查 Git、当前目录是否是 Git repo、acpx 是否可用。传入 `workstream_id` 时，还会检查 workflow current、events、runs、context、Git evidence 等 ledger 文件是否存在。`context` / `git evidence` 这类阶段性产物没生成时只会显示 `WARN`，不会当成阻断错误。

### Start

在目标 Git 仓库里执行：

```bash
dualctl start "给 README 增加一段项目说明"
```

从文件读取需求：

```bash
dualctl start --task-file ./requirement.md
```

启动后直接推进到第一个人工卡点：

```bash
dualctl start --run --task-file ./requirement.md
```

指定目标仓库：

```bash
dualctl start --workdir /path/to/repo --task-file ./requirement.md
```

指定分支或 worktree 目录：

```bash
dualctl start --branch ai/my-task --worktree-dir ../repo-my-task "任务描述"
```

指定 workflow 定义文件：

```bash
dualctl start --workflow ./workflow.json "任务描述"
```

使用仓库内置示例 workflow：

```bash
dualctl start --workflow dual-orchestrator/workflows/agent-lifecycle.v1.json "任务描述"
```

启动前校验 workflow 文件：

```bash
dualctl workflow validate dual-orchestrator/workflows/agent-lifecycle.v1.json
```

查看当前 CLI 内置支持的 handler：

```bash
dualctl workflow handlers
```

`workflow validate` 会检查结构合法性，也会检查 handler type 是否属于当前 CLI 内置能力。未来如果使用外部 runtime 提供的 handler，可以显式放行：

```bash
dualctl workflow validate --allow-unknown-handlers ./workflow.json
```

`dualctl start --workflow` 也会做同样的 handler 检查，避免创建 worktree 后才在 `resume` 阶段发现 handler 拼错。如果确实要使用外部 runtime handler，启动时也需要显式放行：

```bash
dualctl start --workflow ./workflow.json --allow-unknown-handlers "任务描述"
```

导出内置默认 workflow 作为自定义起点：

```bash
dualctl workflow print-default --output ./workflow.json
```

如果不传 `--workflow`，默认使用内置的 `agent-lifecycle@1`。如果传入 workflow 文件，启动时会严格校验 nodes / edges / entry node，并把当时的 workflow definition 固化到 `.agent/workstreams/<workstream_id>/workflow/definition.json`。后续即使原始配置文件变化，这次 run 仍按账本中的 definition 复盘。

最小 workflow 示例：

```json
{
  "workflowId": "custom-lifecycle",
  "version": 1,
  "entryNodeId": "intake.claude_brainstorm",
  "defaults": {
    "sessions": {
      "claude": "claude-main",
      "codex": "codex-reviewer"
    },
    "delivery": {
      "base": "main",
      "draft": true
    },
    "revision": {
      "max_revisions": 1
    }
  },
  "nodes": [
    {
      "id": "intake.claude_brainstorm",
      "kind": "TASK",
      "handlerType": "acpx.agent_run",
      "config": {
        "agent": "claude",
        "prompt_template": "intake-claude-brainstorm"
      }
    },
    {
      "id": "gate.human_requirement_review",
      "kind": "GATE",
      "handlerType": "human.review",
      "config": {
        "allowed_actions": ["approve", "revise"]
      }
    }
  ],
  "edges": [
    {
      "from": "intake.claude_brainstorm",
      "on": "completed",
      "to": "gate.human_requirement_review"
    }
  ]
}
```

`defaults` 是可选的，用来减少 workflow 文件重复配置：

- `defaults.sessions.claude` 会填充 `agent=claude` 且没有显式 `session` 的 `acpx.agent_run` 节点。
- `defaults.sessions.codex` 会填充 `agent=codex` 且没有显式 `session` 的 `acpx.agent_run` 节点。
- `defaults.delivery` 会填充 `delivery.create_pr` 节点缺失的 `base`、`draft` 等配置。
- `defaults.revision.max_revisions` 会填充 `review.decision` 和 `human.review` 节点缺失的 revision 上限。达到上限后，workflow 会停在 recovery gate，不会无限开新 iteration。
- 节点自己的 `config` 优先级更高；一旦节点显式写了 `session`、`base`、`draft` 或 `max_revisions`，不会被 defaults 覆盖。

启动后会打印：

```text
Workstream: ws-...
Workspace: <effective worktree>
Branch: ai/ws-...
Ledger: <worktree>/.agent/workstreams/<workstream_id>
Workflow: agent-lifecycle@1
Next: dualctl resume --workdir <effective worktree> <workstream_id>
```

创建 worktree 时，`dualctl` 会把 `.agent/` 和 `.serena/` 追加到本地 Git exclude（`git rev-parse --git-path info/exclude` 指向的位置），避免 run ledger 和 Codex/Serena 这类本地工具元数据出现在业务仓库的 `git status` 里。它不会修改项目自己的 `.gitignore`。

### Status

```bash
dualctl status --workdir <effective_worktree> <workstream_id>
```

输出分两块：

- `Workflow`：新的 workflow current state，是当前 `resume` / `review` 真正使用的状态。
- `Delivery`：如果已经执行交付阶段，会显示 delivery status、commit hash 和 PR URL。

默认不再显示旧 design-loop checkpoint/stage 快照，避免把 stale 字段误当成当前进度。排查旧账本时可以显式打开：

```bash
dualctl status --workdir <effective_worktree> --show-legacy <workstream_id>
```

判断当前真实 active node 时，以 `Workflow` 区块和 `.agent/workstreams/<workstream_id>/workflow/current.json` 为准。

### Runs

列出当前 workstream 的 Agent run：

```bash
dualctl runs --workdir <effective_worktree> <workstream_id>
dualctl runs --workdir <effective_worktree> --failed <workstream_id>
dualctl runs --workdir <effective_worktree> --agent codex --latest <workstream_id>
```

输出包含 run id、状态、agent、node、exit code、duration 和 failure reason。排障时可以用 `--failed` 只看失败 run，用 `--agent claude|codex` 只看某个 Agent，用 `--status` 按状态过滤，用 `--latest` 只取最后一条匹配结果。它读取：

```text
.agent/workstreams/<workstream_id>/runs/<run_id>/run.json
```

### Events

查看最近 workflow events：

```bash
dualctl events --workdir <effective_worktree> <workstream_id>
dualctl events --workdir <effective_worktree> --limit 50 <workstream_id>
dualctl events --workdir <effective_worktree> --type node_completed <workstream_id>
dualctl events --workdir <effective_worktree> --contains rate_limit <workstream_id>
```

输出原始 JSONL，方便脚本处理：

```bash
dualctl events --workdir <effective_worktree> --raw <workstream_id>
```

### Inspect

查看当前 active node：

```bash
dualctl inspect --workdir <effective_worktree> <workstream_id>
```

查看指定 node：

```bash
dualctl inspect --workdir <effective_worktree> --node <node_instance_id> <workstream_id>
```

直接查看当前 active node 或指定 node 关联的 context package：

```bash
dualctl inspect --workdir <effective_worktree> --show context <workstream_id>
dualctl inspect --workdir <effective_worktree> --node <node_instance_id> --show context <workstream_id>
```

查看指定 run：

```bash
dualctl inspect --workdir <effective_worktree> --run <run_id> <workstream_id>
```

直接查看 run 的 prompt / output / stderr：

```bash
dualctl inspect --workdir <effective_worktree> --run <run_id> --show prompt <workstream_id>
dualctl inspect --workdir <effective_worktree> --run <run_id> --show output <workstream_id>
dualctl inspect --workdir <effective_worktree> --run <run_id> --show stderr <workstream_id>
dualctl inspect --workdir <effective_worktree> --run <run_id> --show output-meta <workstream_id>
```

这是只读命令，不会修改 ledger。

### Resume

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
```

`resume` 会读取：

```text
.agent/workstreams/<workstream_id>/workflow/current.json
```

然后执行当前 active node：

- Task node：调用对应 TaskHandler。
- Decision node：调用 DecisionHandler 并选择 edge。
- Gate node：停住，等待 `review approve` 或 `review revise`。

### Run

`run` 会连续执行 active node，直到遇到人工卡点、恢复卡点、失败或 workflow 完成：

```bash
dualctl run --workdir <effective_worktree> <workstream_id>
dualctl run --workdir <effective_worktree> --max-steps 50 <workstream_id>
```

这是真实使用时的主推进命令。`resume` 仍保留用于单步排查。

当 `run` 或 `resume` 停住时，CLI 会打印 `Next actions`：

- `WAITING_FOR_HUMAN`：显示 `review show`、当前 review packet 路径，以及 `review approve --run` / `review revise --run` 命令。
- `WAITING_FOR_RECOVERY` / `FAILED` / `BLOCKED`：显示 `recovery show`、当前 failure summary 路径，以及 `retry` 命令。
- `COMPLETED`：显示 final report 路径和 `summary` 命令，提醒先查看交付产物。
- 超过 `--max-steps`：显示继续运行的 `run` 命令。

### Autorun

`autorun` 用于“无人值守验证主链路”：它会在每次遇到中间 human gate 时自动批准，然后继续执行；遇到最终 `gate.human_next_stage_approval` 时自动 `complete`，避免把同一个 workstream 带入下一轮。

```bash
dualctl autorun --workdir <effective_worktree> <workstream_id>
dualctl autorun --workdir <effective_worktree> --max-steps 80 --max-gates 12 <workstream_id>
```

它会在以下情况停住：

- workflow 完成。
- 进入 `WAITING_FOR_RECOVERY`。
- 进入 `FAILED` / `BLOCKED`。
- 同一个 gate 在本次 `autorun` 中第二次出现，说明 workflow 进入了验收失败后的修复回路，需要人工查看原因。
- 超过 `--max-gates`。

`autorun` 适合 smoke test、真实仓库小样本验证和“先让系统跑完主链路”的场景。它不是人工审查的替代品；正式交付前仍然应该查看 review packet、final report、Codex acceptance 和 Git diff。需要人工逐关确认时，继续使用 `run` + `review approve|revise`。

### Retry

如果当前 active node 因为 agent runtime、quota、context window 或 prompt contract 问题进入 `FAILED` / `BLOCKED`，workflow 会停在 `WAITING_FOR_RECOVERY`，并打开一个 recovery gate。先修复外部问题，再显式 retry：

失败现场会写入：

```text
.agent/workstreams/<workstream_id>/recovery/current-failure.md
```

这份摘要会列出当前失败节点、handler、失败原因、acpx/session 信息、exit code、output refs，以及建议的 `dualctl retry` 命令。它是给人工恢复使用的入口，不替代 `nodes/*.json`、`events.jsonl` 和具体 run outputs。

可以直接查看：

```bash
dualctl recovery show --workdir <effective_worktree> <workstream_id>
```

`recovery show` 会先打印 `Recovery Assessment`，给出：

- `Classification`：例如 `recoverable_agent_runtime`、`semantic_revision_limit`、`artifact_contract_failure`。
- `Retry Readiness`：例如 `RETRY_AFTER_EXTERNAL_FIX`、`NEEDS_HUMAN_DECISION`。
- `Reason`：为什么建议这么处理。
- `Suggested Command`：下一条建议命令。

这层判断故意保持轻量，只帮助操作员判断“能不能 retry、是不是要先人工处理”。详细证据仍在下面的 `current-failure.md` 正文里。

```bash
dualctl retry --workdir <effective_worktree> --note "claude session reconnected" <workstream_id>
dualctl resume --workdir <effective_worktree> <workstream_id>
```

`retry` 不会直接执行 Agent。它只会：

- 校验当前 active node 是 `FAILED` 或 `BLOCKED`。
- 把该 node 标记回 `READY`。
- 清空该 node 的旧 output refs。
- 记录 `node_retry_requested` event。
- 清空当前 recovery gate，使 workflow 回到 `RUNNING`。

这样可以保留失败现场，同时让下一次 `resume` 重新执行同一个 node。

在 `WAITING_FOR_RECOVERY` 状态下直接执行 `dualctl resume` 不会重新跑 Agent；它只会提示先执行 `dualctl retry`。这避免了 rate limit、context window、输出契约错误还没处理时被重复触发。

如果终端、operator 或外部 quota 中断导致 workflow 仍显示 `RUNNING`，但 status 中的 `Running PID` 已标记为 stale，可以先把同一个 active node 收束到 recovery，再按正常 retry 继续：

```bash
dualctl recovery stale \
  --workdir <effective_worktree> \
  --note "agent process disappeared after quota pause" \
  <workstream_id>

dualctl retry --workdir <effective_worktree> <workstream_id>
dualctl resume --workdir <effective_worktree> <workstream_id>
```

`recovery stale` 会校验记录的 PID 已经不存活；如果进程还活着，它会拒绝执行，避免把正常运行中的 Agent 误判为失败。

如果确认这个 workstream 不再继续，可以终止 recovery：

```bash
dualctl recovery abort \
  --workdir <effective_worktree> \
  --note "scope changed; stop this workstream" \
  <workstream_id>
```

`recovery abort` 会把 workflow 标记为 `FAILED`，清空 active node / open gate，并写入 `workflow_aborted` event。它不会删除 worktree、代码改动或 ledger。

### Calibration

当 Codex acceptance 触发可校准失败时，FailureArbiter 会把失败沉淀成候选：

```text
.agent/workstreams/<workstream_id>/calibration/candidates/<candidate_id>.json
.agent/workstreams/<workstream_id>/calibration/candidates/<candidate_id>.md
```

候选不会自动修改模板、规则或代码。它只是记录“这类失败将来可能值得提升为 contract template / context rule / regression test”的证据。

查看候选：

```bash
dualctl calibration list --workdir <effective_worktree> <workstream_id>
dualctl calibration show --workdir <effective_worktree> <workstream_id> <candidate_id>
dualctl calibration show --workdir <effective_worktree> --json <workstream_id> <candidate_id>
```

人工决策候选：

```bash
dualctl calibration decide \
  --workdir <effective_worktree> \
  --status accepted \
  --note "Promote this into the contract template later" \
  <workstream_id> \
  <candidate_id>
```

生成人工可审的 apply plan：

```bash
dualctl calibration plan \
  --workdir <effective_worktree> \
  <workstream_id> \
  <candidate_id>
```

`plan` 会写入：

```text
.agent/workstreams/<workstream_id>/calibration/apply-plans/<candidate_id>.json
.agent/workstreams/<workstream_id>/calibration/apply-plans/<candidate_id>.md
```

显式提升已接受的 contract template candidate：

```bash
dualctl calibration promote \
  --workdir <effective_worktree> \
  <workstream_id> \
  <candidate_id>
```

`decide` 只会更新 candidate JSON 的 `status`、`decidedAt` 和 `decisionNotes`。`plan` 只会生成下一步建议。`promote` 只接受已 `ACCEPTED` 且已有 apply plan 的 `CONTRACT_TEMPLATE` candidate，并只追加到项目级 `.agent/templates/contract-review-principles.md`。它不会修改内置 prompt、workflow definition、context rule 或测试；`CONTEXT_RULE` / `REGRESSION_TEST` 仍只生成计划，需要人工实现。

### Human Review

查看当前 gate 的 review packet：

```bash
dualctl review show --workdir <effective_worktree> <workstream_id>
```

`review show` 会读取当前 active gate 对应的：

```text
.agent/workstreams/<workstream_id>/reviews/<gate_node_instance_id>.md
```

这个 packet 是后续 CLI watch / Web UI 共用的人工审查读模型，里面会列出 gate、允许动作、TaskContract、最新 Agent decision / Must Fix、Git evidence、delivery artifacts、输入 artifacts 和关键内容摘录。

当 `dualctl resume`、`dualctl run` 或 `dualctl review approve --run` 停在 human gate 时，终端会直接打印 `Next actions`、`dualctl review show ...` 和当前 packet 路径，优先按这份 packet 做人工判断。

### Trace

追踪长任务时，优先用一屏 trace：

```bash
dualctl trace --workdir <effective_worktree> <workstream_id>
```

`trace` 只读取现有 ledger，不改变 workflow 状态。它会聚合 status、当前节点、open gates、Codex final acceptance、repair loops、最近 timeline、最近 agent runs、关键 artifacts 和下一步推荐命令。

### Timeline

ledger 会自动维护当前 workstream 的进度读模型：

```text
.agent/workstreams/<workstream_id>/timeline.json
```

查看节点概览：

```bash
dualctl timeline --workdir <effective_worktree> <workstream_id>
dualctl timeline --workdir <effective_worktree> --raw <workstream_id>
```

持续观察进度：

```bash
dualctl watch --workdir <effective_worktree> <workstream_id>
dualctl watch --workdir <effective_worktree> --once <workstream_id>
dualctl watch --workdir <effective_worktree> --interval-ms 1000 <workstream_id>
```

`timeline.json` 聚合 workflow current 和 nodes 快照，包含 workflow 状态、active nodes、open gates、每个 node 的状态、iteration、artifact refs 和 duration。`watch` / Web UI 会优先消费这个读模型，而不是直接解析 ledger 原始文件。

`metrics/workstream-metrics.json` 还包含 `providerMetrics`。当前指标来自本地 `runs/*/run.json`，覆盖 agent/provider 成功率、失败数和耗时；token / cost 需要 acpx adapter 暴露原始 usage 后再补。

### Workstream Summary

排障或复盘真实 case 时，可以生成一份紧凑 summary：

```bash
dualctl summary --workdir <effective_worktree> <workstream_id>
dualctl summary --workdir <effective_worktree> --write <workstream_id>
dualctl summary --workdir <effective_worktree> --output /tmp/workstream-summary.md <workstream_id>
```

`summary` 只读取现有 ledger，不改变 workflow 状态。它会聚合 workflow status、active nodes、open gates、推荐下一步动作、最近 timeline、final report、recovery summary、delivery 状态、calibration candidates 和 apply plans。

`--write` 默认写入：

```text
.agent/workstreams/<workstream_id>/reports/workstream-summary.md
```

批准当前 gate：

```bash
dualctl review approve --workdir <effective_worktree> <workstream_id>
dualctl review approve --workdir <effective_worktree> --run <workstream_id>
```

对支持更多动作的 gate，例如 `contract_review` 的 `clarify`、`reduce_scope`、`reject`，使用通用 decide 命令：

```bash
dualctl review decide --workdir <effective_worktree> --action reject --note "contract is empty" <workstream_id>
dualctl review decide --workdir <effective_worktree> --action clarify --note "scope.included is empty" --run <workstream_id>
```

`review decide --action approve` 会在需要时兼容老 edge 名 `approved`。

要求 revision：

```bash
dualctl review revise \
  --workdir <effective_worktree> \
  --feedback-file ./human-feedback.md \
  <workstream_id>
dualctl review revise \
  --workdir <effective_worktree> \
  --feedback-file ./human-feedback.md \
  --run \
  <workstream_id>
```

完成当前 workstream：

```bash
dualctl review complete --workdir <effective_worktree> <workstream_id>
```

`approve` / `revise` / `complete` 都会写 workflow event，并按 workflow edge 进入下一个节点或结束 workflow。传 `--run` 时，CLI 会在 gate 处理完成后继续推进到下一个人工卡点、恢复卡点、失败或完成。Gate 会把自己的输入 artifact 继续传给下一个节点，所以人工批准不会切断上一阶段生成的上下文。

如果希望一次性跨过所有中间 gate，用 `dualctl autorun`，不要反复手工执行 `review approve --run`。

`revise` 表示“上一轮产出需要按反馈修改”，因此会开启新的 workflow iteration。`gate.human_next_stage_approval` 只允许 `revise` 或 `complete`：`complete` 表示当前 workstream 已经收口，不再进入下一轮；如果要启动后续阶段，应基于 `next-stage-prompt.md` 明确创建新的 workstream。`retry` 不会开启新 iteration，因为它只是恢复同一个节点的执行尝试。

## Ledger

每个 workstream 在 effective worktree 中写入：

```text
.agent/workstreams/<workstream_id>/
  workflow/
    definition.json
    instance.json
    current.json
  nodes/
    <node_instance_id>.json
  iterations/
    iter-001.json
    iter-002.json
  gates/
    <gate_id>.json
  context/
    <context_node_instance_id>.md
  runs/
    <run_id>/
      run.json
      prompts/
      outputs/
  artifacts/
    git-evidence/
      git-evidence.json
      base-commit.txt
      branch.txt
      git-root.txt
      git-status-short.txt
      diff.stat
      diff.patch
      changed-files.txt
  reports/
    iter-001-final-report.md
    latest-final-report.md
  events.jsonl
```

每次 `acpx.agent_run` 都会写一个结构化 run metadata：

```text
.agent/workstreams/<workstream_id>/runs/<run_id>/run.json
```

当前字段包括：

- `runId`
- `workstreamId`
- `nodeId`
- `agent`
- `session`
- `promptTemplate`
- `workdir`
- `promptPath`
- `outputPath`
- `stderrPath`
- `promptMetadataPath`
- `outputMetadataPath`
- `stderrMetadataPath`
- `command`
- `startedAt`
- `finishedAt`
- `durationMs`
- `exitCode`
- `status`
- `failureReason`
- `recoverable`

这是后续 `inspect/tail` 和只读 UI 的基础数据源。

`promptPath` / `outputPath` / `stderrPath` 旁边会生成对应的 `*.meta.json` sidecar，记录 artifact type、路径、字节数、行数、SHA-256、run id、node id、agent、session 等信息。Markdown 仍然是人读的主要材料，sidecar 用于审计、去重、UI 展示和后续自动校验。

`context.build_package` 会在关键 Agent step 前生成上下文包，例如 Codex review、Claude implementation、Codex final acceptance。上下文包不是新事实源，而是从 ledger 里把本轮 Agent 最该读的东西装到一个 Markdown 文件：

- user requirement。
- 上一个节点传来的 input artifacts。
- 最近 workflow iteration history，包括 `revise` / `NEEDS_REVISION` / `REJECT` 的触发原因。
- 最近 human gate feedback，例如 `review revise --feedback-file` 写入的 Must Fix / 修改意见。
- Git evidence 指针和 `git-evidence.json` 摘要。
- events、workflow current、nodes、runs 等 ledger 指针。

上下文包通过 context governance 架构渲染，每个 profile 有独立的 budget 和 section degradation 策略：

| Profile | Budget | Sections | 特点 |
| --- | --- | --- | --- |
| `product_review` | 16k | requirement, input_artifacts, recent_agent_outputs, ledger_pointers | 轻量，聚焦需求+输出 |
| `architecture_review` | 20k | +human_feedback | 需要人工反馈 |
| `plan_review` | 22k | +iteration_history | 需要完整迭代历史 |
| `implementation` | 24k | 全部 (含 git_evidence) | 最完整，budget 最严 |
| `acceptance` | 26k | 全部 + git_evidence HIGH priority | 验收需要 git evidence |
| `full` (default) | 30k | 全部 | 宽松预算，所有 section FULL |

Governance pipeline 分 6 阶段：加载 profile → 检查 section 可用性 → 按 priority 分配 budget → 按 degradation 策略渲染 → 验证总 budget → 输出 governance summary table。每个 section 有自己的 `MissingPolicy`（FAIL/SKIP/PLACEHOLDER）、`Priority`（CRITICAL/HIGH/MEDIUM/LOW）、`Degradation` 策略（FULL/SUMMARY/LATEST/DECISION_ONLY/DIFF_STAT/POINTER_ONLY）和 `OverflowPolicy`（FAIL/DEGRADE/DROP_OPTIONAL）。超预算时从最低 priority section 开始降级，requirement section 不参与降级。

自定义 workflow 可以在 `context.build_package` 节点里配置：

```json
{
  "profile": "acceptance",
  "include_sections": ["requirement", "recent_agent_outputs", "git_evidence"],
  "max_snippet_chars": 4000,
  "max_recent_agent_outputs": 8
}
```

`include_sections` 会覆盖 profile 的默认章节，适合临时做更窄的验收包或补证据包。

Agent prompt 会出现 `## Context Package`，包含上下文包路径和一个短预览。这样 Agent 可以优先读同一份上下文，减少人工 copy/paste，也给后续 token budget、summary、session handoff 留出统一入口。

`stage.generate_next_prompt` 会读取 Claude handoff 输出，优先提取 `## Suggested Next Prompt` 章节，并生成：

```text
.agent/workstreams/<workstream_id>/artifacts/next-stage/next-stage-prompt.md
.agent/workstreams/<workstream_id>/artifacts/next-stage/handoff.json
```

`next-stage-prompt.md` 是下一阶段的人类 review 入口之一。它会保留 handoff 原文作为上下文，但把建议提示词单独放在 `## Prompt`。

`handoff.json` 是从 agent handoff markdown 解析生成的结构化 sidecar，包含 `completedWork`、`changedFiles`、`openRisks`、`verificationState` 等字段。解析器会容忍 `## 1. Completed Work` 这类编号标题，以及没有列表符号的段落式 handoff，避免 Agent 文风轻微漂移时丢失结构化上下文。下游 `context.build_package` handler 在渲染 git_evidence DIFF_STAT 时会优先使用 `changedFiles` 数组作为数据源，避免重新解析 markdown。如果 handoff.json 不存在，handler 回退到文件读取。

`gate.human_next_stage_approval` 不再通过 `approve` 自动回到 intake。当前 workstream 应通过 `complete` 收口；需要继续下一阶段时，把 `next-stage-prompt.md` 作为新的明确入口启动下一个 workstream。

`implement.artifact_validation` 会在 Claude self-test 之后采集 Git evidence。它不会替 Agent 做测试，也不会判定业务验收是否通过；它只把可追溯证据固定到账本里，让 Codex final acceptance 能基于同一组事实材料判断。采集时会排除 `.agent` 运行账本，避免把账本文件误当成业务改动：

- `git-status-short.txt`：当前 worktree 的 `git status --short`。
- `diff.stat`：`git diff --stat`。
- `diff.patch`：`git diff`，不包含未跟踪文件内容。
- `changed-files.txt`：从 porcelain status 提取的变更文件列表，包含未跟踪文件名。
- `git-evidence.json`：稳定索引，包含 `available`、`workdir`、`gitRoot`、`branch`、`baseCommit`、`hasChanges`、`changedFileCount`、`generatedAt`、`error`。

如果当前目录不是 Git repo，handler 不会把 workflow 打崩；它会写入 `available=false` 的 `git-evidence.json`，让后续验收报告明确说明 Git evidence 不可用。

`acceptance.generate_final_report` 会在 Codex acceptance decision 为 `ACCEPT` 或 `ACCEPT_WITH_NOTES` 后生成：

```text
.agent/workstreams/<workstream_id>/reports/<iteration_id>-final-report.md
.agent/workstreams/<workstream_id>/reports/latest-final-report.md
```

报告会汇总 user request、Codex acceptance 摘要、Claude self-test 摘要、Git evidence 指针和最近 Agent 输出。它不替代 Codex 验收，只负责把本轮结果固定成一个人和后续自动化都能引用的交付物。

`delivery.prepare_pr` 会在 final report 后生成交付材料：

```text
.agent/workstreams/<workstream_id>/delivery/commit-message.txt
.agent/workstreams/<workstream_id>/delivery/pr-body.md
```

`commit-message.txt` 用于提交 worktree 中的业务改动，`pr-body.md` 汇总 user request、verification、Codex final acceptance、changed files 和 run artifacts。默认 workflow 只准备材料，不自动提交、不自动 push。

需要提交业务改动时执行：

```bash
dualctl delivery commit --workdir <effective_worktree> <workstream_id>
```

`delivery commit` 会用生成的 commit message 执行 `git commit`，并排除 `.agent` 运行账本；如果没有 `.agent` 之外的业务改动，会跳过提交。
执行结果会写入 `.agent/workstreams/<workstream_id>/delivery/delivery.json`，并刷新 `reports/latest-final-report.md` 里的 `## Delivery` 段。

需要创建 PR 时执行：

```bash
dualctl delivery create-pr --workdir <effective_worktree> <workstream_id>
dualctl delivery create-pr --workdir <effective_worktree> --base main --draft <workstream_id>
dualctl delivery create-pr --workdir <effective_worktree> --title "feat: my task" <workstream_id>
```

`delivery create-pr` 会先确保业务改动已提交，然后执行 `git push -u origin <branch>` 和 `gh pr create`。它要求当前分支不是 `main` / `master`，并且 worktree 有可识别的 `origin` remote。gh 输出会保存到：

```text
.agent/workstreams/<workstream_id>/delivery/gh-pr-create.stdout.log
.agent/workstreams/<workstream_id>/delivery/gh-pr-create.stderr.log
.agent/workstreams/<workstream_id>/delivery/pr-url.txt
```

`dualctl status` 会读取 `delivery.json` 并显示 delivery status、commit hash 和 PR URL。

过渡期仍会看到旧 design-loop ledger：

```text
.agent/workstreams/<workstream_id>/
  workstream.json
  requirement.md
  timeline.json
  timeline.jsonl
  current/
  stages/
  checkpoints/
  workspaces/
```

事实流是 `events.jsonl`，`workflow/current.json` 是为了快速 `status` / `resume` 的快照。

### Workflow Iteration

workflow iteration 表示一次有业务意义的尝试，不是命令执行次数。

- `approve` / `completed`：沿用当前 iteration。
- `retry`：沿用当前 iteration，只重新执行当前失败或阻塞的 node。
- `revise` / `NEEDS_REVISION` / `REJECT`：开启新 iteration，例如 `iter-002`。

每个 iteration 会写到：

```text
.agent/workstreams/<workstream_id>/iterations/<iteration_id>.json
```

revision iteration 会记录：

- `parentIterationId`：上一轮。
- `triggerNodeInstanceId`：哪个 gate / decision 触发。
- `triggerAction`：`revise`、`NEEDS_REVISION` 或 `REJECT`。
- `targetNodeDefinitionId`：回到哪个节点继续。
- `reason`：来自 human feedback、Codex summary 或错误原因的摘要。

这个模型能区分两类情况：工具失败、quota、context window、格式错误属于执行层恢复，应该 `retry`；Codex 或人明确要求修改产出，才进入下一轮 iteration。这样账本能表达“业务版本在收敛”，也不会把 acpx 断线误记成新一版。

当前仍有一个风险：iteration 只能说明“为什么进入下一轮”，不能保证 Agent 一定完整处理了反馈。业务满足性仍需要看 context package、Agent output、Git evidence、Codex acceptance 和 human gate 的组合证据。后续可以继续补强 output contract validation 和 acceptance checklist。

## Smoke Tests

### Build Smoke

```bash
cd dual-orchestrator
mvn test
mvn -pl dual-cli -am package
```

### Workflow Ledger Smoke

```bash
cd /path/to/repo
dualctl start "给 README 增加一段项目说明"
dualctl status --workdir <effective_worktree> <workstream_id>
```

预期：

- worktree 已创建。
- `.agent/workstreams/<workstream_id>/workflow/current.json` 存在。
- `.agent/workstreams/<workstream_id>/events.jsonl` 包含 `workflow_started`。
- `status` 能看到 `Workflow: agent-lifecycle@1`。

### Controlled Lifecycle Smoke

改 runtime、ledger、CLI 或 workflow routing 后，先跑这个。它会临时注入一个 controlled fake `acpx`，不调用真实 Claude/Codex，用来确定性验证完整生命周期 happy path：

```bash
cd dual-orchestrator
./scripts/smoke-test.sh lifecycle --cleanup
```

预期：

- workflow 从 `start --run` 跑到第一个 human gate。
- 脚本自动 approve 中间 gate，并在最后 next-stage gate 执行 `review complete`。
- 28 个 baseline mainline 节点完成。
- final report、delivery prepare artifact、next-stage prompt、handoff.json 都存在。
- Git evidence 能看到 controlled implementation 对 `main.py` / `README.md` 的改动。

这个 smoke 只验证编排主链路，不验证真实 Agent 能力。

如果想保留 ledger 给 operator panel 查看，不要加 `--cleanup`：

```bash
cd dual-orchestrator
./scripts/smoke-test.sh lifecycle --skip-build
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

然后打开 `http://localhost:8080/`，把脚本输出里的 `Workspace` 粘到页面顶部的 `Effective workdir`。

如果想让 smoke 在每个 human gate 停下来，改由 Operator Panel 完成人工审阅和 approve：

```bash
cd dual-orchestrator
./scripts/smoke-test.sh --case contract-happy-path --manual-gates
```

脚本到达 gate 后会打印 Operator Panel 地址、effective workdir、workstream id 和 review packet 路径。你在页面里读完 `Human Review Packet`，点击 `Approve + Run`、`Revise + Run` 或最后一个 gate 的 `Complete`，再回到终端按 Enter。这个模式不会自动 approve gate。

### Real acpx Smoke

完整生命周期和多 workflow variant 的脚本化 E2E 用法见 [Smoke Test E2E 使用说明](docs/testing/smoke-test-e2e.md)。其中 `scripts/smoke-test.sh --case all` 会顺序运行 `lifecycle` controlled case 以及 baseline、contract、full 三个真实 acpx case。

真实 acpx case：

```bash
cd dual-orchestrator
./scripts/smoke-test.sh --case baseline-basic --cleanup
./scripts/smoke-test.sh --case contract-happy-path --cleanup
./scripts/smoke-test.sh --case full-reject-classify-path --cleanup
```

### Real Repository Lifecycle Case

如果要在真实项目里验证 dual 能不能接管一个小任务，又不想手工盯每个 human gate，可以用 `run-real-case.sh`。它会：

- 构建 `dualctl`。
- 在目标 repo 创建默认安全 worktree。
- 启动 lifecycle workflow。
- 调用 `dualctl autorun` 自动 approve 中间 human gates。
- 在最后 next-stage gate 执行 `review complete`，避免开启下一轮。
- 采集 status、timeline、events、runs、recovery、Git diff 证据。
- 在终端打印每轮 workflow status、active node、open gates 和执行 step，方便不用翻日志也能知道跑到哪里。
- 生成一份报告到 `docs/testing/real-case-reports/<timestamp>/report.md`。
- 生成机器可读摘要到 `docs/testing/real-case-reports/<timestamp>/summary.json`，记录 final status、gate 轮数、Agent run 次数、revision 次数、acceptance decision、final acceptance decision、repair loop count、verification confidence、artifact presence 和原始 repo 是否干净。

它不会自动提交、不会 push、不会创建 PR，也不会删除原始 repo。`--cleanup-worktree` 只会在报告生成后删除本次创建的 worktree。
如果用 `Ctrl+C` 中断，脚本会尽量采集当前 evidence 并写出中断报告，报告里会保留后续 `status` / `resume` 命令。

示例：

```bash
cd dual-orchestrator

./scripts/run-real-case.sh \
  --repo /path/to/your/repo \
  --task-file /tmp/dual-task.md \
  --variant full
```

跑完后查看：

```bash
cat docs/testing/real-case-reports/<timestamp>/report.md
jq . docs/testing/real-case-reports/<timestamp>/summary.json
```

这个脚本适合验证编排主链路，不等于业务最终验收。真实交付前仍然应该人工查看 final report、Codex acceptance、Git diff 和 handoff。

如果结果是 `PASS-WARN`，先看 `summary.json` 里的 `verification_confidence` 和 `report.md` 里的 acceptance metrics。`MEDIUM` 通常表示主链路已经完成，但 Codex 仍然保留 build/runtime/self-test 证据不足等 notes；这类问题属于后续主线增强，不应该简单当成脚本失败。

推荐使用一个明确的临时 worktree 目录，方便跑完清理：

```bash
cd /path/to/repo
dualctl start \
  --branch ai/smoke-dualctl \
  --worktree-dir /tmp/dualctl-smoke \
  "smoke: update README one line"
```

启动后记下输出里的：

```text
Workstream: <workstream_id>
Workspace: <effective_worktree>
Branch: <branch>
```

确认或创建 session。通常 `resume` 会自动创建缺失 session；如果你想提前验证，也可以手工执行：

```bash
cd <effective_worktree>
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

推进 Claude intake 节点：

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
```

预期 active node 从 `intake.claude_brainstorm` 推进到 `intake.codex_product_review`。

继续推进 Codex review：

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
```

预期 active node 推进到 `intake.decision`。

再推进 decision 到人工卡点：

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
dualctl status --workdir <effective_worktree> <workstream_id>
```

预期：

- `Workflow status: WAITING_FOR_HUMAN` 或 `Workflow -> Status: WAITING_FOR_HUMAN`。
- `Open gates` 出现 `gate-...human_requirement_review...`。

批准卡点：

```bash
dualctl review approve --workdir <effective_worktree> <workstream_id>
dualctl status --workdir <effective_worktree> <workstream_id>
```

预期 active node 进入 `design.claude_design`。到这里就可以停止 smoke，不必继续跑完整生命周期。

预期：

- 当前 active node 被执行。
- Agent run metadata 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/run.json`。
- Agent prompt 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/prompts/`。
- stdout/stderr 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/outputs/`。
- 失败时 workflow current 会停在 `FAILED` 或 `BLOCKED`，现场不丢失。

本仓库已经验证过一次最小真实链路：

```text
start -> run(直到 human gate) -> review approve
```

验证结果：

- worktree 创建成功。
- Claude 和 Codex 都通过 acpx 完成一次真实调用。
- run outputs、prompts、events.jsonl、workflow/current.json 均落盘。
- human gate 能打开并被 `review approve` 推进。

## Worktree Cleanup

当前不会自动删除 worktree。

查看：

```bash
git -C /path/to/original/repo worktree list
```

删除 worktree：

```bash
git -C /path/to/original/repo worktree remove <effective_worktree>
```

删除分支：

```bash
git -C /path/to/original/repo branch -D <branch>
```

## Troubleshooting

### `acpx: command not found`

```bash
which acpx
acpx --help
```

如果没有输出，需要先安装或修正 PATH。

### `No acpx session found`

在 effective worktree 中创建 session：

```bash
cd <effective_worktree>
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

### `agent needs reconnect`

这是 acpx agent runtime 状态问题，常见原因：

- Claude Code / Codex session 断开。
- agent runtime 没启动。
- 登录态或额度状态异常。
- acpx session 绑定目录和当前 worktree 状态不一致。

处理后重新执行：

```bash
dualctl retry --workdir <effective_worktree> --note "agent reconnected" <workstream_id>
dualctl resume --workdir <effective_worktree> <workstream_id>
```

acpx adapter 会把这类错误标记为 recovery：

```text
failure_reason: RECOVERABLE_AGENT_RECONNECT
recoverable: true
```

如果 stdout 没有完整 terminal `dual-control` block 或没有 `[done] end_turn`，workflow 会停在 recovery。先恢复 agent session，再执行 `retry` / `resume`。如果 stdout 已经完整结束，adapter 会接受这次产物并记录 `acpx_recoverable_stderr_ignored=true`，让 workflow 继续路由。

### `.serena/` 出现在 worktree 里

如果 Codex 配置了 Serena MCP，`acpx codex` 可能会按当前 worktree 自动生成 `.serena/` 项目元数据。新创建的 worktree 会在本地 Git exclude 中忽略 `.serena/`，避免它污染 diff / PR 证据；`acpx` agent run 结束后也会回收本次运行中新启动的 Codex-context Serena MCP 进程。

这个处理只影响本次 worktree 和本次新启动的 Serena 进程，不会修改目标仓库 `.gitignore`，也不会禁用用户全局的 Serena 配置。

### `git worktree add` 报权限错误

如果在受限 sandbox 中运行，`git worktree add` 需要写原始仓库的 `.git` 目录和目标 worktree 目录。请在正常终端执行，或给当前环境授予创建 worktree 的权限。

### `git worktree add` 报分支目录冲突

如果已有 `ai` 分支，Git 可能无法再创建 `ai/<name>` 这种层级分支。解决方式是指定一个不冲突的分支名：

```bash
dualctl start --branch ai-smoke-dualctl "任务描述"
```

### `workflow ledger already initialized`

同一个 workstream 已经有 workflow ledger。不要重复 `start` 同一个账本；使用 `status` / `resume` 继续。

### `INVALID_DUAL_CONTROL`

Codex review / final acceptance 这类 decision 节点要求 Agent 输出一个严格的 contract block。缺失、格式错误或 decision 取值不合法时，workflow 会停在 `WAITING_FOR_RECOVERY`，并打开 `control.repair` gate；账本会记录 `control_repair_required` 事件。

````markdown
```dual-control
decision: PASS
summary: 一句话说明判断依据
```
````

当前校验规则：

- 只有行首的 ` ```dual-control` 围栏块会被解析，blockquote `> ```dual-control` 和嵌套 code fence 中的不会被匹配。
- 多个 `dual-control` block 时，只有最后一个（terminal block）生效，前面的视为 prose。
- Terminal block 之后不能有非空白内容。
- block 内必须是 `key: value` 行。
- `decision` 必须存在，并且属于当前节点允许的取值。
- Agent task 输出还必须满足当前 node 的 `ContractRegistry`：例如 `summary` 必须存在且非空。
- Agent task 校验通过后会生成相邻的 `*.contract.json` sidecar，后续 Context Package 会优先使用其中的结构化摘要，包括 `must_fix`、`open_risks`、`changed_files`、`verification_summary`。
- 只读最新的 inputRef 文件，不会回退读取更早的旧输出。

修正 Agent 输出后执行：

```bash
dualctl retry --workdir <effective_worktree> --note "fixed dual-control output" <workstream_id>
dualctl resume --workdir <effective_worktree> <workstream_id>
```

### `missing handler`

workflow definition 中的 `handler_type` 没有注册到 `HandlerRegistry`。这通常是开发期配置错误。

### `unsupported gate action`

当前 gate 没有对应 action edge。例如 workflow definition 没有为某个 gate 配置 `revise` 分支。

## Exit Codes

| Exit Code | Meaning |
| --- | --- |
| `0` | 命令完成 |
| `1` | 一般失败或 workflow failed |
| `2` | Picocli 参数错误 |

后续会继续细分 workspace、schema、transport、human gate 等错误码。

## Current Limits

- 仍是 CLI-first；API server 已有 status / timeline / action / scheduler tick / 默认关闭的 background scheduler，Web UI 仍是很薄的 operator panel。
- workflow 已支持 JSON 文件加载、条件表达式（`EdgeCondition`）和并行节点（fan-out + sequential execution）；schema 迁移暂不作为当前主线。
- context package 已有 token-level budget-controlled governance pipeline（6 profile，priority-based degradation，adaptive compression，per-profile config overrides），但真实 tokenizer 仍待替换。
- auto-retry 按 failure type 自动分类并指数退避重试，但还没有交互式修复向导。
- notification 还没接入 Java 版。
