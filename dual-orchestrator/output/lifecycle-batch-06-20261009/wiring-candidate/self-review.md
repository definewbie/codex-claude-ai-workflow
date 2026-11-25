# B06 接线候选：作者自查与证据边界

2026-10-09；DESIGN_CANDIDATE / AWAITING_CC。本文件是作者自查，不是独立评审，也不是实施或运行 PASS。

目标 P0：补齐已获批研发成果的应用、人工提交、确认及中断后的合法出口。候选不扩展目标为 Git 自动化或进程安全平台。Definition 的交付约束、Instance 的终态/最后批准、Execution 的独立 delivery operation 分开；终态不重开，view 只消费原 owner 的事实。

Analyze→Modify→Review→Reflect→Analyze：先核现有 target→L、L07、Git Process 与 scoped view；只修改材料；自查发现并修正“程序观察主进程消失≠实测退出码”、generic artifacts 不自动不可变、重复请求不能先按旧 plan SHA 误拒、closed 不得重新授权、query 不得 L→target。反思重点是合法出口要有真实生产者和消费者，但不能把人确认、锁和目标树都包装成机器全停证明。最终候选据此保留未知窗口并提交独立判断。

| 六维 | 本轮可成立的材料事实 | 未完成的义务 |
|---|---|---|
| Declared by | 原 D09/I6 + 用户补接线授权；候选 §1–§9 | 人工停止声明的适用边界等待 CC，不能预称已冻结 |
| Proven by | 当前源码锚点与字段例子、写序推演；源码内容核验 | 新功能全部 NOT RUN；旧测试不计为本轮证明 |
| Consumed by | 候选具名原 delivery service.resolve / apply / finalize，view 不授执行资格 | 新入口/严格字段解析/process callback/view 组合均未实现 |
| Enforced before | 规定同 L07 决定、新调用前基准、patch 输入前 PID 记录、采纳前后态核验 | 尚无 B06 新执行点，不能称为 ENFORCED |
| Failure route | D06 只补字节；外部不明保留 UNKNOWN；关闭不表示停止 | 各写点、真实进程中断、冷 JVM 全链尚未测试 |
| Cannot-bypass test | preflight V01–V07 与候选 §8 成对合法/拒绝/前缀/锁顺序义务 | 删除式反证、整链和单轮回归待实施 |

三项需独立核验的承重点：

- **人的停止确认**：候选在程序事实不全时使用操作员声明，保留类型和旧 UNKNOWN；已知活跃不能覆盖。原 D09 有人工裁定要求，但不据此推定这个具体信任选择已被批准。CC 若认定需用户取舍，必须明确提出。
- **有限 callback 的窗口**：现有 Git adapter 有真实 Process/waitFor，尚无输入前 durable PID 回调。start→记录崩溃窗口保留；callback、清理和退出记录的实际可实现性仍待实施验证，不声称任意子树受控。
- **组合 query 锁顺序**：现有 status/summary/overview 在 L 中消费 view；新设计先 target→L 取 view，再在原 L 快照按 sequence/plan/candidate 核对，防锁反转及历史误拒。未以代码或并发运行证明。

来源及范围：上级 preflight/source-checks 加本目录 source-checks.json；24 条原条件路径 + 1 条具名 CLI CREATE 候选，不是修改许可；详设 §9.1、主清单 §22.1 与 preflight §10 同步。generic artifacts 的 immutable 检查由该 owner 明示负责，没有新账本、索引或服务。请求/材料读取上限是本入口候选限制，不宣称现有通用限制已经存在。

未核验：本轮不运行测试、临时 Git、native/provider、installer；未实证特定 OS/Git 的停止/配置/子进程行为；未做浏览器验收、真实目标操作或新的整链结果。没有全进程树保证、绝对 exactly-once、自动 commit/回滚或恢复旧样本。

下一步：协议与原预检一次合并 CC 受影响设计复审。WARN/FAIL 回到边界；PASS 仅判断候选是否成立，实施仍需用户授权。届时沿完整 W06 批次实施及离线验证→批末独立判断；真实 NO-GO、总体 W 项及提交权限保持。
