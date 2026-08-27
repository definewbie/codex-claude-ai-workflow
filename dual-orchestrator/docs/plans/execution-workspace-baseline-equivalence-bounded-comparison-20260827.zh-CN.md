# Execution Workspace Baseline Equivalence Bounded Comparison

日期：2026-08-27  
状态：INDEPENDENT REVIEW PASS / DECISION APPROVED / IMPLEMENTATION NOT STARTED  
范围：real-case verification harness；不修改 workflow graph、Java runtime、provider adapter 或目标项目业务代码

## 1. 决策摘要

推荐唯一候选：**Actual Execution Workspace Eligibility Preflight**。

真实验证必须拆成两个阶段：

```text
source checkout identity / build / provider connectivity preflight
-> dualctl start（不带 --run，只创建真实 worktree + ledger）
-> 在该实际 execution workspace 内运行 target eligibility checks
-> 记录 workspace-bound evidence
-> PASS 后才允许第一次 dualctl run / 业务 Agent 调用
```

这不是 Darwin 专用修复。它关闭的是一个通用验证边界：**在 source checkout 上通过的 baseline，不等于在实际执行 worktree 上也成立。**

Darwin 的 `test_snapshot.py` mtime 选择只是本次触发证据。主方案不修改该测试，也不向 Dual 增加 snapshot、pytest 或 Darwin 语义。

## 2. Captured Failure

Case：`ws-20260827T051220Z-2U4R`。

已机械证明：

1. source checkout 在冻结 HEAD `1ad41527` 上执行冻结 runnable-suite 命令得到 `234 passed / 0 failed`；preflight 记录真实。
2. 同一 HEAD 的 fresh Dual worktree 在实现后得到 `238 passed / 2 failed`；新增 focused tests 为 6 条，所以无缺口时应为 240 passed。
3. 两项失败均来自 `tests/test_snapshot.py`。
4. 该测试用 `st_mtime` 选择 `.darwin/runs/run-*` 中“最新”的完整历史工件。
5. Git worktree checkout 重写 tracked fixture 的 mtime；source checkout 选中 `run-d5dd9e4c`，fresh worktree 选中旧 schema 的 `run-fe58b2d1`。
6. 在 fresh worktree 内恢复三个任务文件到 HEAD 后，两项失败仍存在；因此不是 `list --json` 实现导致。
7. Acceptance reviewer 正确执行批准的 contract，返回 typed `REJECT`；workflow 创建 `iter-002` 和第 9 个 Agent run，Gate 按 stop-loss 终止。

结果：small profile 的 A6 缺席和 A5 direct-human path 已观察，但稳定 `9 -> 8` 且质量不退化仍为 `NOT PROVEN`。

## 3. 共性问题

问题不局限于 mtime。source checkout 与 fresh execution workspace 可能因下列事实产生不同语义：

| 类别 | 常见差异 | 仅检查 HEAD/tree 能否发现 |
| --- | --- | --- |
| checkout metadata | mtime、inode、目录枚举顺序、file mode | 不能 |
| ignored/untracked state | 本地 fixture、cache、生成工件、数据库文件 | 不能 |
| repository extensions | submodule、LFS、symlink、sparse checkout | 不完整 |
| environment | cwd、权限、HOME、runtime、PATH、locale、timezone | 不能 |
| test order/state | 先前测试写入 ignored 目录、共享 singleton、端口/容器 | 不能 |
| tool startup side effects | `.serena/`、agent cache、session bootstrap | 不能 |

因此 `source HEAD/tree clean + source baseline PASS` 只能证明 source checkout；不能成为 execution workspace eligibility 的 SoT。

## 4. Owner 与不变量

### 4.1 Owner

- Source repository identity：Git source checkout。
- Execution workspace identity：`Workspace` / persisted workstream ledger。
- Target eligibility command 与成功条件：本次 Verification Gate/operator 提供的 target-specific check script。
- Eligibility evidence owner：real-case verification harness，不是 workflow ledger、LLM、acceptance reviewer 或 target application。
- Product test hermeticity：目标项目自身测试套件；Dual 只 fail-closed，不重写或豁免其测试。

### 4.2 End-to-end invariant

- Declared by：Verification Gate 声明 target eligibility checks 与 PASS 条件。
- Proven by：在 persisted workspace path 内执行 check script，记录 source/workspace identity、命令或 script SHA、exit、stdout/stderr SHA、执行前后 Git 状态。
- Consumed by：real-case harness 的 first-provider eligibility guard。
- Enforced before：第一次 `dualctl run`、`autorun` 或业务 Agent execution。独立的 provider connectivity probe 可以提前执行，但它只能证明连通性，不能创建或替代业务 Agent occurrence。
- Failure route：non-zero、workspace identity 不符、tracked dirty、evidence 缺失或执行异常 -> `INVALID`，不得调用 provider。
- Cannot-bypass test：fake dualctl 证明 workspace preflight 未 PASS 时 `run/autorun` 调用次数为 0；PASS 时顺序严格为 `start(no --run) -> workspace preflight -> run`。
- Residual path：用户可绕开 verification harness 直接调用 `dualctl run`，但该运行不具备 Gate evidence，必须判 `INVALID`，不得冒充受控 real-case。
- TOCTOU residual：eligibility PASS 与首次 `dualctl run` 之间仍存在外部进程改写 workspace 的窄窗口。runner 必须在 run 前重读 persisted workspace identity，并重算 HEAD/tree/tracked-status digest 与 eligibility terminal digest；不一致即 `INVALID`。该二次核对缩小但不声称事务性消除外部换窗，残余由单 operator/workstream 纪律承担。

