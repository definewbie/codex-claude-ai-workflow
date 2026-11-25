# Dual 流程续作与 LiteFlow 委派有界评估

日期：2026-09-10。状态：ASSESSMENT / 非实施计划。
主 agent 只读代码核对与一手文档研究；仅新增本报告。无额外 agent、provider、real-case 或生产改动。

## 1. 结论与评估边界

目标不是穷举所有异常，而是让未知/含糊情况也有安全、可解释、可恢复的出口，
避免不完整证据自动变成代码返工；同时降低修复后的真实 provider 回归成本。

建议：收敛现有 typed 决策、JSON 声明边和生命周期守卫，加离线多跳回归；
不单独追加一个 OKX 分支，不把 ledger/审批/权限整体迁进 LiteFlow，不增加第二 DSL。
该建议仍需后续生产者/消费者映射与失败切面测试设计，不声明 Implementation READY。

已核对：FlowOrchestrationKernel、LiteFlowElCompiler、LiteFlowPrototypeKernel、
RouteCandidateFlowComponent、EmitFlowComponent、ReviewDecisionHandler、SelfTestResult、
RoutingEngine 完成/预算/后继创建段、WorkflowFileLedger 定义持久化、三个生命周期的相关声明边；
并对照 BXWV 最终记录及此前 CC 整体评估。
未完成全仓所有异常出口、所有 UI/CLI 恢复动作、断电恢复与幂等性审计；不以本报告冒充全仓 PASS。

## 2. 代码事实：当前并非没有配置化

| 层 | 实际机制 | 本次意义 |
|---|---|---|
| 定义 | EdgeDefinition(from,on,to,condition,metadata)，节点含 handler 配置 | 已有 JSON DSL，不需要新语言 |
| 编译 | LiteFlowElCompiler 按 from+on 分组为 THEN(prepare,edge...,emit) | 当前委派的是一次路由计算，不是长流程活动执行 |
| 内核 | LiteFlowPrototypeKernel.advance 调用 FlowExecutor；edge component 计算条件 | 引擎会执行声明规则，不理解缺证据与代码缺陷的业务差别 |
| 决策 | ReviewDecisionHandler 从 sidecar 提取 decision/source/summary | 本次细分意图未成为后续选择依据 |
| 实例 | RoutingEngine 写节点、调用 kernel、检查预算、建立 iteration/后继、更新账本 | 仍有流程控制代码；其是否必要须按职责判断，不能按代码量判断 |
| 定义版本 | WorkflowFileLedger.initialize 保存定义，loadDefinition 读实例内副本 | 改仓库 JSON 不等于改已运行实例；schema 迁移也不等于流程语义迁移 |

本次失败：验收 EVIDENCE_COLLECTION -> 自测 SELF_TEST_INCOMPLETE -> 无条件实现返工。
第一跳正确，第二跳没有保留完成当前任务所需的处置区别。不是 LiteFlow 执行错误。
此前 CC 报告覆盖设计阶段，不能拿其当时对未运行后半程的判断否认后续实证。

## 3. 行业机制的可借鉴部分

取证时间为本报告日期；本仓 LiteFlow 依赖 2.16.0，在线文档可能更新，不能代替本仓运行证明。

