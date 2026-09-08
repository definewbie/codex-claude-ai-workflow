# 验收返工最小设计：独立整链评审记录

日期：2026-09-07。对象：`acceptance-repair-minimal-design-20260907.zh-CN.md`。
方式：独立agent只读源码与文档，主线程记录结论；不是production diff review。
两名只读调查agent分别核对分类/成功publication、合同修订入口；第三名未参与设计的agent
评审最终整链。无provider/real-case调用，无production修改。

## Findings

P0：无。P1：两项。当前Implementation READY：NO。

### P1-1 多次修订的两种基线与义务连续性未钉死

序列：已批准并实现A → 验收拒绝 → 新proposal B → product review再次拒绝 → patch C。
当前TaskContractRevisionContextResolver的NEEDS_REVISION分支取B为previousProposal；
但CREATE已存在豁免只能来自已批准A，而非未批准B。人工revise分支同样需检查。
设计虽禁止未批准proposal取得豁免，尚未指定如何沿真实iteration保留A与原验收义务。

要求：明确patch parent、approved authority两种用途，逐次重建但不新增第二批准owner；
测试必须包含再次自动修订及人工修订，不只A→B→批准一次成功。

### P1-2 写入中断后的真实停止出口不完整

resolved gate已写而node/current未同步，或completed node已写而后继未路由时，
部分守卫会拒绝，但未必成为可操作的WAITING_FOR_RECOVERY。
“fail-closed/既有recovery”不能代替冷启动后谁读取、展示什么、允许什么处置的定义。

要求：按实际写入切面说明查询/run/resume/abort的真实消费者；不要求通用原子恢复，
不要求自动挽救旧样本。若不支持继续，明确停止与证据保留，不声称自动恢复。

## 已接受方向与边界

- 复用category值域、publication/重读共享语义，不造repair平台。
- classifier固定基线引用，避免compile覆盖稳定projection后改认新baseline。
- 原批准CREATE保持相对原任务Git base的语义；新越界addition不能冒充继承。
- loader四workflow与custom边界须由实施序列测试证明，不凭图形通过。
- F3撤回：StageOutcomeCompiler已有publication拒绝。主线程实际8/0/0及真实publication
  临时反例与此一致；本独立评审未重跑，不把主线程结果说成独立复现。

## 决定

Boundary Review：WARN / DESIGN GAP。Implementation READY：NO。
六维链的Declared/owner方向基本成立，但跨多轮Consumed与中断Failure route仍有上述缺口，
不能因局部设计成立标PASS。下一步仅收敛两项后做delta评审，禁止直接开生产实现。
当前5–9人日仍为未锁定工程量估计，不承诺设计未闭合时的交付日期。
