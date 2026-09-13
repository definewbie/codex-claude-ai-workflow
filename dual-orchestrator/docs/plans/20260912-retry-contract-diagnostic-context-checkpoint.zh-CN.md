# 人工重试的合同拒绝诊断上下文 Checkpoint

日期：2026-09-12。代码基线：`8708eb764236762e736f15d5775a5e73075a6e33`。
状态：IMPLEMENTED / FULL REGRESSION PASS / INDEPENDENT DIFF REVIEW PASS / CAN COMMIT。
设计：[最小设计核对](20260912-retry-contract-diagnostic-context-minimal-design.zh-CN.md)。

## 1. 完成什么，不完成什么

本批推进P0执行恢复输入完整性：对确定性合同拒绝执行既有人工retry后，同节点新执行会收到
上次拒绝诊断，不再依赖人工重新转述。诊断最多8 KiB UTF-8，截断有标记；不是批准材料。

```text
作者输出 → 原合同校验拒绝 → FAILED / WAITING_FOR_RECOVERY
                                  │ 人工普通retry
                                  ↓
                  READY + 有界诊断快照（同一次node原子写）
                                  ↓
                retry事件 → current RUNNING → 新execution
                                  ↓
              资格与快照/现状校验 → prompt/manifest → provider替身
                                  ↓
                   原合同校验 → 发布 → reviewer → human gate
                       │ 再次不合法
                       └→ 原失败出口，无自动第三次执行
```

人工retry不增加iteration、不修改合同/义务/权限、不代替正式revise，也没有新增次数限制。
每次人工重试仍有执行成本。没有新增workflow、schema、状态、策略平台、模型特判或修复旧样本。

## 2. 唯一生产链

生产修改只有两个文件，合计新增89行：

- `NodeStateWriter`：普通人工retry、FAILED acpx节点、既有合同拒绝reason才创建快照；
  前态须WAITING_FOR_RECOVERY，parse_error必须结构完整。锁内由当前失败result生成固定五字段，
  与READY写入同一node.json；事件不复制诊断。run/execution ID只作标签，不据其读文件。
- `AcpxAgentTaskHandler`：共用入口在资格校验后、grant claim及provider前校验快照形态与
  current RUNNING/active/无open gate，失败返回既有人工恢复出口。合法诊断只增加一个
  `Previous rejected execution` section，存入既有prompt与manifest。

定义不变；instance只拥有本次retry快照；execution仍拥有历史完整诊断；prompt是投影。
快照不进入validated inputRefs、active obligations或parse correction contract。
原ArtifactContractValidator和路由校验不改；正常完成的新result替换旧marker。

### 写入失败与重放

| 切面 | 结果与验证 |
|---|---|
| 前态或诊断损坏 | READY之前拒绝；node/current/events字节不变反证 |
| node READY之后、retry event之前失败 | snapshot与READY同在，但current仍恢复态；新runtime/worker零新增provider |
| retry event之后、current之前失败 | 同上；未把event存在解释成执行授权 |
| 完整retry后重新实例化ledger/runtime | 不读旧execution或稳定诊断文件；新execution更新latest不影响快照 |
| 快照损坏或current不合法 | provider及grant claim前拒绝；完整序列停在人工恢复，无sidecar |
| 模型重试仍越界 | 原validator继续拒绝；无reviewer，下一次人工retry只带最新诊断 |
| 成功与后继 | 原reviewer与human gate仍执行；后继实现不继承retry marker |

这不是node/event/current跨文件事务，也不新增半写自动修复。测试冷读使用新对象而非新OS
进程；没有宣称R1 liveness、抵抗宿主改写ledger，或旧READY由新版本追溯补写。

## 3. 验证证据

### 从红灯到序列绿灯

- 实现前新增8项序列测试全部不通过（4 failures / 4 errors）：正常序列首先明确断言
  retry未保存诊断；负例中的缺失snapshot异常只证明前置功能缺失，不计作独立拦截证明。
- 实现后初次聚焦：ledger 5项 + 序列8项 = 13/0/0/0。
- 接线突变：临时让实际执行跳过`retryFailureInstruction`，仅prepare-only保留。
  `malformedSnapshotStopsTheWholePipelineBeforeProvider`在inline/worker均失败：provider
  调用数预期1、实际2。恢复原接线后再运行全量，不能把只有格式单测视为入口被强制消费。

### 新增与扩展测试的实际范围

| 测试 | 覆盖 |
|---|---|
| RetryFailureContextTest（新增5） | Unicode有界截断；queue/running保留；不复制note/grant；损坏诊断零写入；非合同/非agent/自动retry不创建 |
| RetryFailureContextSequenceTest（新增8） | 真实git/runtime/handler/acquirer/validator/router/context/reviewer/gate；design+plan，Claude+Codex dispatch，inline+worker；scope再次拒绝、两次诊断隔离、半写、冷读、形态损坏 |
| RetryCommandTest（新增1） | CLI同走ledger，READY保留诊断，不自行启动provider |
| WorkstreamActionServiceTest（新增1） | server同走ledger，无新增实现或执行旁路 |
| AcpxAgentTaskHandlerTest（扩展已有1） | prepare-only同守卫且不claim；坏snapshot/current不消费已有grant；合法run仍消费既有grant；后续进程失败不继承诊断与授权 |

