# D04 批准回复与资格撤销：确定提交序列候选

2026-09-30。承接 wiring audit 的材料层 PASS，解决具名冻结阻塞及两项 LOW。本文是**待独立评审的设计候选**；不授权实施、运行或扩大权限。原 assessment、源码材料和 manifest 不改，本文在提交序列上替代 assessment §5.2–§5.3 的未决表述。

## 1. 选择与用户可观察语义

选择：用已有 D06 workstream 写会话，将**一次批准回复的提交**与**关闭该 execution 的后续回复资格**排成确定顺序。两者由同一 WorkflowFileLedger/ExecutionLedgerWriter 边界写入既有 NodeExecutionDocument。run 仍持有原生请求、人的决定和外部观测；不新增授权服务、锁类型、租约、队列或独立 grant 目录。

这里的线性化点是“批准回复已提交”，不是“用户点击批准”，也不是不可观测的原生执行瞬间：

- **撤销先提交：** 后续批准提交失败，零新的 allow 回复。
- **批准先提交：** 此次具体回复已进入外部执行窗口；后续撤销关闭所有新提交，并请求取消这次在途动作。它可能尚未送出、已经送出或已经执行，不能声称即时撤回。
- 确知请求已取消/过期时，原生 owner 不再回复；结果不明时保留 UNKNOWN，不补发。

这不是用 UNKNOWN 容忍“检查时有效、尚未提交就已被撤销”的漏洞：资格核验和回复提交在**同一把 L 锁内**完成，撤销不能插入二者之间。UNKNOWN 只适用于合法提交之后的外部窗口。提交后取消与发送仍可能竞速，这是明确保留的依赖边界；若产品要求撤销在物理发送前必然抢占所有在途回复，本方案不满足，不能暗示满足，须另提成本取舍。

C 证明的是提交时的业务资格，不证明原生请求仍然存活；原生取消/超时由第 8 步再次拒绝。`closed` 禁止新的许可提交，不阻止取消、拒绝清理、读取结果或保存观测。正常 live 请求的精确 deny 决定仍按相同身份与记录链处理；请求已终止或资格已关闭时走原生取消/关闭出口，不为“送达 deny”复活回调，也不把清理转换成 allow。

UI/CLI 区分“决定已保存”“回复已提交，可能在途”“原生已返回结果”。取消入口区分“已禁止新回复”和“旧执行是否停止已确认”；未确认停止不得显示取消完成或开始替代执行。

## 2. 复用模型与唯一事实来源

已核源码：`NodeExecutionDocument.java:11–33` 已持 node/task/execution 身份及 result；`ExecutionLedgerWriter.java:16–23` 明确只写执行事实，`:115–135` 的完成写目前会用结果覆盖 result；`WorkstreamLock.java:21–37` 为既有文件锁；`StaleRunningRecovery.java:34–49` 先终止/核对在途再恢复。以下字段和提交动作是设计增量，**尚未实现**，不能因 result 是 Map 就声称已有校验。

| 事实 | 唯一 owner / 存放 | 消费者与禁止混用 |
|---|---|---|
| 原生请求原文与选项、人的精确决定 | R：AgentRunMetadataWriter；原子发布不可变 request/decision 材料于既有 run outputs，run timeline 引用 | L 校验 ref/SHA、完整身份、选择范围；投影不从截短正文重建决定 |
| 当前业务资格、活动 claim/epoch | L：现有 ledger/domain | 回复提交与所有资格变更共同消费；run 快照不能替代 |
| 回复已提交、后续回复已关闭 | L：NodeExecutionDocument 的具名执行事实，D06 prepared 写集包含文档及对应 event | transport 消费唯一固定提交；R 仅存其 ref/ID，不再写另一个 authoritative reply intent |
| 是否送出、原生响应、工具/turn 结果 | R：transport/run writer | D02/D07、L03 和统一投影；“提交”及“pipe 写成功”都不是执行成功 |

拟在现有 execution 文档中使用 module-private、严格校验的 `permission_reply` 记录，内容限定为：

- `closed`：初始 absent；首次关闭写入 operation id、ledger sequence、原因和作用的 execution identity。对同 execution 单向关闭，不重新打开。它是是否允许新回复提交的 guard，不是 workflow 新状态。
- `commits[request_key]`：固定 dispatch id、完整 workstream/node/task/execution/run/attempt/session/owner-generation/prompt/RPC 身份、原请求 ref/SHA、用户决定 ref/SHA、精确 optionId/许可范围、批准/合同 refs 与 input epoch、有效 deadline、提交 sequence、允许发送的当前执行者身份。

