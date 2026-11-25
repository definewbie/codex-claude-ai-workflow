# B06 / D09：交付裁定与受影响接线候选

2026-10-09；**DESIGN_CANDIDATE / NOT IMPLEMENTED / AWAITING_CC**。落实既有 D09 的“先核实旧操作、明确人的决定、再允许新操作”，不改变最终批准、业务 scope、独立验收、终态或人工 commit 权限。本材料未获独立 PASS，不回写为已冻结或已证明。

## 1. 用户流程与首版取舍

正常交付保持原路径：批准 candidate→`delivery merge`→人工检查暂存改动并实际 commit→`delivery finalize`。重发 merge 只重新核对旧操作；finalize 只核对并登记已有 commit，均不自动创建 commit。

异常交付只增加一个人工入口 `delivery resolve`，两项选择：

- **AUTHORIZE_NEW_APPLY**：核实旧操作已经停止，目标仍精确是批准的 base，明确授权一次新应用。
- **CLOSE_APPLICATION**：不再让 Dual 应用这个 candidate。保留现场、旧结果与历史；不声称原进程已停止，不回滚文件。若之后已有精确合法 commit，仍可只读核验并 finalize，不产生新的 Git 写操作。

已经应用到精确暂存状态，或已经有合法 commit 时，不提供新 apply。分别通过原 merge 补 APPLIED、原 finalize 确认 commit。目标部分变化、额外文件、错误 HEAD/commit 先由人核对现场；本协议不提供 reset/clean、放宽目标、跳过批准或新候选借权。

程序观察与人工确认分开：通常使用程序记录的本次 Git 调用退出事实；记录不全时，允许操作员明确确认自己已核实旧操作停止，并留下依据。**这是 D09 的人工裁定候选接线，不是机器执行证明**；旧结果仍为 UNKNOWN，不回填 exit=0 或“从未执行”。已知原调用/绑定 Git 仍活动时，人的声明不能覆盖程序反证。CC 必须判断此人工确认是否符合原冻结 D09 的信任边界；若认为需要用户新增取舍，应指出而不是以“人工已确认”掩盖差异。

范围只管理 Dual 发起的标准 `git apply --index` 和它的程序事实；Git/文件系统的正常实现是依赖边界。任意用户进程/自定义 Git 外部程序的对抗控制、全进程树证明不属于本批。发现普通受支持 Git 行为也无法满足此边界时止损，不追加 OS 沙箱或监督平台。

## 2. 现有 owner 与承载

| 事实 / 动作 | producer / 唯一强制 consumer | 既有承载与拒绝时点 |
|---|---|---|
| 最终批准 candidate | 原最后 acceptance occurrence、candidate writer / AcceptedDeliveryCandidateResolver→DeliveryApplicationService | exact ref/SHA、producer/iteration、gate/node/event；任何交付写入和 Git 调用前核对 |
| 实际目标身份与观察 | GitPatchMergeService / DeliveryApplicationService | canonical target root+common-dir，HEAD/headTree/indexTree/worktree一致/untracked/status；副作用启动与当前动作生成前 |
| Git 启动/结束 | 本次 adapter 的实际 Process / 原 service | 原 DeliveryMergePlan.operation 的 process 字段及同一 L07 事件；不由模型、CLI 输入或任意 JSON 生产程序事实 |
| 人的裁定 | 原 CLI/API 人工入口 / 原 service.resolve | request 原文、SHA、停止依据类型、旧 operation 绑定进原 delivery event；记录完成不等于已再次应用 |
| 原操作关闭/新 operation intent | 原 service 的同一次 L07 | plan+event+current；旧操作快照留原事件；只有本次完整 commit 返回的 live resolve 调用可启动一次新 apply |
| 当前资格与历史完成 | 原 service.View / CLI/API/console/mgt/report | currentActions、targetObservation 与 historicalStatus 分开；投影不可制造决定或发送资格 |

