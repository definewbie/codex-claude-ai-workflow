# W09 fresh 第六例完整主流程 Gate 候选

2026-10-15。状态：PROPOSED / AWAITING_CURRENT_USER_APPROVAL / NOT_CREATED / NOT_RUN。
本候选接收已通过的身份减负与目标级返修修复，延续已评审主流程 Gate 的目标、验收、预算储备及停止规则；旧案例授权、余额不继承。

## 要验证的主流程

[原产品需求](../w08-preparation-20261010/task-requirement.md)：Python 标准库换行规范化函数、unittest 与 README。AC-1 至少八类输入及幂等；AC-2 实际执行输出有覆盖数及零 failure/error；AC-3 README 例子与代码一致。AC-2 不统一强制真实退出码，但不能把完成状态或模型断言当实际测试证据；新增或变更验收必须经当前批准。

跑到：真实提案/独立复评 → 当前 GOAL 批准 → 设计/计划/独立复评 → readiness 补齐（必要时） → 当前 EXECUTION 精确范围批准 → 实现/自测 → Codex 独立验收 → 当前最终交付批准 → 隔离目标真实 apply → 用户 commit → 精确 COMMITTED 确认。不以设计 READY、实现完成或测试通过代替完整链 PASS。

新 wire：返修模型只引用精确 base ID；完整引用/SHA 由同次 R01 程序固定并强制消费。仅首次 GOAL 前真实目标/禁区变化可走已实施 full proposal 出口；新 subject 仍经新独立复评与当前人门。已授 GOAL/EXECUTION 后目标改变停止交用户，不在线重写。

## Verification Gate

- Target：一个 fresh 合法任务跑完真实研发、逐条验收、批准交付及用户 commit/finalize，验证受审新接线在正常主链可用；不承诺 token 节省。
- Source of truth：当前 typed 合同/node/gate/ref+SHA；R01/R02/R03/L03 固定事实与唯一 outcome；程序 verification-observations、源码/candidate 身份；delivery 唯一 owner 的 operation、实际 HEAD/parent/tree/clean 及 COMMITTED。每次 acquisition/occurrence 的固定 usage 与归因材料支撑预算；Gate observer 只消费这些事实，不产生业务批准或修改账本。
- Trigger precondition：用户当次批准本 Gate/170万/18次/60分钟及隔离准备/运行；受审发行/659 输入一致；新 target/workstream/session；单 INLINE/liteflow/same ClassLoader；旧执行者状态已核、无 UNKNOWN 执行重叠；各验收条款的现有工具/补齐出口可达；observer 与储备规则离线核齐。
- How precondition is checked before run：重核下述发行与输入 SHA；核 selected wrapper、build-info、实际 resolver/config/launch/PATH，并在实际 R01 再核（不以旧常量当当前装配）；原接口核旧具名会话/进程状态，不推断子树全停；新目录确为空且不存在旧账本，动态引用由原 owner 真实生成；核宿主 Python/stdlb 出口但不冒称已证原生工具；固定当次 observer/预算/配置字节，离线执行原预算和观察器正反对照，不先调用模型探针。任何前提未能回答，不派发。
- PASS：完整当前批准谱系、逐条真实验收证据与独立验收、精确 candidate、最终人门、真实隔离 apply、用户 commit 与精确 COMMITTED，且用量归因/预算准入/止损纪律全程成立。
- FAIL：有效装配下误拒、死端、越权采纳、缺证据、主因漂移、产品验收失败；用量不可归因/部分量不能聚合、储备不足或止损触发。领域合法结果保持原状态，不为实验失败伪造恢复需求。
- INVALID：启动前提、冻结输入/装配/需求/出口错误导致实验不能证明目标，或运行中换冻结输入、手改账本/验收。有效运行中的客户端合法形态导致归因失败按 FAIL，不降为 INVALID。
- Stop-loss：170万可见可归因请求总 token（含 cachedRead）；120万预警；18 次顶层真实请求（含 correction）；3600 秒活动时间；单命令600秒。同根因无新证据两次，或有新证据语义循环超过2次；UNKNOWN 外部结果、缺 usage、身份/scope/authority 漂移及成本收益扩围均停止新派发并保留材料，不自动重发/申请加额。
- Run artifact freshness：本轮受审冻结发行与659输入作为启动身份；本次 SHA 复核已通过，开跑前必须再核。不重建/换 JAR 后继续使用本 Gate，不用历史1790绿灯代替本轮1805离线证明，更不把离线 proof 当真实成功。

