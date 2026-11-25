# W08 准备批次：修改前边界分析

2026-10-10；用户批准准备批次。目标 P0：纠正合法 GOAL 提案被旧提示误导，形成当次 Gate 的装配/验收/发行材料。未授权模型调用、installer、真实目标 Git、提交、额外 agent。

Definition=已存在 workflow JSON / contract registry / templates；Instance=GOAL 与 EXECUTION 合同及批准 occurrence；Execution=R01–R03 和原生运行观察；Human decision=原 L04；projection=prompt/read model。批准与范围的 SoT 是已资格核验的合同及批准谱系，提示词只消费，不产生许可。无新模型。

| 前态 / action / owner | 原 durable write / 副作用 / 后继 | 失败与恢复 |
|---|---|---|
| intake 候选 / 只读作者与独立复评 | 原 typed envelope→R03/L03→GOAL compile→人决定 L04 | malformed/identity/材料缺失仍原拒绝；候选路径不授写权 |
| 已批准 GOAL / 只读设计与计划 | renderer 消费合格 GOAL / reviewed subjects；声明 canonical files respecting exclusions→原 validator→R03/L03→复评→EXECUTION compile | 提案不写代码；control/excluded/malformed 仍采纳前拒绝；不满足原失败出口 |
| EXECUTION 候选 / 人批准 | 原 L04 核完整合同、scope、readiness和谱系 | 未批准零 implement；漂移拒绝不补假字段 |
| 已批准 EXECUTION / 实现→自测→独立验收 | renderer 提示严格文件/操作；原 qualification→R01→native→R02→R03→唯一 outcome→发布→后继 | scope/identity/证据仍强制；所有写前缀和 UNKNOWN 沿 D06/D07，不自动重发 |
| 最终批准 / apply→人commit→finalize | 原 L07 + Git/PID/patch/停止/精确tree检查 | 本轮零真实目标副作用；原 delivery failure/recovery 不改 |

本轮不改上述写序、validator、gate、router或 recovery。新旧冻结 binary 不兼容仍 INVALID；不迁移旧 ledger。

Sibling sweep：intake 作者/评审原则、design 作者/评审、plan 作者/评审、implement、自测、共享字段示例/仓库指引及 correction（原 renderer 最终提示回用）。首发现仅 design 指令，扫描显示 intake 的 candidate 被称作 authorization 与 plan reviewer 假定精确 approved scope 同属此分期漂移。

证明义务：实际 qualified GOAL/EXECUTION 渲染（前者无旧复制指令，后者有严格指令）、同族/拓扑对照；合法 canonical 提案→独立复评→精确批准→实现，非法 excluded/control/错身份/缺批准仍拒绝；失败/冷读复用常驻序列，全模块单轮新凭证。先保留红证据再改生产。仅 metadata 子命令核实际 acpx；零 session/new/prompt。

结束不外推：不证明真实模型成功率、原生 ask/工具测试能力、浏览器/worker、多部署或诊断其他族；最终 Gate 前提和预算仍待用户及 CC。