使用原 common-dir `WorkstreamLock` 和原 `LedgerSession`。锁顺序始终 **target→L**；不加新锁类型、长期 daemon、outbox、自动恢复发送或第二交付账本。根 plan 保持 schemaVersion=3，operation 增加具名 schema `dual.delivery-application.v1` 并严格解析必需字段；缺/错结构 INVALID。B06 新 frozenRuntimeIdentity 不兼容历史 binary/ledger，旧样本不升级、旧 plan 不补造 process/裁定事实。

Map 是既有文件承载，不是允许任意 map 放行。字段构造/解析/验证集中在 delivery owner 的受限 DTO/方法；不新增 core model 层、DSL 或通用追踪服务。

## 3. 字段契约、生产入口与幂等

### 3.1 operation 增量

原 `id/apply_intent/before/executor_pid/executor_started_at/approval_node/approval_sha256` 保留。候选必需增量：

| 字段 | 含义 / producer |
|---|---|
| schema | `dual.delivery-application.v1`；本 service 构造 |
| binding | workstream/source realpath、target realpath/common-dir、candidate ref/SHA、批准 node+gate ref/SHA、baseCommit/baseTree/expectedTree、frozenRuntimeIdentity；与权威源逐项核验 |
| process | 未启动时 null；已启动后 `{pid, started_at, command_kind:"GIT_APPLY_INDEX"}` 仅由实际 Process 生成，实际 bytes 输入之前必须 durable 固定 |
| process_exit | `{pid, started_at, termination_observed, observation_source, actual_exit_code, observed_at}`；程序核实准确已登记主进程结束才可写 termination_observed；actual_exit_code 只能来自实际 `waitFor`，无法实测时为 null。人工确认不写本字段 |
| output | 原 stdout/stderr 事实及独立读取失败，不因输出读取失败抹掉已经实测的 exit，也不凭 exit 推导应用状态 |
| observation | 本 service 采集的目标实际状态，含观察时点和确定字段 SHA；不是 caller 的自报 snapshot |
| application_closed | 单向关闭当前 candidate 的 Dual apply 资格；不表示文件回滚、进程停止或 workflow abort |
| predecessor | 重试的新 operation 指向原 delivery 归档 event 的 ref/event_id/entry_sha256；不递归拷贝全历史 |
| authorizing_request | 新 operation 绑定本次请求 id/hash 与记录事件；首个正常 merge 为 null，不伪造人的裁定 |

process 可以证明的是本次实际 Git 主进程的身份/退出，不能将父 JVM PID 消失写成同等证据。参数固定为本产品的 apply 形式；不把来自 custom command 的 arbitrary payload 视为支持入口。

`binding` 无法由 source/target/candidate/批准重建匹配时零新调用：包含 plan.workstreamId/sourceWorktree/targetRepo/baseCommit/baseTree/expectedResultTree、operation 内对应值、candidate accepted/excluded 文件集、candidate patch ref/SHA 和实际 common-dir。相同 common-dir 的不同 linked worktree 不是相同 target；指向相同 canonical target 的路径别名可接受。

### 3.2 人工 request

生产入口：CLI **`dualctl delivery resolve --workdir <source> --decision <JSON文件> <workstream>`**；API **`POST /api/workstreams/{id}/delivery/resolve?workdir=...`**。两 UI 提交同一 request；使用既有人工入口权限，不新建身份/权限系统。`GET delivery` 返回该 operation 的最新模板和依据，但不保存决定、不携发送资格。

request schema `dual.delivery-resolution.v1` 必需字段：

