# 新接线实际消费者与失败切面

## 单一事实链

| 段 | SoT / owner | 写入与消费者 | 拒绝时点与出口 |
|---|---|---|---|
| 返修触发 | 已固定的 reviewer/human decision、iteration/route，ledger | 原 revise/NEEDS_REVISION 产生新作者 occurrence；resolver 的 scoped inspect 一次解析完整基准/义务/资格 | 无当前谱系或不合法历史，准备失败，无模型调用 |
| 准备 | Execution R01，AgentRunCommitProtocol | adapter 同一次 captured context 用于提示词与 binding.contract_revision；完整 ref/SHA、义务、允许形态固定；L claim 绑定 R01 | R01 orphan/pending 不授派发；不按模型 ID 搜全仓或取最新 |
| 派发 | 原 R01/claim + 当前 routed context | qualifyDispatch 原输入 SHA 检查及新绑定逐字段重核→原 R02 外部 intent | 基准/义务/资格漂移在模型调用之前拒绝；无自动重发 |
| 模型产物 | 原 raw envelope / strict parser | patch 只要求 ID；eligible full 使用同 artifact type 并带 ID。旧 SHA 键、重复键、未知字段仍由唯一 grammar 拒 | 原 parse/contract failure；不自动剥离历史 SHA 键 |
| 发布/合并 | ArtifactContractValidator + R01 | 当前完整基准与固定 R01 相等；merger 还读取精确 ref 校验完整 SHA；bounded full delta/coverage 守卫；唯一 outcome 先于 sidecar | 不合格形态/ID/内容/义务零 sidecar/reference/reviewer 推进；固定输入不可核时不捏造成功 |
| 派生与完成 | 原 sidecar/derivation + R03 | SHA 程序加盖；goal_reproposal/revision_patch_merge 保留完整 reference；R03 固定原输出，原 L03 唯一消费 | 无 R03 零完成采纳；中断按原 D06 固定字节、外部未知按 D07 |
| 冷读/审批 | reference/resolver/lineage→review/current human gate/compile | 固定 R01、claim、base bytes、raw 类型/ID、derivation、R03；完整 ref 冲突不按 ID 合并。目标级资格按作者历史截点 | 假 initial/kind、缺 R01、不受支持的旧 revision 不授资格；新 subject 必须复评/当前人批 |
| 后继 | 原 GOAL/EXECUTION qualification | GOAL included=[]→设计/计划/精确 EXECUTION 门→用户批准后才有写资格 | 不借旧 READY/旧人批，未批零实现；已授 GOAL/EXECUTION 不开放在线目标重写 |

## 新字段的实际前缀证明

常驻 `HumanRequirementContinuationSequenceTest` 同时覆盖 inline/worker：

- `interruptedPreparedBindingCannotDispatchAndOnlyFixedBytesMayBeCompleted`：R01 已固定后选择 L bind 的 before_manifest / after_replace / before_pending_delete，patch/full 意图各一组；新请求为零，原 R01 字节不因后续 writer 补齐而改变。不把 orphan/pending 当新调用资格。
- `revisionCompletionPrefixesReplayInColdJvmWithoutRedispatch`：新 R03 已固定后选择 L03 的三处关键前缀，两形态×双入口；真实子 JVM 只恢复固定完成，读 R01/derivation，恢复到 context.intake_codex_product_review，不持有 transport、不重新调用模型。
- `goalReproposalReachesNewReviewHumanApprovalAndBothPhasesAcrossAllVariants`：full/contract/terminal-delivery/small 实际装配与双入口；初始→full→patch→新复评→新需求门→GOAL→计划/EXECUTION，未批准写资格被拒。contract 的冷 JVM 还在后续批准后重读三份历史，正常追加不导致假漂移。
- `fixedRevisionInputsRejectDriftBeforeDispatchAndPublication`：R01 后基准漂移，分别在调用前及产物采纳前拒绝；删除 prepared 不产生成功发布。before-dispatch 的准备失败与后两个 INVALID 固定材料情形分立。

原协议套件覆盖其余共享 R02、publication/outcome/sidecar/reference、R03/L03、L04/compile 写点，最终单轮复跑，但本批不声称为每个旧 failpoint 都另外运行了新 full/patch，也不冒称所有新组合已穷尽。

## 同边界兄弟检查

registry / lifecycle JSON / artifact type 不增；initial / ordinary patch / eligible full / human revise / late clarification 共用 publisher 和固定完成；correction 仍用原受限采集器，未添加旧 wire 兼容。原权限 C/X、验收、scope、证据、恢复投影不授新权。CLI/API/attention/report 的 subject/失败显示继续消费原 artifact/outcome read model，无新的 projection authority。

LOW-1 常驻成对：两个完整引用独立有效但 ID 相同，两个顺序均歧义；完全同引用重复合法。LOW-2 注释：patch_source_output_ref 是历史字段名，承载原作者输出（含 full goal_reproposal），不迁移历史。

## 证据边界

模型 transport 是离线 fixture；临时 Git、prepared/ledger/真实子 JVM 是实际本地生产消费者。客户端能否遵守新 wire、主流程后半链的真实成功与 token 收益均未运行；新发行只证明包含这些字节。第五例原 FAIL 和原 raw 输出不变，不加盖或恢复。