## 5. 候选比较

### Candidate 0：直接在 source checkout 执行真实任务

优点：baseline 与执行路径相同，不存在两个 checkout 的差异。

缺点：破坏 disposable worktree、source-clean 与失败隔离纪律；Agent 和测试副作用会直接污染用户工作目录，且无法安全区分本轮变化与既有本地状态。

判定：**拒绝。不能以消灭 workspace 差异为由删除执行隔离。**

### Candidate A：只修触发项目的非确定性测试

做法：把 Darwin `test_snapshot.py` 从 `st_mtime` 选择改为固定 fixture identity 或每测自建 fixture。

优点：正确修复 Darwin 自身测试债；成本低。

缺点：只覆盖一个已知形态；不能发现 ignored state、submodule、权限、locale、cache、工具启动副作用等同类差异。

判定：**目标项目应独立修复，但拒绝作为 Dual 主方案。**

### Candidate B：在 source checkout 旁创建临时 shadow worktree 预跑

优点：能发现大部分 checkout metadata 与 ignored-state 差异；不需要先创建 workstream。

缺点：shadow worktree 不是实际 execution workspace；路径、branch、exclude、tool bootstrap、post-start ledger side effects 仍可能不同。它把“source vs execution”改成“shadow vs execution”，仍存在等价性假设。

判定：**拒绝。可用于诊断，不能作为 authoritative eligibility proof。**

### Candidate C：实际 execution workspace 两阶段 preflight

做法：复用现有 `dualctl start` 不带 `--run` 的能力，先创建真实 worktree/ledger；在该 workspace 运行 Gate 指定 checks；PASS 后才执行首次 `dualctl run`。

优点：

- 直接验证将被 Agent 修改和最终验收的同一 workspace；
- 跨语言、跨构建工具，不需要 Dual 理解 pytest/Maven/npm；
- 不修改 workflow graph 或业务 artifact contract；
- 失败发生在首次 provider 调用前，不浪费 token；
- 可以复用到 manual-human 与 autorun real-case。

成本：runner sequencing 与 evidence 需要收口；target check script 仍由 Gate/operator 提供。

判定：**推荐。**

### Candidate D：新增 workflow preflight node / Java hook

优点：可由 workflow 强制。

缺点：把 verification procedure 混入 production workflow；需要新 handler/config/contract/状态；target command 执行面和安全边界扩大；普通用户运行也会携带 benchmark 语义。

判定：**拒绝，过度设计且 owner 错位。**

### Candidate E：比较 source/workspace 文件 manifest

优点：便宜，可解释 tracked/untracked 差异。

缺点：hash manifest 不表达 mtime、环境、权限、test order 或运行语义；全量 ignored inventory 还可能巨大或泄漏敏感路径。

判定：**仅作诊断 evidence，不得替代实际 baseline command。**

## 6. 推荐设计

### 6.1 两类 preflight 分离

`Source Preflight`：

- Dual/target HEAD、tree、clean；
- task/workflow hash；
- fresh package 与 JAR identity；
- provider connectivity probe；
- target check script path + SHA。

`Execution Workspace Eligibility`：

- 必须在 `dualctl start` 产生的 persisted workspace path 内执行；
- workspace HEAD/tree 必须与 frozen source 一致；
- 执行前 tracked status 必须 clean；
- 执行 Gate 指定的 target check script；
- check script 必须自行机械验证 target-specific 条件并以 exit code 表达结果；helper 只接受 exit=0，不解析 pytest/npm/Maven 文本或自然语言 summary；
- 执行后 tracked status 必须仍 clean；
- ignored/generated state 允许变化，因为后续 Agent 使用的正是这个 post-check workspace；变化只记录，不由 Dual猜业务含义；
- evidence 必须绑定 workstream id、workspace absolute path、HEAD/tree、script SHA 与时间区间。

### 6.2 最小实现边界

建议一个原子 checkpoint，只修改 verification harness：

