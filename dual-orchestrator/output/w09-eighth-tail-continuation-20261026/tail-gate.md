# 第八例增额续作 Gate

Target：P0 完整主流程；当前实现成功→自测→独立验收→最终交付人门。最终commit仍由用户执行。成本优化不在本轮目标。
Source of truth：原Java ledger/workflow/current、node、固定R03/run/occurrence、验收typed artifact、原交付owner；operator仅核对预算。
Trigger precondition：同一RUNNING workstream、自测READY、无pending/外部未知、原EXECUTION批准有效、冻结发行/配置/E不漂移；新具名授权与新checkpoint SHA匹配。
How checked：原frozen_check、continuation.inspect、逐请求measure、每步原Java资格守卫；先运行离线入口与同预算算术。
PASS：自测、独立验收及逐条证据真实成立；完整交付PASS另需最终人批/用户commit/原finalize。到人门不声称COMMITTED。
FAIL：领域拒绝、非法放行/误拒、预算或储备止损；外部未知立即停，不通过重试探测。
INVALID：发行、配置、checkpoint、观察、预算归因或装配错误使本次不能证明目标。provider/model原值缺口保留，不声称此项已证；金额优化不是本轮验收。
Stop-loss：累计3000000 token（含cachedRead）、18请求、3600活动秒；每次派发前保留未完主链+当次返修+一次修正储备。单请求内部总量仍只能完成后核对。BOUNDARY停；TRANSIENT按既有合法入口及额度，不放宽契约。
Freshness：沿用已受审659源/2JAR和原frozenRuntimeIdentity，固定新operator预算材料；不修改旧包与三份历史stop。

Transition：授权false→材料/测试冻结→填launch SHA→授权true→只读重核→固定step→原Java派发→固定R03/L03→全量核账→下一入口。任一未完成step/回执或未知副作用先停核外部；不重跑同step。新包自身stop仍拒绝。

本轮是用户显式增额后的同一case续作，不救旧Gate结论；旧FAIL保持FAIL。新包无新Java/acpx/schema/锁/服务，不做成本归因修复。
