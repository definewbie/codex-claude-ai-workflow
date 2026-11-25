# G05：首个 fresh 主流程任务、预算与责任审批

2026-10-10；AWAITING_USER / NOT_AUTHORIZED / NOT_RUN。本页摘录已评审 Gate，不增加调用、额度、权限或验收；真实运行授权仍按 Gate 单独明确。

目标：用一个小型真实产品任务证明 Dual 的需求批准→设计/计划独立复评→精确执行批准→实现/自测→独立验收→最终交付批准→目标 Git 应用/实际提交/确认。只跑一个 terminal-delivery / INLINE 样本，保留原项目工作区。

## 1. 待批准的产品需求

原文：[task-requirement.md](../w08-preparation-20261010/task-requirement.md)。仅 Python 标准库的 normalize_newlines(text: str) -> str：CRLF 和单 CR 转 LF，保留其他字符、空串、已有 LF 与末尾有无换行；测试和 README。

- AC-1：至少八类输入及幂等，期望值明确。
- AC-2：真实执行 unittest，实际输出显示覆盖数与零 failure/error，和测试源码对应。本项不要求独立测量的退出码；完成状态或模型说“通过”不能替代测试结果。
- AC-3：README 的导入、混合换行例子和实现一致，有实际读取/例子证据。

建议文件只是提案；精确文件与 CREATE/MODIFY 在首次实现前由 EXECUTION 门再批准，不由这份申请代批。

## 2. 待批准的目标与本地准备

新目标 `/private/tmp/dual-w09-newlines-20261010/target`；独立 effective worktree 为同父目录 `/worktree`；分支 `codex/w09-newlines-20261010`。接收时整个父目录尚不存在，开建前再次核新鲜性；若已有内容则停止，不清理或覆盖。

G05 若获准，允许执行者为这次试验初始化新仓库/必要初始基准提交并创建 worktree，核 canonical/common-dir/base/tree/clean 和冻结发行/配置。初始化只建立基准，不代写产品代码；不触碰原项目 Git 提交。不用已有样本或原生 session。

作者侧沿本机 Claude Code 现有 GLM 配置；独立评审使用 Codex 客户端，实际 provider/model 分别记录。显式本仓 acpx wrapper、600s timeout、liteflow，其他既有设置不改；版本变化核真实协议/行为，不一律当不兼容。

## 3. 待批准的预算与止损

| 项 | 同一调用池/窗口内的限制 |
|---|---|
| 样本 | 1 个 fresh / terminal-delivery / INLINE，不自动第二例或其他变体 |
| 顶层调用 | 最多 18 次，包含读取前缀、所有 correction、修订和后续调用；正常 9 节点，最多 2 个读取前缀，readiness 计划重跑通常再用 2 次后剩 5 次 |
| 时间 | 单次命令最多 600s；累计模型/工具主动运行 60min，人工业务审批停顿另记；每段前后与运行中核进度 |
| token | 可见实际累计 120000 预警，160000 止损；首个已完成调用无实际 usage 则停止核观测链 |
| 修订 | 最多 2 次有新证据的语义循环，仍在同一个 18 次池内；同根因无新证据重复两次即停 |

预算按可观察的顶层调用和真实 usage 治理，不保证原生内部子任务/重试的绝对总量或绝对零超支；未知不填零。GLM/订阅按实际 billing mode 保留状态，不编造金额；若需新增购买/付费额度先停交用户。原生 ask 必须在实际有效期内逐项决定，不靠人工等待延长请求。

缺必需工具/证据、身份或批准漂移、外部 UNKNOWN/旧执行停止不明、达到止损、需要新 authority/扩大目标或成本偏差时停止新 dispatch。外部不明不自动重发，不删账本/plan，不改验收凑成功。结果按 PASS/FAIL/INVALID 与原执行状态分列。

## 4. 待批准的责任分配

- 用户：GOAL、精确 EXECUTION、最终 candidate/交付三个关键门；实际发生的原生 once 请求逐项 approve/deny。此次预算不是这些门的预批准。
- 执行者（当前 Codex 主 agent）：本地准备/终检、材料与环境可用性声明登记、分段调度、读取原事实及止损，不能自批关键门。声明只陈述实际已核条件，不冒称原生工具已执行。
- 用户：最终产品在新 target 的实际 commit。执行者在最终人批准后按原 service apply，人工 commit 后 finalize；G05 不授权执行者代提交产品。必要初始基准提交与最终产品提交分开。

## 5. 批准后的动作与保留边界

先做获准的本地准备和 Gate 运行前终检，展示结果；缺项不得真实调用。取得明确真实运行授权后，才在同一预算内启动客户端读取前缀/分段流程，并在关键门停下。若用户明确一次批准同时包括运行授权，前置核齐后照该授权执行，不再申请同一许可。

当前已评审版本没有实质改动，无需原样再送 CC。新发生的目标/验收/授权取舍按受影响范围再审；能力未知由有界运行取证，不扩为新平台或底层实验。

完整条件：[已评审 Gate](../w08-preparation-20261010/verification-gate-draft.md)；验收生产/消费出口：[verification-exits.md](../w08-preparation-20261010/verification-exits.md)。本页截至当前不记录任何用户批准。