- request_id：沿现有人工 request 的受限标识形式，workstream 内唯一；由操作员客户端生成。
- workstream_id、frozen_runtime_identity（与现有 ledger 相同的完整 Map<String,String>，不另造版本字符串）、operation_id、plan_sha256、ledger_commit_sequence。
- candidate `{ref,sha256}`、approval `{node,ref,sha256}`、target `{repo,common_dir}`。
- target_observation_sha256：最新 view 中确定字段的 SHA；提交时 service 重新采集并逐项比较。观察时点单列，不把时钟前进当源码/目标漂移；仅顶层 observed_at 不参与身份。身份对象固定 repo/common_dir/head/head_tree/index_tree/worktree_matches_index/clean/status_entries/untracked_entries；路径与状态按确定顺序，保留全部语义字段，不递归删同名字段。当前新结构及规范序列化必须有例子与差异反证。规范字节为排序对象键、保留数组确定次序、UTF-8、无额外空白的 JSON；观察对象仅去顶层 observed_at。请求 SHA 则覆盖完整请求，包括 checked_at 和材料引用。合成例子及规范字节见 [examples.json](examples.json)，它们不是可执行决定或运行证据。
- disposition：仅上述两值；operator_note 非空。
- stop_basis：AUTHORIZE_NEW_APPLY 必需，取 PROGRAM_OBSERVED_TERMINATION 或 OPERATOR_CONFIRMED_STOPPED。CLOSE_APPLICATION 可为 null，不能据此显示“已停止”。

PROGRAM_OBSERVED_TERMINATION 只引用 service 已生产且绑定本 operation 的 process/exit event；请求文件不得自报 actual_exit_code。程序查询必须匹配已登记 Git 的 PID 与启动时间；仅当绑定完整且查询实际确认原主进程已不在时，可记录 PROGRAM_PROCESS_ABSENT，实际退出码仍为 null。权限不足、查询失败、身份不全或仅父 JVM 消失均不能如此记录，转 UNKNOWN/人工。主进程终止不泛化为任意子树已停止。

OPERATOR_CONFIRMED_STOPPED 包含 checked_at、非空 checks_performed、明确 `old_operation_stopped=true`、`accept_unknown_result_and_repeat_risk=true`、依据 material_refs。其生产者是可信人工入口中的操作者，不是模型 envelope；material refs 验 SHA 和归属后固定为 `artifacts/delivery-stop-<SHA>.json` 的人工材料快照，仍保留类型 OPERATOR_DECLARATION。文件存在/SHA 匹配只证明材料固定，不证明内容为真。操作员必须实际核实旧操作停止；不是点一个默认 checked 的按钮。

程序同时核实 target 锁、已登记原 Git 身份以及原 sender：已知活动/并发 owner 不得被人工确认覆盖；取得 target 锁只证明没有另一个持锁 Dual live 调用，不作为全停证明。身份缺失等无法程序核实时明确展示 **停止由操作员确认、旧结果 UNKNOWN**，而非 PROGRAM_PROVEN。调用自身仍不得发送给已知未停或目标已变的 operation。

材料读取仅数据，不执行其中命令或指令；不得允许材料覆盖 candidate、gate、plan 等 control。优先沿已有 L07 `artifacts/*.json` 注册写路径固定人工摘要与材料快照，不另建上传/环境认证平台。本入口必须显式设定有界请求/材料读取上限并拒绝超限，不能截掉关键依据后批准；是否存在可直接复用的大小限制尚未逐入口核实，不宣称已有。首版请求 UTF-8 JSON 上限 64 KiB，所引用材料总计上限 1 MiB，仅固定读取到的字节及 SHA；这是局部输入限制，不新增上传平台。首版材料只引用当前 workstream artifacts 内已存在的 UTF-8 JSON/md 数据，规范化路径不得越出该目录，不接收外部任意文件路径；没有文件材料时 material_refs 可为空，人的 checks_performed 和风险确认仍必需。人工检查结果可直接写在声明内，不能因材料文件为空冒称机器已证。内容地址快照已有同名文件时必须逐字节相等，异内容 INVALID，不能依赖通用 artifacts 写器自动提供不可变保证。

### 3.3 重复请求和事件身份

