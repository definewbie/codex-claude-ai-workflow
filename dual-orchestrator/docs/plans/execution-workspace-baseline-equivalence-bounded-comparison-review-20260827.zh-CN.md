# Review: Execution Workspace Baseline Equivalence Bounded Comparison

日期：2026-08-27
评审性质：独立、只读、代码级；未修改 production/scripts/tests/Gate/target；未调用 provider
触发证据基线：`ws-20260827T051220Z-2U4R`（captured ledger 只读复算）

## Findings

**P0：无。P1：无。**

### P2-1 §4.2 Residual 未声明 preflight-PASS→run 之间的换窗 TOCTOU 残余
evidence 绑定的是 preflight 时刻的 workspace 事实；`preflight PASS` 与 `dualctl run` 之间外部
干预（人工改动、另一进程写 workspace）不被现有 evidence 捕获。单 operator 纪律下可接受为声明过的
残余；建议在 §4.2 Residual path 增加一句（或在 evidence 中加 run 起始时的 status 快照 hash）。

### P2-2 §6.2.1 "从 start 输出解析并核对 workstream/workspace" 应改为读持久化 workspace 文档
CLI stdout 不是 SoT 且格式可漂移；workstream 目录下的 `workspace.json`（由
`FileLedgerStore.initializeWorkstream` 写入）是 authoritative workspace path 事实。helper 应读
持久化文档并与 start 输出的 workstream id 交叉核对，而非解析 stdout。

### P2-3 sibling sweep 遗漏 `run-public-benchmark.sh` 委托路径
`run-public-benchmark.sh:7` 自述"delegates lifecycle to run-real-case.sh"——修改
`start_workstream` 时序会自动影响 benchmark 委托。兼容路径保证不破坏，但实施计划必须：(a) 核对
委托的 env/args 契约在时序变更后仍成立；(b) benchmark 的 Gate 模板（`:217-223` 内嵌 preflight
表述）按新纪律同步——凡以 target baseline 为条件的 benchmark case 亦须提供 check script 或显式
声明 legacy 无 workspace proof。

### P2-4（轻微）Candidate 清单可补一个显式拒绝项
"直接在 source checkout 内执行（不建 worktree）"未列——它破坏 disposable/clean 纪律与 target
isolation，正确归属为 REJECT；补一行使完备性封闭。

## 代码/脚本事实核实（全部吻合）

| 声明 | 实测 |
|---|---|
| `dualctl start`（无 `--run`）只创建 worktree+ledger | `StartCommand.java:53-54`（flag 默认 false）、`:131`（无 --run 打印 resume 提示即返回）、`:139`（--run 才进 loop）✓ |
| worktree 真实创建并持久化 | `GitWorkspaceService.createWorktree:28-33`（`git worktree add` + ledger exclude）✓ |
| 现行 runner 无 eligibility 窗口 | `run-real-case.sh:570` `start_workstream` 直接 `start --run`——创建即首跑，缺口真实 ✓ |
| 现行 preflight 无 workspace 逻辑 | `real-case-preflight.sh` 中 worktree/workspace/pytest/baseline 零命中——execution eligibility NOT MODELED 属实 ✓ |
| captured 事实 | workstream ledger 复算：9 runs（8 completed+1 failed）、acceptance `REJECT`、iter-001+iter-002、`WAITING_FOR_RECOVERY`、active=iter-002 的 failed implement node——与 result.md 逐项一致 ✓ |
| smoke sibling 不应纳入 | `smoke-test.sh:759`、`semantic-boundary-smoke.sh:226` 的 `start --run` 均为 fake-dualctl/fixture 的 deterministic smoke——正确排除，未为形式统一扩大 scope ✓ |

## 十四问结论

1. **根因定义正确**——source checkout baseline ≠ execution workspace eligibility 是通用边界；
   Darwin mtime 只是实例。result.md 的同 HEAD 双 workspace 对照（234/0 vs 238+6/2、还原实现文件
   后失败仍在）机械支撑一般性；本方案不修 Darwin 测试、不给 Dual 加 snapshot/pytest 语义。
2. **Owner 正确**——harness 持有 eligibility evidence：ledger 只管 workflow 事实、Java runtime
   不应执行 target 命令（Candidate D 拒绝正确）、LLM/acceptance 是事后且花 provider 调用（本
   case 已证明 acceptance 会正确 REJECT 但那已是第 9 次调用后）、target 自管 hermeticity。
3. **A-E 完整**（P2-4 一个显式拒绝项可补）；无更便宜且同样通用的遗漏候选。
4. **推荐顺序真实可执行**——所需原语全部已在 production（start no-run / run / worktree）；
   fail-closed 由 harness 时序 + fake-dualctl 调用计数测试保证（§7.1/7）。
