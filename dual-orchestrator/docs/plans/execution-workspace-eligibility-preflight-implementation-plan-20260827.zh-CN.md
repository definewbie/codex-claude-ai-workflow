# Execution Workspace Eligibility Preflight Implementation Plan

日期：2026-08-27  
状态：IMPLEMENTED / DETERMINISTIC PASS / AWAITING INDEPENDENT DIFF REVIEW  
决策基线：`fddb356a`  
范围：real-case verification harness；不修改 Java runtime、workflow graph、provider adapter 或目标项目

## 1. 目标与主线决策

本 checkpoint 只关闭一个共性验证缺口：source checkout 的 baseline PASS 不能证明实际 execution worktree 也满足启动条件。

```text
Target phase: P2 verification reliability
Boundary owner: real-case harness
Source of truth strengthened: persisted workspace identity + workspace-bound preflight evidence
Drift path removed: dualctl start --run 在实际 worktree eligibility 尚未验证时启动首个业务 Agent
Explicit non-goals: target-specific 修复、workflow node、Java hook、parser/retry/provider 改动
Proof: fake-dualctl 时序测试 + helper fail-closed 测试 + full script suite
Remaining gap: 未配置 workspace check 的 legacy case 只兼容运行，不具备该证明；外部进程换窗不具备事务隔离
```

长期价值是阻止无效 real-case 消耗 provider 并污染收益判断；短期成本限定为一个 shell helper、runner 时序调整和既有脚本测试。Darwin 的 mtime 缺陷只作为触发样本，不进入本 checkpoint。

## 2. 最终时序

```text
validate inputs / Gate
-> fresh package
-> workflow validation + provider connectivity probe
-> freeze source HEAD/tree + check-script SHA
-> dualctl start（无 --run）
-> 从持久化 ledger 解析并验证 execution workspace
-> 在该 workspace 执行 target check
-> 写入 workspace-preflight evidence
-> 紧邻首次 run 前重验 persisted identity + HEAD/tree/tracked status digest
-> dualctl run（首次业务 execution）
-> dualctl autorun（既有跨 gate 驱动）
-> report / cleanup
```

`dualctl start` 只创建 worktree 和 ledger，不计 Agent run。provider connectivity probe 可在 start 前执行，但它只证明连接可用，不是业务 occurrence，也不能替代 workspace eligibility。

## 3. CLI 与配置契约

### 3.1 `run-real-case.sh`

新增可选参数：

```text
--workspace-preflight-check <executable-script>
```

规则：

1. 路径在 source preflight 阶段规范化；必须是普通可执行文件；记录 SHA-256。
2. check 以实际 execution workspace 为 cwd 执行；通过环境变量提供 `DUAL_WORKSTREAM_ID` 与 `DUAL_EXECUTION_WORKSPACE`，不传入目标项目业务参数。
3. exit `0` 表示 target-specific eligibility PASS；任何非零、signal、文件缺失或 evidence 不完整均为 `INVALID_EXECUTION_WORKSPACE_PREFLIGHT`。
4. helper 不解析 stdout/stderr，不识别 pytest/Maven/npm，也不允许白名单化测试失败；输出只作为 SHA-bound evidence 保存。
5. 参数未配置时保持 legacy case 可运行，但必须记录 `workspace_eligibility_status=not_configured`、`workspace_eligibility_proven=false`。该 case 不得被报告或 Gate 解释为已证明 execution-workspace baseline。

不新增自动推断“Gate 是否 baseline-sensitive”的文本逻辑。新 Gate 若把 target baseline 用作 trigger、PASS 或 INVALID 条件，operator 必须显式提供该参数；这是 verification procedure 的声明责任，不把 Markdown 变成第二个控制协议。

### 3.2 `run-public-benchmark.sh`

新增同名可选参数并原样透传。生成的三类 Gate 文本必须显式区分：

- 参数已配置：声明 execution workspace check path/SHA 由 runner 固化并在首次业务 run 前强制；
- 参数未配置：声明 `workspace eligibility proof not configured`，不得声称 source baseline 等同 execution workspace。

