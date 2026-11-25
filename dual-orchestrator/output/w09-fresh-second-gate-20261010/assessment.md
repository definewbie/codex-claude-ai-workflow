# W09 第二次 fresh 真实 Gate：停止评估

2026-10-10；**Gate FAIL / WAITING_FOR_RECOVERY / 原现场保留**。发行、配置与 fresh 前置有效，未因本轮业务前段失败改 INVALID。用户批准了新目标和新的18调用池及真实运行；三道人门与产品 commit 未获提前批准。本次零源码修改、零额外 agent、零自动 retry。

## 已走到哪里

新 target/worktree 位于 `/private/tmp/dual-w09-newlines-20261010-fresh-02`，新实例 `ws-20261010T085617Z-8SVF`，terminal-delivery@5、INLINE、liteflow。初始化仅有空基准提交；context 内部段正常到 intake。首例 F01 的可选内部ID缺席已被真实正常接纳；ACP/record/schema/name/cwd/open/protocol仍检查。Claude Code 实际产生响应与三个完成的只读工具调用，原生最终响应 request 3、4 均 end_turn，两个 prompt 进程实测 exit=0。GLM/provider/billing 信息来自当前 native session 元数据，非独立上游计费实证；Codex 尚未调用。

首个回答在 JSON 前附加说明，原 acquisition 判 PARSE_FAILURE，按已批准额度执行唯一一次 deny-all 格式修正；第二回答 envelope/schema 合法、decision=READY_FOR_PRODUCT_REVIEW，随后 **必需 task_contract_proposal 追溯校验失败**。run/node唯一 outcome 均 `CONTRACT_FAILED / required_artifact_contract_failed / ARTIFACT_CONTRACT_FAILURE / MANUAL_ONLY`，实例 WAITING_FOR_RECOVERY；没有 sidecar 成功发布或 reviewer/后继推进。F02 的 recovery/current-failure.md 与控制主因一致，不再把此处说成会话不兼容或“模型没有返回 JSON”。这是实际领域产物拒绝，不能仅凭 envelope 合法说整个产物已有效。

## F03：程序证据引用与产品路径规则的缺口（需受影响复核）

当前 tracked inventory=0；固定 repository fact binding 带真实 head、空清单 SHA 与 count=0。模型真实读了程序提供的 repo-map/binding，最终把两条当前 `.agent/.../repo-map.json`、`binding.json` 放进 `result.evidence_refs`。这两份文件真实存在且由程序 owner 绑定，但 `TaskContractProposalValidator:179-181` 对每个 evidence ref 复用 `TaskContractValidator.isCanonicalPath`；后者 :256-258 排除任何 .agent/.git 段，所以都被判 non-canonical。

当前拒绝与代码规则一致；本报告不直接判定应该放宽 validator。可确认的生产/消费不一致风险是：intake 模板 :10 说 additional durable evidence references remain allowed，并渲染上述程序材料；只声明非空清单必须引用现存产品文件，却未明确空清单时 evidence_refs 可为空、程序证据由独立 binding 承载，不应复制为产品 evidence_refs。已有 validator :188-189 在空清单时确不要求产品证据条目。因此主流程不是缺一个真实产品文件，不能在空仓库造文件或删校验凑通过。

建议评审先裁清 evidence_refs 的既有语义：若是产品路径集合，向前对齐模型侧约束与空清单合法输出，程序事实仍由 RepositoryFactBinding owner 消费；若确需容纳程序 artifact 引用，应先核已有 binding/ref+SHA 的承载，不把可写 scope 的 canonical 规则整体放宽。本轮未选后者、未改任何产物、未 rescue 本样本。同步核 intake reviewer、格式修正、非空清单和所有共享 proposal 消费者的同族语义。

## F04：两次 prompt 用量错归属（已实证，根因路径有源码支持）

原生同一 ACP session 的 JSON-RPC 最终响应明确给出 request 3 = **36,945**，request 4 = **26,548**；可见两请求合计 **63,493**。native request_token_usage 两个独立条目也保存对应值。Dual 的 acquisition 却把 occurrence 1 和 2 都填 **26,548**，合计 **53,096**，少 **10,397**。这些是接口返回的 token 事实，不保证覆盖原生任意内部树，不推成账单。

身份比较：初始 prompt 文件有末尾换行，原生线上文本恰少该换行；格式修正文件与线上文本相同。acpx parsePromptSource:283 会 trim；AcpxUsageReader:265-268 做文件文本严格相等匹配。AcpxAgentTaskHandler:408-431 等 acquisition（含 correction）完成后才分别读 usage；初始 exact 未命中，reader :239-245 的 LATEST_PROMPT_IN_SESSION 取了当时已经完成的 correction request 4，用了错误 occurrence 的真数字。该 source/fallback 与本次持久结果逐项吻合。初始 strict 匹配落空不能成为借下一请求用量的理由。

建议有界前向修复核验：优先消费原 completion 已有的真实 request/session/occurrence 身份，固定每请求的 usage 后再聚合；无关联事实时标不可归因，不靠“最近”制造本次事实。检查主调用、correction、重连/同 session 多请求、延迟 usage/缺 usage、snapshot 与美元字段；不做新计费平台、不要把修剪文本作为唯一身份。此为 P0.5 观测边界阻塞，暂停后续 dispatch，不改本次 run 中错误统计以伪装通过。

