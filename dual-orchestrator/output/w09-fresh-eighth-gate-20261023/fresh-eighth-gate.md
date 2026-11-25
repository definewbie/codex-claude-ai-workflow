# W09 fresh 第八例：完整主流程 Gate（待批准）

2026-10-23，Asia/Shanghai。状态：**离线准备；真实运行未授权、未启动**。本包承接已受审的进程核验修正，补记一次禁 prompt 初始化观察实际看到的 SDK 身份；不恢复第七例，不复用旧余额或授权，不新增原生观察。产品源码、发行和预算规则不变。

## 要买到的证明

目标仍是一个新任务走完提案、独立复评和必要返修、当前 GOAL 批准、设计/计划及复评、readiness 补齐、当前 EXECUTION 精确范围批准、实现和实际自测、独立验收、最终人门、隔离 Git apply、用户 commit、精确 COMMITTED。产品需求见 task-requirement.md，与受审第七例字节相同，验收出口见 verification-exits.md。设计 READY 或初始化成功均不是全流程 PASS。

## Verification Gate

- Target：在本包冻结发行和新隔离实例上，真实完成完整主流程及 COMMITTED，同时保持批准、身份、scope、证据、独立复评和预算纪律。
- Source of truth：原 current/node/gate/typed 合同和 ref+SHA；R01/R02/R03/L03 的输入、完成与唯一 outcome；固定 verification-observations、源码/candidate 身份；delivery 唯一 owner 及真实 HEAD/parent/tree/clean。预算只消费每 run+occurrence 固定、可归因的 usage/acquisition 和日志 SHA。observer 与进程检查不是业务 authority。
- Trigger precondition：用户明确批准本次 fresh 真实运行；初始实例仍无 run、session 或批准；659 输入、发行、配置、安装候选和本包观察器身份未漂移；已知旧具名 session 关闭、旧 PID 的成功完整观察为 ABSENT；单 INLINE/liteflow、单 router ClassLoader、无并行 server/worker 路由；逐条验收出口可用。
- How precondition is checked before run：本包已用原 start（不带 --run）生成空实例，保存 initial-ledger-sha256.json 和 gate-local-preflight.json。授权后立即运行 pre-dispatch-recheck.py，重核初始账本/Git、PATH、宿主依赖、安装与配置字节、旧进程/会话和首派发储备。授权单独记录，本包 run-authorization.json 仍为 false。runner 在读实例、创建 step 和启动 Java 前先拒绝未授权；每个未来 step 仍消费同一 process_check.py。实际 R01/兼容性使用原产品接口，静态身份不代替真实接口证明。
- PASS：当前批准谱系完整，每条验收有实际证据及独立验收，exact candidate 经当前最终人门，真实隔离 apply、用户 commit 和精确 COMMITTED；全程用量可核、派发前储备及停止义务成立。
- FAIL：有效装配下发生合法流程误拒/死端、非法采纳、产品验收失败、主因漂移、必要证据缺失；用量不可归因/部分量无法聚合、储备不足或止损触发。实验 FAIL 与领域状态分列，不把合法 SUCCESS/RUNNING 改成恢复态。
- INVALID：错误发行/冻结输入/需求/出口或启动前提使本轮无法证明目标，运行中更换判据、手改账本或伪造证据。合法客户端形态引起的产品/归因缺陷不能降为 INVALID。
- Stop-loss：170万可见可归因请求 totalTokens（含 cachedRead），120万预警，18次顶层 prompt（含 correction），3600秒活动时间，600秒命令上限；session bootstrap 仍为45秒，不因历史一次超时扩大。无新证据同根因两次、有新证据语义循环超过2次、外部 UNKNOWN、用量不可核、身份/scope/authority 漂移或必要出口缺失时停止新派发。失败按当前 AGENTS 的 TRANSIENT/BOUNDARY 分级处理，所有实际请求计预算；不因允许重试而豁免储备，不靠重试探测未知副作用。
- Run artifact freshness：selected-release.json 的659输入、CLI `6e928a89a364fa24271972bfae3b71fe4b9c909782bfa14ca2d4ef7a36aba3a8`、server `34197f5f746821f87ff8e363b61995aaf57e8615c6bd57903bc32da6cda05c4e`，仍为上轮独立冻结副本；本轮逐项重核。原1813/210单轮只作该发行历史依据，本轮未跑 Maven、不加总。本包脚本、配置、SDK候选纳入 launch-identity.json；材料哈希不授运行许可。

## 新实例与已装入的修正

- target：`/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/target`
- worktree：`/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree`
- branch：`codex/w09-newlines-20261023-offline-08`
- workstream：`ws-20261023T005431Z-9YYF`，由原 start 生成。

初始 target/worktree 为 clean 空树，base 与 git-common-dir 一致；current 为 RUNNING、初始 context READY，零 run、门和业务批准。实例放在持久 output 隔离目录，避免准备到批准间隔依赖 /tmp 生存期；不复制 Dual 源码，父项目文档不能充当已批准的产品 scope。