裁定事件 id 为 workstream 内 `delivery-resolution-<request_id>`，payload 固定完整 request、canonical 请求 SHA、stop_basis 类型、旧 operation 快照和本次决定结果。序列化一次，用原 session 的 event append 与计划合并；前驱引用取该计划追加记录的原始条目字节 SHA，不用 events.jsonl 整文件 SHA。原有 session 能读取 planned append；若实际无法有界读取准确条目，停止，不另造事件索引平台。

同 id 同 canonical payload：重读已记录决定及当前 view，零新 operation/零 Git。**在重复判断之前只验证最小 request 结构/归属并定位原记录，不先要求当前 plan 仍是旧 SHA**，否则正常响应丢失后会被误拒。同 id 异 payload：INVALID，不能再发送。全无记录时才核所有当前绑定/sequence/目标 SHA。比较覆盖整个请求及 material refs，不丢 stop_basis、note 或风险确认字段。

合法原记录由 frozen ledger/event 写集生成，未知版本/不完整事件拒绝；body 中“重复”不能制造幂等。重复请求比较的是原始请求及其 material ref/SHA，不重新拷贝材料或生成新的请求 SHA；原事件已固定的材料作为历史依据，材料后来缺失/变化不能让同一请求再次生效，也不证明当前仍满足新动作。新一轮裁定须新 id 与最新模板。查询只读、不自动生成或消费决定。

## 4. 一次新 apply 的完整顺序

AUTHORIZE_NEW_APPLY 的 result 义务是一次有界新调用，不是可在重启后执行的票据：

1. 用短 L 只读会话取得记录 target，释放 L；canonical target 锁先取得。再取 L，重核 workstream/target 和原批准。target 变更不把旧路径悄悄替换为新 target。
2. 在同 target 锁下查重复 request；无原记录时核当前 operation/plan SHA/sequence/候选/批准/frozen身份，service 采集目标并比较 request snapshot，核停止依据与活动反证。application_closed=true 的 candidate 拒绝任何新的 AUTHORIZE_NEW_APPLY；同 id 历史回执只可重读，不清除此标记。
3. 新 apply 只接受精确未应用基准：HEAD=baseCommit、HEAD tree=baseTree、indexTree=baseTree、worktree与index一致、无未跟踪业务变化、Git clean。已应用或已提交走原恢复/确认，不允许再 apply；任何其他形态保留现场，不在 resolve 中清理。
4. 同一 L07 写集固定人工材料、完整裁定事件/旧 operation 快照、旧操作被新操作取代的关联、新 id intent/authorizing_request/predecessor、plan、current。保留历史 UNKNOWN，不回填“未执行”。**本次完整 commit 返回**才给当前 resolve 栈局部的一次启动资格；该资格不写成磁盘布尔值可重读。
5. 释放 L，仍持 target 锁。adapter 在实际 `ProcessBuilder.start` 前再次核 canonical target/HEAD/tree/clean/index 基准；不符合零启动。未发生的启动不能伪造失败进程退出。
6. 实际启动标准 Git 后，由本次 live callback 固定准确 process PID/start 到同 operation 的 L07；回调不得借旧 id/current plan，必须重新核同 operation/原批准、尚未关闭。**PID 记录本次 commit 完整返回之前，不输入 patch bytes。**写失败即关闭 stdin、只清理本次直接 Process 并等待/观察结果；清理无法确认则 UNKNOWN，不新增全局 kill 或新 apply。
7. 输入一次 patch；等待实际 Git 退出。实际 waitFor 的 exit 先作为程序事实固定，再处理输出与 target 观测；输出采集异常分列。stdin 失败/中断不得盲目启动第二个 Git。仅身份完整的本次 live sender可写实际执行事实；冷启动不合成 callback。
8. 同一 operation 的程序结束事实与实际 target 若满足应用后态，L07 固定 APPLIED/观测；否则 UNKNOWN/差异。每个结果记录失败沿 D06 固定字节补齐，零重新调用。target 锁最后释放。

