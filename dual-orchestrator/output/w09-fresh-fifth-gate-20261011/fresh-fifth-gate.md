# Fresh 第五例 Gate 候选（未授权/未运行）

当前修复完成与发行有效须经 CC 受影响复审；该认可不自动授权创建目标或真实运行。继续以跑完研发/测试/独立验收/交付主流程为目标，不优化配置、不变更验收，不沿用前四例余额。

## Verification Gate

- Target：原标准库 Python 换行规范化任务，完整批准谱系→实际实现/自测→Codex 独立验收→当前最终人批→实际目标 apply→用户 commit→精确确认 COMMITTED。不是阶段 Gate 或成本优化实验。
- Source of truth：原合同/node/gate ref+SHA、R01/R02/R03/L03、唯一 outcome、verification-observations/源码与candidate身份、delivery operation 与真实 HEAD/parent/tree/clean；本次调用 transcript+typed completion 固定的各 occurrence usage 和原完整 run aggregate。operator 预算只消费这些事实。
- Trigger precondition：本修复/发行/新 observer 受影响复审认可；用户当次明确批准新预算、fresh 隔离准备和运行；新目标/workstream/session；旧具名执行状态明确、无未知重叠；当次全部验收条款有真实出口。
- How precondition is checked before run：重新核656源码输入及CLI/server JAR、选定wrapper/dist/config/launch与实际R01；start不调模型并核单INLINE/liteflow/同ClassLoader；observer逐调用核run+occurrence/current日志身份/完整数值，预算核对保留尾链和可能correction；运行前保存observer/Gate字节。共享日志重复不撤销有独立本次日志的事实，当前错/重复/部分帧仍拒绝。
- PASS：完整逐条验收/独立复评/批准candidate、真实apply和用户commit/finalize COMMITTED；准确用量与准入/止损纪律全部成立。可在预算内按原链有界合法返修。
- FAIL：有效组合出现误拒/死端/越权/缺证采纳/主因漂移/验收失败/可归因用量缺失、尾链储备不足或实际止损。领域合法 SUCCESS/READY 不改写成模型失败。
- INVALID：发行/配置/fresh/出口开跑前置错误、换冻结输入、手改权威账本或判据；有效运行内的归因错误按FAIL，不伪装INVALID。
- Stop-loss：候选独立170万可见可归因请求token（含cachedRead）、120万预警、18次实际顶层请求（含correction）、3600s主动窗口/600s单命令；同根因无新证据两次/至多两次有新证据语义循环。任何UNKNOWN/用量不可测/身份批准scope漂移/需扩大目标即停新dispatch。完成粒度停止线，不是请求内绝对硬限。
- Run artifact freshness：[release-identity.json](release-identity.json)与[artifact-verification.json](artifact-verification.json)是本轮候选，开跑时重新实测。新binary不续旧ledger，当前原生装配实际握手再核；不锁模型版本或泛化安装缓存SHA为跨版本兼容保证。

预算与临时储备不变：[budget.json](budget.json)、[check_budget.py](check_budget.py)及13项对照。正常9节点940k；readiness重入175k、一次intake返修90k、一次correction150k示例1355k/14调用，余345k/4调用。后六阶段额度仍是未测临时假设。派发前守 T+所有未完成主链+本次重入/返修+150k correction ≤1700k，实际调用同构≤18；主动时间预留1500s。不能降低尾链储备凑通过。

新目标候选 `/private/tmp/dual-w09-newlines-20261011-fresh-05`、分支 `codex/w09-newlines-20261011-fresh-05`；**尚未创建**，当次存在则换nonce不覆盖。任务/验收与[原输入](../w08-preparation-20261010/task-requirement.md)、[完整主链Gate](../w09-mainline-budget-audit-20261011/full-mainline-gate.md)保持；该历史页150万数字由本候选170万替代，其余完整控制链/写入窗/批准/commit责任不变。

GOAL/EXECUTION/最终交付及任何原生once仍须各自当前subject用户决定；运行批准不预批这些门。产品commit仍用户操作。F2工具计数/F3通用恢复提示限制继续登记，不作预算或许可来源；如阻碍必要验收/安全动作立即停止。未触发的ask/UNKNOWN/worker/browser等记NOT_EXERCISED。

**下一步动作**：CC合并受影响只读复审修复/证据/发行/observer/Gate；通过后交用户当次fresh运行批准，再准备实例。此页不启动第五例、不恢复第四例、不授任何业务或原生权限。