三个真实脚本入口（prepare-offline.py、pre-dispatch-recheck.py、run-one-step.py）导入同一份受审且字节不变的 process_check.py。EPERM、超时、非零退出、stderr、空表、畸形/重复行都为 UNKNOWN，已知 PID 存在为 PRESENT；只有成功完整读表且具名 PID 均不在才为 ABSENT。UNKNOWN/PRESENT 均停止，绝不把检查失败解释为进程结束。准备入口在创建 fixture 前检查；重核入口在准入前检查；runner 在创建 step/启动 Java 前检查。PID 复用不静默豁免，具名 PID 缺席不等于任意子树/远端都已停止。

本轮准备中已成功读取进程表核62个已知旧 PID，核18个旧具名 session 关闭；实际 false 授权拒绝发生在 fixture/step/Java 之前。消费者拒绝测试抽取实际入口语句，证明本包连接使用共享 guard；不冒称已执行获准后的完整业务 runner。

## 实际 SDK 身份与证据边界

上次唯一获准的禁 prompt 初始化观察3.548秒成功，随即关闭并核验，未发 prompt。实际子进程是：

- package：`@anthropic-ai/claude-agent-sdk-darwin-arm64`，版本 `0.3.232`。
- 路径：`/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk-darwin-arm64/claude`。
- SHA-256：`7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7`。

observed-sdk-identity.json 给出原进程观察/result 的路径与 SHA。二进制与 package.json 当前字节复核一致，加入 launch-identity.json 的 config_and_installed_sources，由真实 frozen_check 在未来入口强制消费；离线测试模拟两者任一漂移均拒绝，未修改安装文件。

这是“此前实际观察 + 当前已知文件基线”，不是未来 SDK 选择被永久固定的证明。个人 PATH claude 的身份不能替代上述程序；不锁版本、不指定新 SDK、不安装、不修改配置。原生 npx 未来若解析到其他字节，本包不预授兼容；须按实际运行证据处置，不能拿这里的旧基线冒充当次选中身份。bootstrap 成功不证明 GLM 推理/完整主流程，也不能倒推第七例45秒超时的内部原因。初始化观察授权已耗尽，本包没有再次初始化。

显式选本仓受审 acpx wrapper（1517f828 patch对应原固定 dist）、原600秒、liteflow和隔离 operator registry；不使用 PATH 其他 acpx，不把 config 的 approve-all 当 Dual 许可。实际 policy 仍由原 transport 逐turn绑定，correction deny-all；原生 ask 若发生，仍须用户对当前 offered-once 请求决定并经 C/X，ACK 不等于执行。未触发分支记 NOT_EXERCISED，不造探针刷覆盖。当前配置/安装候选相对上一包无字节漂移，补记实际 SDK 两项，共31项。

## 储备与预算（未改）

budget.json 与已受审校准表字节相同：前四阶段有小样本，后五阶段625k仍为假设，均不是p95或成功率承诺。170万是风险上限，不是必要消费量。

| 规划 | token | 请求 |
|---|---:|---:|
| 正常九节点 | 955,000 | 9 |
| 主链+readiness重跑+一次修正 | 1,280,000 | 12 |
| 上行+需求返修 | 1,370,000 | 14 |
| 主链+readiness重跑+设计返修+一次修正 | 1,520,000 | 14 |
| 主链+readiness重跑+需求/设计各返修+一次修正 | 1,610,000 | 16 |

每次模型派发前：已用 + 全部未完成主链 + 当次重入/返修影响的重复工作 + 一次可能修正150k ≤170万，请求池同构≤18。首次储备1,105,000/10请求，不是已花费。下一 agent 块预留1500秒；业务人门等待单独暂停，原生执行/权限等待计活动时间。21项离线算术对照保留，budget ALLOW 不授业务或执行许可。

按完成粒度核真实用量，不承诺请求内部硬抢占/绝对不超支；越线后零下一派发。快照/部分量/UNAVAILABLE不当0，不人工回填。reader/observer保留当前 transcript 优先、无当前完成才走严格唯一共享出口；stream-only跨代重复仍停止，不自动开启B案。token、provider/model、billing、金额可计算状态分开，不把SDK金额当订阅实付。

## 业务门、失败与批准责任

运行授权不代替当前需求、GOAL、EXECUTION、最终交付、readiness声明或原生 once 决定。用户仍是决定者及产品 commit 责任人；Gate 只准运行，不能批量填尚未生成的 subject。readiness 在实际合法入口固定 ref/SHA，不造占位引用；验收需求/证据类型变化仍复评重批。交付只消费当前最终批准 candidate，apply 后等用户 commit 再 finalize；外部未知走原 D07/D09，不直接重发或修改账本。

AC-1/2实际Python测试，AC-3实际read/例核对，不统一强制真实退出码，也不能用 completed 替代条款要求。非阻塞验收备注按当前合法 typed 结果处理，不另造wire枚举。原toolCallCount/通用RUNNING恢复提示的第二类债保留；预算不消费重复工具计数，通用retry不是继续许可，若影响必要证据/安全动作即停。浏览器/多worker/多ClassLoader等不在本Gate证明范围。

停止后关闭本次具名会话、核成功可读的进程观察并保存现场，不泛化子树全停。第七例及原七例材料、停止标记、usage不改；新实例不借旧批准、旧余额。本轮离线材料齐备后交用户批准本次真实运行；获准后必须再只读重核，失败即NO-GO，不能重新生成实例绕过。