步骤 6 增量是标准 apply 的有限 lifecycle callback；Git adapter 不依赖 Java ledger，runtime callback 写原 L07。只覆盖 apply，不仪表化 capture/查询全部 Git 命令。start→process记录之前崩溃仍是明确未知窗口；没有 program receipt 就不声称已停止，按人工核实路径处理。不承诺追回已启动的任意外部程序。

普通首个 `merge` 采用相同 intent/process/观测接线，省略人工 resolve request。**同 id 幂等、D06 补齐、冷 JVM重读、query、普通 merge 重读旧 intent均不能拿到步骤 4 的局部新资格。**目标锁串行化 live apply/resolve/close/finalize，故其他 Dual 入口不能插入本次实际调用；不承诺阻止用户绕锁操作 Git。

失败于任一步，不能把 plan 删除或改成 READY 来重新“首次调用”。无 outbox、IPC 重试、后台恢复发送、自动新 operation。

## 5. 关闭、补观测与 finalize 的兄弟路径

| 入口 | 强制条件 / 本地写序 | 合法 post-state / 外部约束 |
|---|---|---|
| resolve CLOSE_APPLICATION | target→L，幂等/同 operation binding、当前 plan/sequence 与请求一致；同 L07 记录决定+application_closed+event/current。不要求虚假的 stopped；已知 live 持 target 锁时返回 BUSY | 只关闭新的 apply 权限；旧 UNKNOWN/APPLIED/COMMITTED和现场保留，零 Git apply/commit/kill。此标记不可反向清除；该 candidate 不再新应用 |
| merge 重读旧 intent | target→L 核原 binding；新鲜目标精确 applied + 已结束程序事实，或明确人工停止依据，可补同 operation APPLIED；外部不明则只展示核对/resolve | 零二次apply。尚未确认停止不能提供 commit 动作。close标记不妨碍记录已发生的合法结果，但仍不产生新apply |
| finalize 精确 commit | target→L；核历史操作/candidate/原批准/格式、旧 apply已结束依据、实际 clean HEAD=给定SHA、单parent=base、tree=expected；同 L07 plan COMMITTED+event/current | 可从 UNKNOWN/APPLIED/closed确认实际合法commit，零apply/commit。原 exit缺失不填0；手工退出操作结束声明仍保持人工类型。未知停止需先 resolve 记录，不能因 tree正确省略 |
| finalize 同 SHA重复 | 程序重核原记录与commit对象精确匹配；当前HEAD相同且clean返回同确认结果 | 不造新commit；不同SHA拒绝。今日target已后续演进仍保留历史COMMITTED，在view显示非当前HEAD，不改历史。重复finalize的当前核验不把后续演进清理掉 |
| 源 after-apply有无关变化 | 始终重核固定candidate及批准引用；finalize不重新capture当前源去替换candidate | 原获批bytes/commit仍可核验；不因今日源变化一概失效历史成果。新apply准备依旧必须核源/候选合格 |

人工确认在新 apply 和旧结果补确认上均保持“人工事实”的类型；不得只在一处留类型，另一消费者洗成机器退出。裁定原始事实不可被 projection 修改。

resolve 对已应用/提交而尚缺停止依据允许 **CLOSE_APPLICATION 记录人工停止确认**，并由原 merge/finalize核对真实后态；不是再给 apply。这里 stop_basis 可提供，但不必为纯关闭动作填造；动作模板只在用户确认材料齐备时建议下一步确认。没有停止依据的纯 close不能解锁commit/finalize资格。

## 6. 当前 view、锁顺序与动作来源

`View` 增加原 owner 的 `historicalStatus`、`targetObservation`、`operationId/planSha/ledgerCommitSequence`、`stopBasis`（program/operator/unknown）、`currentActions`、`nextAction/nextCommands` 与裁定模板。原 status保持历史计划或明确 BUSY/PENDING/INVALID，canMerge/canFinalize由当前合法动作派生，不只看plan枚举。UI两端只翻译/呈现动作，不从UNKNOWN/COMPLETED自造按钮。

当前动作规则：

