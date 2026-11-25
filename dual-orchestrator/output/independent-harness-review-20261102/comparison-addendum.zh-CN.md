# 独立评审对比附录

日期：2026-11-02。首版独立结论形成并提交后，才阅读上一轮报告。本附录不修改首版；只核对两份结论及 C07 的源码、测试和当前有效设计，没有追加测试、真实调用或历史恢复。

**结论：两份报告对总体机制基本一致，实质分歧是 C07 的判定和优先级。C07 的配置与写后拒收事实成立；把它判成当前已承诺边界 FAIL，并以此抢占第一优先，证据不足。当前明确接受的设计只承诺基线一致性与结果准入，明文排除了 OS 禁写保证。因此，我仍建议先做完整链的决定性检验，再调整已证实造成机械干预的职责分配；不能先把研发编排扩成写入隔离项目。**

这里不等于宣称现有取证权限最佳或风险不存在。复用原生权限、正确匹配角色/配置仍是保留的责任；低成本收紧普通 Write/Edit 可以是候选，但需要先证明不会阻断必要测试，不能从一句任务提示自动推导全部 shell 间接写入的不可绕过义务。

## 一、相同与相反

| 议题 | 两份报告的关系 | 原始依据 |
|---|---|---|
| 系统能否交付 | 相同：绝对“不能交付”不成立；9YYF 是真实 provider、验收、批准、目标 Git 提交的反例 | [9YYF events.jsonl:135](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/events.jsonl:135)、[交付提交事件:149](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/events.jsonl:149)；目标 commit `f8ec3d41cbe1cac554a78c3131372af434440691`，首版已核验字节与测试 |
| 主要系统解释 | 相同：跨层职责、机器事实转抄与后续动作接线比单纯“模型不够强”更有证据；历史混杂，无法给当前可靠交付率或唯一主因 | [7SNA 首计划:26](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/runs/run-1793030521008/outputs/plan-claude.plan.json:26)、[实际拒绝节点:58](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/nodes/node-plan-decision-020.json:58)；[AHBV run.json:119](/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260828T054556Z-AHBV/.agent/workstreams/ws-20260828T054556Z-AHBV/runs/run-1787910664384/run.json:119)、[业务 repair 路由:71](/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260828T054556Z-AHBV/.agent/workstreams/ws-20260828T054556Z-AHBV/events.jsonl:71) |
| 安全恢复 | 相同：当前 typed 失败主要返回 MANUAL_ONLY，runtime 自动入口只消费 AUTO_ALLOWED；应定义有限安全子集，未知副作用继续人工 | [AgentExecutionOutcomeResolver.java:25](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-core/src/main/java/dev/dual/core/execution/AgentExecutionOutcomeResolver.java:25)、[WorkflowRuntime.java:456](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java:456) |
| 7SNA 第二版 method | 相同：文件与当前 exact-equals 规则不符；这是静态预测，原 ledger 未记录第二次 decision 拒绝 | [第二版 method:27](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/runs/run-1793048679201/outputs/plan-claude.plan.json:27)、[ledger 最后事件:87](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/events.jsonl:87)、[当前规则:162](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/VerificationReadiness.java:162) |
| C07 与顺序 | 不同：旧报告判写前权限 FAIL、先修；首版未提出该缺陷，以 WARN 和完整链验证优先。本次核对后，不接受旧报告的该项 FAIL 与必然第一优先 | 旧报告 [C07 论证:119](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/cross-month-ledger-review-20261102/review.zh-CN.md:119)、[排序:155](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/cross-month-ledger-review-20261102/review.zh-CN.md:155)、[判决:190](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/cross-month-ledger-review-20261102/review.zh-CN.md:190)；有效设计见下文 |

## 二、C07：三个不同命题

**事实成立，但证据要分层。** adapter 按 node 选择 permission profile，collection 只增加任务提示；implement 允许 Write/Edit/MultiEdit/Bash，E1 的 allow_write 包含工作树。[AcpxAgentTaskHandler.java:209](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java:209)、[取证提示:259](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java:259)、[profile:99](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxPermissionProfile.java:99)、[policy:106](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/ClaudeExecutionPolicy.java:106)。

`execute` 在 handler 前验证原批准基线，handler 后再次验证；序列测试的假 handler 用 Java Files 写入 source.txt，随后断言失败、不发布可用 sidecar、保留磁盘修改。[EvidenceCollection.java:89](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollection.java:89)、[测试写入:45](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java:45)、[断言:86](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java:86)。这直接证明 guard 不是写前隔离；测试本身没有经过真实原生工具执行，不能冒充当前 provider 已发生污染事故。

**不能据此成立“违反当前已接受设计”的判决。** 最直接的原始规范不是提示词，而是本功能设计：[20260910 计划:26](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/plans/20260910-evidence-collection-source-baseline.md:26) 明确“检查只承诺结果准入，不宣称 OS 禁写、不检测 edit-restore，不自动回滚产品”；[同文:41](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/plans/20260910-evidence-collection-source-baseline.md:41) 明确不新增 profile、permission/sandbox，prompt 仅作提醒。源码也把该提示标为 advisory projection。[EvidenceCollection.java:52](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollection.java:52)。

