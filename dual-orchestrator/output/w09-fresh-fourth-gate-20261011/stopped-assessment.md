# W09 第四例：GOAL 批准后续作与用量止损

结论：**Gate FAIL / STOP_LOSS_USAGE_UNATTRIBUTABLE**。发行、选定装配与源输入仍有效；运行发现的是已登记的请求编号重用限制在真实合法续作中的阻塞，不是无效工件或缺模型 JSON。领域状态保持 `RUNNING`，当前 `node-design-decision-021` READY；最后一次设计复评为 `READY_FOR_ARCH_REVIEW`。不伪造恢复状态、不执行该 decision、不自动继续计划。

## 已发生与未发生

用户对当前需求门的“批准”已通过原 `review decide` 写入，GOAL epoch 1 编译完成，`scope.included=[]`。这不授予精确实施范围或最终交付。随后真实只读设计→Codex 复评 NEEDS_REVISION→作者修订→Codex 复评 READY：必须展示实际输入/期望/实际输出的 AC-2/TR-2 缺口在设计层闭合。精确产品文件尚未创建，EXECUTION 批准、自测、验收和目标 Git 交付均 NOT EXERCISED。

本例共 **8 次真实顶层请求**、零格式 correction，两次有新证据语义循环（intake 与 design），已到循环上限。最后完整核定的前七请求累计 **332,733** token。第八请求原 run/occurrence 为 `UNAVAILABLE / AMBIGUOUS_PROMPT_REQUEST_IDENTITY`，其中零值是不可用槽位，绝不是实测零消耗。该调用自己的固定 transcript 包含一个完成 result，原生 totalTokens=**70,687**；案头诊断相加为 **403,420**，未回填 run、未变为 Gate budget authority、未因此继续派发。active_seconds 见 stopped-progress.json，人工等待不计入该口径。

## 承重事实与判断

1. 同一 Codex acpx record `01a1294c-d0dc-7711-b5ff-74b18d96e7d2` / ACP session `01a1294c-db11-7ef0-a76b-d06171a2666a` 的追加 stream，先有 `initialize id=0`，随后 prompt id=3、4；后来又有 `initialize id=0`，随后 prompt id=2、3。两个 id=3 分别返回 28,010 与 70,687。`request-generation-findings.json` 列条目索引与原始 result。**协议重新初始化与编号重用已观察；具体为何重新初始化、TTL/进程因果未证。** 该 stream 没显示 session/load request，不据此虚构 load 事实。
2. `AcpxPromptCompletion` 当前只携带 requestId/stopReason/finalAssistantMessage/sessionId，不携带连接代或当前命令的 usage。`AcpxUsageReader` 在整份追加 stream 中按 (sessionId,requestId) 查询；多个匹配显式返回 AMBIGUOUS_PROMPT_REQUEST_IDENTITY。`AcpxAgentTaskHandler` 唯一消费 reader。这不是 LATEST 或文本借用回流，而是保守拒绝的首版限制现在真实阻塞主流程。
3. 第八 run 的模型响应、严格 envelope、领域校验和 R03/L03 正常完成；usage 单独不可归因。operator observer 重核全 stream 时先在旧 id=3 处碰到重复；即使跳过该旧项，第八 run 本身的 UNAVAILABLE 也足以禁止新 dispatch。因此不能仅修改 observer 去重恢复本例。
4. 前七项固定事实保持不变；追加新记录不应被解释成历史成功事实被撤销。如何向前区分当前调用与历史重用编号，是接线待审问题，不在本次运行中临场实现。

## 控制链与失败切面

| 权威前态 / 动作 | 写入与消费顺序 | 实际后态 / 失败出口 |
|---|---|---|
| 当前需求门 WAITING + 用户批准 | 原 L04 决定→后继 compile GOAL→GOAL 合同固定 | GOAL 已批准，included 空，设计准入合法 |
| 作者只读设计 | 资格→R01/R02→原客户端→一次 finalize→R03/L03 | 设计 SUCCESS，产品树零改动 |
| Codex 第一次复评 | 同链固定 NEEDS_REVISION→原 decision→作者修订 | 第二语义循环，有具名新证据；不改变验收 |
| 作者完整修订→Codex 复评 | 同链固定当前 completion；usage reader 查询追加 stream | 复评 READY / run SUCCESS；usage UNAVAILABLE |
| operator 核对 / 下一派发前 | helper measure→stop-required 固定；零下一节点/模型派发 | 实验 FAIL，领域仍 RUNNING；不回写 usage/失败状态 |
| 止损清理 | 保存 native before→原 sessions close→show→metadata closed→ledger 静止快照 | 五具名会话 closed；不泛化全部子树停止，不产生新 attempt 权限 |

