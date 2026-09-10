# 人工处置兜底与证据续作设计

日期：2026-09-10。状态：REVIEW DELTA APPLIED / 分相设计；补采写入边界仍有实施闸门，非整体 READY。
基线 HEAD：331257cf77e8e1ba7b74db6011f3a515a3d2b0d8；工作树含既有文档/运行证据，不代表 clean。
原始稿仅设计；当前首批代码实施进展见 §12，无 provider/agent 委派；BXWV 保持只读。
前置比较：20260910-workflow-continuation-and-liteflow-bounded-assessment.zh-CN.md。

## 1. 唯一目标与范围

完成验收返工的整个续作回路：产品缺陷回实现，证据缺口回补采，无法补采或无法分类时进入
知情人工处置；人可选择当前合法动作、提交材料，或登记无法由现有能力表达的新动作需求。
不能再因“未完成”无条件回实现，也不能把兜底做成任意脚本执行平台。

首批落在 acceptance -> self-test -> decision -> human repair -> 再验收这一边界，
以及同边界 bundled siblings。不是所有阶段一次性增加新 schema。
通用性来自无业务标识的处置语义与未知出口，不是覆盖全仓每一种异常。
本轮推进 P0 流程正确性；不重开网络、模型层、合同重编译或交付设计。

## 2. 已核实事实与缺口

- SelfTestResult 有 decision/checksSkipped/missingEvidence，但无明确的下一步处置字段。
  AcceptanceReviewResult 的 failure 已有 VerificationRemediation，不新建平行枚举。
- ReviewDecisionHandler 当前提取 decision/source/summary；workflow 声明
  SELF_TEST_INCOMPLETE -> context.implement_claude，无进一步条件。
- HumanGateActionSemantics 已统一动作可见性；支持 definition 声明 allowed_actions，
  并按 scope-repair source outcome 缩小资格。不是任意配置字符串就有执行能力。
- ReviewDecideCommand / server WorkstreamActionService 收敛 resolveOpenGate(action,note)。
  当前不能把 note 当作已实现的 typed 参数或证据提交接口。
- RoutingEngine.resolveOpenGate 先校验再写 gate resolved，随后 completeAndRoute；
  多次 durable write 不是跨文件事务，新增输入不能偷偷扩大半完成窗口或承诺 crash liveness。
- autorun 也调用 resolveOpenGate；新兜底动作不得进入自动批准路径。
- JSON 定义已编译给 LiteFlow；目的地应由定义拥有，不增加 Java 中的平行目标表。

## 3. 目标流程

```text
acceptance
  ├─ 产品修改项 -> 既有实现修复
  └─ EVIDENCE_COLLECTION -> self-test 补采
       ├─ 完整 -> 既有验证 -> 再验收
       ├─ 发现具体产品修改项 -> 实现修复（仍校验 scope/contract）
       └─ 外部阻塞 / 不确定 / 冲突 -> 既有 human repair gate
            ├─ 选择合法既有处置 -> 补采或修复 -> 原验证链
            ├─ 提交外部证据 -> 补采节点核验材料 -> 原验证链
            └─ 记录新动作需求 -> 保持等待，不执行
```

不能仅靠 prompt 令模型选择 BLOCKED：decision/validator 必须守住证据续作约束。
成功字段、分类字段冲突时，不能优先挑有利字段继续；进入明确拒绝/人工诊断路径。
执行层无有效输出（JSON/启动/进程失败）仍走既有 execution recovery，不强造业务 sidecar。

## 4. Contract / model：最少事实与唯一 owner

采纳评审 F1：SelfTestResult 只增加平字段 mustFix，复用 missingEvidence；不引入嵌套
Failure，不将 VerificationRemediation 扩展到自测，不触非验收结果的 NONE-only 约束。
产品修改项必须明确，但字段内容仍是模型判断，不能把字段存在当语义已证明。
结构非法保持既有 parser/validator 拒绝；语法合法但无法确定处置时进入人工兜底。
两个列表同时非空不自动算矛盾：真实代码修改与缺证据可以共存。

锁定机制：decision handler 从 validated self-test 派生一个 machine-owned 标量
continuation_kind，JSON 使用既有 config_equals 条件选择目的地，不新增模型输出 decision 枚举。
不允许模型直接提供/覆盖该派生字段；Java 只判定处置，不持有目标 node id。

| 输入 | continuation_kind / 处置 |
|---|---|
| COMPLETE，mustFix/missingEvidence 均空 | COMPLETE，沿原验证链 |
| COMPLETE 但任一阻断列表非空 | 语义校验拒绝，不假装完成 |
| INCOMPLETE 且 mustFix 非空（可同时缺证据） | IMPLEMENTATION_REPAIR，修改后仍须验证剩余材料 |
| INCOMPLETE 且仅 missingEvidence 非空 | HUMAN_REVIEW，先人工处理阻塞，不无限自动补采 |
| INCOMPLETE 两者空、或 BLOCKED | HUMAN_REVIEW，理由与原始事实对人可见 |