- 无 intent且批准/源/target基准合格：MERGE。
- 有 intent、结束依据齐备且精确 staged后态：RECONCILE（原merge只补本地）、COMMIT_EXTERNALLY/FINALIZE_AFTER_COMMIT提示。
- 已有精确clean commit且结束依据齐备：FINALIZE，包含UNKNOWN计划，不能误拒合法确认。
- 未应用基准+停止依据齐备：RESOLVE_AUTHORIZE_NEW_APPLY，仅打开人工决定入口，不凭view发送。
- 停止不明：INSPECT/RESOLVE_CLOSE_OR_CONFIRM_STOP；已知活动：BUSY/核对旧进程，无新apply/commit提示。
- drift/不匹配：INSPECT/CLOSE；不提供reset/clean。closed：无任何新apply；合格commit只核验确认。
- 历史COMMITTED但今日target不同：保留提交身份+明确历史状态、关闭当前修改动作；不宣称当下仍clean或该HEAD。

目标观察含 repo/common-dir、HEAD/headTree/indexTree、worktreeMatchesIndex、untracked列表身份、Git status身份与观察时间；依赖字段的计算失败或在观察前后不稳定则当前动作不可用。`git write-tree`可能创建树对象；“观察”只表示不更改HEAD/index/业务worktree，不冒称零文件系统I/O。外部用户并发始终只能检查/发现，不承诺原子隔离所有外部编辑。

**处理现有 L 内调用 view 的 sibling：** core live view先短L定位目标、释放L，target→L生成同代view。CLI status/summary和server status/overview先通过未scoped service取得view并释放target，再进入原L快照；只有view的commitSequence/plan/candidate绑定与该L快照相同才嵌入currentActions，否则显示“交付观察已过期，刷新”并零动作。snapshot函数只消费此view，不在持有L时取target锁或重新调用非scoped view。单独delivery API仍直接走target→L。不安装ThreadLocal，不造默认ledger，不扩成一般跨源快照平台。

所有实际命令再次在 target→L核当前资格，不能借用view/模板中的can*布尔值。query无目标锁/ledger锁资格时分别返回BUSY/PENDING，不写裁定。保证针对Dual入口的锁顺序；OS/Git对象查询故障单独归因，不从异常文本生成新的批准。

## 7. 每个失败前缀与冷启动

| durable / external点 | 失败留下的可信状态 | 允许处置；禁止动作 |
|---|---|---|
| request未持久化、manifest前 | 原operation/决定未生效，零新Git | 最新模板重新提交；不把客户端点击当已批准 |
| 裁定+新intent manifest后任一目标/event/current/pending点 | D06有固定新intent/原决定字节，Git是否未发只有原live栈知道 | 同版只补本地→UNKNOWN；没有fresh commit返回的调用零发送。重复同request仅重读 |
| fresh intent完整commit后、start前 | intent存在，可能未启动 | 当前live调用再验目标才可一次start；崩溃无自动恢复start。停止依据缺失由人工核实，不能凭base推从未执行 |
| Process已start、PID记录前/间/后 | 无receipt或pending PID绑定；是否结束未知 | callback失败关闭stdin/本次清理；不确认就UNKNOWN。冷进程不通过父JVM消失制造receipt |
| PID绑定完整返回后、stdin前/中 | 已知process，输入送达不明 | 零第二次写/第二个Git；实际结束/人工停止确认后核目标 |
| waitFor完成、exit记录失败/输出读取失败 | 结束事实只在原live内存或部分prepared，目标可能已应用 | 同版固定补齐已准备事实；未记录的exit不能冷造。人工依据按其类型；零再次apply |
| target观察/APPLIED写集任一前缀 | 精确观测的字节可能pending | D06只补观测/plan/event/current；下一次重核target，不通过finish-forward发Git |
| 人工commit已发生、COMMITTED写集任一前缀 | 精确commit实际可查，plan可能UNKNOWN/APPLIED/pending | 同版补本地或同SHA finalize，零apply/commit；wrong parent/tree/extra dirty拒绝 |
| close写集失败/重复、target或L忙 | 决定未完整或待补齐；可能仍有原外部活动 | 返回未完成/BUSY/pending；不声称“已取消/已停止”，不提供新apply |