5. **probe vs 业务 occurrence 区分准确**——probe 只证连通性、不计业务调用、不可替代 occurrence；
   测试显式断言（§7）。
6. **exit-code-only 边界正确**——helper 不解析 pytest/npm/Maven 文本、无内置 target 规则；
   target 语义留在 SHA 绑定的 check script 内；跨语言最小面。
7. **ignored/generated 处理正确**——tracked dirty（pre 或 post）→ INVALID；ignored 变化允许并
   记录（post-check workspace 正是 Agent 将使用的状态）；不猜业务含义。Darwin mtime 形态正是
   被"check script 在真实 workspace 重跑"捕获，而非被 status 检查捕获——设计确实覆盖该类。
8. **evidence binding 足够**——workstream id/workspace path/HEAD/tree/script SHA/stdout+stderr
   SHA/pre+post status/时间区间；缺 P2-1 的换窗残余声明。
9. **manual 与 runner 复用同一 helper 成立**——均用既有命令（start/run/review），无
   `--manual-gates`、无自动审批、无 workflow node。
10. **旧 case 兼容诚实**——无 check 的 case 保持行为但不得标记 execution-workspace proof
    （§6.2.1 末条 + §7.10）；Gate discipline 文档要求新 Gate 声明 baseline 条件时必须提供 check
    script。
11. **cannot-bypass 足够**——fake dualctl 对 `run/autorun` 调用次数断言 0/PASS 时恰一次、start
    无 `--run`——直接观测调用序，正确。
12. **最小范围属实**——仅 runner + 新 shell helper + 测试 + 文档；无 Java/workflow/schema 依赖
    （所需 CLI 能力全部现存）；一处改进见 P2-2。
13. **安全面可接受**——check script 是 Gate/operator 提供并 SHA 绑定，与既有 baseline 测试执行
    同信任级（pytest 本就执行任意代码）；无新增信任边界；路径绑定持久化 ledger；TOCTOU 残余见
    P2-1。
14. **解决共性而非局部补丁**——把 baseline 验证移入真实执行 workspace，任何 checkout
    metadata/ignored/env/bootstrap 语义差异都会在正确位置显形为 check 失败；个别成因（target
    hermeticity 债）仍归 target（Candidate A 定位正确）。非另一层局部补丁。

## End-to-end invariant（核实后）

| 维度 | 结论 |
|---|---|
| Declared by | Verification Gate 声明 eligibility checks 与 PASS 条件（check script + SHA） |
| Proven by | persisted workspace path 内执行 check，绑定 workstream/workspace/HEAD/tree/script SHA/exit/输出 SHA/pre+post status |
| Consumed by | real-case harness 的 first-provider eligibility guard（runner 与 manual 共用 helper） |
| Enforced before | 首次 `dualctl run`/`autorun`/业务 Agent execution；probe 提前但不替代 |
| Failure route | non-zero/identity 不符/tracked dirty/evidence 缺失 → INVALID，provider 调用 0 |
| Cannot-bypass test | fake dualctl 调用序与计数断言（§7.1/7/8） |
| Residual path | 绕开 harness 直跑 `dualctl run` → 无 Gate evidence → 判 INVALID；换窗 TOCTOU（P2-1 待声明）；旧 case 无 workspace proof 的显式降级表述 |

## 判定

- **Boundary Review: PASS**
- **推荐：`PROCEED_WITH_GUARDS`** —— guards 即上述 P2-1/2/3/4 的文字 delta，直接可写回计划：
  1. §4.2 Residual 增加换窗 TOCTOU 一句（或 evidence 加 run 起 status hash）；
  2. §6.2.1 改为读持久化 `workspace.json` 而非解析 stdout；
  3. §6.2 增补 benchmark 委托路径核对（`run-public-benchmark.sh` 委托契约 + Gate 模板同步）；
  4. Candidate 清单补"source checkout 直接执行"显式 REJECT 一行。
- **Implementation READY：YES（作为决策文档→下一份 harness-only implementation plan 的输入）**
- **过度设计：无**——单一 shell helper，无 module/framework；Candidate D 的拒绝理由（owner 错位）
  与本仓库边界纪律一致。
- **不新增 module/framework：确认。**

## 下一步精确授权范围

1. 将 P2-1~P2-4 文字 delta 写回本比较文档，选择性提交（连同本评审）；
2. 制定 harness-only implementation plan（`run-real-case.sh` 时序 + `real-case-workspace-preflight.sh`
   helper + fake-dualctl 测试 + Gate discipline 文档 + benchmark 委托核对），独立评审后实施；
3. 全程不调用 provider、不 resume `ws-20260827T051220Z-2U4R`、不创建第二个样本、不修 Darwin
   `test_snapshot.py`（该修复属 target owner，可独立进行但不入 Dual checkpoint）。
