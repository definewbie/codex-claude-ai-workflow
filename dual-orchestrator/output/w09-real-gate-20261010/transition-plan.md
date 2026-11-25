# W09 真实 Gate：执行边界

原 definition 定义节点；原 workflow current/node/gate 拥有实例事实；R01/R02/R03 与原 run.json 拥有执行、原生观察、usage；本目录只索引观察，不写许可或替代账本。

每次顺序：核冻结输入/当前 READY/批准谱系及预算→原 run --max-steps 1（INLINE）→原 R01 固定准备/身份→原 R02 调用意图→外部调用/工具→R03 固定结果→原 L03/路由→只读核实际状态/usage→决定下一段。context/decision/compile 是内部段，不自动算成模型调用；correction与修订的真实调用必须计入同池。

原 WAITING_FOR_HUMAN 只按当前门允许动作由用户决定。原生 once ask 保存/提交/ACK/结果分立；仅用户指定 offered choice可回传，不用预算批准代替。

写入中断依 D06固定字节；外部执行不明依 D07，停止新 dispatch、核旧 session/PID/start/退出，不重发、不手改 ledger/artifact。批准漂移、当前门、实际usage缺失、同类无新证据两次、18调用/60min主动/160k可见token、新范围或成本偏差均停止。

已授权首次真实运行；未授权产品 commit、关键门自批、额外 agent、第二样本、代码修复或底层安全实验。若遇缺陷，保存真实证据并回到 owner/控制链分析，不在本 Gate 中临场补丁。