## 当次隔离与发行

候选 target：`/private/tmp/dual-w09-newlines-20261015-fresh-06`；候选 branch：`codex/w09-newlines-20261015-fresh-06`。目前未创建。若重名已存在，停止选新的唯一后缀并登记，不覆盖旧目录。workstream/session/occurrence 由现有入口生成，不能离线伪造。

使用 `../w09-revision-forward-fix-20261014/release/` 的独立冻结副本：

- CLI SHA：`53c6da9fe711dca9161eddfeee9d3ef74ec48f1b525c41493a253ef638de98da`。
- server SHA：`bca51b56a6f3af8a4cf063e9e85ad58431fe36d3342d04dc89f9679a588ddd7c`（本例不用另启 server/worker）。
- 659 源码输入及发行详情见原 release-identity.json；本次重新核验见 identity-recheck.json。

保留现有 Claude Code 作者/Codex 评审客户端配置；Dual 不要求模型品牌绑定，不新增版本锁/effort/思考/轮次实验。实际配置若与已评审组合出现实质差异，先提交差异，不假定兼容、也不自动扩权。

## 派发前储备与预算口径

预算细项见 budget-proposal.json。正常9节点940k；readiness重入175k、一次intake返修90k、一次格式修正150k的示例为1355k/14请求，余345k/4请求。这些是规划额度，不是p95/每调用硬上限，不预扣任何假设节省。

每次 agent 派发前按实际剩余主链重核：已用token + 全部未完成主链储备 + 本次必需返修/重入 + 一次可能修正150k ≤170万；调用池同构≤18。修正实际发生时避免重复计同一次修正。活动时间按原规则预留1500秒（2×600+300临时准备/收尾额度）。人业务门等待可单列暂停；原生执行/权限等待不得冒充零耗时。禁止为了通过而削减尾链储备。

真实总量通常在 completion 后取得；不承诺单请求内部抢占或绝对零超支。单请求越线后停止下一派发，实验 FAIL，领域结果如实保留。缺量/快照/歧义不能填0，金额与订阅计费独立呈现。

沿用受审 A 案 observer 与预算检查器（出处及SHA见 referenced-inputs.json）；获准后再复制到当次包、核配置与离线对照，不只换常量跑旧脚本。按 run+occurrence 核固定完成/usage/acquisition/日志SHA；当前调用日志优先，必要时严格唯一共享出口；stream-only且跨代重复仍不可归因即停，不自动开启B案。

## 合法出口与边界

readiness 仍按 [原逐条出口](../w08-preparation-20261010/verification-exits.md) 在真实 GOAL/等待门后登记实际 ref/SHA，声明不等于机器执行，不造 fresh 前引用。原生所需工具不可用则停；切 operator 或改 evidence/executor 必须改合同重批。scope、独立验收和最终人门均不降低。

GOAL/EXECUTION/最终交付、原生 offered-once 权限请求各需用户对当前 subject/请求作实际决定；本 Gate 预算批准不代替这些决定，用户负责产品 commit。真实 ask、外部 UNKNOWN、worker、浏览器及未自然触发的故障点记 NOT_EXERCISED，不制造新安全探针。已登记 toolCallCount/通用恢复投影限制不作预算或续作许可；若影响必要证据/安全动作停止。

第五例不恢复，旧余额不继承，历史 FAIL 不改写。当前只完成候选材料与身份复核，全部启动时条件仍待获准后当次核齐。