现有 benchmark case 不虚构 target check；只有 case/suite 明确提供真实 target eligibility script 时才启用。

## 4. Persisted Workspace Identity

### 4.1 启动输出只作 locator/cross-check

runner 从 `start.log` 只接受并交叉核对 `Workstream:` id。它不再把 `Workspace:` 文本直接赋为 authoritative `WORKSPACE_DIR`。

由于 runner 不向 `dualctl start` 传 `--worktree-dir`，它先按 `StartCommand.java:77-79` 的唯一默认布局构造候选目录：

```text
<source-git-root-parent>/<source-git-root-name>-agent-runs/<workstream-id>
```

候选目录只是定位 ledger 的入口，不是 SoT。若 Java 侧布局未来漂移，候选目录下找不到合法 ledger，必须 fail-visible 为 INVALID，不能扫描其他目录兜底。随后必须逐级读取并验证：

```text
<candidate>/.agent/workstreams/<id>/workstream.json
  .workstreamId == start.log id
  .repository.gitRoot == frozen source git root
  .primaryWorkspaceId != blank

<candidate>/.agent/workstreams/<id>/workspaces/<primaryWorkspaceId>.json
  .workstreamId == start.log id
  .workspaceId == primaryWorkspaceId
  .originalWorkdir == frozen source git root
  .effectiveWorkdir == canonical candidate path
```

全部成立后，`workspace.json.effectiveWorkdir` 才赋给 `WORKSPACE_DIR`，并据此生成 `WS_DIR`。任一文件缺失、JSON 解析失败、id/path 不一致均在首次 `dualctl run` 前 INVALID。禁止扫描 `*-agent-runs` 猜最新目录，也禁止把 operator registry 或 stdout 当 workflow SoT。

### 4.2 Source identity

`validate_inputs` 将 `REPO` 收敛为 `git rev-parse --show-toplevel` 的 canonical root，避免子目录或符号路径让默认 worktree locator 漂移。source preflight 在 start 前冻结：

- source HEAD；
- source tree；
- source tracked status digest；
- check script canonical path 与 SHA-256。

source tracked dirty 继续按具体 Gate 约束处理；execution workspace 在 check 前后必须 tracked clean。

## 5. Helper 单一职责

新增 `scripts/lib/real-case-workspace-preflight.sh`。它既可被 runner source，也可由 operator 直接执行，提供两个动作：

```text
capture  # 验证 ledger/workspace identity、执行 check、原子写 evidence
verify   # 首次 run 前重读 ledger 与 workspace facts，并核对 terminal digest
```

### 5.1 `capture`

输入：source root、candidate workspace、workstream id、expected HEAD/tree、check script、evidence dir。

固定顺序：

1. 按 §4 解析 persisted workspace identity；
2. 验证 workspace 是 Git worktree、HEAD/tree 与 source frozen identity 一致；
3. 记录 pre-check `git status --porcelain=v1 --untracked-files=no`，非空即拒绝；
4. 在 workspace cwd 执行 check，stdout/stderr 写入独立文件；
5. 记录 exit code、stdout/stderr SHA；
6. 记录 post-check tracked status，非空即拒绝；
7. 记录 ignored/untracked status 作为观察证据，但不据其内容猜业务语义；
8. 生成 terminal digest，原子写 `workspace-preflight.json`。

evidence 至少包含：schemaVersion、status/reason、workstream id、primary workspace id、canonical workspace path、source/workspace HEAD/tree、check path/SHA、started/finished time、exit、stdout/stderr path+SHA、pre/post tracked status+SHA、post untracked status+SHA、terminal digest。

失败也必须尽力写 typed status/reason；只有 evidence 成功落盘且 status=`passed` 才返回 0。

### 5.2 `verify`

紧邻首次 `dualctl run` 调用前：

1. 读取 capture evidence；
2. 重读 `workstream.json` 与 primary `workspace.json`；
3. 重算 canonical path、HEAD、tree 和 tracked status digest；
4. 重算 terminal digest并逐字段核对；
5. 不一致、evidence 缺失或 status 非 passed即拒绝。

