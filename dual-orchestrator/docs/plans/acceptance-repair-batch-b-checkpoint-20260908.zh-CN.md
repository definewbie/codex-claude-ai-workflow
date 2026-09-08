# 验收返工批次B：分类、重新批准与累计止损

日期：2026-09-08。状态：INTEGRATION VERIFIED / MAIN-AGENT REVIEW COMPLETE / FINAL INDEPENDENT REVIEW NOT RUN。
依据：`acceptance-repair-minimal-design-20260907.zh-CN.md` 及其独立delta评审。
目标：P0有效验收拒绝进入正确生产者，真正合同修订重新取得批准，自动返工有界。
不是网络Phase B；不恢复SPM9旧070、不调用provider、不新增批准机制或repair artifact。

## Owner 与消费者

- 分类：既有FailureCategory值域，acceptance显式category_hint经同一语义规则校验后由arbiter消费。
  删除列表优先级/changed-file数量/合同ambiguity等覆写分类的旧路径；正文不控制路由。
- 合同返工：classifier在现行qualified合同仍可验证时固定历史proposal/review/compile/gate引用。
  下一iteration resolver只沿这些引用与父iteration重建，不读取最新稳定resolution选择旧基线。
- 修订：直接patch父提案与已批准scope基线分开。中间未批准草稿不能授予CREATE存在豁免。
  义务及候选响应通过同一个resolver进入product context及两道human packet；展示不授予批准。
- 批准：沿用requirement批准→真实compile→contract批准→新设计/计划；旧epoch不能续用。
- 止损：四definition的classifier配置既有max_revisions；计数按source/target，不是全局三次。

## 当前验证

- `AcceptanceRepairSequenceTest`：真实bundled contract图与runtime/ledger/publication/compile/qualification，
  只替换agent输出。合同返工走新proposal、两级批准、新design/plan、implementation/self-test和再次验收；
  纯补证据只重跑self-test；混合实现与证据问题先回implementation。2项通过。
- `LateProposalScopeTest`：真实Git base上的CREATE继承及累计scope评价，三种通用路径；
  新addition未批准、错误改为MODIFY、目录/非规范路径与缺base拒绝。3项通过。
- `AcceptanceRepairInterruptionTest`：真实冷run/approve/retry/abort入口对resolved gate/current旧态拒绝；
  已完成source/current旧态不派发孤立后继；半写及合法JSON混代不能qualify或供旧authored输入重用。4项通过。
- 全模块初跑暴露的旧fixture缺字段及iteration身份缺失已修正，未放宽生产校验。
- 多轮义务、clarify历史重读、冷JVM、预算与三面展示均有通过测试。
- 2026-09-08 20:24–20:30，主 agent 在最终代码上整仓离线 `mvn -q -o test` 退出0，
  171份XML合计1423/0/0/0，含最后gate来源增量。全部报告均为本次运行生成
  （最早mtime为20:24:57），不是旧报告与增量结果拼接。未package或跳过测试。
  运行前后575份模块src文件及pom的排序内容指纹一致：
  `95415e86480e52fba0b70dcce68296403512fdf35771fa1fe4a12b3baf507570`。
  `git diff --check`通过。

## 单 agent 收尾记录

按用户要求停止继续委派，收回既有工作；未启动额外 agent。仓库 AGENTS.md 已加入
Agent Delegation Cost Discipline：默认单 agent，自查不冒充独立评审，禁止以节约 token 降低边界验证。
此前独立检查只给过阶段性意见，不能作为最终批次 PASS。

主 agent 收尾确认并处理：初始展示测试补齐真实 iteration 身份，不放宽缺失记录的拒绝；
原验收完整 sidecar 经 resolver 的历史引用/SHA 验证后进入作者最终协议提示与审批投影，
非合同缺陷作为证据保留，不升级为合同义务；当前合同审批包核对 gate/node/event/source 关系。
增加合法 JSON 的新合同配旧 resolution/批准拒绝反例，不只测试损坏 JSON。
这些都是既定 F2 消费链的补全，不新增 artifact、权限或恢复机制。

## 主 agent 整链自查

### 本轮只读评审发现与修复（2026-09-08）

