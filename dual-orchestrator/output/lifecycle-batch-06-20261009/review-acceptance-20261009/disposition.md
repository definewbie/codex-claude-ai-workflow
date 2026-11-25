# B06/D09 设计复审接收与冻结补记

2026-10-09。用户带回的 CC 结论：设计层 PASS、可冻结；无 WARN/FAIL，2 LOW。原文见 cc-review.md。接收核验：15 项交评材料内容、653 项源码输入均与交评身份一致；这是材料身份核验，不重演或替代 CC 独立判断，不是新测试。

当前状态：**B06/D09 具体接线 DESIGN_FROZEN / NOT IMPLEMENTED / AWAITING_IMPLEMENTATION_AUTHORIZATION**。冻结对象为 ../wiring-candidate/protocol.md 及本补记；reviewed-material-sha256.json 保留交评时字节身份。原候选/preflight/self-review 的当时状态保留，主清单和详设显示当前状态。W06/I6、整体 W 项及真实 NO-GO 不关闭。

## LOW 处置与实施义务

**LOW-1：理由落文完成。** commit 对象存在且 parent/tree 正确，证明的是当前记录的提交内容；它不能单独证明旧 apply 进程已经结束或不会再产生变化。这两类事实由不同生产者提供，必须分别消费。停止依据齐备时可由原 finalize 合法确认；缺依据时经 resolve 固定人工核实结果，始终保留人工类型及旧 UNKNOWN。不得要求已经有合法程序结束证据的用户再走人工裁定，也不得凭 tree 正确跳过缺失的停止依据。

实施成对验证：精确合法 commit + 程序结束依据→原 finalize 可达；同 commit + 无停止依据→不宣告当前完成，并提供 resolve；已知活跃 + 人工声明→拒绝放行；合法人工停止确认后→原 finalize 可达、零 apply/commit。历史已确认 COMMITTED 与今日 target 后续变化仍分别展示，不重判历史为未知。

**LOW-2：纳入 B06 必需验证，尚未执行。** 例子是合成字段样例，不是实现证明。实施应在同一规范字节函数上验证：对象键排列或仅顶层 observed_at 改变不改变 target 身份；所有实际身份字段（repo/common-dir、HEAD/tree/index、clean/worktree、status/untracked）改变必须改变身份；数组按契约确定次序；不能递归忽略同名时间字段；请求完整 SHA 保留 checked_at、停止依据、note 和材料引用。等价请求在不同入口产生同一规范字节；同 id 不同语义仍 INVALID。删除这些比较/错误忽略字段必须被反证抓住。该项 NOT RUN，B06 收口前必须落入常驻回归，不能用 examples.json 的格式核验替代。

## 对评审措辞的限界

采纳的是本次受影响范围的设计 PASS。源码可实现性、实际成本、并发、故障前缀、冷启动和真实行为均未证明；“最优”“无死端/误拒残留”“消除死锁”只作评审对候选的设计判断，不升级为运行保证或穷尽性证明。

“全部设计侧收束”不能外推为所有今后设计问题都不存在：D01 其余族仍按既定取舍未完成，W 项也未关闭；实施发现新的语义缺口/成本偏差仍必须停下分析与取舍。两 LOW 仅补理由和既有验证义务，没有增加新权限或平台，本次不另开设计评审轮次。

## 下一步

等待用户明确授权 **B06 完整 W06 工作包实施及离线验证**。授权后沿原 preflight G01–G07/V01–V07 与冻结协议 §8 推进：统一目标/批准身份→标准 apply 有限 callback 与 L07→resolve/幂等/停止依据→同源 view 与兄弟入口→合法/拒绝/每写点/冷 JVM/锁序/规范字节反证→内容稳定单轮回归→CC 批末独立判断。

单个切片通过不关闭 W06；不自动 commit、push、installer 或真实 native/provider/目标部署，不启动额外 agent。发现需要新 authority、服务、全进程树监督或实质成本偏差时停止交证据。最终完成口径是批准成果能够合法应用、由人实际提交、确认，并有明确中断出口；不是新增字段或全部拒绝。
