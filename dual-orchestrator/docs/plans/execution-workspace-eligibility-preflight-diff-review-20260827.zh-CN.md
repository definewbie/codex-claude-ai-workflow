# Diff Review: Execution Workspace Eligibility Preflight Checkpoint

日期：2026-08-27
评审性质：独立、只读、代码级；未修改任何文件；未调用 provider；未创建/恢复 workstream
基线核对：HEAD `fddb356a`（= 决策基线）；`git diff --check` clean；**零 Java 变更**（git status
仅 scripts + docs）

## Findings

**P0：无。P1：无。**

### P2-1（知会）verify 以 evidence 自身字段为重解析输入的伪造残余
`real_case_workspace_preflight_verify`（helper `:292-316`）从 evidence 读取
`sourceRoot/effectiveWorkdir` 后重走身份链与全部 digest——攻击者若重建整套持久化链并复现全部
SHA/HEAD/tree/status/digest 仍可通过。计划 §5.2 已把该窗口声明为单 operator 纪律下的残余；
实现与声明一致，无需修改。记录在案以防未来被误读为事务隔离。

### P2-2（轻微）capture 子shell 的 exit 125 复用
`helper :230-239`：`cd` 失败与 check script 自身 exit 125 同值——两者都 fail-closed 且 reason
相同（`check_nonzero_exit`），无行为差异；仅知会。

## 核实结论（控制流自行追踪，未采信实现说明）

**1. 共性问题真正解决** — check 在 `effectiveWorkdir`（经持久化链解析，非 stdout）内以 cwd
执行（helper `:230-235`）；任何 checkout metadata/ignored/env/bootstrap 语义差异显形为 check
失败（Darwin mtime 形态将被 preflight 阶段捕获而非 acceptance 阶段）；source checkout 与
execution workspace 的 baseline 判定从此分离。

**2. 首次业务调用前无绕过/错误放行** —
- `start` 不再携带 `--run`（runner `:616` 区改动）；stdout `Workspace:` 解析**已删除**，
  replaced by 默认布局 candidate + `real_case_workspace_resolve_identity` 全链校验
  （runner `parse_started_workstream` 重写体）；任何身份缺失 → `INVALID_EXECUTION_WORKSPACE_IDENTITY`，
  业务调用序列实测为 `start\ncollect_evidence`（runner test `:277-278` 精确断言）。
- capture 失败 → `INVALID_EXECUTION_WORKSPACE_PREFLIGHT`，调用序列恰为 `capture`
  （runner test `:624-632`）；verify 失败 → 同 INVALID，序列 `capture\nverify`（`:640+`）。
- PASS 路径顺序恰为 `capture\nverify\nrun\nautorun`（runner test sequencing 断言）。
- 无证据失败：失败也落 typed evidence（helper `:266-270` 尽力写；`write_evidence` 失败才
  return 1）；`invalid_reason` 对 missing evidence 显式输出 `workspace_preflight_evidence_missing`。

**3. Legacy/interrupt/benchmark/projection 无回归** —
- legacy：`not_configured` evidence + `workspaceEligibilityProven=false`，仍走
  `start(no-run)→identity re-resolve→run→autorun`（T6 断言 report 不得出现 workspace PASS）；
- interrupt：`BUSINESS_RUN_STARTED` 分界——业务前中断不做 stale recovery/session reap
  （`handle_interrupt` 改动 + runner test `:411-415` 的 recovery-marker 缺席断言）；业务后中断
  保持 quiesce 语义；
- benchmark：`--workspace-preflight-check` 透传 + 配置/未配置两类 Gate 文案
  （benchmark test `:49-61` 双向断言）；现有 probe/validation-target 参数未退化；
- projection：`summary.json`/`report.md` 新增字段全部由 typed evidence 投影
  （`workspace_eligibility_*` 只读 `workspace-preflight.json`）；**`proven=true` 要求
  `status==passed` 且 `verificationStatus==passed`**——即 verify 必须真实发生，capture-only
  不算 proven（正确的消费语义）。

**4. 测试证明边界而非实现细节** — helper 测试默认 fixture 即含空格路径
（`execution workspace`/`check scripts/workspace check.sh`——前轮 P2-1 已吸收）；断言对象是
typed reason 字段与调用序列，不是内部函数行为；T1-T7 逐项落地：
identity（`workspace_identity_invalid`/stdout 不影响 persisted 结果/无目录扫描回退）、
check execution（nonzero/missing/信号、cwd=effectiveWorkdir、PASS 文本不救非零 exit）、
git boundary（HEAD/tree/pre/post tracked dirty；ignored 只记录）、sequencing（`--run` 缺席、
精确调用序）、TOCTOU（tracked/identity/check-SHA/output/HEAD 五类 drift → verify 拒绝）、
legacy（not_configured/false 不得显示 PASS）、benchmark sibling（透传 + 双文案 + probe 不退化）。

**5. 最小范围保持** — 8 文件与计划 §8 一致（3 harness + 3 tests + 2 docs + 本组计划/评审
文档）；helper 405 行单文件、无新 module/registry/framework；`verification-gate.sh` 九字段
parser 未动；无 Maven 需要（零 Java）。

## 独立复跑结果

- `real-case-workspace-preflight.test.sh`：**[PASS]**（exit 0）
- `run-public-benchmark.test.sh`：**exit 0**（两条 DEPRECATION 提示为既有行为，非本 diff）
- `run-real-case.test.sh`：**exit 0**
- `git diff --check`：clean

## 闭环（事实链）

| 角色 | 结论 |
|---|---|
| 事实由谁声明 | persisted `workstream.json → workspaces/<id>.json` 身份链 + Gate/runner 声明的 check path/SHA + source frozen HEAD/tree/status |
| 谁证明 | helper `capture`（workspace 内执行 check + 全量 evidence）与 `verify`（run 前重算身份/HEAD/tree/status/digest） |
| 谁消费 | runner `run_initial_segment` 的 first-run guard；`summary.json`/`report.md` 只投影 typed evidence |
| 在哪个副作用前强制 | 首次 `dualctl run`（即首次业务 Agent execution）之前；probe 不替代 |
| 失败如何路由 | capture/verify/identity 失败 → `INVALID_EXECUTION_WORKSPACE_*`、run/autorun=0、report+cleanup；legacy → not_configured/false 显式降级；业务后中断 → 既有 quiesce |
| 哪个测试证明不可绕过 | runner test 的精确调用序列断言（identity/capture/verify 失败时业务调用为 0；PASS 时恰为 capture→verify→run→autorun）+ helper 的五类 drift 拒绝 |

## 已证明 / 尚未证明 / 残余

- **已证明（deterministic）**：harness 边界——configured 路径下首次业务调用前 fail-closed、
  身份链不可伪造于普通操作、evidence 完整可复现、legacy 不冒充。成熟度
  `ENFORCED WHEN CONFIGURED / NOT PROVEN`。
- **尚未证明**：真实 provider 环境下该 verification procedure 的端到端价值（留给下一次自然
  且配置了 check 的 real-case）；small-profile `9→8` 仍 `NOT PROVEN`。
- **残余**：verify→Java lock 窗口与全套伪造链（P2-1，计划已声明，单 operator 纪律承担）；
  exit 125 复用（P2-2，无行为差异）。

## 判定

- **Boundary Review: PASS**
- **选择性提交条件：满足** —— 建议将本 diff（6 modified + 2 新 scripts + 4 文档，含两份
  plan/review 与本评审）作为单一原子 harness checkpoint 提交。