未故意注入本地写入中断；D06/D07 的写失败/冷重启分支本例 NOT EXERCISED。本例所有 prompt 已有 completion，不将关会话当成补齐未知执行结果。没有开启第五例或恢复第四例的授权；未用剩余额度继续，也未修改客户端参数来掩盖此限制。

## 六维与边界成熟度

| 维度 | 本例实际证据 |
|---|---|
| Declared by | approved-gate.md：可归因 usage 不完整即停止；当前 GOAL 批准有真实记录 |
| Proven by | 8 个固定 occurrence；当前合法设计复评及原生重新初始化/编号重用；reader 实际返回 UNAVAILABLE |
| Consumed by | AcpxAgentTaskHandler→固定 run/occurrence→operator measure；预算核对器不是生产路由 guard |
| Enforced before | 核对失败后零后续 decision/plan/provider dispatch；不承诺请求内部 token 硬抢占 |
| Failure route | Gate FAIL + 原通道关闭 + 保存现场；不伪造 WAITING_FOR_RECOVERY、不以零用量放行 |
| Cannot-bypass | stop-required 存在，后续 runner 入口禁止；静止 ledger SHA 重核。真实编号重用的合法归因正向尚不成立 |

停止边界有本例 PROVEN 证据；跨连接重用编号的合法归因尚未闭合；整体主流程仍未证明成功。自查不冒充独立评审。

## 前向修复方向（待审，未实施）

先核对当前命令 completion / 固定 transcript 是否已足够承载当前请求 usage；优先复用现有运行/命令身份及原始结果。若须连接代或有界片段身份，必须在生产者产生、唯一消费者强制使用，并保留错 session、重复结果、缺 usage 的拒绝。不要借最新记录、prompt 文本、人工回填、简单选择第一个/最后一个匹配，或为了预算绕过改会话策略。双客户端、主调用/correction、重连/排队、历史追加后重读、部分量/快照、R 写失败均须按实际受影响范围成对检查。此处不是已选新 schema 或实现授权，也不新增服务、账本、平台。

本次根因判断是 root-cause forward fix 候选，不能变成 single-case rescue。是否以及如何支持同冻结版本的后续 fresh 验证，由受影响评审与用户运行批准决定；不默认恢复本例。

## 证据索引与尚未核验

- goal-decision-authorization.json / goal-decision-proof.json：当前批准、门谱系与实际 GOAL 合同。
- steps/010–017：每段前态、预算、命令、完成及后态；017 的 CLI 领域执行已完成，外层 observer 非零退出另列。
- request-generation-findings.json / request-ambiguity-readonly.json：共享 stream 重用与当前固定 transcript 对照；最后一份 ambiguity JSON 对应最新 run，所有 prompt 顺序在 findings。
- stopped-progress.json / stopped-release-identity-verification.json / stopped-product-tree.json：分类、身份、实际零产品变更。
- stop-session-proof.json / stop-session-operations/：具名清理前后原材料。
- stopped-ledger-snapshot/ / stopped-ledger-identity.json：437 文件静止快照（不将运行 lock 文件当权威内容）。前三例 116/153/487 工件字节未变；654 冻结输入、JAR/受审 adapter/observer 核验通过。
- 辅助记录字段误名、历史索引路径误读已单独记录为 OPERATOR_ERROR；不是模型失败或生产反证。旧等待检查点文件保留，本文是新的停止结论。

尚未核验：原生连接重新初始化的完整进程/TTL 因果；当前请求 usage 的最简可信生产入口与全部 sibling；任何修复实现/回归；计划与 readiness、EXECUTION、人批准、自测、验收、目标交付。生产代码、冻结发行及原账本未修改。

**下一步：CC 只读复审本次分类、身份适用域与前向接线方向；确认后再决定修复和重冻，不开始第五例。**
