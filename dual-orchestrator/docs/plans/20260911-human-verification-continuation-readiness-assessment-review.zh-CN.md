# 人工验证续作与 E2E 就绪性漏评复盘 独立评审

日期：2026-09-11。评审者：CC（只读；基线 `0cb2a36b`，实现 `88107d42`；未调 provider、
未恢复样本、未改 production、未启动 agent）。所有结论基于我对 245D 原始工件与代码的直接核证。

## 0. 总判定

F0–F5 **全部成立，未见过度归因**；本轮止损依据充分（与 BXWV 不同：Gate 早在 2026-09-10
增量里预声明了"submit_evidence 未实现若必须使用则停止"，本轮是按约执行，不是误判）。
链条闭合到 gate-050 后死端于材料接收——与自评一致。存在一个**更小的合法闭环**（R2）与
三处需要收紧的细节。评估自身质量高：区分事实/判断（F2 明标"合理判断"）、不外推（F4 明标
未实证）、不连坐（F5 明标 server/UI 未见错——我已核实 console.js:1154 确实消费 packet
真实动作清单，仅 CLI 有缺陷）。

## 1. F0–F5 逐项核证结果

| 项 | 核证 | 判定 |
|---|---|---|
| F0 已知必需出口被延期仍开跑 | Gate 0909 文档 :75 预声明止损；设计 §5/分相明确 submit_evidence 未实现；245D 实际停在 gate-050 三动作（revise/request_action/collect_evidence）无一能达验收 | **成立**。补充 R4：这是共同盲区——设计 §9 与我的分相评审都未写"完整 E2E 开跑前所有必需出口必须存在"的就绪规则 |
| F1 前置未覆盖验收执行能力 | transcript :296-320 Mach `bootstrap_check_in Permission denied`，且模型用不经产品代码的 headless_shell 直启复现（环境问题的正确隔离法）；:253-259 blocked-by-allowlist；health 只证模型/ACP/Read | **成立**。需求明文要求浏览器级验证，属可预检项 |
| F2 实现/自测职责放大往返 | implement-claude.md:22-26 逐字核实（"owns code changes, not exhaustive verification"+禁 broad test/build）；成本因果是推断但标为推断 | **成立且自律**。可延期，独立评审后再动 |
| F3 清理规则与证据保留冲突 | 两 prompt hygiene 段核实；产品工作区 target/ 已删、全仓零 surefire XML；transcript+typed sidecar 仍在 | **成立**。见 R3 的收敛要求 |
| F4 三种检查混用 | verify-workspace.sh:19 `test -z git status --porcelain`（tracked-clean）+:23-27 真实 OKX curl 逐字核实；实现期未提交即必然失败 | **成立**。与材料回路耦合（见 R6） |
| F5 CLI 提示未消费当前动作 | CliNextActionHints.printHumanGate 硬编码 approve/revise；gate-050 packet 实际 `['revise','request_action','collect_evidence']` | **成立且被低估**：提示的 `approve` 根本不在本 gate 合法集内，是主动误导而非仅漏展示。调用面无缺陷——`review decide` 经 `EvidenceCollection.resolveGate`，collect/request 语义守卫在 HumanGateActionSemantics:64-74 在位 |

另核实：自评"旧的'仅缺证据却回实现'没有复发"属实——must_fix=[] 的 INCOMPLETE 正确停在
human gate 而非回 implement（第一增量路由按设计工作）；request_action 载体（gate 节点 result
`action_requests[]`）真实落盘且被使用（request_id `external-evidence-245D-050`）。

## 2. 链条闭合与失败切面

**闭合到 gate-050，死端于材料接收**：人工处理（三动作在位）→ 提交证据（缺）→ 自测核验
（缺）→ 验收（因 self-test 无法诚实 COMPLETE 而不可达）。失败切面：已运行段的切片
（transcript 持久、sidecar typed、gate 节点记录、止损按预声明条款执行）完整；未实施段的
切面在设计 §8 有清单、诚实标注未实现。六维：Declared/Proven/Consumed/Enforced 对**已运行段**
成立（我逐项核证）；材料回路的六维是实施前要求而非声明——自评未冒充，予以确认。