它缩小 preflight PASS 到 run 的 TOCTOU，但不宣称对外部进程写入提供事务隔离。`verify` 返回后到 Java 获取 workstream lock 前仍有窄窗口；该 residual 由单 operator、单 workstream 和不共享 execution workspace 的运行纪律承担。

## 6. Runner 变更

### 6.1 `start_workstream`

- 删除 `start` 参数中的 `--run`；
- start 失败保持现有 report/cleanup 路径；
- 成功后只解析 workstream id，再通过 helper 验证 persisted workspace identity；
- 未产生 Agent run 时不调用 session reap，不把“没有 session”误报为 closure failure。

### 6.2 `run_workspace_preflight`

- 配置 check 时调用 `capture`；失败设置 `FINAL_STATUS=INVALID_EXECUTION_WORKSPACE_PREFLIGHT`；
- 未配置时写最小 `not_configured` evidence，不执行 target command；
- INVALID 时 collect/status/report/optional cleanup 后退出，业务 `run/autorun` 调用必须为 0。

### 6.3 `run_initial_segment`

- 配置 check 时先调用 `verify`；未配置时仍重验 persisted workspace id/path，防止 start 后错误定位；
- 调用 `dualctl run --workdir "$WORKSPACE_DIR" --max-steps "$MAX_STEPS" "$WORKSTREAM_ID"`；
- 其退出处理复用现有 start/autorun stop-loss 语义：记录状态、关闭实际存在的 scoped sessions、收集证据，不自动 retry/resume；
- 成功到达 gate/terminal 后才进入既有 `run_autorun`。

`main` 的新顺序固定为：

```text
source preflight -> trap -> start -> workspace preflight -> initial run -> autorun
```

## 7. Evidence 与 Report

`preflight.json` 增加 source/check 配置事实，但不冒充 execution 结果：

- sourceHead/sourceTree/sourceTrackedStatusSha256；
- workspacePreflightConfigured；
- workspacePreflightCheckPath/Sha256。

实际结果只写入 `workspace-preflight.json`，并在 `summary.json`/`report.md` 投影：

- `workspace_eligibility_status`；
- `workspace_eligibility_reason`；
- `workspace_eligibility_proven`；
- persisted workspace id/path；
- terminal digest 与 evidence path。

projection 只读 helper evidence，不从 console 或测试摘要重推断。未配置必须显示 `not_configured / false`，不能省略或显示 PASS。

## 8. 文件清单

Production harness：

1. `scripts/run-real-case.sh`
2. `scripts/lib/real-case-workspace-preflight.sh`（新增）
3. `scripts/run-public-benchmark.sh`

Tests：

4. `scripts/run-real-case.test.sh`
5. `scripts/lib/real-case-workspace-preflight.test.sh`（新增）
6. `scripts/run-public-benchmark.test.sh`

Documentation：

7. `docs/testing/real-case-benchmarks/README.md`
8. `docs/plans/__ACTIVE__real-case-driven-orchestrator-simplification.zh-CN.md`

不修改 `scripts/lib/verification-gate.sh` 的九字段 parser。它只验证 Gate 基本完整性；workspace check 是否配置及其结果由 runner 参数和 typed evidence 负责。

## 9. Cannot-Bypass Tests

### T1 Helper identity

- persisted workstream id、primary workspace id、effective path、original root 任一不符 -> capture non-zero；
- stdout 声称另一个 workspace 不得改变 persisted result；
- 缺失/非法 JSON 不得 fallback 到目录扫描或 registry。
- source root、candidate workspace 与 check script 路径包含空格时仍须完整解析、执行和写入 evidence。

### T2 Check execution

- missing/non-executable/non-zero/signal -> failed evidence；
- check 的 cwd 必须是 persisted effective workspace；
- stdout/stderr 只存档和 hash，出现 `PASS` 文本但 exit 非零仍失败；exit 0 即使无 summary 文本也通过。

### T3 Git boundary

- HEAD/tree mismatch、pre/post tracked dirty -> INVALID；
- ignored/untracked 变化只记录，不阻断；
- source checkout PASS、execution workspace check FAIL -> run/autorun 计数均为 0。

