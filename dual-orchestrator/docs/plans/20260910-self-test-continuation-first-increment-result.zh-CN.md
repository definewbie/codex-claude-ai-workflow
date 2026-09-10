# 自测续作首批实施记录

日期：2026-09-10。状态：首批实施、完整 reactor 与自查完成；未提交，完整方案未完成。
这是主 agent 自查，不是独立同行评审。未启动额外 agent、provider 或 real-case；BXWV 未修改。

## 目标与实际范围

修复“仅缺验证证据也自动回实现”的错误步骤，不增加网络、模型层或新的执行框架。

```text
validated self-test sidecar
  -> decision 派生 continuation_kind
     -> 完整：既有 artifact/Git 验证 -> acceptance
     -> 明确代码问题（可同时缺证据）：既有实现修复
     -> 仅缺证据：既有 human repair gate
```

模型只提供 must_fix/missing_evidence 事实；handler 派生处置值，JSON 定义目的地，
LiteFlow 评估既有条件，ledger 产生实例。COMPLETE 带阻断项不得发布/路由为成功。
scope 检查不是产品 bytes 不变检查，本批未改变自测运行的写权限。
must_fix 的内容仍来自模型判断；本批保证按合法结构化事实分流，不保证模型对缺陷的语义判断永远正确。
新规则没有业务名称、域名或特定产品文件路径，适用于同一自测契约的其他项目。

## 必要的同边界调整

- 三个 bundled lifecycle 与 small experiment 同步条件边，无新增节点。
- TaskContractRevisionContextResolver 与 CompletionEvidenceLineageResolver 补注册既有条件解析器。
  全量测试实际暴露前者无法读取带条件的冻结 definition；同边界扫描确认后者同样需要注册。
- SelfTestContinuationSemantics 在定义构造时拒绝缺失/重复/无条件的 INCOMPLETE 分支；
  RoutingEngine 在第一次 durable write 前拒绝缺失/未知 continuation_kind。
  不增加目标表、动态动作或另一套工作流运行时。
- runtime 仅增加 test-scope LiteFlow 依赖，用真实 kernel 测试，不新增 production 模块依赖。

## Transition 与边界

| 环节 | 事实与结果 |
|---|---|
| pre-state | 当前自测 decision occurrence active，输入为已发布的 source/sidecar refs |
| owner | 自测 producer 提供结构化事实；decision handler 唯一派生处置；definition 拥有目标 |
| 新增拒绝点 | 定义构造检查；完成节点前的处置值检查，无新增 durable 文件 |
| 写入顺序 | 通过检查后沿既有节点完成、后继创建、事件/current 更新顺序，不重写路由事务 |
| post-state | 验证节点、实现 context 或 human gate 成为后继；原 refs 继续路由 |
| 冷读 | 从同一磁盘账本重建后继；未知处置被拒时 current/节点/事件均不变 |
| 旧实例 | 无条件 SELF_TEST_INCOMPLETE 定义被拒绝；不补默认值，不迁移、不恢复旧样本 |
| 失败窗口 | 未新增跨文件提交协议；既有路由写入间 crash/liveness 不在本批完成声明内 |

Declared by：SelfTestResult + 条件 definition。
Proven by：source/sidecar lineage + typed lists。
Consumed by：ReviewDecisionHandler → LiteFlow → RoutingEngine。
Enforced before：语义发布及 decision 完成写入前；不满足时不得自动产生实现后继。
Failure route：无效输入走既有拒绝/执行恢复；合法证据不足进入人工 gate。
Cannot-bypass：分支删除/条件删除拒绝、处置缺失/未知零写入、真实阶段序列冷读。

## 测试记录

- 初始三个聚焦测试：两个按预期失败（字段缺失、处置缺失），一个 scope 特征测试通过。
  后者专门证明批准路径内修改仍可通过 scope，因此不能据它声明补采无写入。
- SelfTestContinuationSequenceTest：三个测试，其中序列测试执行三 variant × 四输入；
  包含真实 handler/kernel/ledger 与冷读，并保留空 note 拒绝。不是整个 human 材料提交流程。
- 新 guard 聚焦：WorkflowFileLedgerTest 50、AuthoredBoundarySequenceTest 14、续作序列 3，全绿。
- guard 增量前完整 reactor exit 0；最终 guard 增量后完整 reactor 同样 exit 0。
  Surefire XML 聚合：172 files，1446 tests / 0 failures / 0 errors / 0 skipped；git diff --check clean。
  最终运行日志：/private/tmp/dual-continuation-reactor-final.log（本机诊断日志，不是版本化 authority）。
- 一次受限环境复跑遇到 ProcessHandle.descendants 的 sysctl 权限拒绝；未跳过该测试，
  已改在获准的本机环境运行。早期 definition 解析错误与旧边数/hash 断言均已修正。

## 剩余边界与下一步

当前只证明首批分流，不宣称整个首相或自然 E2E 完成。
首批处置派生/声明校验/路由前拒绝：ENFORCED；正常阶段序列与冷读有离线测试证明。
完整人工处置闭环与自然 E2E：NOT PROVEN。
request_action 登记已在后续本地增量实现，见 `20260910-human-action-registration-result.zh-CN.md`；
本首增量收口时 collect_evidence 人工续作、submit_evidence 材料消费均尚未实现。后续 collect_evidence 已接入批准时源码基准与前后校验，见 [源码基准收口](20260910-evidence-collection-source-baseline.md)；submit_evidence 仍未实现。
自动补采的产品 bytes 不变检查仍是开启条件；inventory SHA 不能替代它。
先完成本批最终测试与自查，再推进有界人工动作及材料消费；不因局部分流通过就启动真实样本。
