# B06 / W06 / D09 批末材料（2026-10-09）

状态：**实施与离线验证完成 / 作者自查 PASS / PENDING_CC_BATCH_REVIEW**。用户在接线设计 CC PASS 后明确“授权”；本批不自行收口 W06、不解除真实 NO-GO、不启动下一批。交付目标为批准候选→目标应用→人工实际 commit→finalize，异常通过具名裁定合法续作，零盲目再次应用。

## 1. 范围与依据

冻结依据：[协议](../wiring-candidate/protocol.md)、[设计复审处置](../review-acceptance-20261009/disposition.md)、原 [preflight G01–G07/V01–V07](../preflight.md)。本次 21 项源码/资源增量（含唯一新生产文件 DeliveryResolveCommand）及 3 项产品文档增量；条件范围见 [approved-scope](approved-scope.json)，内容处置见 [changed-files](changed-files.json)。原 dirty 工作区的 653 项内容为基线，新 CLI CREATE 单列；不把此前 B01–B05 改动当本批增量。工作清单/详设只同步本批状态。

[源码增量](incremental.patch)来自开工前文本与现在内容；[文档增量](documentation.patch)的基线由本批修改逆向重建且与开工前已测 SHA 完全相等，不冒称另有开工时文档全文实测。[文档基线说明](documentation-baseline.json)。未改 resolver 和 plan writer：原严格谱系与 L07 writer 可复用；未因候选列名就修改。

## 2. 实际接线

- 原 delivery owner 严格消费完整批准/candidate/source/target/common-dir/base/tree/path sets/runtime/operation 绑定，最新 occurrence 不回退。普通 merge 重读旧 intent 只核对/补同操作 APPLIED，不能启动新的 Git。
- 原 live apply：intent 完整 L07 → adapter 再核当前目标 → 实际 Git 启动 → PID/start 同操作 L07 完整返回 → 输入一次 patch → waitFor 实测退出先固定 → 输出/目标观察 → APPLIED 或 UNKNOWN。记录/收尾失败不伪造未执行、不冷造 callback。
- resolve：同 id 同完整 request 先匹配原耐久记录，再返回当前 view；不先要求已过期原 plan/sequence。新请求才核全部当前身份/停止依据/目标 SHA；CLOSE 单向关闭且不声称停止；AUTHORIZE 仅旧操作确认停止、精确 base、原批准/源码合格，记录决定/前驱/新 intent 后仅本次 live 调用尝试一次。
- 程序 PID 必须有同 operation/binding 的 start event；程序结束具名来源或完整已登记主进程已消失，不推出任意子树全停。人工核查固定为 OPERATOR_DECLARATION / OPERATOR_CONFIRMED_STOPPED，不能写 process_exit，不能覆盖活动反证。精确 commit 本身不证明旧 apply 结束。
- finalize 从 UNKNOWN/APPLIED/closed 核停止依据、实际 clean HEAD/单 parent/base/expected tree，再 L07 固定 COMMITTED；零 apply/commit。历史 COMMITTED 遇目标后续演进仍保留历史，不提供当前修改动作。
- CLI/API/console/mgt 共用 owner currentActions/nextCommands/stopBasis/history/template。status/summary/overview 先 target→L 采样、释放 target，再 L 同代嵌入，stale 零动作；不在 L 内反向取 target 锁。实际命令始终重核，不借 view 布尔授许可。

完整 consumer/义务/用例映射见 [consumer-map](consumer-map.json)，写序/失败出口沿 [transition-plan](transition-plan.md) 与冻结协议，不另造模型、锁服务、outbox、DSL、安全平台或自动提交能力。

## 3. 验证凭证与边界

**单轮全模块 clean：1752 tests / 206 suites / 0 failures / 0 errors / 0 skipped，exit 0**；[test-results](test-results.json)、[完整日志](reactor-final-01.log)、[原始 XML](final-reports/)；前后 654 个输入 SHA 完全一致。最终回归后未改任何源码/资源。[before](reactor-before-sha256.json) / [after](reactor-after-sha256.json)。普通必要验证未改断言或绕开权限；仅为既有自建 Java 子进程检查放行系统接口。

证据不能混合：