### T4 Sequencing

- fake dualctl 断言 `start` 无 `--run`；
- `start` 成功但 persisted identity 无法解析时，必须在任何 `run` 前以
  `INVALID_EXECUTION_WORKSPACE_IDENTITY` 失败并留下报告；
- PASS 顺序恰为 `start -> capture -> verify -> run -> autorun`；
- capture/verify failure 时 `run=0`、`autorun=0`；
- initial run failure 不进入 autorun，并走现有 quiesce/report 路径。

### T5 TOCTOU

- capture 后修改 tracked file、HEAD、tree、workspace document、primary workspace id 或 stdout/stderr
  evidence；verify 必须拒绝且业务调用为 0。

### T6 Legacy compatibility

- 未配置 check 的既有 case仍执行 `start(no-run) -> run -> autorun`；
- evidence 与 report 必须明确 `not_configured/false`；不得出现 workspace PASS。

### T7 Public benchmark sibling

- 参数完整透传；
- 配置/未配置两种 Gate 文案与 runner evidence 口径一致；
- existing validation-target、runner-kind、provider probe 参数不退化。

## 10. 验证顺序与提交边界

1. focused helper test；
2. `run-real-case.test.sh`；
3. `run-public-benchmark.test.sh`；
4. 现有 shell script suite；
5. `git diff --check`；
6. 独立代码级 diff review；
7. 仅在 review PASS 后选择性提交该 harness checkpoint。

本 checkpoint 不要求 Maven reactor：没有 Java 变更。若 diff 意外触及 Java、workflow JSON 或 schema，立即停止并回到架构评审。

## 11. 成熟度与后续

实施和 deterministic tests PASS 后：

- execution-workspace eligibility capability：`ENFORCED WHEN CONFIGURED / NOT PROVEN`；
- legacy no-check case：`COMPATIBLE / WORKSPACE PROOF NOT CONFIGURED`；
- Darwin target hermeticity：仍属于 target owner，`FAILED / NOT FIXED BY DUAL`；
- small-profile `9 -> 8`：仍为 `NOT PROVEN`。

不会立即制造第二个 Darwin 样本。下一次自然且明确提供 workspace check 的 real-case 完整完成后，才评估将该 verification procedure 标为 `PROVEN`。

## 12. Implementation Result

实施严格保持 harness-only：新增 workspace helper 与 focused test；`run-real-case.sh` 改为
`start(no-run) -> capture -> verify -> run -> autorun`；`run-public-benchmark.sh` 只透传参数并生成诚实的配置/未配置
Gate 文案；README 与 active plan 同步。Java、workflow JSON、schema、provider adapter 均无变更。

实施中 sibling sweep 发现 trap 原先隐含假设“收到中断时一定已有 provider execution”。两阶段时序使 start/check 期间也可能
中断，因此新增 `BUSINESS_RUN_STARTED` 边界：首次 `dualctl run` 前中断只收集证据，不调用 stale recovery 或 session reap；
首次业务 run 开始后继续复用既有 quiescent closure。对应 cannot-bypass test 已覆盖，未新增 lifecycle state。

Deterministic verification：

- `real-case-workspace-preflight.test.sh`：PASS，含空格路径、持久化 identity、exit-only、pre/post tracked dirty、ignored
  允许、check SHA、stdout/stderr evidence 篡改、ledger/HEAD/tree/status TOCTOU；
- `run-real-case.test.sh`：PASS，含 start 无 `--run`、start 后 identity 解析失败 fail-closed、capture/verify failure 时 run/autorun=0、legacy
  `not_configured/false`、pre-run interrupt 不伪造 recovery；
- `run-public-benchmark.test.sh`：PASS，含参数原样透传与三类 Gate 文案；
- 全部 8 个 `scripts/**/*.test.sh`：PASS；
- shell syntax check：PASS；
- `git diff --check`：PASS。

当前只允许独立代码级 diff review。未调用 provider、未创建或恢复 workstream；成熟度保持 configured capability
`ENFORCED / NOT PROVEN`，legacy `WORKSPACE PROOF NOT CONFIGURED`。
