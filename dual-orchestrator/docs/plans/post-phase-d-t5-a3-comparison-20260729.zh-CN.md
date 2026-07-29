# T5 A3 Same-Case Comparison

日期：2026-07-29
状态：`ATTEMPT 1+2 FAIL / BENEFIT NOT PROVEN / V3 AUTHORIZED FOR ONE RUN`

## 1. 目标

使用与 T1 相同的 A3 task、target baseline 和 full workflow，验证 T4 删除六个
active context profile 中重复 `requirement` 投影后：

- 结构化控制主链和目标项目质量不退化；
- 六个 active context package 不再包含 requirement；
- 最终 prompt 仍由 `AgentPromptRenderer` 注入 requirement 且只出现一次；
- estimated context 与 measured input token 相对 T1 下降；
- Agent runs 不超过 10，revision 和 protocol correction 均保持 0。

## 2. 前置验证

本次运行的 Verification Gate 已通过：

- Dual base HEAD：`0e0d77ee3a7a1b78cca69e64e1c46623c4f6cd5d`；
- T4 production diff 仅包含
  `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextProfiles.java`；
- T4 file SHA-256：
  `d2d06a35cb23b4e5d35ed8915562ac26e2a8a62e1eaf7364e2c80b461f003deb`；
- fresh Maven package：9/9 modules PASS；
- `preflight.json`：
  `artifactFreshnessProven=true`、`providerProbeStatus=passed`、
  runner kind 与 expected runner kind 一致；
- Claude Code / GLM-5.2 最小 acpx probe：PASS；
- Codex / OpenAI 最小 acpx probe：PASS；
- target repo 仍位于要求的 clean baseline。

报告：
`docs/testing/real-case-reports/20260729T062639Z/report.md`。

Workstream：
`ws-20260729T062937Z-G2XF`。

## 3. Stop-Loss 事实

运行在第 3 个已完成 Agent node `design.claude_design` 首次出现 protocol
correction。第一次 occurrence 的 assistant message 是：

```text
自然语言前缀
+ AgentResultEnvelope JSON
```

对应 authoritative `run.json` 事实：

```text
agentResultAcquisition.correctionAttempted = true
agentResultAcquisition.correctionReason = PARSE_FAILURE
occurrence[1].contractDecodable = false
occurrence[1].contractSchemaValid = false
occurrence[1].parseErrorCode = AGENT_RESULT_PARSE_FAILED
occurrence[2].contractDecodable = true
occurrence[2].contractSchemaValid = true
selectedCandidateOccurrence = 2
```

Strict parser 没有提取或猜测正文中的 JSON。现有 bounded correction 在同一
Claude session 中执行一次，第二份结果通过 strict contract。节点本身最终为
`COMPLETED / SUCCESS`。

但 T5 Verification Gate 明确规定：

```text
first protocol correction -> stop
```

因此 workflow 在第 5 个 Agent run `plan.claude_plan` 运行期间被主动中断，没有
继续到 acceptance 或 delivery。

## 4. 分类

本次 T5 为 `FAIL`，不是 `INVALID`：

- fresh artifact、runner kind、Claude/Codex probes 均有效；
- provider/model/usage evidence 对已完成 Agent run 可用；
- 失败条件来自已预先声明的 protocol-correction stop-loss；
- 没有 provider timeout、rate limit、model mismatch 或 stale artifact。

本次结果也不能证明 T4 导致 protocol correction。T4 只删除 context package
中的重复 requirement section，没有修改 strict output contract、parser、
correction policy、workflow graph 或 Agent 调用数。单次真实运行只能证明：

```text
在这次 T5 样本中，零 correction 质量门未满足，
所以 before/after 收益比较无法完成。
```

不能推出：

```text
T4 subtraction 导致输出格式退化。
```

## 5. 当前可声明与不可声明

可以声明：

- T4 deterministic boundary 仍为 `PASS`；
- strict parser 正确拒绝 prose + JSON；
- bounded correction 正常工作；
- T5 stop-loss 被实际执行，没有用后续成功掩盖 gate failure；
- 本次只运行一个 A3 case，没有 retry、模型切换、prompt/timeout 调参或第二次 case。

不能声明：

- T4 已降低真实 input token、cache 或总耗时；
- T4 已通过 full A3 quality regression；
- T5 部分成功；
- T6 已解锁；
- protocol correction 是 T4 的因果结果。

## 6. Gate 复核

只读 owner 复核确认：

- bounded correction 是 `AcpxAgentResultAcquirer` 明确定义的正常可靠性机制；
- correction 最多一次、保持 same session，第二份结果仍经过 strict
  parser/schema/lineage/semantic validation；
- correction occurrence 与 usage 由 `run.json.agentResultAcquisition` 记录；
- `AcpxAgentTaskHandler` 通过 `aggregatePromptOccurrences()` 把两次 usage 汇总，
  没有从 token 或 duration 指标中隐藏 correction；
- 因此 correction=0 不是功能质量不变量，而是一次效率实验的控制变量。

原 gate 把“功能质量”和“效率可比性”合并为同一个失败条件，结论为 `WARN`。
修订后的 gate：

`/tmp/dual-t5-a3-verification-gate-v2.md`

新口径：

