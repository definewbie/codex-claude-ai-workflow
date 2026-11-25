# 第一步完成记录：精确枚举诊断

日期：2026-11-01。范围为本轮第一项实施工作，对应总验收表第 2 行的**枚举诊断子项**；不代表五项总验收闭合。

2026-11-02 因果勘误：下方原始证据表所并列的历史枚举异常与 correction 提示来自不同 occurrence。提示针对首次输出的正文前缀；枚举异常出现在纠正输出返回后。因此撤回“HHZL 证明纠正提示降格了已识别的枚举错误”的解释。下述局部诊断改前/改后与测试结果不变，但它们没有证明原始两次序列能够获救，也没有证明这一改动应优先于路由、环境或合格恢复问题。新增这一勘误不改写原始 IO、测试结果或旧 manifest。

## 结论与改动

原始响应中的非法枚举现在由 parser 产生 `INVALID_ENUM_VALUE`，携带 JSON Pointer 字段路径、实际值和由实际 enum 定义派生的允许值。acquirer 在既有单次 correction 调用前强制将这些信息及 `AGENT_RESULT_PARSE_FAILED` 渲染到纠正输入。没有从 Jackson 异常正文猜分类，没有把整个任务输出或异常全文复制到提示中。

- [parser 类型异常入口](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultEnvelopeParser.java:49)
- [诊断与允许值来源](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultEnvelopeParser.java:71)
- [纠正输入消费方](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentResultAcquirer.java:250)

类型允许值并不授予当前节点使用这些值的资格。例如 HHZL 节点仍只允许 `NONE`；新提示明确节点合同可以进一步收紧，候选仍通过原有严格 parser/validator。合法输出集合、审批、身份、scope、终态、写序和最多一次 correction 均未改变。本项按 TRANSIENT 诊断前向修复验证，不引入 workflow outcome、重试或续作状态。

## 原始证据与纠正后的实验口径

历史证据来自 workstream `ws-20260903T225544Z-HHZL`、run `run-1788476621271`。

| 原始文件 | 字节 / SHA-256 | 本次核验 |
|---|---|---|
| [attempt-1 原输出](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/outputs/intake-codex-product-review.attempt-1.json:1) | 2572 / `72956cadc7da1033068fe58b77ffcbac7f1ddcd874439ad1b8cdc7428568bbb9` | 正文前缀先触发 INVALID_JSON，不能冒称它已经进入枚举类型检查。 |
| [历史 correction 原输出](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/outputs/intake-codex-product-review.contract-correction.json:1) | 2401 / `b33d3a2ecbac2dd8f41ca3728157b5e6cd72be498813ba07be4e04e9c4139502` | 纯 JSON，`verification_remediation=REVISE_PROPOSAL` 非法。 |
| [run.json](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/run.json:218) | 原文件未修改 | Jackson 原诊断有非法值和枚举白名单；[历史 correction 提示](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/prompts/intake-codex-product-review.contract-correction.md:3)只有 INVALID_JSON。 |

两份输出均逐字节复制为测试资源，并留存[来源清单](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/test/resources/agent-result/hhzl-invalid-remediation.source.json)。以同一纯 JSON 输入、当前相同依赖，分别运行改前 parser 源码与改后 parser：[对照结果](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/enum-diagnostic-forward-fix-20261101/parser-before-after.json)为 `INVALID_JSON → INVALID_ENUM_VALUE`，两者均拒绝输出。这是当前版本的历史输入回归和 parser 对照，**不是历史 binary/schema/validator 身份下的确定性复现**。

[acquirer 历史输入测试](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentResultAcquirerTest.java:29)将历史纯 JSON 作为当前初始候选，证明 Claude/Codex 两条入口都会在真实写出的纠正提示中消费枚举诊断。它不模拟历史原有两次调用的位置。

[原始两次输出序列测试](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentResultAcquirerTest.java:65)则保留原顺序：正文前缀 → 非法枚举。它仍在第二次输出失败，且不获得第三次调用。**本次不能宣称 HHZL 已可闭环或旧链已救回。**初版 red 测试误把带前缀的输出当作枚举输入，失败日志仅作过程记录；枚举改前/改后证据采用上述纯 JSON 对照，未用放宽解析掩盖输入差异。

## 边界闭合自查：仅诊断子链

| 义务 | 核验结果 |
|---|---|
| Declared by | AgentResultEnvelope / TaskContract 现有枚举定义；未新增允许值。 |
| authoritative fact / owner | 原始响应及 Jackson typed exception 的 path/value/target；parser 唯一产生类型诊断。 |
| Consumed by | AcpxAgentResultAcquirer 的 PARSE_FAILURE 提示渲染，复用唯一 correction 通道。 |
| Enforced before | 先通过原有 process/completion/provider 资格检查，再写纠正文件、调用原 deny-all transport；诊断本身不授权执行或发布。 |
| Failure route | 写文件失败沿原异常出口；执行不完整/非零/provider 阻断保持原拒绝；纠正后非法、错身份或节点禁用值仍拒绝，不追加调用。 |
| Proven by / Cannot-bypass | 新增 7 个测试方法：历史纯 JSON 与原序列、两入口、错误身份、节点禁用值、字符串转义、4 个 sibling enum 和非枚举错误；原未知字段/EOF/完成与权限阻断测试继续通过。 |

完整 transition 与写序、失败切面分析见 [preflight](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/enum-diagnostic-forward-fix-20261101/preflight.md)。Review 为作者自查，无独立 reviewer，不冒称独立评审。Reflect：初始 fixture 的正文前缀暴露了“同一 run 的不同输出不可互换”的证据边界；已拆成两条测试。再 Analyze：诊断子链达到本轮离线 slice 的 PROVEN，不外推为恢复、产品正确性或 E2E 交付已证。

## 验证与尚未完成

7 个测试族共 **165 项通过，0 失败、0 错误、0 跳过**：parser 17、validator 1、prompt 16、acquirer 37、handler 54、outcome resolver 22、生命周期 18。最终定向运行通过 147 项；同一生产代码的前次选定运行中，18 项生命周期测试通过，唯一失败来自随后已修正的 fixture 选择。本次没有因仅测试资源变化重复跑生命周期。

命令、报告副本、来源与编译类哈希见 [verification.json](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/enum-diagnostic-forward-fix-20261101/verification.json)。原四个修改文件开工前已有变更，保存了 before 快照；[incremental.patch](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/enum-diagnostic-forward-fix-20261101/incremental.patch)仅记录本次增量。

尚未验证或实施：真实 provider 的纠正成功率、自动恢复资格与共享额度、历史账目核销、发行冻结、历史冻结身份重放、外部 grader 对照。本项未调用真实 provider、未重开历史 workstream、未提交代码。下一必要工作仍是已授权 TRANSIENT 恢复资格/限额的边界分析，不能仅把 MANUAL_ONLY 改为 AUTO_ALLOWED。
