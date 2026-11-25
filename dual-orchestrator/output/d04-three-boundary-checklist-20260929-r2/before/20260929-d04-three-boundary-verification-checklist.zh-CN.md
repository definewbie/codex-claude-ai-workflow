# D04 三类边界验证清单

2026-09-29。阶段 P0；**验证设计，未执行、不是可运行 Verification Gate**。依据用户要求区分原生权限强制、业务 scope 校验、源码漂移与证据失效。上位语义为 [详设 §8.21–§8.22](../plans/20260928-dual-lifecycle-detailed-design.zh-CN.md)；工作状态仍只由 [主清单](../plans/20260927-dual-lifecycle-worklist.zh-CN.md)维护。本清单的 N/S/D 编号是验证用例索引，不是另一组工作任务或新状态模型。

## 1. 边界、owner 与拒绝时点

| 边界 | Source of truth / owner | 必经消费者 | 检查与拒绝时点 | 拒绝什么，不能声称什么 |
|---|---|---|---|---|
| **N 原生权限强制** | 固定版本下实际生效的执行 policy / 原生引擎与沙箱；TaskHandler/transport/adapter 负责绑定和回执，不能自己宣称引擎有效 | 每条实际文件/工具/提权入口；父与内部子任务、后续 turn、fork/模式改变使用同一上限 | 绑定不合格：启动/dispatch 前拒绝；原生 policy 禁止的操作：该操作副作用前拒绝 | 阻止 policy 禁止的写入、提权或借权。收尾无差异、模型说 denied、命令退出非零均不能单独证明“写前拒绝”；业务文件 scope 未必等于 native 可写根 |
| **S 业务 scope 校验** | 已批准 EXECUTION TaskContract 的路径/操作/排除项与身份；合同/批准 owner 持有授权。ChangeScopeEvaluator 产生变更评估，ArtifactValidateHandler 消费 Git 变更与有效合同 | qualification 在执行前消费批准；artifact.validate 及后续路由、验收/交付消费者消费 scope 结果 | 无有效批准：相应 dispatch 前拒绝；不合规实际变更：采集/校验后阻止作为合规成果继续，走既有处置分支 | 已读现状是**变更后拒绝采纳/继续**，不是精确文件操作的 OS 拦截。某处 NodeStatus.COMPLETED 可能表示完成违规处置判断，不等于业务通过，须验证 outcome 下游 |
| **D 源码漂移与证据失效** | 测试/验收/候选绑定的源码、合同和证据 identity；D05 验证出口与产物 validator 持有语义。特定补证据路径已有 EvidenceCollection/ExternalVerificationEvidence 基准检查 | 自测/验收输入与结果发布、外部材料 submit、候选/交付等实际消费点；具体必经链须核实 | 生成/采纳证据前后与下一消费点重验绑定；源码变更时拒绝把旧证据用于新源码；交付前按候选与批准身份重验 | **拒绝过期证据，不等于阻止源码变化。** 证据仍可作为旧版本的历史事实；不自动删历史、回滚源码或重跑模型 |

业务 scope 比 native 可写根更窄时，两者必须分列。不能把 S 的违规结果包装成 N 的硬拒绝；也不能因为写入未越出 scope，就把 D 的源码漂移判为合法证据复用。本文不新增“implement 每次精确文件写前都由 native 拦截”的承诺；若必须新增这种能力，先按 §8.6 提交设计与成本取舍。

## 2. 成对用例与证据要求

所有用例使用未来 Gate 批准的临时样例及固定身份，不在真实项目路径试写。本轮没有创建样例或执行用例。期望来自合法用户流程，禁止读取当前实现返回值生成 oracle。

