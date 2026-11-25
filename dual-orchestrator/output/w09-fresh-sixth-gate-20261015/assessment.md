# W09 第六例：停止与受影响评审材料

2026-10-15。结论：**Gate FAIL；领域 WAITING_FOR_RECOVERY，返修节点 FAILED**。未到 GOAL 批准、设计、实现、独立验收或交付。本次发行/配置前提有效，不以发生在需求阶段改判 INVALID。没有恢复本例、手改账本、放宽契约、调用第五个请求或自动开启第七例。

## 授权、前置和实际进展

用户当前批准170万token/18请求/60min完整主流程Gate。659输入、CLI/server冻结副本匹配；9观察器与13预算离线对照通过，旧五例具名会话关闭/已知PID缺席；Codex配置只增加此前临时目标同级trust登记，其他字节与受审基线一致。新空target/worktree由原start创建，单INLINE/liteflow；宿主Python观察不冒充原生执行。

[位置](instance-location.json)记录 workstream `ws-20261014T234009Z-RN9X`。上下文→Claude真实提案成功（evidence_refs=[]合法）→Codex独立复评NEEDS_REVISION（CO-1：建议文件不应在GOAL exclusions提前锁定）→原decision返修路由→同次R01固定基准/CO-1和两种允许形态→Claude输出full proposal→严格解析拒绝→唯一deny-all格式修正→同字段仍拒绝→唯一outcome OUTPUT_INCOMPLETE/agent_result_missing_or_invalid→R03/L03→WAITING_FOR_RECOVERY。

R01真实形成 contract_revision（精确ID、完整reference/SHA、义务、REVISION_PATCH/GOAL_REPROPOSAL），派发已通过；模型只引用ID、未重现SHA转录错误。**不能据此声称新full形态已通过merger/lineage/复评/批准**：它在解析前端就被拒绝，后续未运行。

## F-1：返修结果封闭结构与修正诊断

确认事实：两次模型原始输出均可由Python标准库JSON解析（仅作语法观察，不替代Java领域契约）；均在 `revision_responses` 元素增加 `response`。Java RevisionResponse只允许review_obligation_id/resolved_by_clause_ids，严格拒绝正确，不应忽略未知键。原错误完整保留于artifactContractResults。

本轮渲染事实与源码：AgentResultPromptContract:126–157的revision分支为full形态给出revision_responses=[]例子，要求补全，但只为patch的obligation_patches输出closedItemFields；正常initial分支:180–182才有RevisionResponse的封闭字段枚举。因revision分支提前返回，full返修未得到这条枚举。与此同时，AgentResultEnvelopeParser:39–44把未知字段异常降为INVALID_JSON，Acquirer:289–307修正提示仅给kind，不给具体未知字段/合法字段；真实修正输出仍保留response。见stop-source-index.json、source-excerpts.txt和固定prompt/raw。

判断：模型多加字段是直接失败事实；生产契约提示覆盖缺口与修正诊断丢失是有据的前向改进候选，**需CC判断最小充分修复和ROI**。不因一次失败引入新schema/DSL/宽松解析器或通用纠错平台。不在本轮实施。

## F-2：可读stdout被当协议帧

确认事实：Claude当前日志是acpx可读输出（[client]/[thinking]/[agent]），初次返修回复单行，修正回复为多行JSON；修正日志有独立一行 `{`。AcpxUsageReader:157–161将任何以 `{` 起首且wire parser报MALFORMED的行直接判INVALID_CURRENT_TRANSCRIPT，未区分可读模型正文片段与JSON-RPC。因该判定阻止原严格共享出口，修正occurrence=UNAVAILABLE，整个返修run按MULTI_PROMPT_USAGE_REQUIRES_OCCURRENCE_SUM不可加总。观察器因此无法生成完整可归因总量，按原Gate停派发。

停止后只读原生stream：四个请求各有唯一(session,request)配对，最后一个原生result.totalTokens=19570；前三个已固定可加总量51231，案头合计70801。**70801只为诊断，不回填run、不作预算authority、不救本例**。固定aggregate仍UNAVAILABLE，字段中的0不是真实零消耗；次数保守计4（提案、复评、返修、修正）。[逐请求材料](stopped-progress.json)带stream/log SHA与不作authority声明。

判断：是原生呈现与协议消费边界错配，有现有owner内的有界修复可能。不得以“忽略所有坏帧”换通过；应区分真正JSON-RPC损坏/错身份/重复/部分量与合法可读正文，保留严格拒绝。具体方案及兄弟路径由CC核对，不预选连接代或新平台。

## 停止、现场和诚实边界

3个具名会话经原close/show观察closed，13个已知PID缺席；不宣称任意子树全停。191个账本文件（排除锁文件）静止快照；target/worktree产品树仍空。659源码输入、1929原实施提交材料、冻结JAR及快照逐条复核一致。旧五例不恢复、不动raw、不沿用余额。本次活动步骤合计216.292秒，非60min耗尽；缺总量非170万耗尽。

run-one-step看到FAILED节点后按既有assert退出；它不是产品根因。operator立即写stop-required，未再派发。停止脚本仅为本例适配“测量失败也应清理”，将总量保留null而非0；原日志未改。无生产/测试源码变更、安装、配置修改、额外agent、提交。bootstrap空commit属已批准隔离初始化，不是用户产品commit。

## 六维与未核验

- Declared by：当次用户批准、fresh-sixth Gate及原strict envelope/可归因用量义务。
- Proven by：4个真实请求、合法提案/复评、R01固定基准、两次未知字段拒绝、固定R03/L03、原生stream只读对照、停止观察/快照。
- Consumed by：原parser/validator/acquirer→唯一outcome→R03/L03/recovery；usage reader→固定occurrence/run→operator观察器。F-2误拒位于用量reader，未以案头结果替代。
- Enforced before：身份/基准资格在dispatch前；strict错误在sidecar/后继前；停止后零新dispatch。
- Failure route：OUTPUT_INCOMPLETE/MANUAL_ONLY/WAITING_FOR_RECOVERY，未知用量不计0、不自动重试。
- Cannot-bypass：本次真实未知字段拒绝且无返修sidecar/新复评；完整合法full返修及后半链仍NOT_PROVEN。

未核验：其他stdout格式/双客户端全部到达形态、同兄弟分支全量扫描、任何前向修复回归、完整主流程和真实交付。没有对“模型为什么加字段”作心理推断。源摘录和固定材料可核，不将分析当独立评审。

## 下一步动作

先交CC合并受影响复审，判断分类、两条生产/消费错配、最小owner内前向修复及必要成对回归；不修现场、不重跑模型。得到裁决后再提交/确认前向修复范围；修复、重冻发行与后续fresh运行分别办理，余14请求不构成继续资格。