```text
功能质量：
  correction <= 1
  最终 strict contract valid
  protected semantics 不变

效率：
  Agent node runs <= 10
  prompt occurrences <= 11
  correction 的全部 token/duration 必须计入
  primary metric = generated context-package estimated tokens
  provider input/cache/duration = supporting measured metrics
```

## 7. 后续门

Attempt 1 保持 `FAIL`。修订 gate 随后得到一次明确 provider 授权并执行 Attempt 2：

- 报告目录：
  `docs/testing/real-case-reports/20260729T072220Z/`；
- workstream：`ws-20260729T072508Z-NHNK`；
- fresh reactor 9/9 PASS，Claude Code/GLM-5.2 与 Codex/OpenAI probes PASS；
- 首轮 `intake.claude_brainstorm` 与 `intake.codex_product_review` 均为 strict
  typed result，correction=0；
- Claude proposal 把“非对象输入”扩展为“字符串、数字、布尔、数组等必须拒绝”；
- Codex 以 typed `NEEDS_REVISION` 指出 JavaScript 数组属于对象，该测试要求收窄了
  用户未明确规定的语义；
- ledger 随后记录 `revisionCount=1`、`iterationCount=2` 并启动第二次 intake；
- v2 gate 明确规定 first semantic revision 即 stop，因此运行被主动中断。

Attempt 2 为 `FAIL / STOP-LOSS ENFORCED`，不是 provider/configuration
`INVALID`。Codex 的 revision 有具体 contract evidence，不是自然语言误分类；但该
model-level semantic variation 与 T4 删除重复 requirement projection 没有已证明的
因果关系。

由于中断发生在 `run-real-case.sh` 的 start 子进程尚未返回时，脚本生成的 report
没有捕获已经创建的 workstream id，错误显示 `workstream=unknown` 和 0 runs。
authoritative ledger 与 `metrics/workstream-metrics.json` 实际记录 3 个 Agent run、
2 个已完成 measured provider run、1 个 revision。该差异是 interruption-report
projection gap，不改变 stop-loss 分类，报告中的 0-run projection不得作为真实
执行事实。

该 projection gap 已由 checkpoint `f04cdedb` 修复：

```text
handle_interrupt
  -> parse_started_workstream
  -> collect_evidence
  -> write_report
```

修复复用正常路径已有的 `parse_started_workstream()`，从 captured `start.log`
恢复 workstream、workspace 与 ledger identity，不扫描目录、不创造 execution
事实。`run-real-case.test.sh` 的 synthetic interruption cannot-bypass test 证明：

- report/summary 不再投影 `workstream=unknown`；
- effective workdir 与 ledger identity 正确；
- identity 恢复发生在 evidence collection 之前；
- 已收集的 Agent run 不再错误投影为 0。

T5 当前为 `BENEFIT NOT PROVEN`，T6 保持 `BLOCKED`。在新的显式决策前：

- 不重跑 A3；
- 不修改 parser 或放宽 strict contract；
- 不自动补默认字段；
- 不增加 retry、protocol framework、registry 或新 phase；
- 不根据未完成样本做 token/latency 收益归因。

不得自动启动第三次 T5。下一次 A3 只能在本文第 8 节 v3 gate 获得一次新的显式
provider 授权后运行。

## 8. 下一次 A3 的唯一目标与 v3 Gate

### 决策：接受一次 semantic revision

Semantic revision 是 typed review decision 驱动的业务质量机制，不是 provider
故障，也不是无效样本。下一次运行允许：

```text
protocol correction <= 1
semantic revision <= 1
```

两者产生的全部 Agent runs、prompt occurrences、token、cache 与 duration 必须计入
最终结果。不得把 revision/correction 对应的 usage 从 after 样本中删除，也不得仅
比较“无 revision 的 matched nodes”后宣称端到端收益。

### Verification Gate

唯一可执行 gate：

`docs/testing/verification-gates/20260729-t5-a3-requirement-subtraction-v3.md`

该文件是下一次运行的 `--verification-gate` 输入，也是 PASS / FAIL / INVALID /
stop-loss 的唯一 owner。本文只记录决策和解释，不维护第二份 gate。

### 可比较性说明

T1 baseline 是一次真实的 10-run、0-revision、0-correction 样本。v3 不要求下一次
也恰好零波动，因为那会把正常可靠性和质量机制排除在系统成本之外。它采用更严格、
也更简单的端到端口径：

```text
after 的全部真实开销 < before 的全部真实开销
```

若一次 revision 使总 token 不降，则结果为 `FAIL`，不能用 T4 的局部 context
缩减抵消或隐藏。generated context-package estimated tokens 仍用于解释 T4 是否按
设计生效，但不替代端到端 PASS 指标。

即使 v3 得到 PASS，也只证明该次完整 A3 样本的 observed end-to-end benefit；
它不单独证明跨任务、跨时段或跨 provider 波动的普遍因果收益。

### 授权状态

```text
V3 GATE READY
REAL-CASE AUTHORIZED ONCE
```

用户已在 2026-07-29 明确授权一次 A3 full real-case。授权在 workstream 创建后即
消耗；不得自动重跑、修改 prompt/parser/model/timeout，或在失败后继续调 gate
追求 PASS。
