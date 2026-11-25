# W09 第三例：真实设计调用触发 token 止损

作者判断：**Gate FAIL / 已停止新派发 / 原 workflow 仍 RUNNING（下一节点 READY）/ 不恢复或另开样本**。Gate 的实验结论与领域执行状态分列：五次 agent run 均 SUCCESS，设计产物合法；本次 FAIL 来自批准的 token 止损触发，不伪造模型/合同失败或将账本改为 WAITING_FOR_RECOVERY。发行、配置、fresh 和原生观察有效，当前没有把它降为 INVALID 的证据；待 CC 独立判断。

## 1. 当次授权与进展

用户先批准第三例隔离准备与真实运行，再在当前 GOAL 材料展示后批准该实际目标门。精确实施文件、最终交付与产品 commit 均未授权。原 Gate 18 次顶层/600s 单命令/累计主动60min/120k预警/160k止损维持；预算是可观察事实下的分段停止规则，不是原生内部 token 绝对硬限。

新实例 `ws-20261010T124812Z-XCZL`；terminal-delivery@5、INLINE/liteflow。作者合法空产品引用 → Codex 发现 C-4 过早固化候选范围 → 原 decision 自动返作者 → 合法 revision patch/CO-1 谱系合并 → Codex READY → 用户实际 GOAL approve → GOAL 编译（included=[]）→ 只读设计。没有 case rescue、手改权威材料、验收放宽或自批。

F03 在真实空仓产物中 evidence_refs=[] 被原 validator 采纳；F04 五次原请求与 fixed occurrence/run 用量逐项一致（EXACT_REQUEST_MATCH）。它们是本轮真实正向，不推断设计独立复评、工具验收或交付已证。

| 调用 | 原 run | 原生报告/固定 token | 领域 decision |
|---|---|---|---|
| intake.claude_brainstorm | run-1791636537680 | 20590 | READY_FOR_PRODUCT_REVIEW |
| intake.codex_product_review | run-1791636690133 | 27560 | NEEDS_REVISION |
| intake.claude_brainstorm | run-1791636803114 | 16911 | READY_FOR_PRODUCT_REVIEW |
| intake.codex_product_review | run-1791637010369 | 39574 | READY_FOR_PRODUCT_REVIEW |
| design.claude_design | run-1791641578962 | 130731 | READY_FOR_ARCH_REVIEW |

总量 **235366**；设计调用前104635，设计单次 **130731 = input42999 + output14900 + cachedRead72832**，超原止损75366。所有数值来自已关联原生 result，current-observation-proof.json 与 native-evidence/ 保留原值；不把缓存读取剔除来凑额度、不把 SDK 美元数当 GLM 订阅账单。Claude zhipu/glm-5.3/subscription_token_only 与 Codex openai/GPT-6-Astra/metered 分列，未知内部调用不填零。

累计分段 runner 主动窗口557.255s；五次模型进程521662ms，设计 run197181ms，三者不互冒。GOAL 人工批准等待另计、不计为模型主动运行。实际5顶层调用（无格式 correction），有一个有新证据的正常语义返修循环；余13调用不是继续许可，因为 token 已止损。

## 2. 控制链与失败切面

| 前态/owner | 实际动作及写序 | 后态/消费者 | 失败/恢复边界 |
|---|---|---|---|
| GOAL 人门 open，L 为SoT | 用户当前批准→原 review decide/L04 记录 action 与谱系 | resolved + contract.compile READY | 决定写失败依D06，不造批准/调用；同门决定有原ref/SHA |
| contract.compile READY | 原编译检查→固定 GOAL/resolution→L 写完成/路由 | GOAL phase epoch1、included=[]，design资格唯一消费 | 未编译/未批准不得 design；不从GOAL造精确实现权限 |
| design READY、资格有效 | 原R01准备/原生身份→R02 command intent→客户端prompt→实际result→R03/L03固定 | design SUCCESS、context.design_codex_arch_review READY | 输入/本地写失败依D02/D06；外部不明依D07，不重发 |
| 先前总量104635，单次在途 | 原生请求result报告130731→按(session,request)固定→operator核aggregate | 总量235366→原Gate止损，零下一dispatch | 调用前无法保证其未知最终token；不能把usage_update上下文used当已消耗请求量 |
| prompt已完成，无pending权限请求 | 停新调度→原sessions close→show核closed→具名PID/result核对 | 4具名会话closed、已知16 PID不在 | 不声称任意子树物理全停；close不是新执行许可 |
| 权威现场静止 | 只读status/trace/recovery→288非lock文件快照、原生stream归档 | 本目录仅索引/投影；原账本字节不改 | 不手改为终态/恢复状态，不因13调用余量续跑 |