该事实仅说明既有业务批准与该次人决定在提交点有效，不授予新 scope 或更广原生权限。事件是同次提交的历史，run 中的 commit 引用是关联，二者均不能独立重新授予发送资格。同键同 payload 返回已存在记录；同键异 payload INVALID。完成/失败 writer 必须保留这些事实，不能由 agent result 覆盖或注入同名字段。

资格变化不再靠 R 在发送前重复读取 L 解决。**唯一允许提交的入口在 L**；acpx 只接受 Dual 为这条具体 live 请求送来的固定提交，不接受模型正文、投影或旧 escalation 事件。现有普通执行的 R02 dispatch intent 不变；只将此次新增“权限回复提交”明确归入 L，R 不保留第二份批准资格。

## 3. 明确协议

L 为现有 workstream 锁；R 为 D06 已选定的 run 写锁。两锁不嵌套，不跨人工等待或原生调用持锁。

### A. 收到请求与保存人的决定

1. acpx 在既有 queue owner 内登记当前 RPC 回调及逐请求取消信号，传完整请求身份。此登记不是批准。
2. R 核验所属活动 attempt，原子发布不可变请求材料，随后在 run 中登记 ref/SHA。任何失败不答 allow。释放 R 后由统一投影呈现。
3. 人选择该请求实际提供的 optionId；应用入口检查操作权限。R 在请求仍符合身份时原子发布不可变决定材料并登记引用，释放 R。决定尚未经过 B，因此 UI 不得显示“已回复”。取消/过期/结果不明均不能借此复活请求。

### B. 提交一次回复（与撤销排序的唯一位置）

4. 进入 L 写会话，先按 D06 处理 pending，并核验冻结版本。读取并校验不可变 request/decision 材料，不持 R、不读取 R 活跃文件后拼成伪原子快照。
5. 在同一 L 会话内验证：当前 node/task/execution 唯一 claim、未终态、当前合同/业务批准/epoch、`permission_reply.closed` 未设置、请求/决定完整身份与 SHA、精确选项、deadline 及发送执行者身份。任何业务 scope/模式/持久权限扩大仍走既有用户授权，不由该动作代替。已存在的 commit 只返回历史结果，不获得新的发送资格。
6. 计算包含 `commits[key]` 与确定事件 bytes 的 D06 mutation；完整提交后才返回本次 live 调用的 `newly_committed` 结果。**此完成点记为 C**。从第 4 步到 C 始终持 L，随后释放。部分写只形成 pending，不允许发送；重放只补本地，不返回新的发送许可。

### C. 发给仍存活的原生请求

7. 只有获得第 6 步本次 `newly_committed` 返回、且仍为相同已 claim 的执行者，才可将固定 commit 身份与 exact optionId 交给既有 acpx queue owner。不得凭扫描磁盘发现一个 commit 就发送，也不在重启时补发。该 live 调用只发起一次 IPC 交付；回执丢失不自动重试，既有 IPC/client 的隐式重试必须在该命令上关闭。没有可恢复发件箱或后台补送路径。该发送资格只属于此次调用，不是可转移的 bearer token。
8. acpx 在自身同一请求分派中校验 owner generation、prompt/RPC 身份、原文/选项匹配、deadline、取消信号及该 key 尚未消费；先将回调标为已消费，再尝试向 ACP 返回原始 optionId。检查及标记之间不 await；请求取消也进入同一 owner 的请求状态消费。相同 key 重复不再次调用回调；新 owner 不恢复旧回调或继承发送资格。仅保证至多一次尝试，不宣称 exactly-once 执行。
9. 发送观测与随后工具/turn 结果由 R 独立保存。写失败或连接状态不明即 D07 UNKNOWN；不重答、不重发 prompt。如果 C 后获知已取消，发送者可停止此次尚未发出的尝试；这不是跨进程即时撤销保证。允许的唯一后续执行，是 C 已绑定的同一 live 请求，不能转成新 request、扩权或新 attempt。

### D. 撤销、改 epoch、结束与恢复

10. 所有会使原 execution 不再取得回复资格的入口，都在 L 短会话内先/同时将 `permission_reply.closed` 写入相应 D06 mutation。**提交完成点记为 X**。同次可提交的业务资格变更一并写；已有 C 记录保留不删。原生停止不在 mutation 重放中执行。
11. X 后所有新 B 必拒。释放 L 后，既有 transport/reconciler 请求停止旧调用、取消待决 RPC 并核对结果；不持 L 等待。已提交但未确认的 C 作为在途事实呈现，未证明停止不能显示取消成功、终结为无外部影响或让新 attempt 与旧执行重叠。
12. 取得可信停止/完成事实后，R 保存，释放 R，再以 L 短会话完成已有 L03/L06 的后续提交。改 epoch 后旧结果可保留历史，但不得为新 scope 完成业务成功。UNKNOWN 沿已冻结的结构化人工裁定处理，不因存在 C 就自动 retry。

