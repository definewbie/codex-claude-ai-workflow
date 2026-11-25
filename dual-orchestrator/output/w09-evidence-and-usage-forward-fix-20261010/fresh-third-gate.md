# W09 fresh 第三次 Gate · 受影响候选

DRAFT / AWAITING_CC_AND_FRESH_RUN_AUTHORIZATION。前两例仍 FAIL；本页不恢复前两例、不授权新调用或目标创建。

复用已批准的产品目标与准则：[原完整 Gate](../w08-preparation-20261010/verification-gate-draft.md)、[纯任务输入](../w08-preparation-20261010/task-requirement.md)、[验收出口](../w08-preparation-20261010/verification-exits.md)。这三份原文保留，前两次运行的证据与旧发行不重写。本候选只替换修复后的发行身份和 fresh 实例；不改变批准 scope/验收/交付责任。

## Verification Gate

- Target：一个 fresh 标准库 Python 换行规范化任务，经 Claude Code 作者、Codex 独立复评、GOAL/EXECUTION/最终用户批准、真实逐条验收、目标 apply 与用户 commit 后由 Dual 精确确认 COMMITTED。先证明这一合法主流程，不推算 80% 成功率。
- Source of truth：当前冻结发行身份；原合同/occurrence/ref+SHA；R01/R02/R03 与唯一 outcome、实际 verification-observations；source/candidate lineage；delivery 事实与真实 Git HEAD/parent/tree/clean。摘要为投影。可选内部 ID 缺席按 NOT_PROVIDED；产品evidence_refs与程序binding分立；token按实际completion session/request逐次固定后完整聚合，不以latest、快照或部分量替代总量。
- Trigger precondition：F03/F04 必要离线回归与新发行 byte 核验通过，受影响 CC 评审完成；实际选定 wrapper/config 与 reviewed identity 一致；新隔离 target/workspace，无旧账本/session 借用；用户明确授权本次 fresh Gate。关键和最终批准仍须到门时决定。
- How precondition is checked before run：读取本目录 release-identity/test-results/artifact-verification，重核全部冻结源码/发行/原生入口与配置 SHA；新目录存在则不覆盖，记录 canonical/common-dir/base/tree/clean；start 不自动 run，核实际 R01/runtime/loader/kernel/runner，再分段 dispatch。原 Gate P01–P08 在新实例核齐；真实 GOAL 后由原 record-readiness 入口固定当前 ref/epoch，不造假引用。启动前复查原具名调用状态，无已知活执行重叠；首例零 prompt/停止事实不能扩大成原生任意子树物理全停证明。
- PASS：完整批准与独立复评谱系成立，三项验收按原出口有真实证据，用户最终批准当前 candidate；apply、人实际 commit、finalize=COMMITTED；预算与停止纪律满足，事实/投影无主因漂移。自然合法 revise/补证据允许在预算内原链推进。
- FAIL：有效冻结组合中出现合法入口误拒、无续作出口、未批准调度/采纳/交付、错误身份/权限决定、缺证据被采纳、主因漂移、验收不满足或止损。有效组合早期接口误拒仍为 FAIL，不因 prompt 前失败改 INVALID。
- INVALID：发行/配置/fresh/观测前置不满足或运行中改变冻结输入/手改账本。缺席可选 agentSessionId 本身既不是 FAIL 也不是 INVALID。外部结果不明保持 UNKNOWN，不从缺 exit/日志推零执行。
- Stop-loss：仍提案最多 18 顶层调用、单命令 600s、累计主动窗口 60min、可见实际 token 120k 警告/160k 止损、最多两次有新证据的语义循环；每段前核余额、身份、批准和 drift。同类无新证据两次、外部不明/旧活执行、缺实际或可归因 usage、权限/范围/成本变化即停止新 dispatch，不自动 retry/reset/clean/另开 case。顶层预算不等于内部绝对硬限；金额未知不填零。前两例分别消耗1次attempt和2次prompt，剩余17/16均不自动继承；第三次限额是待本次明确授权的提案。
- Run artifact freshness：本目录的新 release-identity 固定源码/资源与 CLI/server JAR SHA、实际 kernel/命令超时；前两例分别绑定旧发行，不支持换新 binary 续作。原生版本使用实际环境/兼容协议，不新增精确版本 pin；启动后当前会话握手及必要工具仍须真实核验。

## 有界执行与待决

- 保持 terminal-delivery / 单 INLINE runner / 同时最多一个路由 JVM、同一 ClassLoader；无新 worker/变体/浏览器/底层权限实验。
- 候选新父目录 `/private/tmp/dual-w09-newlines-20261010-fresh-03`，target/worktree 在其下，分支 `codex/w09-newlines-20261010-fresh-03`；本轮未创建。若目录已存在，换 fresh nonce，不覆盖。
- 正常主链 9 次调用；可选连接读取前缀最多2次，若 readiness 导致计划作者/复评再跑2次，则余5次；correction/修订共用18次池，派发前预留可能 correction，余额不足不启动。
- GOAL 用户确认目标/准则/禁止范围，EXECUTION 用户批准设计/计划后的精确文件/操作，最终用户批准 candidate；产品 commit 仍由用户执行，原生 once approve 不扩大业务批准。
- 有效Gate中用量归因缺陷为FAIL/止损；若启动前归因/冻结前置不齐则INVALID/不得启动。SDK金额不冒称实付账单。
- 若自然未出现 native ask，记 NOT_EXERCISED，不故意制造权限请求；若出现，核原 session/prompt/request/generation → C/X → 一次回复 → ACK/后续结果，ACK不等于已执行。
- 本轮修复只证离线产品引用语义与请求归因；新发行下的真实模型产出、独立业务复评、工具证据与目标交付均待运行；第二例已取得的模型响应与F01/F02正向证据保留。不得借首例会话创建或离线 peers 宣称已证。

下一步：本修复与新发行/Gate 合并做一次 CC 受影响复审；通过后交用户本次 fresh 运行授权，才创建新目标并执行。