## 3. Findings（按严重度；事实/推测分列）

**R1（P2，事实）F5 修复应消除主动误导**：不只补齐动作清单——硬编码的 `approve` 对 repair
gate 是非法动作。修法即自评所述：消费当前 gate 动作资格（packet/definition 单一来源），
不维护第二份清单。独立小修，可先行。

**R2（P2，设计裁定）存在更小的合法闭环，挑战 §4.4 成立**：设计 §6 的材料源码身份若按
20260910 评审更正后的"逐文件内容身份"执行，对宿主执行类材料（surefire/浏览器日志）偏重。
更小 v1：**材料固化 = workstream 自有不可变目录 + 保存字节 SHA + 提交时 (git HEAD +
porcelain 状态) 快照**；collect_evidence 重跑时核对 HEAD/porcelain 未变即可堵"旧材料复用"
（tracked 树上同路径集内容变化必然改变 HEAD 或 porcelain）。逐文件内容身份延期。更激进的
替代（自测报告加 typed `external_verification[]` 由人工验收门终裁、零摄取机制）列为后备，
其 Proven 弱化（无机器绑定）须如实声明；放开 sandbox 与执行任意脚本按自评一并排除。

**R3（P2，事实+要求）F3 的修复必须与材料回路收敛**：保留落点若自造一套，将出现两个证据
保存机制。要求：自跑验证产物（surefire XML 等）的 workstream 落点与 R2 的材料固化目录
使用同一 ref/hash 习惯（可同目录不同子树），self-test hygiene 规则改为"先按清单保留再清理"。

**R4（P3，事实）就绪规则应进 Gate 模板**：F0 的最小纠正（每项必需验证：在哪执行/谁接收/
如何回验收）应成为所有 fresh E2E Gate 的常设前置项，而不是本次复盘的局部教训。我方盲区
一并记录：分相评审未包含此规则。

**R5（P3，边界）F1 前置的范围钉住**：仅"需求明文要求 + 沙箱未证明"的能力做短探针
（本轮=浏览器启动+公网出口，约 30 秒级）；防止膨胀为每轮全能力矩阵——自评已有此意，评审
予以固定。

**R6（P3，事实）F4 与材料回路的耦合点**：missing_evidence 第三项（verify-workspace 全记录）
只能在交付阶段、内容已提交且有公网出口的环境产生——材料回路设计必须把"该记录的执行阶段
与执行者"写清，脚本本身的分段化（基线/实现/提交后三用途）可与其后独立处理。

## 4. 必须一起解决 vs 可延期

**一个设计单元内必须一起**（对应自评 §6，予以认可）：F0+F1 的就绪规则（文档级）、
R2 最小材料回路（固化/绑定/collect 消费/回验收）、R3 保留落点、R1 CLI 修复。
理由：四者共同闭合"人工处理→提交→自测核验→验收"；缺一则该链仍在某点死端。
**可延期**：F2（提示收紧，独立评审后单独改）、逐文件内容身份（R2 后备）、F4 脚本分段、
server/UI 动作面（无缺陷）。
**平台化检查**：全部方案落在既有节点/载体/ref-hash 习惯内；无新模型层、网络机制、
权限平台、评估器或第二运行时。自评的禁止清单与我的核证一致。

## 5. 结论

足以进入最小实施计划：按 §4 的"必须一起"单元做有界设计（材料回路 v1 用 R2 的轻绑定），
F0/F1/R4/R5 并入 Gate 模板与操作规程；F5/R1 可立即独立小修。不预设 PASS——材料回路的
六维与 Cannot-bypass 序列在实施后按自评 §5 的不变量清单另行验证；245D 保持只读，
是否合法续作由该设计明确后另行判断（现有三动作中 collect_evidence 对 Mach 限制无意义、
request_action 已登记、revise 无代码可修——除非新材料回路落地，否则无合法前进路径，
这一判断与自评一致）。