1. `scripts/run-real-case.sh`
   - `start_workstream` 改为 `dualctl start`，明确禁止 `--run`；
   - stdout 只用于交叉核对 workstream id，不作为 workspace path SoT；
   - 从 ledger 的 `workstream.json.primaryWorkspaceId` 定位 `workspaces/<workspaceId>.json`，以其中 `effectiveWorkdir` 作为 authoritative execution workspace；禁止扫描目录或从 stdout 猜路径；
   - workspace eligibility PASS 后新增 `run_initial_segment`，再调用 `dualctl run`；
   - `run_initial_segment` 前重读 persisted workspace identity 并重算 HEAD/tree/tracked-status digest；与 eligibility evidence 不一致时 fail-closed；
   - 无 eligibility 配置的旧 case 保持既有行为，但凡 Gate 声称 target baseline 是 PASS/INVALID 条件，新 Gate 必须提供 check script。
2. `scripts/lib/real-case-workspace-preflight.sh`（小型 shell helper，不是新 module/framework）
   - 接收 workspace、expected HEAD/tree、check script、evidence dir；
   - fail-closed 执行并写 workspace-bound evidence；
   - 不解析 pytest/npm/Maven 文本，不内置 target 规则。
3. `scripts/run-real-case.test.sh` 与新增 focused helper test
   - fake dualctl 证明调用顺序；
   - preflight non-zero、identity mismatch、tracked dirty、missing evidence 时业务 `run/autorun` 调用为 0；provider connectivity probe 不计为业务调用；
   - PASS 时首次 `run` 恰好一次；
   - `start` 命令不可含 `--run`。
4. Verification Gate 模板/discipline 文档
   - 新 Gate 若以 target baseline 为 trigger/PASS/INVALID 条件，必须声明 execution-workspace check script 与 evidence；
   - source-checkout baseline 只能标作 source evidence，不能替代 execution eligibility。
5. `scripts/run-public-benchmark.sh`
   - 核对其委托给 `run-real-case.sh` 的参数、环境变量与 lifecycle 契约在两阶段时序下保持成立；
   - 同步其内嵌 Verification Gate 模板：以 target baseline 为条件的 benchmark 必须提供 execution-workspace check script；未提供者必须明确标记 legacy/no-workspace-proof，不得声称具备该证明。

Manual-human Gate 不要求新增 `--manual-gates` 或自动审批能力。operator 仍可用现有命令：

```text
dualctl start (no --run)
-> shared workspace-preflight helper
-> verify evidence
-> dualctl run
-> 后续 human gate 继续由用户决定
```

### 6.3 明确不做

- 不修改 Darwin `test_snapshot.py` 作为本 checkpoint 的主修复；
- 不把 pytest/npm/Maven 命令写死到 Dual；
- 不新增 workflow node、handler、registry、schema 或 event store；
- 不让 LLM 判断 baseline 是否“看起来可接受”；
- 不用 source/workspace manifest 相等替代真实命令；
- 不自动豁免 fresh worktree 中出现的历史失败；
- 不恢复或重跑 `ws-20260827T051220Z-2U4R`；
- 不启动第二个 provider 样本验证此设计。

## 7. Tests

必须覆盖：

1. `start` 不带 `--run`，且 eligibility 前无 `run/autorun`；
2. actual workspace path 与 ledger/start 输出绑定；
3. HEAD/tree mismatch -> INVALID；
4. pre-check tracked dirty -> INVALID；
5. target script missing/non-executable/non-zero -> INVALID；
6. post-check tracked dirty -> INVALID；
7. source checkout PASS、execution workspace FAIL -> INVALID 且业务 Agent calls=0；
8. execution workspace PASS -> 首次 `dualctl run` 恰一次；
9. evidence 包含 workstream/workspace/head/tree/script SHA/exit/stdout+stderr SHA/pre+post status；
10. eligibility PASS 后、首次 run 前 identity/status digest 漂移 -> INVALID 且业务 Agent calls=0；
11. existing no-workspace-check cases 的兼容边界明确，不得被误标为具备 execution-workspace proof；
12. `run-public-benchmark.sh` 委托仍透传既有参数/环境，且生成的 baseline-sensitive Gate 不得遗漏 execution-workspace proof 状态。

## 8. 成熟度与退出条件

当前：

- 共性缺口：`VALIDATED`（captured real-case + 同 HEAD 双 workspace 对照）；
- execution-workspace eligibility：`NOT MODELED / NOT ENFORCED`；
- Darwin target test hermeticity：`FAILED`，但属于 target owner；
- small-profile `9 -> 8`：`NOT PROVEN`。

实施后只有 deterministic cannot-bypass tests PASS 时，verification harness 边界可标 `ENFORCED / NOT PROVEN`。无需立即运行 provider。未来任一自然 real-case 在实际 workspace preflight PASS 后完整完成，方可将该 verification procedure 标 `PROVEN`。

如果独立评审证明现有 runner 无法在不扩大 workflow/runtime 的情况下复用 `start(no --run)`，则停止实施，不退回 target-only patch；重新比较一个独立 verification wrapper 与保持人工 procedure 的成本。

## 9. 推荐决策

`PROCEED_WITH_GUARDS`：独立评审已 PASS，P2-1 至 P2-4 guards 已写回。下一步只授权制定 harness-only implementation plan 并接受独立评审；本文件不授权编码、provider probe、real-case、target test 修复或 workstream resume。