| 层次 | 本批实际证明 | 不外推 |
|---|---|---|
| 常驻完整正常链 | WorkflowFirstLifecycleSequenceTest，四变体×inline/worker，真实程序作者/复评/三道人门→目标实际 apply→测试扮演人创建 commit→finalize/幂等；未经最终批准零目标应用 | 外部 provider 是合成替身，非真实 Claude/Codex E2E |
| 常驻 service/adapter 序列 | 生命周期回调先记录后输入；停止/批准/目标/来源拒绝；合法别名、同内容/异内容、CLOSE/新授权、材料快照、真实临时 Git 提交与历史；两 workstream/两实例共用 target锁，stale 零动作 | 若 fixture 手写了批准/终态，只证明交付消费者；不与完整链拼成“真实模型能力” |
| 常驻失败序列 | 应用 intent/PID/exit/observation 四阶段×八前缀、真正冷子 JVM 固定补齐/重读零再应用；close/finalize 两阶段×八，新裁定八前缀；程序/人工类型分立 | close/finalize/新裁定的补齐不另冒称各有冷 JVM；冷 JVM 实证在应用四阶段 |
| 额外逐目标补证 | [22 个具体 plan/current/compatibility 写前/后](write-target-proof.json)，pending reader 拒绝、固定补齐零重新应用 | 一次性编译补证，不计 Maven 或冒称常驻 |
| 额外真实窗口/查询补证 | [intent 后 clean HEAD 漂移拒绝且零子进程](apply-window-proof.json)；[实际 status/overview 顺序与反向顺序断言反证](query-lock-proof.json) | 不计 Maven；不声称能阻止所有绕锁外部 Git |
| 删除守卫反证 | [5/5 隔离 expected assertion failures](counterproof/results.json)：停止/fresh/重复判定次序/PID回执/启动身份；另有查询反向顺序补证 | 保存在隔离副本，仅证明用例可辨别，未改变主工作区源码 |
| 两 UI | [Node VM/表单检查](ui-verification.json) + 两文件语法检查，取消零提交、合法明确授权保留 owner 绑定、纯 close 不伪造停止，动作来自 core | 未做浏览器视觉验收，不宣称 UI real E2E |

先红证据 [red.log](red.log)/[red-reports](red-reports/)是旧实现面对新合法要求的 10/1：精确 commit 缺停止依据错误可 finalize。compile-01 是实施阶段 checked exception 编译错误；focused-01 是合法 canonical source 被误拒，已修；focused-03 的注入代次和 focused-05 的 stale code 期待是测试作者错误，非生产反证；write-target-1-fail1 是补证选择器路径别名导致未注入，已修正后 22 点通过。focused-04 期间后续 runtime 新用例已继续修改，因此不作为当前整批凭证；最终单轮唯一凭证如上，不拼旧绿灯。

## 4. 六维自查与完整性

Declared：D09/I6、受审具体协议、用户本批授权。Proven：上述分列离线序列/临时 Git/冷 JVM/写点/反证。Consumed：delivery 唯一 owner→实际 Git callback→原 L07→同源 view。Enforced before：当前批准和目标/启动前、PID完整提交/patch输入前、停止/commit匹配/成功写入前、请求 identity/决定/new apply前、同代 current action前。Failure route：D06 固定本地字节，UNKNOWN 人工核实，零自动外部重发，closed 不伪装停机。Cannot-bypass：隔离删除式反证与合法正向成对。详见 [作者自查](self-review.md)，不是独立评审。

两设计 LOW 已落地：停止与 commit 独立；canonical target/request 等价、每个 identity 字段差异、只去顶层 observed_at、不递归丢同名字段的常驻测试。请求保留 note/停止/risk/material 内容；事件条目 SHA 不用整个可追加日志 SHA。

## 5. 未验证/不随批成立

未运行真实 native/provider/installer/真实目标 Git、浏览器视觉验收；没有提交本项目、推送或调用额外 agent。真实客户端权限/停止/工具退出行为仍不能由本批合成 provider 推导。D01 其余族、W07 减负、W08 总体组合与 Gate、W09 fresh 真实交付、外部部署/多 ClassLoader、kernel I/O、B05 非驻留时钟 LOW 均不因本批关闭。

不支持旧 ledger/binary 兼容救援，不新增 no-change 空交付、任意子树控制、安全沙箱、后台恢复发送或 reset/clean。query/write-target/apply-window 的一次性补证如实登记；常驻基础与补证不能互相冒领。

**下一步：用 [CC prompt](cc-review-prompt.txt) 做 B06 合并批末独立评审；通过并接收发现后才能收口本批。之后核对主清单剩余项和 W08 准入前提，真实运行仍需独立 Gate 与授权。**