- LiteFlow 的[平滑热刷新](https://liteflow.cc/pages/204d71/)区分正在执行的旧流程与后续请求。
  热刷新证明规则可以更新，不证明 Dual 已运行到一半的账本、合同批准与新流程自动兼容。
- LiteFlow 的[组件回滚](https://liteflow.cc/pages/y172l7/)提供异常时逆序调用 rollback 的机制。
  回滚业务逻辑仍需组件提供，不能自动撤销已调用模型、文件编辑、人工决定或外部交付。
- AWS Step Functions 的[redrive](https://docs.aws.amazon.com/step-functions/latest/dg/redrive-executions.html)
  支持保留成功步骤、用原输入重跑失败步骤，但明确复用原定义；改变定义需要新执行。
  这正说明“局部续跑”和“改定义后续跑”不能混为一个能力。
- Temporal 的[安全部署与 replay](https://github.com/temporalio/documentation/blob/main/docs/develop/safe-deployments.mdx)
  把旧 history 与新实现的兼容性作为需验证事项，而非配置化自然带来的保证。

本报告的工程判断：借鉴显式处置、版本绑定、活动与调度分离、历史回放测试；不引入这些产品。
业务拒绝、缺材料不是 Java exception；把它们统统放进 CATCH/retry 不会解决错误返工。

## 4. 三方案比较

| 方案 | 收益 | 代价/失败面 | 判断 |
|---|---|---|---|
| A 只给本次缺浏览器/API 增加特判 | 立即可改 | 新原因再加分支，无法保证未知情况出口；业务名污染 harness | 不采用 |
| B 现有模型与配置中的处置语义收敛 | 流程目的地可配置，未知情况安全等待；保留现有账本与授权 | 需核对实际字段、多个节点再入和人工证据消费，不能只改一条边 | 推荐 |
| C 把活动执行/等待/恢复全部交给 LiteFlow | 可能减少自有编排代码，单图表达更集中 | 必须重新证明跨进程等待、幂等、取消、人工批准、终态、历史版本和失败写入；若仍调用原 ledger 只是换包装 | 当前不采用 |

工作量仅作相对比较：A 小，B 中，C 大且不确定。未完成全出口 sweep，不给精确人日承诺。
C 若未来能以一个跨人工等待/重启/副作用的离线 slice 证明实际删除自有机制且保持语义，
可重新比较；本轮不启动该实验，不为比较预先实现第二运行时。

## 5. 通用性：按处置类型，而非故障名称

下表是检查维度，不是要求新增六个 enum/文档/服务；优先映射已有 contract、failure、gate、retry。

| 情况 | 合法处置 | 不能做什么 |
|---|---|---|
| 当前工作完成且证据有效 | 正常前进 | 不能仅凭模型自述跳过验收 |
| 有具体产品修改项 | 对应作者修订/实现修复 | 不把环境问题当产品修改项 |
| 仅需获取/运行验证证据 | 补证据，再回原验收 | 不因再次缺证据自动回实现 |
| 需要环境、权限或人工材料 | 既有人工/恢复入口，明确下一动作 | 不默认 grant、不伪造材料、不无限轮询 |
| 执行失败、没有可用业务结果 | 按既有执行失败与 retrySafe 处理 | 不将语法/进程失败当业务 REVISE |
| 未知、冲突、无法可靠分类 | 停止自动副作用并暴露不确定性，允许合法人工裁定/abort | 不默认代码返工，不自动 approve |

通用性检验：浏览器换成硬件测试、API 换成企业凭据、依赖缓存换成编译工具，处置仍成立。
不解析具体文件名、域名、HTTP 文案或模型品牌做流程选择。
混合“代码缺陷+缺证据”不能被证据模式掩盖；分类错误仍可能发生，不能承诺自动识别所有原因。
未知情况被安全承接不等于任务自动完成，人工仍是有意保留的兜底。

## 6. 推荐职责边界

typed 事实/合法动作资格（Dual contract/validator）
  -> JSON 中声明 outcome/条件/后继（唯一业务目的地）
  -> LiteFlow 计算候选转换
  -> ledger 验证权限、预算、实例状态并持久化
  -> 既有 handler/adapter 执行动作。

代码保留“是否允许做”，配置表达“允许时往哪里去”。不得另建并行路由表。
新增路由条件若输入事实尚未建模，必须先明确 owner；不把自然语言 summary 当条件。
终态、合同批准、权限与证据 hash 守卫不能变成任意脚本可绕过的配置。
预算耗尽映射 BLOCKED 与安全拒绝可以保留核心语义，不要求所有 if 都迁入 DSL。
本轮不变更 provider、网络、产品合同、交付权限，不增加脚本引擎/可视化设计器/通用 evaluator。

## 7. 从错误节点回归：支持目标，区分三件事

1. 离线路由回归：用冻结输入/sidecar/history 的测试副本跑新规则，不调用 provider。
   应走真实 handler -> kernel -> ledger -> 后继，而非仅断言一条边。跨至少两跳到再验收/人工出口。
2. 同定义执行 retry：输入/批准/副作用仍有效时，按现有合法 retry 机制执行新的 run。
   不是重置旧成功节点，也不是重新使用已消费授权。
3. 改定义恢复旧样本：必须判断上游批准、输入引用、schema、已有副作用是否仍兼容。
   本轮不承诺支持，不手改 BXWV frozen definition；若恢复需要迁移，独立比较 fresh/离线迁移成本。

所以无需每次跑一遍昂贵 provider E2E，但也不能完全取消全链回归：
每次改动先离线局部多跳与全图 sibling 测试；共享 contract/ledger 变更仍跑 reactor；
最终以一次真实 E2E 验证实际材料、工具和交付链。离线绿不冒充自然 E2E PROVEN。
读取 captured 事实仅作测试输入，缺少必要 authority 时应拒绝；不得靠伪造历史获得通过。

## 8. 下一步及闭环验收要求

只制定一个有界实施计划：盘点自测/验收/修复再入的现有类型，确定最少缺失事实、合法人工证据输入
和环境配置消费点；形成完整 transition table。阶段目标不是“修改 JSON”，而是：
证据补采失败不启动无依据源码返工，完成补采后能回验收，未知情况有明确合法出口。

| 维度 | 计划必须给出的证明 |
|---|---|
| Declared by | 现有 typed contract + 唯一 workflow 定义，未知/混合结果处置明确 |
| Proven by | validated sidecar、原验收 lineage、必要输入与审批引用；不靠正文猜测 |
| Consumed by | decision -> LiteFlow -> ledger -> 下一 handler，二次再入不丢语义 |
| Enforced before | 在启动写入型 provider、交付动作或消耗 grant 之前拒绝不合法转换 |
| Failure route | 证据不足/环境等待/执行失败/未知/取消各归原 owner，不混用 retry 和 revise |
| Cannot-bypass | 正常、代码修复、补采成功、补采仍缺、混合、未知、人工拒绝、篡改/过期输入与冷启动多跳测试 |

写入切面：读取输入失败不得启动动作；结果已落盘但后继未写、后继已写但 current 未更新，
必须沿已有恢复机制测试；不在本轮顺带引入通用事务日志。若该切面无法安全支持，明确停止，不伪造闭环。
反证：去掉处置保持/未知守卫必须使测试失败；三个 lifecycle sibling 与 custom definition 明确支持边界。
历史 snapshot 回归与新真实 provider 执行分开记录。BXWV 保持只读事故证据。

评估结论：PROCEED_WITH_GUARDS（进入有界设计），不是全量迁移授权或实现已就绪。