后续有效范围没有恢复该保证。9/30 当前详设保留正确角色/配置、原生批准接入与业务 scope/证据，撤销独立文件系统隔离及底层不可绕过证明；六维评审明确不得重新要求撤销的保证。[详设 §8.25:849](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/plans/20260928-dual-lifecycle-detailed-design.zh-CN.md:849)、[保留/撤销:855](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/plans/20260928-dual-lifecycle-detailed-design.zh-CN.md:855)、[六维口径:891](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/plans/20260928-dual-lifecycle-detailed-design.zh-CN.md:891)。当前入口亦确认用户因成本收益主动收敛目标。[主清单:445](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/plans/20260927-dual-lifecycle-worklist.zh-CN.md:445)。不能把这些限制改名为“已修安全缺口”，也不能按撤销的保证给当前功能判 FAIL。

按实际承诺检查，Declared by 是人工基线/计划；Proven by 是 gate result 中合同与源码身份；Consumed by 是 inline/worker 的 EvidenceCollection 与发布 validator；Enforced before 是基线不符零 handler 调用、执行后不符零有效发布；Failure route 是 typed recovery；Cannot-bypass 是冷读、标记篡改与 direct publication 拒绝测试。[计划完整链:34](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/plans/20260910-evidence-collection-source-baseline.md:34)、[零调用断言:74](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java:74)、[冷读:98](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java:98)、[直接发布:126](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java:126)。这些切面支持结果准入 ENFORCED，已有离线序列证明；不能提升为文件系统禁写 PROVEN，也不能据此宣称整个产品 PASS。

**根因与第一优先均未证明。** C07 没有展示当前取证污染导致真实案例失败、人工修复的原始事件，也没有比较它与接线错误、恢复缺口的频率/损失。它能解释一种可能的返工方式，不能解释既有多月干预的主要来源。若要提出新增普通工具最小权限，应作为范围/收益待评估的改进，而不是依据提示文案直接宣布已接受设计违约。

## 三、哪个顺序更直接推进交付

我更支持首版的**先验证、再改职责**顺序，但不把它宣称为已证明最优：冻结当前组合，以 7SNA 首次已发生的错误重建到 execution compile、批准和 dispatch；先得到零人工改 JSON、错身份仍拒绝的贯通目标，再判断确定性装配或提前校验是否真的减少干预。之后接安全恢复子集和必要验证出口。它直接针对已发生的机械救火；旧报告把尚未关联真实损失、且超出现有承诺的 C07 提升为硬前置，可能延后该验证。

支持“恢复也很紧迫”的证据是当前 MANUAL_ONLY→AUTO_ALLOWED 接口不衔接；但不能据此把未知副作用全部自动化。支持“先守取证”的最强反例是：现有测试已证明一次写入可留下真实磁盘污染；如果后续原案显示这类污染频繁或不可逆、且现成原生配置能低成本阻止而不妨碍测试，C07 的优先级可以超过接线优化。这仍是条件判断，本次没有对应真实事故或成本比较。保留基线 guard 是所有顺序的共同前提。

首版顺序自身的最强反例是 9YYF：相同主要链已经成功，7SNA 的一次接线失败不能代表全部任务；当前 prompt 又已前向收紧。若当前冻结链无需结构变更就能顺利通过，第一项应收口为验证，不为证明诊断而强行改契约。最终排序仍需同版本同任务的人工干预账与实际交付结果决定。

## 四、我自己的首版需要收紧

1. “最有行动价值的结构性解释”应理解为优先检验的假说，不能读成已证明的总体主因；样本不足以给四类机制排损失占比。
2. 7SNA 的 TR/AC 等首次错误是已发生事故；第二版 method 不等只是当前规则下的风险，不能把两次相加成连续两次真实边界失败。
3. 机械绑定只能装配由现有 owner 唯一决定的字段。选择执行者、是否有可用环境等仍可能是真实业务/操作决定；不能用“程序装配”掩盖缺失批准或把它们自动猜造。
4. 首版的恢复、能力准入建议仍是待设计/证明工作。当前严格拒绝、有限 correction、已修权限分类和成功交付是反证，不能描述成系统没有鲁棒性。
5. 首版未专门评估取证写能力的残余风险；本附录补齐后，结果是保留风险并纠正评分范围，而不是把首版 WARN 改写成无条件 PASS。
6. 决定性检验必须分开三个口径：旧协议/旧输入原样复现时，非法 method 应继续拒绝；候选生产者若不再生成机器字段，须按明确的新契约由原 owner 装配；业务内容不变的候选对照只是反事实实验。直接替换旧 JSON 的非法 method 可能吞掉模型越权，不能把这种成功叫历史原样重放，也不能一边保留原协议、一边要求同一非法输入直接进入实现。验收目标是合法生产链不再制造该错误，同时保留旧协议的负例拒绝。

本附录用到的两份报告、C07 源码/测试和范围规范另存哈希于 comparison-source-sha256.json，便于区分工作区后续变化。没有新增测试结果；首版的 59 项离线测试与交付副本检查仍只代表原先标明的切面。