第一次 acceptance EVIDENCE_COLLECTION 仍自动进入一次补采；再次不完整按上表进入人工。
人工 collect_evidence 才再次触发补采，失败仍返回等待。普通首次自测同样适用。
这里不从自由文本猜“环境是否已解决”，也不凭旧验收意图阻止有依据的新代码发现。

原验收意图来自已验证的 routed acceptance sidecar；补采输入须继续携带其 ref，
每次 decision 从合法 input lineage 消费，不另存可变的全局“当前模式”。
若模型在补采中发现新代码问题，可提交 typed 依据，不能以“上轮无 must-fix”永久禁止新发现。
没有该依据时，INCOMPLETE 不得启动写入型实现。普通首次自测同样遵守，不能只修验收再入。

增加 mustFix 涉及 parser/prompt/projector/sidecar/validator，同批完整映射，
保留其他结果类型的既有约束，不为自测放宽 product-review/plan。
旧 frozen 实例不原地升级；新 schema/binary 配对要求明确，BXWV 不补填历史结果。

## 5. 人工兜底输入与动作

复用现有 human repair gate；按来源与当前事实展示动作，不给所有审批 gate 全量开放。
现有 action/note 调用继续兼容；增补一个有界请求参数对象，不能接受 arbitrary map/命令/target node。
请求共同绑定：workstream、gate occurrence、当前 source refs 身份、理由；来源身份与当前状态
不符则在写入前拒绝。客户端只能选动作，不决定后继节点。

| 处置 | 请求内容 | 结果 |
|---|---|---|
| 修改实现 | 复用 revise，明确修改项/理由 | 走已声明实现边，scope/contract 守卫不变 |
| 重新补采 | 拟新增 collect_evidence，理由 | 新执行走 self-test，不去 implement |
| 提交外部材料 | 拟新增 submit_evidence，材料引用与用途、理由 | 材料先固化后交 self-test 核验，再走原验收 |
| 无法表达的新需求 | 拟新增 request_action，预期结果、现有动作为何不足 | 非 resolving 操作，保留 gate WAITING，无 provider |
| 放弃 | 复用当前生命周期允许的 abort | 既有终止语义，不再创建后继 |

上述两个补采动作目标可相同，但前者不要求已有材料，后者强制至少一个有效材料。
任何 action 都不自动 ACCEPT；不在此 gate 增加“跳过验收通过”。
环境授权沿既有 Phase A 专用入口，不在本请求中复制 domain grant；note 不能授予权限。
v1 删除“已有环境说明”参数；不开放任意 env/启动参数透传。环境授权只走 Phase A。
现有缓存配置是否连续消费是待核对的运行前置，不能因删字段就宣称已经解决；
禁止把本机 scratch 路径写死在 production。

request_action 的名称/描述是待处理需求，不是可执行动作标识；无动态插件、DSL片段或脚本。
记录在 gate 节点 result 的 machine-owned action_requests 数组中，作为唯一 SoT；
action_requested 事件为派生审计，缺事件不抹掉已持久化请求，不另建需求管理系统。
该非 resolving API 不允许覆盖原批准或把 gate 标成 resolved；重复 request id 返回原记录，
不同内容同 id 拒绝。锁内读/校验 -> 写 node -> append 事件；中途失败冷读以 node 为准。
gate resolution 必须保留请求记录，不能因覆盖 result 而丢失历史；不为修补派生事件缺口重做动作。
operator 可之后改选现有合法动作或 abort；真正新能力另走代码设计/测试/发布，不能自动恢复旧实例。

## 6. 外部证据不是一句“我测过了”

提交材料须先由 Dual 固化到 workstream 自有、不可变的证据位置；使用已有 artifact/ref/hash
习惯，不创建另一套 evidence platform。外部原路径不是长期 authority，不读取任意 URL。
复制时计算保存字节的 SHA；绑定源代码树/候选身份、验证环境说明、用途及提交者声明。
源码身份应覆盖工作树内容，不能仅用未提交源码的 HEAD；优先复用现有 bytes identity 实现。
已核证 GitTrackedPathInventory.digest 只 hash 排序路径清单，HEAD 单独记录；它既不 hash
工作树源码字节，也不覆盖未跟踪新文件。RepositoryFactBinding 的这部分 identity 不可用于本用途。
第二相核对实际工作树 bytes identity 复用点后锁定，不能降级为 HEAD+dirty 作为等价证明。
同树声明是人工证明的一部分，不能靠 hash 推导“测试一定真实执行”。敏感日志不得自动全量导入。