SDK/native 与 run 返回的 costAmount 数值保留于原事实；billingMode=subscription_token_only，本报告不把数值当实际支付金额或自行折算费用。usage_update.used 是上下文快照，不当请求计费 token 加总。

## 停止、预算与现场

本次 **2次真实顶层 prompt**（主调用+一次格式修正）；剩16只表示池余额，不授权 retry/fresh第三例。两个段 runner 共 **441.492秒**（内部context1.644 + intake/correction439.848），原 run.duration=434.495秒，二者口径分列。每个原生命令在600秒内返回，未因超时止损；未达到60min/120k/160k线。停止原因是合同准入拒绝及预算归因失真，而非虚称额度耗尽。

具名 session 已由原 close 通道关闭，show=closed；主/修正 prompt 进程 89299/90167 与运行入口 89259 均已不存在。没有未完成 prompt 回执；不把父进程/会话记录扩大为任意原生子树物理全停证明，不启动新 attempt。原 target/worktree 仍 clean，head/tree未变，无产品源码、业务门批准、交付 apply 或产品 commit。

654冻结输入、CLI/server JAR、选定 wrapper/dist/config 前后相同，实际启动日志指向受审入口与 LiteFlow；本次未复跑回归，不增加已受审1768数字。首例77个归档索引文件仍一致。第二例停止账本归档92文件（明确不索引非权威 mutation lock），原账本不改；归档只定位事实、不供执行替代。只读报告采集一次误取 current/current-failure.md（实际在 recovery/），另有源码文件路径检索错误；均为案头检查错误，已改正确读路径，不属于真实 Gate 故障/反证。收尾通用JSON扫描还曾错误要求原始attempt-1.json可解析；该文件本就是带说明前缀的原答复，已改为仅验证结构化材料并显式保留此不可解析原证据，见material-validation.json。

## 控制链与失败切面

| 原事实/owner | 顺序/实际消费 | 本次处置 |
|---|---|---|
| 新 Git base、原 definition/instance | target init→start无run→runtime identity | 正常前缀；无任意副作用批准 |
| READY context/node、预算/用户授权 | 单段context→原 intake准备/R01 | F01合法缺席通过，程序观察先固定 |
| 原 R02/command intent、原 ACP | 主prompt完成→唯一格式correction完成→原 R03固定 | 两次调用原完成/exit=0；原生未知不能自动重发 |
| RepositoryFactBinding +候选proposal | schema通过→artifact追溯guard→唯一outcome→L03/恢复 | artifact拒绝，零成功采纳/后继；原多写协议保持 |
| 每请求usage、reader/handler | 收集→acquisition occurrence→run聚合→预算核对 | 错用latest关联已记录，停止新dispatch |
| 唯一 outcome、RecoveryAssessment/summary | 固定失败→恢复/read model/摘要 | 原因同源，MANUAL_ONLY；CLI retry字样不作执行许可 |

没有主动注入写入故障；D06/D07离线前缀证明沿用受审内容身份，不把本次正常固定冒称全失败切面真实证明。若其中任何 durable write/外部完成不明，本Gate按原D06固定补齐/原D07 UNKNOWN与人工裁定，零盲重跑。

## 六维与成熟度

| 维度 | 事实与限度 |
|---|---|
| Declared by | 用户 fresh 第二次 Gate 任务/预算/执行授权；实际 GOAL/EXECUTION/最终门尚无决定 |
| Proven by | 当前真实 Claude 两次响应/工具、必需artifact拒绝、原固定run/node、session关闭及两请求usage对照；独立验收/交付未运行 |
| Consumed by | 原 Compatibility→transport；RepositoryFactBinding/proposal validator→唯一 finalize→R03/L03→RecoveryAssessment/Writer；usageReader/handler归因缺陷已定位 |
| Enforced before | 必需身份/政策在prompt前；artifact不合格在采纳/复评/后继前拒；operator因观测缺陷停止新调用。并非OS写前防护 |
| Failure route | CONTRACT_FAILED/MANUAL_ONLY/WAITING_FOR_RECOVERY；零自动retry、不手改产物/旧现场；外部UNKNOWN仍D07 |
| Cannot-bypass | 本次真实非法refs拒绝；合法空清单产出未成功，需补同族正反成对回归。没有新删除式反证，不冒领全部主流程PROVEN |

首例F01/F02修复在本次Claude形态有真实正向证据，不外推两个客户端全部能力。当前完整主流程仍 **未闭合**；F03规则对齐与F04请求用量归因仍需独立判断和前向回归。

## 下一步

把本次事实、两处边界及建议合并交CC只读受影响复核。核实后在原owner内做必要前向修复、合法/拒绝及兄弟成对回归，重新冻结发行与受影响Gate；不临场改本样本、不自动用剩余16调用、不自动开启第三例。无需为初始化材料单独开评审。预算/收益或权限目标变化继续停交用户。
