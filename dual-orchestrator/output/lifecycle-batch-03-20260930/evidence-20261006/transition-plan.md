# B03 D05 证据接线续作

状态：IN_PROGRESS；按既有 B03 授权及用户确认的逐项退出码语义推进。P0 生命周期可靠性，非通用 runner、环境认证或安全平台；零 native/provider 调用、零新增 agent。当前 dirty 源码 630 文件内容保存在 before-source.json；不覆盖其他改动。最终全模块回归和 CC 独立评审前不关闭 B03。

## Boundary Review

Definition 是已批准 TaskContract 的逐 criterion verification binding；Instance 是作者/复评、批准与自测/验收 occurrence；Execution 是现有 run/attempt/session/tool-call 事实。adapter 拥有原生输入/结果观察，runtime 负责来源/身份/源码基准验证，模型只拥有语义断言候选，独立 acceptance 负责语义复评；ledger R/L 协议拥有耐久事实和续作。投影不得产生证据或批准。

既有 TaskContract/AgentResultEnvelope/AgentExecutionEvidence/固定 run 完成足以承载增量，不新增领域层。verification binding 显式声明是否要求真实退出码及可选期望值，缺声明不默认为不要求。声明随计划复评、EXECUTION 批准固定；修改它必须按同授权边界比较规则决定重新批准。tool observation 区分原生状态/已报告 exit 字段/有来源证明的实际 exit；未知不填 0。仅解析具名协议字段，不从 stdout 文案或模型自述提取“事实”。

## 完整转换与失败切面

| 转换 | authoritative pre-state / owner | 写序、消费者与后状态 | 失败、续作、重启 |
|---|---|---|---|
| 计划→执行合同批准 | 已批 GOAL、真实 plan/reviewer；原 compiler/qualification | 逐项要求→同现有不可变 EXECUTION 引用→原人工批准 | 缺声明/引用不授 dispatch；不修改既有批准字节 |
| 准备→工具调用 | 当前合格 claim、固定合同；runtime/adapter | R01 固定源码/环境基准和输入→R02 intent→原生工具观察 | 缺资格零调用；intent 后不明按 UNKNOWN，不从无 exit 猜未执行 |
| 原生观察→自测候选 | 本次 prompt 的实际 tool_call/update；adapter | typed 输入/状态/结果/来源→本 run 固定观察材料→模型 criterion/assertion 候选 | 历史尾读、错 session、无输入/结果、重复歧义不能充完成证据；原生状态不冒充实际 exit |
| 自测→独立验收 | 同批准 criterion、工具与源码身份；统一证据消费者 | 验证必需证据与断言引用→原 outcome 固定/sidecar/R03→L03 | 缺要求 exit 或坏身份阻止成功采纳；未要求 exit 的合法材料须可通过；过程失败仍保原 typed 主因 |
| 复用→验收/交付 | 已固定完成材料；现有 lineage/报告/交付消费者 | 同规则重核适用源码、合同、材料→独立验收结论→原交付门 | 迟到源码变化使旧证明失效；无关证据/工具成功不能替代 criterion；不得只凭模型 ACCEPT 放行 |
| 写入中断→恢复 | R01/R02/R03 和 D06 prepared 写集 | 补齐同次固定字节，无新 provider | 判定前失败重验本地材料；固定后只补本地发布；非 before/after INVALID；旧格式不静默兼容 |

## 兄弟入口与验证义务

inline/worker/correction/retry/冷恢复共用观察及证据消费者；四种 definition、计划 shape/compile/批准重验、readiness 复用、self-test/acceptance/report/delivery 不能绕过。程序 readiness 只复用同来源/环境依赖事实，不把旧测试结论直接充当前源码通过。permission 非必需判断须绑定精确信号和独立完成证据，缺关联仍保守阻断；不以通用成功/坏产物推导。

常驻对照至少包含：无需 exit 且足证据正常通过；明确要求 exit 而只有合成值拒绝；真实要求值不匹配拒绝；仅工具成功/纯模型声明拒绝；错调用/错源码/漂移/写失败/冷启动拒绝与合法补齐；无变化可复用；放宽已批准 exit 条件要求重批。具体测试记录以实际运行工件为准；最终结果与未覆盖范围见 checkpoint.md / verification.json，不把全部设计义务标成已证。

原生运行身份核对限定实际使用的 acpx/adapter/config 与明确行为设置；不做通用依赖扫描。若现有接口无法关联批准要求需要的事实，或出现需新增 runner/权限/平台的成本增量，停止受影响工作并提交具体证据，不通过降验收或追加实验绕过。

## 实施中复查补记

- 同一次消费中共享同一执行的多个 criterion 只做一次当前源码采集；不跨准备/调用/发布/恢复缓存。此处复用的是一次检查的读结果，不是把旧源码身份当新身份。
- 固定 publication 现在包含原候选 ref/SHA。恢复发布仍核对逐项证据与当前适用源码，拒绝不能重写原已固定 execution outcome，不能重发外部执行。
- 验收 context 和 agent 准备、每次非 cleanup 命令 intent 前检查真实 routed 自测。禁止把历史展示的证据全部隐藏；只有作为当前输入时重核有效性。
- operator 分支按批准的 evidence_kind 保留声明+材料，不因程序读取材料升级为 machine-executed。测试中的 README 读取只提供真实文件观察，不能用它证明业务测试通过。