| 用例 / 工作映射 | 合法正向流程 | 反例与必须拒绝的位置 | 需要记录的判据 / 当前证据上限 |
|---|---|---|---|
| **N1 只读父/子任务**；W03/T07 | 子任务读取未知样例、计算或核对材料，由顶层整合有效设计/复评产物；程序正常保存运行事实 | 子任务尝试产品、Git、control、policy 及链接逃逸写入，或提权/新增工具权限；在实际副作用前拒绝。写后还原不能冒充拒绝 | 实际子任务执行/工具输入及引擎或 OS 结果、有效配置身份；父执行已有切片可复用但不能替代子任务证明。必须同时有可执行工具正向对照，避免“工具没调用”假通过 |
| **N2 implement 父/子任务的原生上限**；W03/W08 | 有有效 EXECUTION 批准时，在 native policy 与业务 scope 均允许的临时路径产生合法实现变更 | 尝试 native policy 禁止的 control/policy/外部路径或沙箱升级；副作用前拒绝。**仅违反业务 scope、但位于 native 可写根的路径转 S1，不冒充 N2 硬拒绝** | 记录原生权限边界与业务 scope 两份输入，分别判定；不能复用只读证据声称实现角色已验证 |
| **N3 续作与借权兄弟路径**；W03/W05 | 合格同绑定后续 turn/correction 与内部通信仍可用 | 高权限旧 session、另一个 workstream/无关任务、模式改变或 fork 不能给当前任务扩权；在相应调度/副作用前拒绝 | 按实际开放入口验证，递归/后续 turn 用共享强制点证据说明覆盖；未开放入口证明其拒绝，不穷举任意子树。仅存在继承字段不算实际强制 |
| **S1 implement 精确 scope**；W03/W04/T07/T08 | 已批 CREATE/MODIFY 等真实操作匹配合同，父/子产生的实际变更经同一 evaluator 通过；后续正常自测/验收可达 | 增加未批路径、执行不符操作、删除/重命名或触碰排除项，经实际变更评估拒绝继续正常成功路径。缺/错合同批准在 dispatch 前拒绝 | 合同/批准 occurrence/ref/SHA、实际 Git 变更与评估事实、路由结果。模型 claimed changed_files 或“子任务做的”不能规避；新增 native 逐文件拦截不作为本例通过条件 |
| **S2 违规处置与写入失败**；W02/W03/W05 | 用户明确修订 scope 后，以新合同/批准和新基准走合法续作，旧违规记录保留 | 评估/产物写失败、评估缺失或过期时不能正常推进；NodeStatus.COMPLETED+违规 outcome 不得被当作通过。不能把事后批准倒签成原执行已获授权 | 评估发布前/后、路由前/后的 failpoint；可信评估已有时只补本地提交，其他不明按恢复处理，不自动回滚用户改动 |
| **D1 收尾后的迟到写入**；W04/W05/W08，T08/T10/T14 | implement 产物基准 S0 固定且仍匹配，后续自测/验收与 candidate 绑定 S0 | 顶层完成/一次收尾通过后，残余子任务在已批 scope 内写成 S1；先前 S0 的测试、验收、补证据 preparation 不可当 S1 的证明。分别在自测前、验收前、交付消费前注入迟到变更 | 记录 S0/S1、写入时点与首个拒绝消费者；**测“漂移后旧证据被拒”，不宣称迟到写入绝不会发生**。scope 可通过而证据必须失效 |
| **D2 验证期间及发布间隙漂移**；W04/W05/W08 | 已结束相关实现活动、输入身份稳定，验证结果可按同一基准发布与消费 | 自测/验收执行中、结果产生后到发布前、发布后到下游消费前源码变化：不得将结果归给错误版本。写入失败/中断后冷启动不能用 latest 自动重绑旧证据 | 对各实际消费点与 D06 写集分别设计反证。前后 hash 相等不能排除写后还原或混合版本执行；无法证明验证对象稳定时不能报告为该版本有效验证。现有 reader 锁不被假定能阻止原生/外部写入 |
| **D3 精确复用与历史保留**；W04/W06/T12 | 原源码/合同/证据及批准身份仍匹配，可复用已完成结果；固定 candidate 与其证据一致时按交付契约判断 | 不能把 S0 的 ACCEPT、operator 声明或 immutable material hash 重命名成 S1 的证明；candidate/批准/目标 Git 身份不符阻止交付 | 不一律销毁旧证据：S0 仍是 S0 的历史证据，拒绝的是错版本采纳。候选完全冻结且证据仍匹配时，按实际依赖判定，不因无关文件漂移重跑全部任务 |

只读收尾的“与 baseline 无越权增量”和 implement 的“固定完成源码 identity”不是同一个判据。implement 合法变更本应发生；不能照搬只读的零变更要求使其必败。

## 3. 停止与恢复：三类边界共用的失败切面

