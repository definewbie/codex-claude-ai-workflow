# B05 首族边界自查（进行中）

本文件为主 agent 自查，不是独立 CC 评审。

已验证：实际批准快照、真实 readiness 依赖变化与原输入恢复、消费时未来 compiler 条件拒绝、测试自造错误 checker 的矛盾解释、未采集直接入口 UNKNOWN、同 code 异 rule/错 target/version/decision SHA 拒绝、L04/L03 八类故障点、冷子 JVM 固定续完、R03 写失败及删 pair 拒绝。事件用条目字节身份，普通追加无关事件保留合法资格；原生/provider 为零。

Review → Reflect → Analyze 的追加发现：原检查器除 native 准备外，还被 context、scope、failure classify 与 report 的任务入口消费。它们的既有 qualification_failure builder 只保存原 reason/code，未传递新的 typed 诊断；需补同一失败事实的携带，不能把 native 切片通过视为全族消费闭合。原 outcome/reason/failure_code/errorMessage、产物拒绝和原 L03 顺序须保持。非原生本地任务走 L03 prepared 写集，不伪造 R01/R02/R03 或 providerInvoked=false。此增量是同族 sibling 消费接线，不采集其它 guard 家族、不加许可。

此外，covered 决定 API 的 null 与明确未采集的内部决定 API 应分开；缺实际 snapshot 的 covered 入口零决定写入。恢复输入比较只剔除 readiness 的检查时刻/覆盖元数据，不能在任意对象递归忽略同名字段。

第一轮全模块回归正在验证其已冻结源码；追加自查修正后仍需稳定源码的最终单轮回归，不能拼接二轮数字当最终 PASS。

2026-10-09 追加：上述四个任务、covered null、限定 readiness 元数据和原 code 关联均已修正。focused-12（实际两入口四变体）通过；R01/R02 补测中的 focused-13 是 fixture 路径错误，focused-14 真正暴露 dispatch 诊断在已持 L 的会话里重取 L、使可靠配对退为 UNKNOWN；改为传原 scoped ledger 后 focused-15 的 49 项全部通过。R03 准备拒绝与 R02 意图在原 R 锁内互斥，固定拒绝不授新发送，已存在 R02 不可伪装零执行。

额外时钟补证与隔离删除式反证通过（见同目录凭证，零原生/provider），产品源码保持冻结。最终单轮 clean reactor 正在运行；最终完成与独立收口另按 batch.md，不把此前绿项拼接成总数。

最终（2026-10-09）：reactor-final 单轮 clean 1,731 tests / 206 suites / 零失败、错误、跳过，653 个源码/资源/pom 前后 SHA 相同，报告新鲜。主 agent 自查完成，独立批末评审仍待 CC；所有失败/INVALID 日志保留，不拼接计数。

收尾格式核对：清除本批 Verifier 的 13 处行尾空格并同步仅测试的 source-bound 索引后，重新编译及索引守卫通过；1,109 个已编译 class 字节全部与最终整轮回归相同。整轮 653 源码前后 SHA 凭证对应其运行期间，当前源码的格式变化另由 post-format-source-sha256.json / format-proof.json 记录，不伪称回归之后未改源码、不拼接测试数字。git diff --check 剩余告警属于此前 acpx patch 的上下文空格，本批没有修改该补丁。