若 X 的本地提交失败，不能报告资格已撤销；可尝试取消原生以止损，但必须保留“本地关闭未确认”的状态，后续写入口先处理 pending。若原生取消失败，X 仍有效阻止新提交，只是旧在途效果未决。

## 4. 交错分析与失败前缀

| 顺序/失败位置 | 可观察结果与允许行为 |
|---|---|
| X → B | B 在锁内发现 closed/失效身份，零 C、零新 allow。合法请求不会被套用旧批准 |
| B持L核验 → 另一个撤销入口尝试获取L → C → X | 撤销不能插进核验与 C；批准先提交，显示在途；随后关闭新提交并取消。不能称撤销先发生 |
| C → X → 第7步尚未送出 | 该动作已经提交在途；可取消并尽量停止发送，但 X 不承诺追回 C。若产品不接受这一语义，必须拒绝本候选而非改测试期望掩盖 |
| 决定R已保存 → X → B | 保存人的意愿不授发送资格，B拒绝；可显示决定因资格变化未提交 |
| B manifest尚未耐久即失败 | 无 C，不回复；若请求仍live且身份有效，可重新申请 B，不伪造已批准执行 |
| B manifest耐久/部分文件/事件/删除pending之间崩溃 | 先D06补本地；没有原 live 调用的成功返回，不发送。记录已提交或待完成的事实与发送未证，D07处置；重放零外部调用 |
| C之后、交付IPC之前进程崩溃 | 有合法提交、缺发送证明，保守UNKNOWN；新进程不能恢复旧发送资格。重启完成本地记录不会触发调用 |
| IPC交付或ACP回复中断 / R观测写失败 | 至多一次尝试；丢回执不重发。不能从接收者缺日志推导没执行 |
| 回调已消费但ACP回复失败 | 可能零执行或未知，保留失败；不得重置已消费标记使旧请求可再次答复 |
| X已提交、原生尚未停止 / 停止结果写失败 | 新B均拒；旧C仍未决。持续显示停止未确认；普通retry及新claim不得绕过 |
| 重复人决定、重复B、重复队列回复 | 同内容读取原结果，不产生新发送；异内容拒绝；重启后原生RPC id重用也因generation/attempt不符拒绝 |
| 旧binary/缺新字段/损坏不可变材料 | INVALID，只读诊断；不从历史默认值补出许可，不迁移旧样本 |

上述为设计推演，不是已运行测试。它解决的是本地 C/X 排序，不声称把 ledger 与原生副作用变成原子事务。已提交在途窗口与旧方案“无提交点的检查后发送”必须在测试和展示中分开。

## 5. 写集、兄弟入口与两项 LOW

**D06 受影响增量：** L02 所属的 execution writer 增加 B 的具名执行事实 mutation；L03/L06 及会使该 execution 的 claim/epoch/批准失效的既有 domain mutation，合并写 closed。不新增第二 lock，不改变不嵌套原则，不把 R 的可变日志纳入 L manifest。run outputs 的不可变 request/decision 和已有 completion 一样以 ref/SHA 桥接。原冻结附录保持历史，本节须与 D06/D07 一并接受受影响复审。

| 必查兄弟路径 | 必须消费的同一规则 |
|---|---|
| inline、worker、correction、fresh/reused session、原生子请求 | 同一 B 和请求身份绑定；子请求还须当前委派谱系，无旁路 `approve-all`；已有自动许可仍受固定运行配置治理，不伪造人决定 |
| 用户 abort、取消、scope/批准/epoch 变更、stale/retry、node完成及替换claim | 全部先/同时在 L 关闭旧 execution 新回复提交；新attempt不能与停止未确认的旧执行重叠；完成writer不得覆盖 C/X 历史 |
| CLI/API/UI/report | 统一 read model 显示决定、C、发送观测、结果、X与停止确认；不得把“批准已保存”当“动作成功” |
| 原生 request_permission 与开放的宿主fs/terminal confirm | 需要人的正向许可都走同一 C；unsupported明确拒绝；非交互失败不是待决请求 |

**LOW-1：** permission reply 是命令；run 保存人的决定和外部观测；L 仅保存资格核验后的具体回复提交事实。旧 permission_escalation 是非交互已拒绝/取消后的诊断，不能生成另一套待决批准。同一请求选择一条消费路径，旧 WebSearch 专用重试不同时激活；历史可读不转新许可。

