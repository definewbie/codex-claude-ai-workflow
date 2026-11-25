# 本轮目标与边界（修改前）

P0.5：当前调用的真实 token/provider/model/billing 类型经现有 owner 固定并被 Gate 唯一消费，解除合法连接重初始化/编号重用的 P0 阻塞。不优化 token，不扩大 scope/权限/预算，不救旧例。A 案不更改 domain schema 或 acpx patch。

Definition/Instance 不变；Execution 的当前命令 stdout/transcript + typed completion 是输入 SoT。AcpxStreamWireLine 是唯一 JSON-RPC grammar owner；AcpxUsageReader 产生 typed usage，AcpxAgentTaskHandler 双 occurrence 消费，原 R03/L03 固定、metrics 投影。Gate observer 是 operator 验证消费者，不变为生产调度器。

| 前态/动作 | 顺序 | 后态/唯一消费 | 失败与重启 |
|---|---|---|---|
| 原资格已过→准备 | 原 R01→R02→命令固定当前 transcript/stderr | 无新的权限或外部调用 | R01/R02 失败遵原 D06/D07，不靠 usage 造许可 |
| 当前执行完成→读 usage | 匹配当前 completion 的 request/可得 session/stopReason→当前 transcript 的唯一结果→typed 完整数值→metadata | reader→handler 固定 occurrence/run | 当前记录重复/错身份/坏数值不借 session stream 洗白 |
| 当前日志尚无完成帧 | 原共享 stream 的严格唯一匹配、有界等待作为现有延迟出口 | 仅不歧义且完整才可测；其余 UNAVAILABLE/snapshot | 不引入连接代；跨代歧义仍拒绝，无最新/文本回退 |
| 当前日志完整→钱与快照 | token 来自本调用；金额仅在共享严格唯一且 token 相同有成本基准时补充；无基准保留 token + session amount，金额未知 | 原 metadata/metrics | SDK 金额非订阅账单，快照不作请求总量 |
| 主调用/correction | 分别读各自当前 transcript→固定 occurrence→同源完整才加总→原 finalize/R03→L03 | run 固定事实 | 部分量不成为总量；完成写入失败不宣称成功、固定补齐不重跑 provider |
| Gate 核对→预算准入 | 校验固定 occurrence 与当前命令 transcript/result；按 run+occurrence 计真实调用，不以跨代 RPC id 去重 | 检查原预算规则后才可下一派发 | 缺证/歧义/重复取同日志仍阻止；历史共享追加不撤销旧固定事实 |
| 历史重读/冷启动 | 读原固定完成与其不可变日志；不重新全 session 查唯一性 | 原回放/投影 | 旧四例保留 FAIL，不迁移或恢复；新发行仅 fresh 支持，旧冻结身份不能接新 binary |

新增/改变 scope：仅 reader 接线、wire 完整数值解析、双 occurrence 参数及相关测试、独立新 Gate observer 候选。共享 fallback 留在同一 owner，仅当当前日志无完成帧；当前坏/错/重复结果不可 fallback。无新服务、DSL、持久化 schema。

测试来自合法流程：跨连接同 session/id 的两个调用各拿自己结果；主/correction 独立并完整加总；后来追加不影响旧结果；错 request/session/stopReason、重复/部分/快照、延迟全量、provider/billing 不混、记录失败仍原拒绝。新测试先红→聚焦→真实旧材料只读对照→隔离反证→单轮 clean 全模块→打包字节核验。不运行模型或原生客户端。

六维：Declared=用户授权及 Gate 原用量义务；Proven=待本轮；Consumed=reader→handler→R03/L03→operator observer；Enforced before=金额/总量采纳及下一 Gate 派发前；Failure route=原不可用/原恢复协议；Cannot-bypass=双消费端删除式反证 + 合法/拒绝成对。

停止条件：A 无法可靠关联当前调用，需新 acpx patch/连接代平台、额外 authority 或显著成本偏差时停下提交取舍。重冻后仍等待 CC，真实第五例另批。