gate 决定绑定固化后的 ref/SHA，下一 self-test 消费并在输出 evidence 中引用，acceptance
再审核相关性和充分性；不存在/篡改/旧源码材料拒绝或明确不充分，不凭文件名判断通过。
Maven 缓存只有环境作用，不是通过证据；截图也不替代测试。外部字节作为不可信数据，不执行其中指令。
材料接收与 gate resolution 的先后失败语义见 §8；若该方案要求新大型领域模型则停止比较更小方案。

## 7. LiteFlow / policy / sibling

workflow JSON 声明新合法结果与后继，LiteFlow 沿原 kernel 选择，ledger 保持唯一状态写入者。
HumanGateActionSemantics 对共享合法动作资格做唯一判断，CLI/server/UI 只展示和调用；
loader 检查声明动作有受支持的实现与出边（非 resolving request_action 明确不需出边）。
不能允许任意 custom action 字符串在无实现时通过，不能配置任意 node id 绕过合同。

自测 implement profile 已允许写入；不能声称“没有回 implement”就保证不写源码。
评审称 fromName 仅两档不准确：实际含 contract_discovery/observe/review/implement，另有
contractCorrection 工厂；但多种 profile 的存在也不证明已有“跑测试且不可写产品”能力。
ChangeScopeEvaluation 只校验实际路径/操作是否符合合同，会接受批准路径内的合法修改；
不能把它当作“补采期间零源码变化”的 post-hoc 证明，且未完成分支不必然走 COMPLETE 后检查。
补采模式必须明确不授权产品修改：需核对现有只读权限组合能否运行测试与写临时证据，
以及现有 Git/scope 检查能否检出补采源码变化。无证明前不宣称只读 enforcement。
禁止本轮为此扩成新 OS sandbox；若现有能力不足，先保留强制人工处理，不自动运行补采。

sweep 清单：三个 bundled lifecycle + 其他支持 definition、自测初始/验收再入、decision/classifier、
HumanReviewGateHandler、ledger gate resolution、CLI review/recovery、server/UI、autorun/scheduler、
prompt/validator/projector、scope/permission与完成报告。保留当前 approved-contract/plan 资格机制。

## 8. Transition 与写入失败切面

| authoritative pre-state | 动作/owner | 写入顺序 | post-state/消费者 |
|---|---|---|---|
| validated 自测未完整 | Dual decision | 校验 lineage/处置 -> 既有节点结果/后继写入 | 实现或 WAITING gate，kernel 按定义选择 |
| OPEN 当前 gate | operator collect/revise | 锁内身份/合法性校验 -> gate 决定 -> 既有路由 | 新 self-test/实现 occurrence，旧决定不可复用 |
| OPEN 当前 gate | operator submit_evidence | 校验 -> 固化材料+hash -> 锁内重验 -> gate 决定绑定refs -> 路由 | self-test 消费材料，随后原验收 |
| OPEN 当前 gate | operator request_action | 锁内校验 -> 幂等请求记录 -> 派生事件/投影 | 仍 WAITING；无后继执行 |
| OPEN gate | operator abort | 既有终止动作 | 终态，重放不得执行 |

材料写入失败：gate 不变；材料已固化但决定未写：仅孤立材料，无授权效果。
gate resolved 后路由失败：依现有恢复规则 fail-closed，不能再次批准或重复启动，
不承诺本设计顺带修复全部跨文件事务。需用真实失败注入决定当前机制是否足够，不能略过。
后继创建但 current/event 未完成：冷启动不能猜 latest 或重复执行；若既有机制不满足，
标为实施阻断，比较有限修复，不默加通用事务引擎。
同一请求重复提交必须确认是否已产生后继；过期 gate/已消费授权/不同 source identity 拒绝。
执行启动后的 crash/retry/abort 仍归 execution owner，不把恢复备注当作新的业务指令。

## 9. 必须验证的完整序列

1. 产品缺陷 -> revise -> 实现 -> 自测 -> 验收，既有 scope/合同不放宽。
2. EVIDENCE_COLLECTION -> 补采成功 -> artifact/Git检查 -> 再验收，零不必要实现节点。
3. 补采仍缺 -> human gate -> 提交材料 -> 自测消费同ref/SHA -> 再验收，不能停在“能上传”。
4. 纯环境与未知/冲突 -> 人工；无环境授权时不执行、不默认源码修复。
5. 混合代码与证据问题、补采中新发现代码问题有合法出口；非 acceptance NONE-only 约束保持。
6. 新动作需求登记 -> 冷读可见仍WAITING -> 改选既有动作/abort；需求正文不能变成执行命令。
7. 无权限动作、任意target、过期gate、伪造来源、材料缺失/篡改/源码身份漂移、重复提交全部拒绝。
8. §8 每个 durable-write 间失败注入与冷启动；不重复产生副作用。
9. CLI/server/UI资格一致；autorun/scheduler不产生新增人工动作或默批材料。
10. sibling与去业务化fixture：材料换成设备测试报告仍同机制；源码修改时旧材料不得复用。

