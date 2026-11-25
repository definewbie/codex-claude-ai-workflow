# B06 边界自查（主 agent，不是独立评审）

P0 / W06 / D09：批准候选→一次目标应用→人工真实 commit→finalize；失败→保留现场→同版固定字节/具名人工裁定→合法续作。Definition/原 workflow terminal 不重开；Instance 批准来自最后 occurrence；Execution 程序事实仅实际 Process callback，人工声明仍人工；Projection 统一消费原 delivery owner。

## Analyze → Modify → Review → Reflect → Analyze

核对冻结完整转移和失败点后，在原 service/adapter/operation/target锁/L07 内落码；两 UI 和 CLI/API 只显示核心允许动作。本批未新建 model、锁服务、outbox、权限平台或自动 commit。

自查中修复并保留证据：合法 `/var`/`/private/var` 源路径曾被字面比较误拒，改为完整 canonical 路径比较而非放松源码校验；停止观察必须先有同 operation/binding 的耐久 start event，避免无来源 PID 被当程序已停；严格 operation 必需/未知字段和 request 重复键/尾随 JSON 拒绝；当前采样失败分列 DELIVERY_IO，ledger 格式不明不当模型失败。注入点匹配与 stale reason 的两次测试作者错误分别登记 FAIL，不能当生产反证。

六维：Declared=原 D09/I6+受审协议/用户授权；Proven=正常真实程序链（外部 provider 合成）+真实临时 Git+常驻服务序列+分列案头编译补证，最终全模块结果以 test-results.json 为准；Consumed=delivery 唯一 owner→原 Git callback→L07→view；Enforced before=当前批准/目标/start前，PID完整提交/patch输入前，停止/commit匹配/finalize写前，决定及原引用/newapply前；Failure route=D06 fixed-byte repair, UNKNOWN 保留现场及可信人工核实，零自动外部重发；Cannot-bypass=隔离删除守卫断言失败及同锁/冷启动/合法正向对照。

兄弟扫：merge/resolve/finalize；普通首调/重复/裁定新调/关闭/冷启/固定补齐；canonical alias 与不同 linked worktree；双 workstream/双 service 共用 common-dir；CLI status/summary、API status/overview/direct delivery、console/mgt；历史/current/BUSY/pending/stale 不混。未修改 AcceptedDeliveryCandidateResolver（原严格谱系可复用）、DeliveryMergePlanWriter（原 L07 owner 可复用）、FinalizeCommand（原单一调用无需第二判断）；候选未动路径不是欠账。

保持限制：父 JVM/锁释放不证明全停；只核已登记直接 Git 进程，不控制任意子树；无恶意全目录防护承诺；人工材料不证明真假/退出码；精确 commit 不替代停止事实。DOM/Node 验证非浏览器视觉验收。query/apply-window/write-target 编译补证不计入 Maven，未声称常驻；其机制分别由常驻 scoped-snapshot/target互斥与 adapter launch 拒绝用例保护。

无新主线设计缺口/authority 或成本扩围已登记；若独立复审发现，回对应边界纠偏。B06 仍待 CC 批末判断，不关闭其他 W 项，不启动下一批或真实 Gate。

最终单轮 1,752/206/0/0/0、654 输入身份相同；逐目标补证先因路径别名未注入，归验证工具 FAIL，修选择器后 22 点通过，不报生产反证。当前作者自查 PASS；需 CC 独立批末判断。
