# 开工边界与次序

目标P0：把已受审的辅助进程修正装成第八例的独立续作Gate；保留所有历史结果，预算/请求/活动时间累计，不改变Java或创建新例。P0整体未Promoted。本轮只离线装配，真实运行禁止。

SoT：领域current/node/gate/合同/完成仍归原ledger；用量仍归原R03/occurrence，observer只核验；原记录只读；新包只拥有本次准入、操作回执和实验停止记录。Definition/Instance仍是原工作流与实例，Execution不重放。新的continuation-spec是实验授权范围而非领域状态机。

|前态|动作/owner|写入/外部次序|后态/消费者|失败/恢复|
|---|---|---|---|---|
|原包INVALID、领域RUNNING/READY|离线装配|固定原包/账本检查点、累计回执、原观察链接；真实授权false|新包待批，不改变原状态|任一依据不匹配停止准备，无原账本写入|
|新包未批准|runner/共享continuation guard|先检查新授权，之后才读取/检查执行状态|拒绝在目录/Java前|无凭旧fresh授权继承；冷启动仍false|
|未来具名批准（本轮只合成验证）|原身份检查+续作检查|固定原包、首次完整checkpoint、观察SHA、原固定使用量底线、活动时间→进程前置→当前READY与身份→预算→新step目录|只授一个原Java入口的调用资格|原停止标记保持；新停止/身份/未知/时间不足拒绝；已有不完整新step不得重发|
|首次后正常追加|相同runner|完整历史固定使用量重读，旧用量不得减少；旧时间底数+新回执，禁止覆盖step|原Java仍守资格、合同、业务批准、terminal|不因新step目录存在就容忍无result；不为失败/终态造恢复解释|
|合法Java入口（本轮替身）|原run --max-steps 1|启动→process回执→wait→result→只读重核/预算|原ledger真实结果；新包记录观察|外部未知/超时停止；回执写失败不自动重试，检查原外部状态|

业务批准、精确scope、最终交付门、用户commit均不委派。初次下一步plan.decision不是implement；已有readiness/GOAL材料不能自动扩大为写许可。

测试范围：整个新辅助包的离线测试、完整runner/重核脚本执行到本地进程替身、实际历史reader只读复核、失败写点/冷进程/篡改/预算/拒绝正反。不是Java/provider真实E2E。无新后台服务/锁/DSL，复用原observer和runner；需要改产品恢复协议则停止提交取舍。