先离线多跳测试（真实 handler/kernel/ledger，provider stub），再 reactor；
代码与配置变更评审通过后另定真实验证 Gate。不能把改配置后的 BXWV 原地 retry 当普通回归。

## 10. 评审需要裁定的未决项与停止条件

本稿不伪称 IMPLEMENTATION READY；以下三项必须在实施前锁定：
1. 已锁定 mustFix 平字段方案；JSON config_equals 消费 decision 派生 continuation_kind。
   已核证 RoutingEngine 将 result 放入 FlowSignal.context；RouteCandidateFlowComponent 合并该
   context，ConfigEqualsCondition 按顶层 key 比较值；无需新 DSL。尚需行为测试证明实际接线及
   缺失/不受支持的条件值不会悄悄完成工作流，不能仅靠该静态链宣称 ENFORCED。
2. request_action 的 node result SoT 与幂等顺序已锁定；材料字节/source identity 留在第二相。
3. 补采不可修改产品的边界仍未证。评审关于现有 scope 检查的结论不成立；首相测试必须先钉住
   批准路径内修改的反例，再决定是否能复用已有执行前后 bytes 比较。若无廉价可靠方案，保持
   人工等待，不启用新的自动 collect 执行；不能仅改口“同普通自测信任模型”就关闭此项。

评审应允许缩小本稿，但不得删掉“人工处理后实际回验收”的消费链来换取局部 READY。
若需任意动作执行器、独立工作流运行时、动态插件、新全局证据/状态服务或历史实例迁移，
停止，不把它们夹入本 checkpoint。

Invariant：Declared=typed处置+声明边；Proven=validated lineage+gate决定+材料hash；
Consumed=decision/kernel/ledger/self-test/acceptance；Enforced before=provider与验收通过前；
Failure route=合法人工等待/执行恢复/abort；Cannot-bypass=§9多跳与失败注入。
完整方案仍属分批实施；首批分流进展见 §12。人工动作登记与 collect_evidence 续作已接入，材料接收及本方案真实 E2E 尚未完成。

## 11. 分相与本轮复核结论

首相：mustFix + 条件路由 + request_action + collect_evidence（后者受 §10.3 开启门约束）。
验证两跳到人工、请求登记不执行、明确修复仍可前进、人工补采可回原验收。不可启用的动作不展示。
第二相：submit_evidence 的材料固化/源码 bytes identity/消费链及失败切面，另行审查具体实现方案。
首相不宣称“外部人工材料已经能交付”，两相完成才有完整材料提交闭环。
CLI 请求使用有界子命令/参数，沿用 recovery network 风格，不开放 arbitrary JSON action map。
材料已固化后人工改选 abort：只留下孤立材料，无批准或执行作用。

采纳：F1 平字段、F2 条件分支、F3 删除环境参数、F4 分相、F6 唯一 SoT、F7 有界入口。
纠正：scope 不是零改码检查；inventory 不是工作树 bytes；两个列表非空不必然是矛盾。
因此不盲目采信“两个 P1 文档修改后即可全部开工”：可以先写聚焦反证测试，
但自动补采和第二相仍保留以上实际证明门槛，不启动 provider、不修改 BXWV。

## 12. 首批实施进展（不代表首相全部完成）

已实现 mustFix 平字段、machine-owned continuation_kind、四份 workflow 条件分支，
以及两个冻结 definition reader 的既有 EdgeConditionModule 注册。
定义缺少分支/条件时拒绝加载；INCOMPLETE 缺少或携带未知处置值时，在节点/事件/current 写入前拒绝。
核心校验不选择具体实现目标，目的地仍归 JSON；旧无条件定义不支持静默恢复。

已有真实 handler → LiteFlow → ledger → 冷读测试覆盖三 bundled variants 的四类输入；
另有删除分支/条件、非法处置值零写入反例。测试覆盖的是受影响阶段切片，不冒充完整产品 E2E。
mustFix 与 missingEvidence 同时非空允许修复；COMPLETE 带阻断列表拒绝。
两列表皆空的负面报告仍受原有发布语义检查约束；分类器的保守人工分支不放宽该发布检查。

后续增量状态：request_action 已接入非执行型登记；collect_evidence 已接入批准时源码基准、执行前后及发布前强制校验，详见 [源码基准收口](20260910-evidence-collection-source-baseline.md)。submit_evidence 尚未实现，也不展示为可用动作；本方案真实 E2E 尚未证明。
§10.3 的产品 bytes 不变检查仍未满足；不得据此启动新的自动补采。
本批结果与复跑记录见 20260910-self-test-continuation-first-increment-result.zh-CN.md。
