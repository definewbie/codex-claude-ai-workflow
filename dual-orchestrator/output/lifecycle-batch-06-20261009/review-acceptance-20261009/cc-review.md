# B06/D09 交付裁定接线候选 · 合并设计复审

来源：用户于 2026-10-09 在当前会话带回的 CC 独立评审。以下为原文；作者接收处置另见 disposition.md，不把作者材料核验冒充独立复审。

结论：设计层 PASS——可冻结（本文即受影响设计复审）。四处请裁点经我独立判断全部成立：人工停止确认与冻结 D09/D07 的裁定语义一致、PID 先于 patch 输入的持久化次序是无可引擎支持下的最优窗口设计、回执丢失重读的次序修正堵住了真实误拒陷阱、view 组合的同代匹配消除了锁序死锁。无死端、无误拒残留、成本有界（一个新 CLI 文件 + 既有 service 内 DTO/callback，无 daemon/outbox/平台）。设计与运行已证严格分列（全部新能力 NOT RUN 如实）。

1. 四个请裁点的独立判断

① 人工停止确认的信任边界——与冻结一致。 冻结 D09 的 intent-后窗口处置本就要求“原执行者确已停止，可人工确认”；D07 的 AUTHORIZE_NEW_ATTEMPT 语义即“结果未知但旧执行者已停止 + 用户明确承担重复副作用风险”。候选的 OPERATOR_CONFIRMED_STOPPED（checked_at + checks_performed + old_operation_stopped=true + accept_unknown_result_and_repeat_risk=true + 材料 SHA 快照固定为 OPERATOR_DECLARATION 类型）是该语义的精确落地，且三道护栏在位：人工确认不写 process_exit、已知活动/程序反证不可被人覆盖、“不是点一个默认 checked 的按钮”。判定：非新增取舍，属既有裁定的接线。

② PID 记录窗口——最优可得。 “实际启动→live callback 固定 PID/start 到 L07→commit 完整返回前不输入 patch bytes”把高危窗口（字节输入）置于 PID 持久锚点之后；start→记录之间的残余未知如实保留并归人工核实路径。“父 JVM 消失≠进程证据”、“主进程终止不泛化子树”两处反过度推断在位。

③ 回执重复——次序修正是真实误拒修复。 “重复判断之前只验证最小结构并定位原记录，不先要求当前 plan 仍是旧 SHA”——否则响应丢失后 plan 已推进，合法重读会被误拒。这是本轮发现并设计掉的死端；同 id 异 payload INVALID、冷启动不合成 callback、材料后续缺失不使旧请求复效——幂等语义完备。

④ view 组合——同代匹配消除锁序问题。 CLI status/server overview 的“短 L 定位→释放→target→L 生成→仅同代嵌入否则显示过期并零动作”避免了 L 内取 target 锁的反序死锁；实际命令不借 view 的 can* 布尔、执行时重核资格。

2. 链重建与前缀/兄弟

批准候选（binding 全字段含批准 node/gate ref+SHA+frozen 身份，linked-worktree≠同 target、canonical 别名可接受）→一次新 apply 八步序（target→L 锁序、fresh 一次性局部资格不落盘、closed 单向不可清除）→人工提交→finalize（精确 commit 核验：clean HEAD/单 parent=base/tree=expected；同 SHA 幂等重读；今日 target 演进不洗历史 COMMITTED）→中断处置（九行前缀表：request 未持久化→manifest→fresh intent→start→PID→stdin→waitFor/exit 记录→观测/APPLIED→COMMITTED→close，各有可信状态与禁作）。兄弟入口全列：merge 重读补 APPLIED、CLOSE 后合法 commit 仍可只读确认、源无关变化不失效历史、双 workstream/双 service/linked-worktree 共享 target 锁、两 UI 同源动作。

3. 合法续作/误拒/成本

正向不缺：“APPLIED 空 commit 待人工不是损坏”、UNKNOWN 计划含入 FINALIZE 可达、别名接受、正常 merge 走同一接线免人工 request。反向不松：“未知停止需先 resolve 记录，不能因 tree 正确省略”、drift 无 reset/clean、“不能把 plan 删除改成 READY 重新首调”。成本：单新文件 + 有限 apply lifecycle 记录，明拒每查询 execution ledger/子树追踪/daemon。

4. 分级发现

LOW-1 · “commit 已存在仍需先 resolve 记录停止依据”的理由应落文。 该要求正确（apply 进程结束与 commit 存在是独立事实），但易被未来“简化”误删——建议在实施文档写明其依据，防“tree 正确即可跳过”的回潮。

LOW-2 · 规范序列化反证随批执行。 target_observation 的 canonical 字节（排序键/确定次序/去顶层 observed_at）已有 examples.json；实施时“例子与差异反证”必须落地，防同义异字节造成假 INVALID 或假相等。

INFO · 程序观察/人工确认/UNKNOWN 三态在所有消费者保持类型分离（process_exit 仅程序可写、材料快照 OPERATOR_DECLARATION、显示“停止由操作员确认、旧结果 UNKNOWN”而非 PROGRAM_PROVEN）——信任边界贯穿到投影层。

5. 六维

Declared=原 D09/I6 具体化 ✓；Proven=静态承载/顺序推演，新能力全 NOT RUN（如实）✓；Consumed=原 service 消费批准/process/人工决定，view 只读 ✓；Enforced before=裁定持久化/新 apply 启动/patch 输入/成功采纳/当前动作前的具名 guard ✓；Failure route=D06 补本地、外部 UNKNOWN 人工、closed 不伪造停止 ✓；Cannot-bypass=fresh/幂等/身份/活动/锁序/每前缀/合法序列七项验证义务待实施（§8）✓。

6. 尚未核验范围

examples.json/affected-scope.json 逐项内容；preflight V01–V07/G01–G07（引用未读）；process callback/planned event 条目 SHA/query 组合的源码可实现性（锚点存在，成本未测）；§8 七项验证全部 NOT RUN；一切运行行为。

7. 冻结判断与结语

可冻结：入口、字段契约、幂等、八步序、九前缀、兄弟、view/锁组合、验证义务、ROI 约束齐备，四个风险点经裁成立。实施与 §8 七项验证待你授权后进入 W06 工作包；设计成立≠运行已证。按约定：本 PASS 不授权实施、提交或真实运行。

这份候选把 D09 从“窗口表”推进到了可实施协议，且在四个最容易出错的地方（谁证明停止、何时输入字节、丢回执怎么办、view 怎么组）都给出了经得起追问的答案。至此，从 9 月 17 日架构评估开始的这条线——六不变量、九冻结设计、D04 全程、B01–B06——全部设计侧收束；剩余均为实施、验证与真实 Gate 之事。