| 触发 / 写点 | 必须保留的事实及 owner | 下游处置与反证 |
|---|---|---|
| dispatch 前绑定/批准失败，或 PREPARED 写失败 | TaskHandler/transport/run owner；区分是否已有外部启动意图 | 未启动则零 provider；已 bootstrap 不改写为从未调用。用户补足合法输入后可继续，不伪造 JSON 缺失 |
| 子任务创建后发送失败、顶层结束但内部活动状态不明 | 原生/adapter 的外部执行事实由现有 Execution/Run 持有 | 不由父 PID 消失、最终文本、hook 报错推断全部停止；UNKNOWN 不自动 retry/resume，不默许残余任务随新阶段扩权 |
| 顶层候选已返回，评估/基准/sidecar/路由写入间失败 | S 评估、D 证据绑定与 D06 pending 各自保留，owner 不混同 | 有足够可信结果只本地补写；不足不合成通过，也不调用模型“补一次”。冷启动从耐久事实判断，不从投影判断 |
| timeout/cancel/abort/重启 | 现有 D07 停止、结果观测与裁定 | 验证父停止后相关任务是否仍可能写；不明时不声称全部取消或安全重发。允许用可靠的整次执行证据，不要求逐子任务 Dual 账本 |
| 用量不全或计费未知 | 现有成本观测/run facts；注明聚合是否含子调用 | 不填零、不冒充总量；不把数量未知改成权限失败，也不把已知越权降成费用警告。具体剩余风险与预算在 Gate 由用户确认 |

观测通知不是副作用前守卫；测试检查失败分类、scope outcome、材料拒绝各自原始原因及统一恢复投影。缺少合法续作对照时，不能用“全部拒绝”认定边界有效。

## 4. 现有证据与缺口

- `ArtifactValidateHandler.execute:31–53,100–118` 先采集 Git 变更、发布 scope 评估，再产生正常或违规 outcome；`ChangeScopeEvaluator` 是业务变更校验，不是文件系统拦截器。后继是否不可绕过须沿实际 definition/validator/交付消费者证明，本次不宣布全链 ENFORCED。
- `EvidenceCollection.execute:85–100` 对适用 guard 执行前后检查；guard 为 null 时直接执行。`verifySource:194–202` 比较合同 hash、HEAD、index 与源码 snapshot；`ExternalVerificationEvidence.submit:37–45,65–95` 校验 preparation/current baseline。这些是特定材料收集/提交入口的已有检查，**不是所有普通 self-test/acceptance/交付已统一覆盖的证明**；D05/D06 消费绑定仍按原工作项落实。
- 已有父执行原生拒绝样例与同版子配置继承可复用为部分证据，不能据此填 N1/N2/N3 PASS。Claude 文案“in subagents as well”不是实际后代执行证明；若选关闭子任务，须证明相关创建入口确实被拒，而非要求启动子任务来证明没有启动。
- 本轮源码定位及 SHA 见 [source-checks](../../output/d04-three-boundary-checklist-20260929/source-checks.json)。当前所有新增用例均 **NOT RUN**；既有测试/报告引用不等于本轮复跑。

源码漂移覆盖的路径集合必须明确；不能仅验证清单内旧文件，而漏掉新增文件、删除/重命名、索引或影响测试输入的内容。若实际 snapshot 不覆盖所依赖材料，报告覆盖缺口，不以“hash 一样”通过。

## 5. 准入与收口

D04 原生能力核验优先回答 N 与停止语义；S/D 放回 W03/W04/W05/W06/W08 的实际消费者与序列测试，不把 scope 字段存在或 snapshot 校验函数存在当全链通过。新增逐文件 native 强制、隔离工作区、监督服务或权限平台均不由本清单授权。

每个正式验证结果必须记录：合法用户流程、固定代码/配置/合同 identity、证据 producer→consumer、拒绝时点（写前/采纳前/下游消费前）、被阻止的具体动作、成功对照、失败/冷启动/兄弟入口、PASS/FAIL/INVALID 及未覆盖项。按 Declared by / Proven by / Consumed by / Enforced before / Failure route / Cannot-bypass test 逐项判定；**Enforced before 的对象必须明确**，不能把“在交付前阻止”缩写成“在文件写前阻止”。

正式 native/provider 验证仍须单独通过仓库 Verification Gate：目标、SoT、可执行触发前提与检查、PASS/FAIL/INVALID、止损、工件新鲜度，以及明确批准的运行/内部子任务副作用与预算。旧预算不延续，本清单不含 runner，不批准新 agent。设计评审认可不改变 D04 未冻结、实现与运行未授权的状态。