主agent只读复核发现P1：contract packet虽核对gate来源，正文却仍无SHA约束地读取稳定路径。
临时真实流程复现：修改合法合同JSON的objective后仍可生成WAITING packet；批准后才由
qualification以HASH_MISMATCH拒绝。它没有绕过执行权限，但破坏知情审批，不能因下游拦住就忽略。
另有P2：原整链fixture的patch全部为空变化，未证明实际变更条款被后续消费者采用。

本轮仅修改HumanReviewGateHandler、TaskContractRevisionContextResolver与三个测试文件：
- 初次/晚期contract gate共用来源校验，对contract/resolution两份正文与source compile的SHA
  比对，失败前不替换JSON/Markdown packet；成功返回校验过的同一读取快照，不再次读取稳定路径。
  仍标记unapproved_candidate，不产生新的批准或执行资格；不改变ledger/审批动作。
- `contractPacketRejectsChangedCompileBytesBeforeReplacingEitherPacket`在旧实现先红，修复后绿，
  覆盖初次/晚期与两份工件，保留旧packet字节不变。
- `changedClauseReachesDownstreamOnlyAfterBothApprovals`实际REPLACE验收条款，核对新SHA和两道批准，
  并断言design/plan/implement/self-test/acceptance的真实governance输入均含新条款。
- 旧ContractReviewGateTest空目录fixture改用真实初次审批链，保留三个人工动作断言，不加兼容回退。

局部13项通过（展示7、序列3、审批动作3）；2026-09-08 20:52–20:57最终整仓离线
`mvn -q -o test`退出0，171份本次XML合计1425/0/0/0，最早报告mtime为20:52:01。
运行前后源码指纹均为`b016337edb4f1a9ca641645662d88b8cb0eaa0b5e96103a2fb097b12735d60c4`；
`git diff --check`通过。此结果覆盖本节全部增量，取代上方20:24的1423项结果作为最终基线。
P1审批正文身份缺口、P2非空修订序列缺口均已修复并自查通过；没有运行真实E2E或更新CLI JAR。
最终复核仍由主agent承担，不冒称独立review；没有新增agent或provider执行。

| 维度 | 已核对链路与反例 |
| --- | --- |
| Declared by | 四definition显式返工边与max_revisions；typed category统一值域 |
| Proven by | 已验证sidecar、classifier冻结历史引用、iteration与gate/compile身份；无latest回退 |
| Consumed by | resolver至真实作者协议提示、product context、两道人审packet，再compile与qualified消费者 |
| Enforced before | 无效分类不路由；根引用失效不出包；新合同未批准不得复用旧authored输入 |
| Failure route | typed拒绝或既有预算recovery；部分写入停止，不新增透明救援 |
| Cannot-bypass | 真实流程序列、累计义务与冷JVM、root篡改、gate来源/subject漂移、CREATE继承、半写与合法JSON混代、四workflow/预算反例 |

阶段性外部检查所列输入保留、当前gate来源、合法JSON混代三项已补代码/测试并由主agent复核；
此表是自查，不代表外部评审者已确认最后增量。最终代码完整reactor复跑已完成；
真实E2E仍需独立的fresh Gate，不能借此恢复旧070状态。

## CC 独立评审入口

对本checkpoint及其最小设计做只读代码级diff review，独立重建有效验收拒绝到正确修复生产者、
重新批准、下游执行及自动预算止损的完整transition，不采信自评或测试计数。
检查Declared/Proven/Consumed/Enforced-before/Failure-route/Cannot-bypass，重点核验多轮义务与
原验收证据的最终输入、两道批准、当前gate与compile来源、旧epoch/半写/冷读、四workflow sibling。
识别通用性与不必要复杂度。工作树混有批次A及网络/transport旧改动，按设计§7划定本批范围，
不要将全部dirty diff算作本批；报告P0/P1/P2和是否可选择性提交。禁止修改、provider和真实case。

## 成熟度与限制

本轮实现与离线集成验证已完成，最终独立diff review未进行；不宣称真实E2E PROVEN。
真实语义误判仍可能发生；结构一致性不证明模型分类理由正确。
中断切片没有新增透明恢复保证，具体支持面遵循设计§5.1；不把停止等同自动WAITING_FOR_RECOVERY。
原批准CREATE可继承，但“先越界创建再追加scope”明确拒绝。scope删除与任意目标替换不在patch能力内。
现有CLI JAR不随test自动更新，不能用于本批真实验证。选择性提交和fresh Gate均在完整测试与评审之后。