本地幂等并不等于外部exactly-once。保证是每个live授权operation至多一次apply启动、对外部未知零自动重发；新的人工授权另有新operation且原史保留。确认点后外部编辑不被自动清理。

## 8. 实施落点、验证义务与 ROI

完整范围仍为 preflight V01–V07/G01–G07。本次确定候选新增文件仅一个 CLI `DeliveryResolveCommand.java`；request DTO和process callback放既有delivery service/adapter中；新的整链测试类只在现有harness无法有界承载时添加并登记。新增命令注册及API入口均需实际测试。文件范围见 [affected-scope.json](affected-scope.json)。无product/测试源码改动。

实施必须证明：

1. 真实程序批准的完整研发→目标Git→人工测试commit→finalize合法序列；四变体/inline-worker按实际共链复用，合成agent与真实临时Git分列。
2. target干净但HEAD变更在实际启动前被拒；plan各身份字段的错绑定拒绝；合法canonical别名及普通版本配置不被误拒。
3. adapter实际进程/退出记录、callback失败零patch输入、异常后正确清理/UNKNOWN；人工确认不进process_exit，已知活动不能被人工override。
4. 同request同内容回执丢失合法重读、异内容拒绝；删除fresh区分必须重复调用失败；manifest前后/各L07写点/真正冷JVM补齐零Git；closed不放行但合法历史确认仍可达。
5. 未应用的人工裁定→新operation一次apply；已应用/已commit→原入口补确认；正常APPLIED空commit待人工不是损坏；输出读取失败不抹真实退出。
6. 两workstream/common-dir/linked-worktree/两service实例，共用target锁；query反向取锁mutation必须被测试抓住；两UI/CLI/API对UNKNOWN/active/closed/stale/history的动作同源，不以DOM桩称浏览器验收。
7. 标准Git临时repo验证process start/record/input/exit窗口。若生命周期callback导致普通apply或清洁观察误拒，回到接线设计，不能删停止/基准guard换绿。最终内容稳定单轮回归与独立批末判断。

本轮无测试运行，以上均 NOT RUN。process callback、planned event条目SHA和query组合方式有当前源码承载锚点，但可实现性/成本未冒称已测。人工停止确认属于候选的明示信任边界；不能当作消除未知窗口或对抗证明。

ROI约束：不为每个Git查询加execution ledger、不追踪任意子树、不为异常样本新建daemon；正常路径只有有限apply lifecycle记录与原L07。成本偏离、原session不能有界承载、需要新增authority/平台或人工分支实质降验收时停交证据和选项。不用CLOSE/UNKNOWN全拒绝替代合法主流程；也不因要保证合法出口而默许重复apply。

## 9. 六维与当前收口状态

Declared：原D09/I6及本候选具体化；Proven：静态源码承载/顺序推演，全部新能力NOT RUN；Consumed：原service消费批准、process与人工决定，view只读；Enforced before：裁定持久化、新apply启动、patch输入、成功采纳/当前动作之前的具名guard；Failure route：原D06补本地、外部UNKNOWN人工、closed不伪造停止；Cannot-bypass：上述fresh/幂等/身份/活动/锁顺序/每前缀/合法序列待实施。

作者自查：候选已把入口/数据/写序/失败/兄弟路径写明，**未取得独立设计PASS，不具备自动实施或真实运行授权**。尤其请独立复核人工停止确认的适用性、PID记录窗口、回执重复与当前view组合是否仍存在死端/误拒/过度成本。需修改时回到对应边界，不写临场补丁。

下一步：用户将本材料与preflight作为一次受影响范围合并CC设计复审；评审通过且实施获授权后，按完整W06工作包实施/离线验证→批末CC判断。总体W项、真实NO-GO、提交权限不变。