D06/D07写失败分支本次未实际触发，NOT_EXERCISED；继承离线证据不作本次运行证明。自然无原生ask，C/X真实请求回复 NOT_EXERCISED。安全停止基于已完成run、具名session与PID事实，非模型正文。

## 3. 能确认什么、还不能断言什么

**已确认**：设计 prompt manifest10334 bytes/估计2584 token；这是Dual发送内容的估计，不是native总输入。原生stream有3个唯一toolCallId（一个Find、两个Read），12条tool call/update消息；完成工具结果内容约12376/4451/2574字符。思考chunk文本47122字符、消息chunk4320字符；原合法设计JSON7166 bytes，这些字符量不是token测量。request result中实际总量130731与Dual固定值一致，所以当前不是F04那种归因错配。

**合理推测**：原生客户端的多轮内部输入/缓存读取和较长思考参与了本次增耗；每个内部模型turn的贡献未拆明。不能仅凭prompt很短断言引擎出错，也不能把总消耗全归因于Dual模板。design-native-stream-size-observation、design-native-usage-updates、原prompt/manifest和原stream提供案头入口。

**实时边界**：原生中途usage_update使用used/size（上下文快照），末尾used=51158，不能替代request总量130731。真实完整请求量在完成result里获得，所以120k预警与160k止损在此单次完成时一起越过。已停止下一派发，但没有证明可在单次请求内部抢占到160k；这与Gate预声明的非绝对硬限一致，也是下一次预算可执行性必须讨论的实际证据。

**另发现的观测不一致**：run.toolUsage.toolCallCount=12，与3个原生唯一toolCallId不同；AcpxTranscriptToolUsageReader按行的telemetry increments可解释重复计数。它不是235366请求token的来源，不能以12当实际工具调用次数；路由/验收应仍消费独立typed观察。具体兄弟影响未全审，不在本轮临场修复（tool-count-discrepancy.json）。

**恢复投影边界**：原recovery show对当前RUNNING快照返回manual_recovery_required及通用retry提示；本次没有失败agent run，它不能解释预算原因或授予继续许可。原输出保留，不拿它替代Gate止损SoT；是否需要产品投影修正由受影响审查判断，不手改成typed执行失败。

## 4. 停止、身份和诚实边界

4具名session原close/show确认；所有5prompt进程与分段runner的16个具名PID均不在。无活prompt/pending mutation/权限请求；不泛化任意原生子任务全停。source654、发行、wrapper/dist/config/Gate环境与1526受审材料全部原字节。前两例117/154材料文件原样；当前target/worktree clean、零产品改动、未apply/commit。原生stream归档，原metadata只存去命令/环境的选择字段与原SHA。

零生产实现/回归复跑/额外agent/新case。只读路径检查有两处操作员错误已纠正并留档；猜测collector文件名不存在的搜索也非Gate失败。首轮临时计数observer候选在任何执行前撤回，所有执行仍用原起始observer字节，预算/判据不变；该候选日志不当已实施修复或先红。

## 5. 六维

| 维度 | 本次证据与边界 |
|---|---|
| Declared by | 用户第三例授权、当前GOAL授权、原Gate160k止损与严格批准/证据条件 |
| Proven by | 5次真实关联result、合法返修/独立复评/实际GOAL、设计合法产物、原close/PID、静止快照；全流程未证 |
| Consumed by | 原qualification/validator/编译/唯一outcome；预算operator消费固定usage，不另造领域状态 |
| Enforced before | GOAL在设计前；EXECUTION未授写；235366被观察后零下一dispatch。单次内部160k硬拦截未证明也不声明 |
| Failure route | Gate FAIL→停新调用→原关闭/观察→证据归档；领域设计SUCCESS保持，UNKNOWN不造结果/许可 |
| Cannot-bypass test | 本次真实未批零实现、token止损后零复评/后继调用；异常写前缀/冷恢复仍是历史离线证明，不冒成本轮实测 |

完整合法主流程未闭合、W09不完成。F03/F04本轮正向与本Gate FAIL可同时成立。

## 6. 下一步动作

先合并只读CC复审本次分类、实际正向边界、token来源与下一Gate的必要前置/ROI。优先核已有客户端可用配置、内部轮次与输入收敛办法；不默认抬160k继续、改模型/权限/验收、补新预算平台或开第四例。任何实质运行方案或额度变化交用户；当前不恢复第三例、不新运行。独立评审尚未发生，不将本自查称CC PASS。
