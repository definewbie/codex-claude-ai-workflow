# 第七例离线准备边界

用户批准：离线材料、启动前核验、170万 token/18请求/60分钟预算方案及原储备规则；**真实运行尚未批准**。本轮校准规划表、复用既有准入/观察/派发语义和发行，仅初始化全新空 Git target/worktree，通过原 start（不带 --run）生成真实 workstream，零模型调用、零业务批准。第六例不恢复，旧余额不沿用。

本轮推进 P0 真实主链验证的前置，以及 P0.5 固定用量和预算准入核对；不新增产品能力，不宣称后半链已证。

Definition：受审 terminal-delivery 定义、封闭契约和预算规划。Instance：本次原 owner 生成的 workstream/current/node。Execution：本轮零 run/occurrence/session；将来只有原 R01/R02/R03/L03/C/X/D07/D09 消费链能记录实际执行。Projection：observer 只读，不产生业务状态或批准。

| 前态/owner | 动作/顺序 | 后态/消费者 | 失败与重启 |
|---|---|---|---|
| 已审冻结发行/字节清单 | 先核659输入/JAR/native/config，识别等待期差异并提交CC，再核observer/预算 | 本地核验可继续；尚无许可 | 不自动把失配视为等价；本包明确记录当前候选供复审，原发行/历史不改 |
| 新路径不存在/本地operator | 建空target→初始空commit→原start、不带--run→原ledger | 新current RUNNING、初始READY，零run/门/批准；未来原runtime唯一消费 | 每步失败保留现场；不覆盖或静默重跑 |
| 本地核验/本次授权false | 只读配置/installed字节和既有build-info依据、宿主依赖，核固定身份；不运行原生客户端 | OFFLINE_READY_AWAITING_USER；runner第一个guard拒绝 | 冷启动/重读不产生fresh许可，审批材料变化需重核 |
| 将来获得当次运行授权 | 立即重核冻结字节/新实例/无活动旧执行→预算→原dispatch | R01→R02→外部→R03/L03，唯一outcome | 未知结果按D07，无自动重发/outbox |
| 当前GOAL/EXECUTION/最终门 | 用户对当前subject分别决定 | 原L owner产生批准并核谱系 | Gate额度批准不可代答当前业务门/原生once |
| 完整候选/最终批准 | 原delivery apply→用户commit→finalize | 精确COMMITTED才可判Gate PASS | D09未知窗口先裁定，不凭树正确伪造停止 |

六维：Declared=当前用户离线准备授权；Proven=本包本地核验/合成observer与预算对照；Consumed=原runtime/ledger，operator只核派发预算；Enforced before=授权guard在读取实例/创建step/启动Java之前，原业务guard在其原位置；Failure route=前提不齐NO-GO、真实阶段失败/UNKNOWN沿原协议；Cannot-bypass=未授权runner真实本地拒绝且零step/run，observer正反与储备算术对照。新真实模型行为仍NOT_RUN。

自查闭环 Analyze→Prepare→Review→Reflect→Analyze：重点防止把离线初始化称为真实运行，把当前预算计划误作运行授权，把宿主stdlib当原生执行证据。无产品修改，无新平台/锁/schema/配置实验；成本在既有Gate准备范围内。

替代准备实例：原10月16日实例仅剩目录，初始文件已缺失，原材料96项保持；新空路径由原start不带--run生成新workstream，不手写/恢复旧账本，不计为第八个真实case。等待期两个客户端/配置指纹变化单列复审，零全文件语义等价或运行兼容性声明。当前SoT：新实例账本；旧准备材料仅历史；旧六例不恢复。