**LOW-2：** 现存 Codex optionId 投影错误与接线共用原始选项映射修复。optionId→kind/实际范围仅来自该请求；没有映射就未知，不用字符串条件猜测。先完成设计冻结与实施授权，再实施该缺陷修复，不自动单独开工。

## 6. 验证设计与成本止损

已有 wiring audit §7 的合法自动操作、批准续作、拒绝、选项精确性、多请求、宿主入口、恢复和投影序列全部保留，新增定点要求：

0. 合法 deny 可送达并终止相应操作；X 后取消/拒绝清理仍可完成，且不产生 C 或 allow。C 时原生已取消但本地尚未知的对照，必须在原生请求 owner 拒绝，不伪造已执行。
1. 用 barrier 固定 `核验后、C前`，并发 X 必须被同一 L 锁挡住；反向让 X 先完成则 C 必拒。不能只验证读两次相等。
2. C 先/X 后的用例允许原生消费既有 C，但必须关闭所有新 C、呈现在途并核对停止；不得把此用例写成“撤销必然追回批准”。在原生请求已取消时，迟到回复必拒。
3. 固定 R决定保存后/X先/B后的序列，证明保存决定不会越过关闭。多请求 A已有C、B未有C 时 X 只留下 A在途可能性，B无新allow。
4. 对B/X的每个D06写入前缀做冷启动反证；任何恢复路径不得产生 `newly_committed` 发送资格。人为移除这个区别后，测试必须发现重复/重启发送。
5. IPC重复、错误owner generation、相同RPC id的新session、callback消费后异常、原生取消及timeout竞态均有明确拒绝；删除owner/请求身份校验必须失败。
6. R并发进度/决定/完成与L完成/关闭后历史保留；任一writer覆盖C/X，测试必失败。公开资格变更入口逐个试验，不能只测一个abort方法。
7. 合法稳定资格下，一次决定→一次C→一次原请求回复→合法产物→独立复评/验收/交付正常可达；无谓拒绝或强制重跑prompt均失败。

这些是待实施验证义务，本轮不写产品测试或运行行为实验。已有固定源码及字段容器只证明方案落点，不证明机制已 ENFORCED/PROVEN。

代价：一个既有 execution 文档内的提交/关闭事实、对应D06 mutation与资格变更接线、原有callback/queue增量；完成writer须保留历史。这是对原“全部回复状态归R”的**显式职责细化**：R拥有外部事实，L拥有与业务资格排序的回复提交；没有新增批准者或通用授权平台。若评审认为这仍构成新authority、需要更多存储/监督设施，或用户不接受C后在途语义，停止由用户取舍，不能继续实现。

## 7. 六维与评审出口

- Declared by：§8.25的合法续作/拒绝/未知恢复，本节显式确定C/X与在途语义；未声称已获用户或CC冻结认可。
- Proven by：现有文档/owner源码及交错推演；原生和产品行为NOT RUN。
- Consumed by：L写会话唯一核验并提交C/X；当前live sender与acpx回调消费同一具体C；R记录外部事实；恢复/投影不生产新许可。
- Enforced before：候选要求C之前同锁核业务资格；原生回复前核请求取消/身份/期限。不是逐底层写入安全承诺，目前尚无产品执行点。
- Failure route：本地D06补齐零外部动作；C后不明走D07；失败保真走D02；X关闭新提交且不伪造原生已停止。
- Cannot-bypass test：§6正反序列、写入前缀、公开失效入口、冷启动与重复消费；尚未运行，不称闭环。

作者自查：协议候选现已明确排序点、owner、写集、发送资格及恢复解释，具备提交受影响设计复审的材料。**D04仍DESIGN_GAP，待评审确认，不自动冻结。** 未核验包括实现成本实测、跨进程运行及固定原生接口行为；此前材料评审未核验部分不因本节而转成已证。

### CC 提示词

请对 `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator` 的 D04 批准回复提交协议做独立只读评审，遵守 AGENTS.md。

入口：`output/d04-permission-wiring-audit-20260930/reply-commit-protocol.md`；前轮接口材料在同目录，详设 §8.27、主清单 §9.19 同步。此次包含 D06/D07 的受影响职责与写序。

请自行检验批准提交、资格撤销、原生回复和恢复的完整链及失败前缀。重点判断 C/X 排序是否真正消除原竞态，C 后在途语义是否符合产品目标、是否偷换原保证，owner/写集/重复消费是否完整，成本是否仍在既有边界内。按 Declared by / Proven by / Consumed by / Enforced before / Failure route / Cannot-bypass test 给分级发现、证据和未核验范围，独立判断能否设计冻结。

不修改文件、不运行 native/provider/注入探针、不启动额外 agent，不恢复沙箱证明。PASS 不授权实施。
