# 受审验证义务与实际证明入口

这是作者覆盖索引，不是完成率或独立评审结论。最终测试状态只认 test-results.json 和 reactor-final.log；设计包的 ALL NOT RUN 保留为设计提交时历史。真实客户端全部未运行。

| 受审义务 | 本轮可复核入口 | 证据边界 |
|---|---|---|
| V01 / V02 / V04 | HumanRequirementContinuationSequenceTest.goalReproposalReachesNewReviewHumanApprovalAndBothPhasesAcrossAllVariants | 四实际 variant × inline/worker，初始→full→patch→新复评/人门→GOAL→设计/计划→EXECUTION；拒绝未批写资格。模型为离线 transport |
| V03 | humanGoalRevisionUsesFreshSubjectAndMissingOrForgedFormsNeverPublish | 原人门 revise 的实际新 subject→复评→当前人门；后来 clarify/full 拒绝，不能用结构类比代替该入口 |
| V05 | 上述合法链、ColdRevisionRead、原 HumanRequirementContinuationSequenceTest、MergerTest | 历史后来批准/追加之后读初始/full/patch；不宣称全部现有冷读组合均换新形态 |
| V06 / LOW-1 | ProposalReferenceIdentityTest、RevisionIdentityWireTest、MergerTest、humanGoalRevisionUsesFreshSubjectAndMissingOrForgedFormsNeverPublish | 完整 reference 相同/冲突及反向顺序、合法 ID/错 ID、完整基准变字节；跨 claim/workstream 保留原协议守卫和既有 suite，不冒称新增每个交叉组合 |
| V07 | fixedRevisionInputsRejectDriftBeforeDispatchAndPublication；interruptedPreparedBindingCannotDispatchAndOnlyFixedBytesMayBeCompleted | 专用新反例为基准字节漂移、删除 prepared；新 R01 在 L bind 三关键前缀实际中断、零模型。其余固定 input 身份/claim/R02 沿原共享协议测试 |
| V08 | RevisionIdentityWireTest、原 strict parser/envelope/correction suites | 新旧 wire 精确区分，含第五例 SHA 错字；不为旧 raw 静默去键或回填；correction 不冒称真实客户端行为 |
| V09 | goalReproposal... 的四个 source corruption、reference/lineage/compile suites | missing-R01 保持 sidecar 字节，只删除 prepared；其他伪派生保持真实引用守卫链。若较早 SHA 守卫先拒，不冒称独立测到所有后层 guard |
| V10 | humanGoalRevision... 的 same-goal/wrong-id/delete-scope/delete-clause/change-question；原 revision coverage/target suites | 专用新形态与既有 delta/义务拒绝分列；不是所有字段/排列/形态的笛卡尔积 |
| V11 / V12 | humanGoalRevision... 的 late clarification，原 qualification/late/revision/display/compile suites；合法序列中的未批 EXECUTION | 当前与历史授权边界共同验证；旧展示测试移到真实的更早 required-subject 拒绝，未松规则 |
| V13 | 原 scope/readiness/退出码重批/证据/批准 suites，在最终单轮重跑 | 本轮不改这些 owner 或验收语义；不把共享回归当真实工具能力 |
| LOW-2 | AgentOutputContractDocument.ProposalDerivation 注释 | 历史字段 patch_source_output_ref 表示 authored revision output，包括 full；无字段迁移 |
| 新字段写前缀/重启 | interruptedPreparedBinding...；revisionCompletionPrefixesReplayInColdJvmWithoutRedispatch | 两形态 × 双入口 × R01/L03 各三关键前缀；R03 已固定后真实子 JVM 恢复下一 review context，零 transport。其余共享写点没有另行穷举所有新组合 |

删除守卫的三个隔离实验见 counterproof-results.json。完整 reference、程序 SHA、full 必须有目标实变分别有独立预期失败；不加入最终 suite 数量。提示词正向 fixture 的 typed raw 迁移是合成来源，不是原生输出实证。自查不替代 CC 检查实际断言、拒绝时点或遗漏条件。