新增序列终点是设计/计划经各自review和人工批准后进入实现，并使该测试workflow COMPLETED。
没有在这个fixture里伪造产品验收或delivery；真实provider只由transport替身替代。既有
authored correction、证据提交→自测→acceptance、delivery与terminal测试另随全reactor回归。
因此既不是单字符串测试，也不是自然产品E2E证明；没有声称所有异常的组合遍历。

### 全量回归

执行：`rtk mvn -o test`。首轮默认沙箱遇到两项既有进程测试的macOS `sysctl`权限错误：
`AgentExecutionAttemptReconcilerTest.stopLossTerminatesDurableInFlightProcessTreeBeforeReconciliation`
与`StaleRunningRecoveryTest.terminateAndRecoverClosesExecutionBeforeInstanceTransition`。
未跳过、删除或弱化测试；在允许检查测试子进程的环境下原样重跑。

最终：BUILD SUCCESS，2026-09-12 22:10:22 +08:00结束，耗时5分44秒。
181份Surefire XML按结构化解析汇总：**1508 / 0 / 0 / 0**（tests/failures/errors/skipped）。

| 模块 | tests / failures / errors / skipped |
|---|---|
| core | 117 / 0 / 0 / 0 |
| ledger-file | 139 / 0 / 0 / 0 |
| flow-liteflow | 12 / 0 / 0 / 0 |
| adapter-git | 6 / 0 / 0 / 0 |
| runtime | 686 / 0 / 0 / 0 |
| adapter-acpx | 389 / 0 / 0 / 0 |
| cli | 87 / 0 / 0 / 0 |
| server | 72 / 0 / 0 / 0 |

`git diff --check`通过；新增源码/测试/文档另以`git diff --no-index --check`检查，无空白错误。
本次新增15项，既有handler测试增加断言不增加计数。接线突变已恢复，最终8项新序列全绿。

## 4. 六维自查与通用性

| 维度 | 实现与反证 |
|---|---|
| Declared by | 原节点/批准输入 + 既有人工作用域；retry不替代语义revise |
| Proven by | 同写的READY快照、实际prompt/manifest；非引用链防篡改证据 |
| Consumed by | ledger唯一生成、共用acpx handler唯一读取；CLI/API只是入口 |
| Enforced before | 无效快照阻止provider/grant claim；不合格产物仍在发布和reviewer前拒绝 |
| Failure route | 原人工恢复；没有自动循环；半写停住而非rescue |
| Cannot-bypass | 入口突变、真实序列、再次越界、两个写间故障、磁盘冷读、后继隔离与grant反例 |

主agent自查：没有发现需要新增机制的阻塞项；不冒充独立评审。原因是一个已有失败事实
在retry重置result时丢失，而不是需要新增语义修订状态。采用有界内嵌副本足以恢复输入，
原批准链继续拥有正确性。scope拒绝只用作触发测试，production不匹配业务路径或错误正文。

Sibling sweep：普通retry/自动retry/WebSearch批准、CLI/server、prepare/execute、
Claude/Codex、inline/worker、network grant均已定位。未引入历史扫描fallback；既有历史
诊断摘要不被当作本次快照来源，也未顺手删除。正常路径无marker时不新增该section。

## 5. 收口边界与下一步

- INPUT CONTINUITY：ENFORCED / DETERMINISTIC SEQUENCE PROVEN（替身provider）；全量回归与独立diff review均PASS。
- NATURAL E2E / 真实模型返工成功率：NOT PROVEN。
- 本轮未调用provider、未开额外agent、未恢复KPPZ/GCTW、未修改冻结JAR；`mvn test`不是重新打包。
- 选择性提交范围：两处production、五个测试文件、本设计、checkpoint与评审归档；
  排除工作树其他计划、旧Gate、截图、output与运行证据。提交后再按既有验证纪律
  重建运行工件；不因离线测试通过直接宣布旧case可resume。

## 6. 独立评审收口（2026-09-13）

[CC原文归档](20260913-retry-contract-diagnostic-context-implementation-review.zh-CN.md)：
CAN COMMIT，未发现阻塞缺陷；评审者独立复跑全reactor为1508/0/0/0。本次归档未再运行
另一遍全量测试，未修改评审通过的production/test行为。

四项P3按其实际性质保留，不借机扩展：守卫之前的空run目录/孤立seed属于既有资格失败残留；
历史ID标题不作防宿主改账本承诺；acquisition层拒绝只核证了相同result形状，尚无本批
retry组合序列；接线突变由实施方完成，reviewer只核证了现存反证断言，没有声称亲跑突变。
前三项没有改变批准输入或外部副作用边界，第四项是证据归属注记，均不要求新增production机制。

下一验证不得把这些结论扩大为真实模型修复率或自然E2E成功。若测试目标指向GCTW类
acquisition拒绝，应先补对应触发序列，再按已有Gate纪律验证；不为凑自然触发制造缺陷。
