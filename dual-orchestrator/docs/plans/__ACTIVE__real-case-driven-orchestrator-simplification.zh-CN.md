# __ACTIVE__ Real-Case-Driven Orchestrator Simplification

## 总体目标

1. 简化 Dual Orchestrator。
2. 让 node executor/handler 只负责编排，领域规则归属唯一 domain owner。
3. LLM workflow 结果只通过 strict `AgentResultEnvelope` 进入状态流转；Markdown 仅供人阅读，不参与控制。

## 当前唯一优先级：先跑通一个完整端到端

在 captcha-auth 从需求进入真实产品交付并达到 workflow `COMPLETED` 之前，暂停新的 schema、workflow node、Gate、
projection、registry、通用框架、性能优化与成熟度扩张。只允许处理阻止当前 happy path 继续运行的已证实缺陷，
并且优先恢复当前 `ws-20260818T011340Z-WA53`；仅当冻结实例无法安全消费修复时才创建 fresh workstream。

当前 blocker 是确定性的 Definition/Instance consumer 矛盾：workflow 与 ledger 合法产生 human requirement
`trigger_action=revise`，但 `TaskContractRevisionContextResolver` 未消费该既有动作，导致 provider dispatch 前失败。
本 checkpoint 只闭合这条既有 typed lineage：从 completed human gate 读取 verified `BLOCKED` review、上一版 proposal
与人工 note，形成 revision patch emission context。它不新增领域概念或调用。完成 focused tests、fresh package 和一次
代码级 diff review 后，直接继续当前 workstream，以首个完整 E2E `COMPLETED` 作为唯一成功口径。

首次原位重试证明 trigger consumer 已闭合并成功启动 provider，但暴露同一 typed 链的下一处丢字段：resolver 产出的
`ReviewObligation(id,text)` 在 `AgentResultPromptContract` 中只投影 id，人工 note 没有进入 prompt。模型因而沿用旧 review
上下文，反向选择了“允许本地号码 + 默认地区”，并再次输出未闭合 JSON。当前只修复 authoritative obligation 的 id+text
完整投影；不自动补 JSON、不放宽 parser，也不把错误业务语义包装成成功。

当前成熟度：Phase A 的 strict agent-result、typed lineage 与完整小案例已达到 `ENFORCED + PROVEN`；Phase B 的 read-governance subtraction 与 typed change-boundary enforcement 已由 deterministic tests 和 controlled real-case 达到 `ENFORCED + PROVEN`；Phase C 的 typed context boundary 已由 deterministic tests、README 与 A3 controlled real-case 达到 `ENFORCED + PROVEN`；Phase D 已通过四个删除式 slice 收敛已证明的 handler 重复 owner，并达到 `ENFORCED + PROVEN`。

## Current Active Step: Human Revise Revision-Context Boundary / v16 FAIL, CONSUMED

> Captured replay 的 evidence 与结果已由独立评审复现，并提交为 `f413cb44`。该样本证明 current design typed
> projection 与 strict contract enforcement 工作正常，但否定了“单次 LLM architecture judge 能稳定给出完整
> blocker 集”的假设：reviewer 发现新的有效 B5，却遗漏已冻结的 B1-B4；同时输出自由文本 enum，被 strict parser
> 正确拒绝。继续增强 judge prompt、增加 retry 或更换 judge 不能建立业务裁决 authority。
>
> 当前只读收敛计划：`docs/plans/architecture-review-advisory-human-authority-plan-20260815.zh-CN.md`。目标是撤销
> LLM `NEEDS_REVISION` 对自动 design revision 的控制权：三个 bundled workflow 的合法 review outcomes 全部进入
> 既有 human design gate；进入 gate 不启动 iteration，只有 canonical human `revise` 才消耗 gate-owned revision
> budget 并返回 Claude。独立设计评审首轮 `WARN` 指出 `dualctl autorun` 会自动 approve 非 BLOCKED gate；修订计划已将
> autorun 纳入同一 authority 边界：design gate 无论 reviewer 建议为何都必须停给人，其他 gate 的
> `BLOCKED/NEEDS_REVISION` 也不得自动批准。严格 review contract 继续 fail closed，现有 packet/UI/API/CLI 复用，
> 不新增 schema、Agent 调用、judge panel、retry、module 或平台能力。修订计划 delta review 为
> `PASS / Implementation READY`。单一 checkpoint 已实施但未提交：三个 workflow 的 design review 三态全部进入 human gate；进入 gate
> 不写 iteration；design revise 必须携带人工 note；autorun 对 design gate 三态全部停止；人工可覆盖 negative advisory
> 直接 approve，也可 revise 并由 gate-owned budget 计数。focused tests PASS，最终非沙箱 full Maven reactor
> 9/9 modules、`1249/0/0/0`，`git diff --check` clean。独立代码级 diff review 为
> `PASS（P0=0 / P1=0 / P2=4 非阻塞观察项）`，确认 authority owner、canonical gate action、iteration budget 和
> autorun stop 边界不可绕过，满足选择性提交条件。当前成熟度为 `ENFORCED / NOT PROVEN`；提交后不重放已消费样本，
> 等待下一次自然 design review 验证任意 verdict 都先停 human gate，再由人工决定 approve/revise。
>
> checkpoint 已选择性提交为 `d5a36483`，Gate 提交为 `820db46d`。v14 fresh package、隔离 PostgreSQL、双 provider
> probes 与历史资源清理均 PASS，随后只创建一个 fresh workstream `ws-20260815T033043Z-Z95C`。初始 Claude intake 与
> Codex product review 完成；Codex 自然给出四项 contract obligations。第二次 Claude intake 返回合法 strict
> `task_contract_revision_patch`，但在 CO-1 与 CO-3 的 `evidence_ref_additions` 中重复声明
> `src/main/resources/application.yml`。merger 以 typed
> `DUPLICATE_EVIDENCE_PATCH_TARGET` fail-closed，workflow 停在 `WAITING_FOR_RECOVERY`。未 retry/resume，两个 scoped
> sessions 已关闭，产品仍 `NOT ACHIEVED`；流程未进入 design，因此 human-authority 仍为 `ENFORCED / NOT PROVEN`。
>
> 只读归因确认：baseline `evidence_refs` 只有 `README.md`，新增 `application.yml` 本身合法；问题是 Agent-facing
> revision contract 未投影 merger 已强制的“所有 obligation patches 共享全局 mutation target namespace”不变量。
> prompt 只要求 obligation ID 恰好一次，没有说明 clause/scope/evidence target 跨 patch 全局唯一。当前 active 动作是制定并
> 评审一个 bounded contract-projection checkpoint：共享 owner 定义 target uniqueness，prompt 完整投影，envelope
> validator 提前给出 typed error，merger 保留 durable fail-closed guard，并用本次 captured JSON 做一拒一受的
> deterministic replay。禁止自动去重、parser 放宽、correction/retry、额外 Agent 调用或 resume v14。结果见
> `docs/testing/verification-gates/20260815-terminal-delivery-captcha-auth-human-authority-v14-result.md`。
>
> 收敛计划已通过独立架构评审并完成未提交实现：新增一个 package-private target semantic owner，early envelope
> validator 与 durable merger 双消费；merger 原有三组本地 Set 已删除；prompt 完整投影 clause/scope/evidence 的
> patch-wide uniqueness。captured v14 duplicate/valid pair、三类 sibling、比较规则 parity 与 `NOT_REPAIRABLE` correction
> 边界均有 deterministic tests。focused 18/0/0/0，full reactor 1253/0/0/0，`git diff --check` PASS。当前等待独立
> code-level diff review；未提交、未运行 provider 或新 real-case。
>
> checkpoint 随后通过独立 diff review：`PASS（P0=0 / P1=0 / P2=3 非阻塞观察项）`，focused 18/0/0/0、full
> reactor 1253/0/0/0 与 diff-check 均被独立复现。已选择性提交为
> `0c77ce0c61a4699a608e4322781da4d604df9d5b`，工作树 clean，成熟度 `ENFORCED / NOT PROVEN`。
> 下一 Gate 为
> `docs/testing/verification-gates/20260815-terminal-delivery-captcha-auth-completion-v15.md`：继续以完成 captcha-auth 产品为唯一
> 主目标，target uniqueness 与 human authority 只在自然触发时形成条件结论；不 resume v14，不诱导 revision/reviewer verdict。
> 首轮独立 review 为 `WARN（P0=0 / P1=1 / P2=3）`：v14 的唯一 `.agent` evidence 仍只存在于待清理 worktree，branch 与
> result SHA 不能替代 artifact 内容。Gate 已修订为先完整复制 workstream ledger 到独立只读 archive、生成逐文件 SHA manifest、
> 与 result 四个工件对账，再删除 source worktree；session hygiene 明确核对 open 状态而非 record 存在；target 下 preflight
> evidence 明确只是临时目录，后续 clean 前必须复制到 durable case report。当前等待 delta review；provider 与 fresh workstream
> 未授权。delta review 已 `PASS（P0=0 / P1=0 / P2=1 非阻塞）`，原 P1-1 与 session/evidence durability 均 CLOSED；Gate
> 又补充 source/archive 双侧同规格排序 manifest 必须逐行一致，闭合最后一个复制完整性 P2。满足只提交 Gate + active plan 的条件；
> 提交后只授权归档清理、fresh package 与 non-provider PostgreSQL preflight。
>
> Gate 与 active plan 已提交为 `5e48bfec`。v14 ledger 归档/双 manifest 对账、fresh package `1253/0/0/0`、隔离
> PostgreSQL、Claude/Codex minimal probes 与 session closure 均 PASS。用户随后授权创建唯一 fresh workstream；runner 在任何
> package、provider 或 workstream 副作用前 fail-closed，因为 Gate 九个必填字段使用全角 `：`，而
> `scripts/lib/verification-gate.sh` 的 typed text contract 只接受 ASCII `:`。本次为 pre-start `INVALID`，未创建样本、未消费业务
> Agent 调用。当前只做机械协议闭合：九个字段改为 ASCII `:`，用 production validator 离线验证后选择性提交，再沿用本次授权启动唯一
> fresh workstream；不修改 Gate 业务语义、workflow、prompt、provider 配置或 production 代码。
>
> 机械修复已提交为 `070ad84d64f58d442308cb5995c1e842b1a1d018`。唯一 fresh workstream
> `ws-20260817T141747Z-HUAX` 随后运行：initial intake 与 Codex product review 完成，第二次
> `intake.claude_brainstorm` 的 initial occurrence 和唯一 bounded correction 均 exit `0`、`end_turn`，但都只缺根对象最后一个
> `}`。首次 typed correction 已准确携带 `UNEXPECTED_END_OF_INPUT` 与位置，第二次仍失败；strict parser 正确阻止 sidecar、reference、
> reviewer 与 successor，workflow 进入 `WAITING_FOR_RECOVERY / OUTPUT_INCOMPLETE / MANUAL_ONLY`。未 retry/resume、未创建第二样本，
> sessions 已关闭。独立同行审查确认 Dual 捕获、投影、strict parse、bounded correction、terminal outcome 与 recovery 路由均正确；
> acpx record history 中的重复呈现不是 Dual 控制输入，其确切来源保持 `UNKNOWN`。结果见
> `docs/testing/verification-gates/20260817-terminal-delivery-captcha-auth-completion-v15-result.md`。
>
> 后续独立评审直接重建 T6 v5 幸存的 authoritative ACP wire streams，推翻了“两个独立 real-case、四个 occurrence 同形”的历史外推：
> T6 v5 实际包含 fence/前置 prose、多文档与 depth-2 深截断，没有任何 occurrence 可由追加一个根 `}` 恢复；其 ledger 已删除，完整 replay
> 为 `NOT VERIFIABLE`。当前可确认下界因此修正为 **1 个 run / 2 个 occurrence**，即仅 captcha-auth v15。评审同时离线证明 v15 两个
> candidate 追加一个 `}` 后可通过 production parser、schema/semantic、coverage、lineage、target-uniqueness 与 merger 全链，但该事实不足以
> 支持 production normalization。root-closure 方案评审结论为 `WARN / Implementation NOT READY`，继续维持完全拒绝。
>
> 此前拟议的 active 动作是 bounded prompt-projection checkpoint：把相关单行 JSON 示例改为结构清晰的多行示例并强调最终根闭合检查，
> 保持 schema、strict parser、单次 correction、workflow 与 Agent 调用数不变。只有 prompt projection 落地后的独立自然样本再次出现 exact
> root-only morphology，或第三个历史样本由原始 wire/ledger 工件证实时，才重新评估 root-closure normalization。必须先完成架构评审和
> deterministic prompt tests；不授权修改 parser、自动补 `}`、增加 correction/retry、resume v15 或运行新 provider 样本。
>
> 进入该步骤后核对 production 与 v15 captured prompt，发现原定 checkpoint 的前提不成立：
> `AgentResultPromptContract` 自 strict-envelope checkpoint 起就使用 Jackson pretty printer 输出多行完整 JSON，且 v15 initial/correction prompt
> 均明确声明 every object closed、展示最终根 `}`；correction 还提供 root EOF 的 typed diagnostic。所谓“单行示例改多行”没有代码 delta，
> 再加末字符提醒只是无法形成 cannot-bypass 的 soft-prompt 同义重复。已形成停止性评估：
> `docs/plans/agent-result-prompt-projection-adequacy-assessment-20260817.zh-CN.md`。建议 `NO IMPLEMENTATION / KEEP STRICT REJECTION`，
> 独立架构评审已 `PASS`，确认所有 bundled Agent 路径共用同一 pretty-printed contract、correction 复用完整 contract、没有 legacy bypass，
> 且不存在有效 production delta。prompt checkpoint 正式关闭，未修改 production、未调用 provider。下一步返回 captcha-auth 产品完成主目标，
> 制定 fresh v16 Verification Gate；provider JSON compliance 只能作为运行期风险，不能伪装成启动前保证。
>
> v16 Gate 草案已形成：
> `docs/testing/verification-gates/20260817-terminal-delivery-captcha-auth-completion-v16.md`。唯一主目标仍是完成 captcha-auth 产品；provider
> probes 只证明可调用，不保证 JSON compliance。现有 strict parser、单次 correction 与 fail-closed recovery 保持不变；exact root-only EOF
> 仅作为有原始 wire 证据的运行期观察与 root-normalization 重评触发，不进入 workflow 控制。v15 ledger 必须先完整归档、双 manifest 对账并
> 核对 sessions closed，才可删除旧 worktree。独立评审为 `WARN（P0=0 / P1=1）`：Gate 错称 v15 result 已记录四个 SHA，且 ledger 外的
> authoritative ACP stream 未纳入归档程序。两处已修正：只复算 result 已记录的两个 candidate SHA，归档时新增登记 run SHA；三个 HUAX
> scoped session record 与 stream 全部复制进 archive 并核对 SHA。90 分钟 active runtime 也明确为 operator 依据 ledger 时间戳计算，
> 非 runner 自动计时器。按评审结论，P1 修复后 Gate `Execution READY`，满足选择性提交条件；仍不授权 package、probe 或 fresh workstream。
>
> v16 随后完成 v15 ledger/session 归档、fresh package `1253/0/0/0`、隔离 PostgreSQL 与各一次 Claude/Codex minimal
> probe，全部 PASS。唯一 fresh workstream `ws-20260818T011340Z-WA53` 持久化为
> `agent-lifecycle-terminal-delivery@5`（31 nodes / 51 edges / 9 ACP agent nodes / 0 handoff）。三轮 intake proposal 与
> Codex product review 均形成合法 typed result；第三轮自然 `BLOCKED` 到达 human requirement gate。用户选择 `revise`，完整产品决策
> 被持久化，并唯一激活 `intake.claude_brainstorm` / `iter-004`。human gate 的 authority、note 与 route slice 因而得到真实样本证明。
>
> 但 revision successor 在 provider 启动前失败：`RoutingEngine` 合法写入 human action
> `trigger_action=revise`，`TaskContractRevisionContextResolver` 却只接受 `workflow_started`、`clarify`、`NEEDS_REVISION`，返回
> `contract revision reached an unsupported iteration trigger`。失败节点没有 `runRef`、没有 `run.json`、没有 ACP/provider
> occurrence；随后缺失 output 被投影为 `AGENT_RESULT_MISSING / AGENT_OUTPUT_CONTRACT_VIOLATION`，workflow 进入
> `WAITING_FOR_RECOVERY`。本轮按 Gate 判定 `FAIL / STOP-LOSS / CONSUMED`，未 retry/resume；sessions 已关闭，PostgreSQL probe
> container 已删除。结果见 `docs/testing/real-case-reports/20260818T011340Z/result.md`。
>
> 下一步只允许对 human `revise` authoritative fact 到 TaskContract revision context 的 owner、lineage 与 patch shape 做只读架构归因，
> 制定单一 bounded checkpoint 与真实 cannot-bypass test。不得继续 WA53、创建第二个 real-case、放宽 parser 或增加 retry。

## Historical Active Step: Design Review Convergence Captured Replay / FAIL, CONSUMED

> Captcha-auth v13 workstream `ws-20260814T103857Z-AEHF` 已在第四轮 architecture review 后耗尽 design semantic
> revision budget，进入 `WAITING_FOR_RECOVERY / semantic_revision_limit`；实现尚未开始，该 workstream 为
> `FAIL / CONSUMED`，不得 resume。只读归因确认旧 context 将当前 design 的 typed checks/evidence 降为 summary，
> bounded iteration history 未进入 architecture-review profile，且空 workstream-memory 文案冒充 incremental
> prior verdict，造成 reviewer 串行发现 blocker。
>
> checkpoint `44e72306494c6df3edc31ff9de2907c7a1efbacc` 已将当前 lineage-validated design 作为 budget-exempt
> `Required Architecture Review Subject` 完整投影，加入 bounded iteration reasons，修正 empty-memory honesty，
> 并要求 Claude 输出完整 replacement design、Codex 在当前 turn 扫描完整 lifecycle boundary。focused/full reactor
> `1245/0/0/0`、两轮 diff review 与 P2-1 delta review 均 PASS；成熟度为
> `PROJECTED / VALIDATED / NOT PROVEN`。
>
> 一次性 Gate：`docs/testing/verification-gates/20260815-design-review-convergence-captured-replay.md`。已用 current
> production `ContextBuildPackageHandler + AgentPromptRenderer` 从 v13 final design 与真实 `iter-003..005` 生成并冻结
> exact prompt；历史 reviewer 答案不在 prompt 中。Gate/fixture 已提交为 `5499f4e`，non-provider preflight PASS。
> 唯一授权 Codex replay 随后执行：1 次 prompt、0 correction/retry、0 permission/tool、exit=0、`end_turn`、session
> closed。但 final assistant message 因 `failure.verification_remediation` 使用自由文本而被 strict enum 拒绝，同时只
> 发现新的 in-flight resend blocker，未覆盖 Gate B1-B4。结果为 `FAIL / CONSUMED`；不得运行第二次 replay 或 fresh
> captcha-auth workstream。结果见
> `docs/testing/verification-gates/20260815-design-review-convergence-captured-replay-result.md`。

## Historical Active Step: Captcha-Auth Completion v13 / FAIL, CONSUMED

> Gate：`docs/testing/verification-gates/20260814-terminal-delivery-captcha-auth-completion-v13.md`。唯一 fresh
> workstream `ws-20260814T103857Z-AEHF` 完成 intake、TaskContract 与 human contract gates，但在 design stage
> 连续经历 4 次 Claude design 与 4 次 Codex architecture review。第四次 review 从 final typed design 发现 partial
> unique index 的 stale-PENDING、过期 activation 与 rollback/conflict recovery 缺口；design semantic revision budget
> 已耗尽，workflow 进入 `WAITING_FOR_RECOVERY / semantic_revision_limit`。
>
> 该轮未进入 plan、implementation、self-test、acceptance 或 delivery，captcha-auth 仍为 `NOT ACHIEVED`。workstream、
> worktree 与 named sessions 均为 `FAIL / CONSUMED`，不得 resume/reuse。其 captured final design、iteration ledger 与
> historical reviewer contract 只作为当前一次性 replay 的只读证据。

## Historical Active Step: Captcha-Auth Completion v12 / FAIL, CONSUMED

> Gate：`docs/testing/verification-gates/20260814-terminal-delivery-captcha-auth-completion-v12.md`。唯一 fresh
> workstream `ws-20260813T235458Z-4RQE` 已通过 scope、contract、design、plan 与 human gates，在
> `implement.claude_implement` 因 caller timeout、未取消 persistent prompt 与历史 permission evidence 污染进入
> `WAITING_FOR_RECOVERY`。worktree 中虽已有产品实现，但 self-test、acceptance、completion audit 与 delivery
> 均未闭合，因此 v12=`FAIL / CONSUMED`、captcha-auth=`NOT ACHIEVED`。该 workstream/worktree/session 不得复用。

## Historical Active Step: Captcha-Auth Completion v11 / FAIL, CONSUMED

> v10 fresh workstream `ws-20260813T143229Z-BAS8` 已消费。ScopeOperation early boundary、validated proposal
> reference、Codex product review 与真实 human `BLOCKED -> approved` 决策均通过；产品方明确选择手机号仅接受
> E.164。随后 compiled `TaskContract` 仍保留与该决定相反的本地号码规则，operator 在 contract review 选择
> `clarify`。旧 bundled `terminal-delivery@3` 错误地把该语义动作直接路由回确定性 `contract.compile`，且回边
> 只携带 compiled artifacts，导致 `TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING / review_subject_count=0`，workflow
> 进入 `WAITING_FOR_RECOVERY`。这是有效技术 FAIL，不是 provider、parser 或模型失败；旧 workstream 不 retry/
> resume，captcha-auth 仍为 `NOT ACHIEVED`。
>
> 根因是 owner 错配：contract clarification 应由 typed proposal owner 处理，compiler 只能消费已验证 proposal，
> 不能修改业务语义。checkpoint `4afd15e2` 已将三个 bundled workflow 的 `clarify` 收敛为 loop 到
> `intake.claude_brainstorm`；human note 经新 iteration 成为 lineage-bound typed obligation；resolver 从 verified
> requirement gate、contract resolution、Codex review 与 proposal reference 恢复唯一 baseline，缺失/篡改
> fail closed。当前 patch schema 不支持 scope deletion，因此删除了虚假的 `reduce_scope` action/edge/CLI 示例。
> focused tests 与非沙箱 full Maven reactor PASS，`git diff --check` clean；当前成熟度为
> `ENFORCED / NOT PROVEN`。
>
> 新 Gate：`docs/testing/verification-gates/20260814-terminal-delivery-captcha-auth-completion-v11.md`。主目标仍是
> 完成 captcha-auth；contract clarification 只在自然触发时形成条件性证明，不人为制造。当前授权提交 Gate 与
> active plan，并执行 fresh package、Dual/target/workflow identity 与隔离 PostgreSQL non-provider preflight；
> provider probes 与 fresh workstream 只在 preflight PASS 后执行。

## Historical Active Step: Captcha-Auth Completion v10 / FAIL, CONSUMED

> v9 fresh workstream `ws-20260813T115256Z-8G74` 已消费。fresh package、target identity、PostgreSQL 与双
> provider probes 均 PASS；首个 intake Agent run 正常 `end_turn`，strict JSON、schema 和 Agent result
> contract 均有效，但 proposal 把不存在的 `README.md` 声明为 `MODIFY`，required artifact contract 在
> reviewer 与实现副作用前 fail closed。workflow 进入 `WAITING_FOR_RECOVERY`，target 与 fresh worktree 均无
> 业务代码改动，因此本轮是有效 `FAIL`，不是 provider、parser、transport 或 stale artifact 导致的 `INVALID`。
>
> 根因不应简化为模型偏差。`TaskContractProposalValidator` 用 workspace filesystem 判定 `CREATE/MODIFY`，
> 但 seed-only intake 同时禁止工具调用；此时 `repo-map.json` 的来源仅是尚未存在的 task contract 与 Git
> evidence，所以 files 为空，prompt 没有投影 authoritative existing-path fact。当前 active objective 是收敛
> 这一信息边界：validator 与 prompt 必须消费同一份 deterministic repository-baseline fact；不得自动把
> `MODIFY` 改成 `CREATE`，不得放宽 validator，也不得通过 retry 掩盖缺口。
>
> v9 报告位于 `docs/testing/real-case-reports/20260813T114916Z/`，external preflight 已按原始 SHA-256
> 固化。captcha-auth 仍为 `NOT ACHIEVED`；self-test repair 未被运行到，仍为 `ENFORCED / NOT PROVEN`。
> 原子实施计划已落到
> `docs/plans/intake-scope-operation-fact-projection-plan-20260813.zh-CN.md`：Agent-facing contract 只声明 exact
> path，Dual 由单一 resolver 根据 effective worktree 派生 durable `CREATE/MODIFY`；initial 与 revision patch
> 同步收敛，不扩大 repo-map、不增加 correction/retry。CC 设计评审已 `PASS`；实现现已完成，focused tests
> `55/0/0/0`、受影响 ACP adapter tests `41/0/0/0`、full Maven reactor `1223/0/0/0`，
> `git diff --check` clean。CC 独立代码级 diff review 已 `PASS`，checkpoint 已提交为 `c753cfc`；当前成熟度为
> deterministic `ENFORCED / NOT PROVEN`。失败表面同步拆分为：Agent-facing
> path shape/resolution 错误使用 `TASK_CONTRACT_SCOPE_RESOLUTION_FAILED`，resolver 之后 durable operation 被篡改或
> 与 filesystem 相反仍由 `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED` fail closed；routing/recovery 不按这两个
> code 分支。
>
> 下一版产品 Gate 已起草为
> `docs/testing/verification-gates/20260813-terminal-delivery-captcha-auth-completion-v10.md`。唯一主目标仍是完成
> captcha-auth；ScopeOperation 只是在首个 reviewer 前必须通过的 early boundary，不单独制造样本或调用。
> Gate 独立同行评审已 `PASS`，两项非阻塞措辞建议已闭合：human action 必须来自当前 packet 的
> `allowed_actions`；ScopeOperation 复核以 frozen target tree 为存在性事实来源。提交 Gate、active plan 与
> review 后，授权 fresh package 和 non-provider preflight；provider probes、workstream 创建与 real-case
> 仍未授权。

## Historical Active Step: Captcha-Auth Completion v8 / CONSUMED, PRODUCT NOT ACHIEVED

> v7 fresh workstream `ws-20260812T035340Z-E3P3` 已消费并停止于 design Agent run
> `run-1786509652821`。captured evidence 证明两个独立 Dual-owned 缺口：assistant message 的完整 JSON
> 被 prose + fence presentation 包裹，旧 normalization 未提取；同时 Claude 尝试的未批准可选工具没有
> 执行，但模型随后交付完整结果，旧 `PERMISSION_BLOCKED` 仍覆盖 terminal outcome。该失败不是 target
> 业务实现失败，target 仍保持 baseline HEAD `7c7e35e8`、tree `bab065a1`、requirement hash
> `42078b01`，无实现文件。
>
> 两条边界已在 checkpoint `023924b6` 原子收敛：`AcpxAgentResultAcquirer` 只提取恰好一个完整、独占行
> 的 JSON fence，提取后仍进入原 strict parser/schema/semantic/artifact validators；
> `AgentExecutionOutcomeResolver` 只有在 non-zero complete、AgentResult valid、全部 required artifacts
> satisfied 时才把 `PERMISSION_BLOCKED` 保留为 secondary warning，否则继续 fail closed。
> `allowedTools` 内部语义同步更名为 `autoApprovedTools`，明确它是免确认集合，不是用户工具可见性白名单。
> 独立代码级 review 为 PASS，full reactor `1213/0/0/0`，当前成熟度为 `ENFORCED / NOT PROVEN`。
>
> 新 Gate 已起草：
> `docs/testing/verification-gates/20260812-terminal-delivery-captcha-auth-completion-v8.md`。主目标仍是完成
> captcha-auth 产品；presentation extraction、contained permission denial 和 human `BLOCKED` 只在自然
> 触发时形成条件性技术结论，不人为制造。Gate 独立同行评审为 PASS。当前授权提交 Gate、active plan
> 与 review，并执行 fresh package 及 Git/workflow/JAR/target/PostgreSQL 非 provider preflight；Claude/Codex
> probes、其他 provider 调用与 real-case 仍未授权。

## Historical Active Step: Captcha-Auth Completion v6 / FAIL, CONSUMED

> 唯一 fresh workstream `ws-20260811T143704Z-RZQL` 已结束为有效 `FAIL`，不是 `INVALID`。
> clean SHA、fresh package、target baseline、隔离 PostgreSQL 与双 provider probes 全部通过；
> target 和执行 worktree 均未产生实现改动。工作流在 intake 运行 8 次 Agent（Claude 4、Codex 4），
> 自然触发并完成 3 次 typed revision transition，最终由 `RoutingEngine` 在 revision budget 耗尽后
> 置为 `WAITING_FOR_RECOVERY / SEMANTIC_REVISION_LIMIT`。全程 8/8 token 可测，总计 265213 token，
> Agent duration 588419ms。
>
> 三次 patch 的 merge、coverage、delta、derived reference 与下一 reviewer 消费均成功，证明
> revision-patch 到 reviewer 的真实链路可运行；本轮未到达 compiler，因此 checkpoint `fa1f4818`
> 仍是 `ENFORCED / NOT PROVEN`。第四次 review 的唯一剩余阻塞是手机号输入范围：Claude 选择
> `E.164-only`，Codex 判定这是原始需求未授权的用户可见范围收窄，要求 human decision。
> captcha-auth 仍为 `NOT ACHIEVED`。
>
> 四次 review/revision 的只读归因已完成：revision producer 确实逐轮修复 typed obligations，最终
> 剩余项是真实 human product decision。human-decision convergence 已按单一 checkpoint 实施：business
> `BLOCKED` 现在以 execution `COMPLETED` 路由到既有 human gate；`GateDocument.source_outcome`、human
> note、reviewer `must_fix/missing_evidence` 与 compiler resolution 形成单一 ledger truth；UI、API、CLI
> 和 autorun 消费同一边界；三个 bundled workflow 已同步补齐 BLOCKED edge。focused tests 与完整
> Maven reactor 为 `1210/0/0/0`，`git diff --check` clean，当前成熟度为 `ENFORCED / NOT PROVEN`。
>
> 独立代码级 diff review 已 `PASS`；v6 历史证据提交为 `01eca0e6`，human-decision checkpoint 提交为
> `99f827d5`，worktree clean。新的 v7 Gate 已起草：
> `docs/testing/verification-gates/20260812-terminal-delivery-captcha-auth-completion-v7.md`。
> Gate 独立同行评审已 `PASS`：
> `docs/testing/verification-gates/20260812-terminal-delivery-captcha-auth-completion-v7-review.md`。
> 当前授权提交 Gate 文档，并在 clean SHA 上执行 Git/workflow identity、fresh package 与隔离 PostgreSQL
> preflight；Claude/Codex probes、provider 和 real-case 尚未授权。infrastructure preflight PASS 后，
> 才能在 fresh workstream 中自然到达 human gate，由用户通过 UI 或 CLI 提交决定并继续到 compile，才可把该边界
> 标记为 `PROVEN` 并继续完成产品。不 resume v6，不调高 revision limit，不改 provider/model/prompt。
> v6 report：`docs/testing/real-case-reports/20260811T143322Z/report.md`；实现设计：
> `docs/plans/human-decision-gate-convergence-20260812.zh-CN.md`。

## Historical Active Step: Captcha-Auth Completion v5 / FAIL, CONSUMED

> Gate、preflight 与真实执行证据见
> `docs/testing/verification-gates/20260811-terminal-delivery-captcha-auth-completion-v5.md` 和
> `docs/testing/real-case-reports/20260811T131005Z/report.md`。本轮未进入 implementation，target
> baseline 保持 clean；不 retry、不 resume、不复用 session。

## Historical Active Step: Captcha-Auth Completion v4 / FAIL, CONSUMED

> captcha-auth 是尚未完成的真实业务任务，不是人工 prompt 样本。新 Gate 以产品
> 交付为唯一主目标：workflow COMPLETED、terminal acceptance、完整功能、PostgreSQL/
> Maven tests、README、change-scope、lineage 和 completion audit 必须同时通过。旧
> `ws-20260810T011244Z-8YVB` 不 resume；在 Gate checkpoint、fresh package、target baseline
> 和 Claude/Codex probes 全部通过后，启动一个 fresh production terminal-delivery
> workstream。typed-scope provider compliance 只是自然观测项，不是产品 PASS 前置条件。
>
> Gate：`docs/testing/verification-gates/20260810-terminal-delivery-captcha-auth-completion-v4.md`。
>
> 唯一 fresh workstream `ws-20260810T072433Z-J75J` 已运行。第一次 Codex review
> 后，Claude revision 正确增加了 obligation 要求的 exact typed scope entry，但同时
> 改写了未被 obligation 声明的 `AC-6`。revision-delta validator 因
> `UNDECLARED_CONTRACT_CLAUSE_CHANGE: AC-6` 在下一次 reviewer 调用前 fail closed。
> 统一分类为模型 revision 逐字符保真失败，不是 transport/parser/schema/scope
> enforcement 失败。本轮未进入实现，captcha-auth 产品仍 `NOT ACHIEVED`；不
> retry/resume，不自动创建第二 workstream。report：
> `docs/testing/real-case-reports/20260810T072051Z/report.md`。

## Historical Active Step: Captcha-Auth Full v3 / FAIL, CONSUMED

> 2026-08-10 在 clean Gate SHA `232250f2` 上执行了唯一 fresh
> captcha-auth full workstream `ws-20260810T011244Z-8YVB`。fresh package
> 9/9 modules、1199/0/0，双 provider probes PASS，persisted workflow 为
> `agent-lifecycle-terminal-delivery@1`（29 nodes / 45 edges / 9 nominal Agent
> nodes / no handoff）。
>
> 本轮结果为 `FAIL / CONSUMED / STOP-LOSS ENFORCED`：同一
> `intake.codex_product_review` 连续第二次 `NEEDS_REVISION`，按预定义 Gate 立即停止，
> 未进入 design/implementation。runner 已受控关闭第三次 Codex review：run=
> `OUTPUT_INCOMPLETE`、node=`FAILED`、workflow=`WAITING_FOR_RECOVERY`，无残留进程，
> target Git clean。该 interrupted run 是 stop-loss closure，不是 provider 自发失败。
>
> 两次 revision 的最大贡献项是 intake completeness 的串行发现。初始 proposal 遗漏显式
> UX 和 server-input validation，并加入未要求的实现约束；第一次 review 提出 5 项，但漏掉
> server-input validation。第一次 revision 又错误地以 `TR-3` 声称解决含 scope flexibility
> 的 `CO-5`，并把 loading-state 误收紧为 asynchronous transport；第二次 review 因而合理
> 拒绝并新增 3 项 obligation。第三次 Claude proposal 已机械、coverage、delta、lineage
> 全部通过并显式回应这 3 项，但未获得第三次 reviewer 终局结论，不能标 READY。
>
> 当前边界结论：presentation normalization、typed delta、lineage、stop-loss closure 已由本
> real-case 继续证明；terminal-delivery full workflow 与实际 9-call 收益仍未证明。captured
> completeness/serial-discovery 只读评估已完成，唯一候选为 product-review typed-scope
> semantics：第一次 review 的 `CO-5` 错把 authoritative exact `scope.included` 当成可由
> “illustrative/non-exhaustive” prose 放宽的 implementation inventory，第三版 `C-11` 也不会
> 被 change-scope enforcement 消费。统一失败分类为：主要运行来源是 Claude contract/revision
> 语义质量，额外 revision 成本包含 Codex 首轮 completeness 漏项，唯一已证明的 Harness 缺口
> 是 typed-scope semantics projection；scope enforcement 本身正确。该唯一
> prompt-semantics 候选已在既有 producer/reviewer prompt owner 中实施：
> `scope.included` 被明确投影为穷尽的 exact path+operation authorization，prose 不得
> 扩大授权，scope gap 必须直接修改 typed entries。定向 prompt tests 41/0，
> 当前成熟度仅为 `PROJECTED / DETERMINISTIC PASS`；provider compliance、reviewer
> completeness 与 terminal-delivery full workflow 仍为 `NOT PROVEN`。不得 retry/resume 本
> workstream，不自动创建第二样本。
> Commit `07a11db3` 的独立代码级 diff review 已 `PASS`（0 P0、0 P1），
> typed-scope prompt-semantics checkpoint 正式关闭。两项非阻塞 P2 不改变该结论，
> 不再为方法命名或投影次数增加代码。
>
> Gate：`docs/testing/verification-gates/20260810-terminal-delivery-captcha-auth-full-v3.md`；
> report：`docs/testing/real-case-reports/20260810T010901Z/report.md`。
> assessment：`docs/plans/post-captcha-full-v3-intake-serial-discovery-assessment-20260810.zh-CN.md`。

## Historical Active Step: Revision Verbatim Probe v2 / FAIL, CONSUMED

> 2026-08-07 已在 clean SHA `46ba2241` 上完成 provider probes，并创建唯一
> captcha-auth production workstream `ws-20260807T134039Z-R47H`。运行使用
> `agent-lifecycle-terminal-delivery@1`（29 nodes / 45 edges / 9 nominal Agent
> nodes），但在第一次 intake semantic revision 被 strict revision-delta validator
> 阻断，workflow 停于 `WAITING_FOR_RECOVERY`，没有进入 implementation。
>
> 自然任务选定为用户已给出的 captcha-auth Java 新项目：手机号或邮箱
> 注册/登录，注册使用 SMS/email OTP，登录前使用图形 Captcha。该任务在
> final human acceptance 后没有已声明的下一阶段，因此 handoff/A10 的
> `NOT_APPLICABLE` 是业务语义，而非为减少调用而人工构造。
>
> 本次唯一新样本结果为 `FAIL / SAMPLE CONSUMED`：3 次 Agent execution、3 次
> provider occurrence、1 次 semantic revision、0 次 bounded correction。初始 Claude
> proposal 和 Codex `NEEDS_REVISION` 均成功；修订 Claude 输出 strict-valid envelope，
> 但修改了大量未声明条款、增加 5 个未声明条款并删除 `R-7`。系统以
> `TASK_CONTRACT_REVISION_DELTA_INVALID` 在新 proposal ref 和第二次 Codex review
> 之前 fail closed。中途 `AGENT_RECONNECT_REQUIRED` 已标记 `superseded=true`，不是
> authoritative failure owner。
>
> 只读归因与 CC 同行评审确认一个 Dual-owned P1 projection gap：修订 prompt 投影了 verified
> previous proposal 的 ref/hash 和完整 reviewer obligations，但
> `taskContractProposal=null`，没有投影上一版 clause body；同一 prompt 却要求逐条
> 保留未点名 clauses。因此 immediate behavior 是模型重写未授权条款，validator 正确
> 阻断；但 Dual 不能把逐字 delta 的正确性依赖于同一 session 的隐式记忆。
>
> 最小修复已实现并通过 CC 独立代码级审查：`TaskContractProposalReference` 继续唯一验证 baseline
> identity/hash/lineage，`TypedContextPayloadBoundary` 继续唯一序列化 trusted proposal，
> `AgentPromptRenderer` 只在 validated intake `NEEDS_REVISION` evidence 上附加 exact
> baseline；baseline 无法验证时在 provider dispatch 前 fail closed。未修改 schema、
> validator、workflow、retry 或 correction。Focused tests `53/0`，full reactor
> `1194/0/0`，CC verdict 为 `PASS`，无 P0/P1；因此该 projection boundary 达到
> deterministic `ENFORCED`。不得 retry/resume R47H，也不得创建第二个 real-case。
>
> 该结论不覆盖 human gate `revise -> intake.claude_brainstorm`：该既有路径不产生
> `intake.codex_product_review / NEEDS_REVISION` evidence，故不触发本次 baseline guard。
> 它作为独立后续边界保留，不扩大当前 checkpoint。模型看到 baseline 后能否稳定产生
> 合法 delta 仍需自然 revision 样本证明，当前不得标记为 `PROVEN`。
>
> Gate 为
> `docs/testing/verification-gates/20260807-terminal-delivery-captcha-auth-production.md`。
> 本次报告为
> `docs/testing/real-case-reports/20260807T135202Z/report.md`。该 Gate 不证明
> production terminal delivery、自然任务 `10 -> 9`、PASS B 或模型稳定性。
>
> 用户确认 captcha-auth 仍是未完成的真实开发任务，因此不等待另一项自然任务。新 Gate
> `docs/testing/verification-gates/20260807-terminal-delivery-captcha-auth-completion-v2.md`
> 使用 fresh workstream 在 `08417221` 后重新执行同一业务需求；旧 `R47H` 只作为历史失败
> 证据，不 retry/resume。顺序固定为 clean Gate checkpoint -> fresh package -> deterministic
> preflight -> 双 provider probes -> 一次完整 real-case。若 preflight/probes PASS，可直接运行，
> 不再请求第二次授权。
>
> v2 workstream `ws-20260807T150938Z-2D5Y` 已运行并按 stop-loss 结束。Fresh package
> 9/9 modules、`1194/0/0`，双 probes PASS，workflow identity 正确。Initial intake 与 Codex
> review 成功，revision prompt 真实包含 exact verified baseline，因此 `08417221` 的
> projection boundary 已由 real-case `PROVEN`。
>
> 失败 owner 不再是 baseline 缺失：prompt 明确禁止 tool 并要求 exactly one JSON object，
> configured Claude backend `zhipu/glm-5.2` 仍发起 Terminal，得到
> `PERMISSION_BLOCKED / exit 5`，随后输出前导 prose 和 malformed JSON。Dual 以
> `SKIPPED_PROCESS_NON_ZERO` 禁止 correction 恢复并 fail closed。该样本不得 retry/resume；
> captcha-auth 仍未进入 implementation。
>
> 运行另暴露一个独立 harness 缺口：initial `start --run` failure 没有自动执行与 autorun
> failure 相同的 session reap。本次已显式关闭 2/2 sessions 且无残留进程；后续只允许先做
> 该 closure 对称性的最小 deterministic 修复，再决定是否使用更符合 strict JSON/tool
> discipline 的 Claude backend 完成同一业务任务。不得通过放宽 parser、permission 或
> non-zero correction policy 迁就本次输出。
>
> `5580926` 已补齐 initial `start --run` failure 与 autorun/interruption 的 session-reap
> 对称性，deterministic script gate PASS。下一步只执行
> `docs/testing/verification-gates/20260809-captcha-revision-current-backend-probe.md`：使用当前
> Claude Code 新 session 配置精确重放 captured revision prompt，不判断模型名称。只有 exit 0、
> 无工具请求、strict parser、coverage 和 delta 全 PASS，才允许新的完整 captcha-auth
> workstream。
>
> 该 probe 已在 clean SHA `f1bdf05` 上执行一次且仅一次。ACP exit 0、`end_turn`、无 tool /
> permission request，证明 execution permission 边界通过；但 production
> `AcpxSessionStreamProjector` 从 captured stream 提取的唯一最终 candidate 仍带 Markdown
> `json` fence。`AgentResultEnvelopeParser` 以
> `AGENT_RESULT_PARSE_FAILED / INVALID_JSON` 拒绝。Gate 因 strict JSON 条件失败而
> `FAIL / CONSUMED`，未 correction、retry、resume，也未启动新的 captcha-auth full
> workstream。probe session 已关闭且无残留进程。
>
> 后续纯离线 semantic replay 精确移除首尾 fence，并复用 production parser、envelope
> validator、proposal validator、真实 revision context resolver、obligation coverage 和
> revision-delta validator。前五项通过，但 delta 仍拒绝未声明修改 `AC-12`、`TR-16`：两项
> 都只是删除 baseline 中 `mvn test` 外层 Markdown backticks。故本次不能定性为单一格式
> 偏差，也不能只把 bounded syntax correction 加入下一 Gate。full captcha-auth 继续冻结；
> 下一步先收敛 full-proposal revision 的逐字保真协议，不放宽 parser、不做文本归一化、不忽略
> undeclared delta。
>
> 收敛已落在唯一 `AgentResultPromptContract` owner：revision 输出现在明确要求未声明 clause 的
> `id`/`text` 从 verified previous proposal 逐字符复制，包括空格、标点、大小写和 Markdown
> backticks；formatting normalization 明确定义为 modification，只有 clause ID 出现在
> `resolved_by_clause_ids` 时才允许。既有 strict parser 和 revision-delta validator 未修改。
> core/ledger/runtime deterministic tests `742/0/0`，`git diff --check` clean。当前成熟度为：
> prompt requirement=`PROJECTED`，非法 delta rejection=`ENFORCED`，新措辞的 provider
> compliance=`NOT PROVEN`。
>
> 下一次 backend eligibility Gate 可以允许同一 session 最多一次既有 bounded syntax
> correction，但不得把 correction 当作 semantic/delta fallback；最终 candidate 必须通过
> strict parser、mechanical、obligation coverage 和 revision delta，且两次 occurrence 的
> 调用、token、耗时全部计入。Gate 未定稿前不得调用 provider；captcha-auth full workstream
> 继续冻结。
>
> v2 Gate 已定稿为
> `docs/testing/verification-gates/20260809-captcha-revision-verbatim-probe-v2.md`。production
> renderer 生成的 captured prompt 为 34,870 bytes，SHA-256
> `f3d686fa84a1fcfe14a7b97b6489ac7900da3d90a0dc94172cd013818261329f`；只含一个 Output
> Contract，并包含 verified baseline 与 character-for-character instruction。Gate checkpoint
> 提交前状态仍为 `READY / NOT RUN`，不得提前调用 provider。
>
> v2 Probe 已在 Gate SHA `c145c00` 上执行并消耗。production acquirer 运行 initial 和唯一
> bounded syntax correction：两次均 exit 0 / end_turn、无 tool 或 permission request，但两次
> 最终 assistant candidate 均带 Markdown `json` fence；correction 后 strict parser 仍以
> `AGENT_RESULT_PARSE_FAILED / INVALID_JSON` 拒绝。两次合计 40,953 total tokens、159,416 ms，
> 无第三次 occurrence。
>
> 诊断性去掉 correction candidate 的单一首尾 fence 后，envelope、mechanical、verified
> CO-1/CO-2 coverage 和 revision delta 全部 PASS，`AC-12/TR-16` 不再漂移。因此 `bc08027`
> 的字符级保真修复已由该样本证明有效；剩余失败严格收敛为 assistant presentation format
> 与 strict JSON boundary 不兼容。该诊断不进入 production acceptance，Gate 仍为
> `FAIL / CONSUMED`。session 已关闭，无残留进程；fresh captcha-auth full workstream 未启动。

## Completed Step: Revision Delta Integrity PASS A / PROVEN

> `6fb51608` 将 schema-v3 revision 的 declared clause delta 收敛为 typed
> validator boundary。受控 PHZX continuation 产生一次 strict-valid Claude
> occurrence，但包含 undeclared clause changes 和 prohibited deletions；系统以
> `TASK_CONTRACT_REVISION_DELTA_INVALID` 在 proposal reference 发布和 Codex review
> dispatch 之前阻断。无新 proposal ref、review context 或 Codex occurrence。
>
> 独立同行评审为 PASS，结果 checkpoint 为 `41e0ea70`。成熟度严格区分：
> invalid revision early rejection=`PROVEN`；valid revision-to-review=`ENFORCED /
> NOT PROVEN`；full workflow/model quality 未由该 Gate 证明。不为 PASS B 人为创建新样本。

## Completed Step: A10 Same-Case Gate / PASS, PROVEN

> 2026-08-05 已确认下一目标：对 full workflow happy path 的 10 次固定 Agent 调用
> 逐一完成成本、owner、consumer、质量增量和 counterfactual 价值举证。最新模型增强
> 了长程规划、自检与多阶段执行能力，但没有证据证明 strict contract、independent
> review、ledger、recovery 或 human responsibility 已失去价值；也没有证据证明十次
> 固定调用对所有任务规模都必须启用。
>
> 单一计划为
> `docs/plans/fixed-agent-call-value-validation-20260805.zh-CN.md`。只读 baseline 必须
> 同时覆盖 A1-A10，不得发现第一个候选后停止。可定制 workflow 只是后续对一个候选
> 做同 case 对照的实验手段，不是当前主目标。
>
> 只读 baseline 已完成 A1-A10 的成本、owner、consumer、artifact delta 和
> counterfactual 裁决。A1/A2 与 A5/A6 在中等复杂 T6 中的 revision 收益已证明；
> A7/A8/A9 是 governed full workflow 的必要核心；A4 证据不足，不得修改。
>
> A10 terminal-delivery deterministic experiment 已通过：该 definition
> 将 full 的 32 nodes / 49 edges / 10 Agent calls 缩为 29 / 45 / 9，
> 仅删除 acceptance approval 之后的 handoff/next-stage 尾部。A1-A9 和其他边逐对象
> 相等，full definition 未修改。
>
> completion audit 已改为消费 WorkflowDefinition 判定 handoff/next-stage 适用性：
> terminal profile 缺失它们时由 definition 推导为 `NOT_APPLICABLE`，不产生 missing
> gap；声明它们的 full-like definition 缺失时仍 `needs_review`。查询侧 fallback 与
> 固定 required-artifact 重推断已经删除，损坏 audit fail visible。focused 58/0，
> full Maven reactor 9/9 PASS。
>
> 当前成熟度为 `ROUTING DETERMINISTIC ENFORCED / AUDIT APPLICABILITY ENFORCED +
> CANNOT-BYPASS / ONE A3 SAME-CASE PROVEN`。deterministic checkpoint 已提交为
> `e25cd9f4`；same-case Gate 已定义在
> `docs/testing/verification-gates/20260805-a10-terminal-delivery-a3.md`。
>
> Gate 已使用同一 A3 task 与 target tree 运行一次：workstream
> `ws-20260805T084458Z-54Y3` 达到 `COMPLETED / ACCEPT`，实际 Agent runs 与 provider
> occurrences 均从 10 降为 9，revision=0、correction=0，change scope PASS，独立
> `npm test` 11/11 PASS，completion audit=`ready / gaps=[]`，且未生成 handoff/next-stage
> 工件。原始 harness 报告错误地用固定文件清单把两项 `NOT_APPLICABLE` 工件重算为
> missing，暴露 report projection owner 漂移；原报告保留为历史证据。
>
> `missing_artifacts()` 现只消费 persisted completion audit 的 status/gaps，不再按固定
> 文件列表重推 applicability；audit 缺失、非法或非 ready 均 fail closed。脚本
> cannot-bypass PASS，并对同一 captured workstream 做 deterministic replay：保持
> 9 runs、`ACCEPT` 和 handoff/prompt absent，同时得到 `Benchmark Grade: PASS`、
> `Verification Confidence: HIGH`。未再次调用 provider，也未运行第二个 A3 样本。
>
> 在 same-case proof 后，`terminal-delivery` 已提升为正式可选 bundled variant：唯一
> definition 位于 main resources，`WorkflowDefinitionLoader`、CLI start/runner-kind 和
> real-case harness 均接受显式 `terminal-delivery`，Operator 新任务命令生成器也投影同一
> CLI 参数；`full` 继续作为默认值。没有增加自动
> profile 选择、防误选规则、registry、engine 或 DSL。正式 definition 与已验证 experiment
> 除稳定 workflow id 外保持同一 graph delta；contract、handler、permission、runner-kind
> consistency sweep 覆盖三个 variants。full reactor `1161/0/0`，production wiring 达到
> `ENFORCED`；同 graph 的 A3 same-case evidence 保持 `PROVEN`。

## Completed Step: Post-T6 Efficiency Attribution / COMPLETE

> 当前 production baseline 为 `72ae925a`。最新完整 T6 workstream
> `ws-20260804T092758Z-73XJ` 已达到 `COMPLETED / ACCEPT_WITH_NOTES`：14 个 Agent
> runs、2 次 semantic revision、6 个 gate rounds，全部 execution outcome 一致，
> change-scope PASS，最终产物与 delivery artifacts 完整。报告：
> `docs/testing/real-case-reports/20260804T092743Z/report.md`。
>
> 该运行同时暴露 `ACCEPT_WITH_NOTES` 被错误送入 failure classifier、生成
> `NOTED_IMPLEMENTATION_BUG` 的语义矛盾。checkpoint `72ae925a` 已删除该旁路：full
> 与 contract variant 的 accepted-with-notes 均直接进入 final report，classifier
> 只接受 `REJECT_*`，错误直调 fail closed。Full reactor `1182/0/0`。这条边界当前
> 为 `ENFORCED`；最新 T6 运行早于该提交，因此当前精确组合 SHA 尚未再次做 provider
> real-case，不据此重复消耗样本。
>
> 当前优化结论必须保持分层：可靠性主链与已发现 owner 冲突基本收敛；T4/T5 已在
> 同一 A3 样本证明 context estimated tokens 下降 10.18%、provider-reported total
> tokens 下降 7.64%、Agent duration 下降 13.88%；但 full workflow 仍有 32 个
> definition nodes、10 个 Agent nodes、6 个 context nodes、6 个 human gates。删除
> `NOTED_*` 后 edge 数为 49。最新中等复杂度 T6 仍消耗 14 次 Agent 调用和 2 次
> revision，因此总体简化与效率优化尚未完成。
>
> 只读归因已完成，见
> `docs/plans/post-t6-v9-revision-value-attribution-20260804.zh-CN.md`。两次 revision
> 额外产生 4 个 Agent runs、402,846 ms Agent duration 和 335,843
> provider-reported total tokens。唯一按成本选中的候选是 plan revision pair，但它
> 发现并阻止了 `cycle_number NULLS LAST`、受限响应状态和 invalid-response 分支三个
> 会进入实现的真实偏差；intake revision 同样补齐最终实现实际依赖的排序和权威
> cycleId 义务。因此裁决为 `READ-ONLY PASS / NO_SAFE_SUBTRACTION`，不修改
> production，也不转选第二候选凑进度。
>
> 后续没有 active production optimization。新的 subtraction 只有在另一份真实
> ledger 证明同一固定节点或投影持续无独特产出时才能重新激活。禁止新增 phase、
> module、engine、registry、graph、DSL、state machine、event store 或 metrics store；
> 禁止自动启动新 real-case。

## Historical Step: TaskContractProposal v3 Revision Chain / SAMPLE CONSUMED

> TaskContractProposal v3 checkpoint `83b01da` 的真实
> `NEEDS_REVISION -> revision -> READY -> compile` 路径已由 workstream
> `ws-20260804T051509Z-CUVU` 观察并保留为历史证据。该样本后续因 operator stop-loss
> 误判而未形成完整 workflow PASS；其历史 gate 与结论不得作为当前 active objective。

## Superseded Active Step: T6 Full Lifecycle v8 / GATE CONSUMED

> v6 已以 `FAIL / STOP-LOSS ENFORCED` 收口，历史结果和已消费授权保留在
> `docs/testing/verification-gates/20260803-t6-typed-eof-correction-v6.md`，不得
> retry、resume 或重新解释为 EOF correction 失败。
>
> A（typed contract projection preservation）已提交为 `7f75509b`：verified
> `TaskContractProposalReference` 是唯一 trust 来源，typed proposal 的生产与消费
> 共用 `TypedContextPayloadBoundary.wrapJson`，合法 route-like literal 逐字投影，
> untyped workspace 外路径继续清洗。C（stop-loss quiescent closure）已提交为
> `331df2f7`：runner 中断时先终止并等待 autorun Java 进程，再通过统一
> `StaleRunningRecovery` 终止 durable provider tree、reconcile run 并进入
> `WAITING_FOR_RECOVERY`；失败时 fail closed。
>
> 后续 checkpoint `39ef0299` 收敛两条 stop-loss 边界：acpx exit `5` 由 transport
> 唯一映射为 typed `PERMISSION_BLOCKED`，且 explicit `sessions-reap --workstream`
> 只允许终态或 `WAITING_FOR_RECOVERY` 的已停止 workstream，不得关闭 `RUNNING` /
> `WAITING_FOR_HUMAN` 会话。完整 reactor、真实 process-tree tests 与
> `run-real-case.test.sh` 均 PASS。A/C/权限分支当前均为 `ENFORCED`，未经新 T6
> real-case 不得标 `PROVEN`。
>
> 唯一有效的新 gate 为
> `docs/testing/verification-gates/20260804-t6-full-lifecycle-v8.md`。
> 当前只允许提交 gate、获得 clean SHA、执行 deterministic/target/backend/
> frontend/PostgreSQL preflight，并执行 Claude/Codex probes。用户已授权：所有前置
> PASS 后直接运行一次且仅一次 v8 样本；任一前置失败则不得创建 workstream。
>
> v8 主目标是完整 workflow 达到 `COMPLETED + ACCEPT`（或非退化
> `ACCEPT_WITH_NOTES`）。A 是本样本必须触发并证明的子目标；权限拒绝与 C session
> quiescence 仅在真实触发时判为 `PROVEN`，否则记 `NOT_TRIGGERED`，不得为了证明它们
> 人为破坏可完成样本。EOF correction 同样只按实际触发记 `PROVEN/NOT_TRIGGERED`。
> 禁止放宽 parser/review、修改 prompt/model、retry/resume 或运行第二样本。

### Historical Result: T6 Interruption Ledger Ownership v5 / FAIL

> ACP session 与 recovery projection ownership convergence 已作为单一 checkpoint
> 提交为 `dc08cf2e`。Full reactor `1138/0`、独立代码审查与 deterministic
> cannot-bypass 均为 PASS，当前成熟度为
> `ENFORCED + DETERMINISTICALLY PROVEN / T6 REAL-CASE PROOF PENDING`。
>
> 新的一次性 T6 授权已经消耗。唯一有效 gate 为
> `docs/testing/verification-gates/20260802-t6-session-projection-ownership-v4.md`。
> target/backend/frontend/database、fresh Dual package 与 Claude/Codex probes
> 全部通过，workstream `ws-20260802T101230Z-DLRW` 已运行。前四个完成的 Agent
> ledger-completed run 均为零退出、typed outcome 一致、无 active bootstrap failure、无
> outcome-level `retrySafe`，且 Claude/GLM-5.2 与 Codex/OpenAI provider identity
> 未串线；这些正常完成路径的 ownership 投影成立。
>
> 但 `intake.codex_product_review` 第二次返回 `NEEDS_REVISION`，超过本 gate
> “每 stage 最多一次 semantic revision”的 stop-loss。重启后从 acpx 持久化记录
> `84d2f90c-1315-4fa6-8f53-cc8838185b50` 恢复出的证据进一步证明：第三轮 Claude
> 实际已收到 prompt 并生成 assistant response，但对应 `run.json` 仍停在
> `prepared`，没有 process/usage/outcome。这是 interruption window 中的
> execution-fact ownership 缺口。完整 workflow 未到 `COMPLETED/ACCEPT`，且
> ownership 不变量被绕过，故 v4 结论为 `FAIL / STOP-LOSS ENFORCED`，不能把
> checkpoint 整体升级为 `PROVEN`。报告：
> `docs/testing/real-case-reports/20260802T100848Z/report.md`。不授权 retry、resume
> 或第二个 T6 样本；下一步必须同时处理 ledger interruption ownership 缺口，并
> 将模型保真比较保持为独立 qualification，不得修改 prompt/parser 来追求通过。
>
> 该 interruption ownership 缺口已由 checkpoint `0c81fbc8` 修复：typed ACP
> lifecycle 在运行中持久化，in-flight run 从 `prepared` 推进到 `running`，异常和
> resume 路径通过同一 reconciler/finalizer 闭合；reconciler 不再伪造 occurrence、
> candidate、usage 或 correction。Full reactor `1145/0`，独立同行复审 PASS。
> 用户已明确授权一次且仅一次新的 T6。唯一有效 gate 为
> `docs/testing/verification-gates/20260802-t6-interruption-ledger-ownership-v5.md`。
> 必须先提交 gate、获得 clean SHA，并完成 target/backend/frontend/database、fresh
> package 与 Claude/Codex probes；全部 PASS 后才能创建 workstream。不得 retry、
> resume、修改 prompt/parser/model 或运行第二个样本。
>
> v5 一次性授权已消耗。运行使用 clean Dual SHA `c2340654`、target SHA
> `ed82f578`，workstream 为 `ws-20260802T152626Z-QN2R`，报告为
> `docs/testing/real-case-reports/20260802T152244Z/report.md`。Fresh build、
> backend `191/191`、miniprogram build、PostgreSQL 与 provider probe 均通过。
> 七个 Agent run 的 execution facts 均闭合，没有 run 留在 `prepared`，也没有
> reconciler 伪造 occurrence/usage/candidate/correction；因此本次观察到的
> execution-ledger ownership 路径成立。
>
> 但 `plan.claude_plan` 的首次输出与唯一一次 bounded correction 均为 exit 0
> 且缺少根对象最后一个 `}`，strict parser 正确返回
> `AGENT_RESULT_PARSE_FAILED`，workflow 停在 `WAITING_FOR_RECOVERY`。按 v5 gate，
> failed correction 直接判定 `FAIL / STOP-LOSS ENFORCED`；没有 retry、resume 或
> 第二样本。实际 Claude provider metadata 仍为 `zhipu / glm-5.2`，并非 Claude
> Code UI 当前默认模型。checkpoint 保持
> `ENFORCED + DETERMINISTICALLY PROVEN / FULL-WORKFLOW PROOF FAILED`。

### Superseded Active Step: P0 ACP Execution Fact Ownership Convergence / ENFORCED

> T6 stop-loss 暴露的 `SUCCESS + SKIPPED_PROVIDER_FAILURE` 矛盾已确认不是局部
> tail bug，而是 ACP execution control facts 的 architecture ownership failure。
> 当前唯一 active objective 是：按
> `docs/plans/__CHECKPOINT__acpx-execution-fact-ownership-convergence.zh-CN.md`
> 完成修复性设计同行评审。首次 CC review 为 `WARN`；W1-W7 已写入 checkpoint，
> CC re-review 已确认 W1-W7 全部闭合并给出 `PASS`。单一 convergence
> change-set 已完成 deterministic implementation：关键矩阵 `122/0`、source
> architecture gate `3/0`、full reactor `1130/0`（9/9 modules）。此前独立架构
> review 为 `PASS`；最新 CC final-diff 复审确认 F1 已闭合并给出 `PASS`。当前成熟度为
> `ENFORCED / NOT YET PROVEN`；T6 继续冻结，只有 clean checkpoint 与新的
> Verification Gate 获得显式授权后，才允许用一次 T6 real-case 升级为 `PROVEN`。
>
> 本轮不再拆分新的 phase 或小 slice。CC review PASS 后，整个 ownership migration、
> 旧路径删除、deterministic matrix、cannot-bypass、full reactor 与独立架构复核必须
> 作为一个 checkpoint 整体完成。T6 在 clean implementation checkpoint 和新 gate
> 获得显式授权前继续冻结。

> Convergence checkpoint 已提交为 `909317a2`，T6 v3 gate-only clean SHA 为
> `d9426990`。v3 一次性授权已消耗：报告
> `docs/testing/real-case-reports/20260802T001704Z/report.md`，workstream
> `ws-20260802T002001Z-36TY`。Fresh build `1130/0`、target backend
> `191/191`、miniprogram build、Claude/Codex minimal probes 与 artifact freshness
> 均通过，因此本次不是 preflight `INVALID`。
>
> 运行在第二个 Agent run `intake.codex_product_review` 停止：acpx 在 ACP session
> 建立前报告 named Codex session 不存在，进程 117 ms 内以 exit `4` 退出，0 token。
> ownership 链一致记录 `EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT`、
> `SKIPPED_PROCESS_NON_ZERO`、`OUTPUT_INCOMPLETE` 并进入
> `WAITING_FOR_RECOVERY`，没有再次出现 success/failure contradiction；但
> `AcpxAgentTransport.runCodexDesignReview()` 的 lazy bootstrap 只消费 typed
> `SESSION_RESUME_FAILED`，无法处理 ACP wire 建立前的 session-missing CLI failure。
> 因此 v3=`FAIL / STOP-LOSS ENFORCED / NO RETRY AUTHORIZED`，checkpoint 仍为
> `ENFORCED / NOT YET PROVEN`。下一步只允许先收敛 transport session bootstrap
> ownership；禁止恢复 post-hoc log classifier、修改 prompt/parser 或直接重跑 T6。

> Phase A-D 已关闭，不再继续建模或拆 handler。
>
> T1 已在 clean SHA `0e0d77ee` 上通过 A3 full real-case：
> `COMPLETED / ACCEPT`、10 Agent runs、0 revisions、0 protocol corrections、
> change scope PASS、target tests 9/9 PASS。
>
> T2 已从该运行的 `runs/*/run.json` 与
> `metrics/workstream-metrics.json` 提取只读效率 baseline。调用、token、cache、
> context 与 latency baseline 已成立；whole-run monetary cost 因 OpenAI cost
> reason 缺失及 GLM `subscription_token_only` 的金额可靠性未闭合而保持
> `NOT_PROVEN`。
>
> 2026-07-29 优先级决策：金额计算延后。T2 的调用、token、cache 与 latency
> attribution 已足以支持 T3 只读瓶颈选择；monetary cost 继续保持
> `DEFERRED / NOT_PROVEN`，不得用于成本节省或 ROI 声明。
>
> T3 已完成只读价值审查，唯一候选是从 full workflow 的 6 个 active context
> profile 中删除重复的 `requirement` section，保留 `AgentPromptRenderer` 对
> authoritative `requirement.md` 的唯一注入。self-test、stage handoff 和 6 个
> context-build 节点本身均保留。
>
> T4 已按显式授权完成唯一 subtraction：6 个 active context profile 不再重复投影
> `requirement.md`；`AgentPromptRenderer` 继续作为 full-workflow 唯一 requirement
> 注入 owner。显式 `full` profile 与自定义 section 仍保留 requirement 能力。
>
> Focused tests、sibling integration tests 与 9/9 module full reactor 已通过，
> T4 deterministic boundary 保持 `PASS`。
>
> T5 已使用同一 A3 case 启动一次 full real-case。fresh build、runner kind 以及
> Claude Code/GLM-5.2、Codex/OpenAI 最小 probe 均通过；运行在
> `design.claude_design` 首次出现 protocol correction。Strict parser 正确拒绝
> “自然语言前缀 + JSON”，bounded correction 成功，但 T5 Verification Gate
> 明确要求 correction=0，因此按 stop-loss 中断。T5=`FAIL`，不是 provider
> `INVALID`；T4 的真实 token、cache 与 latency 收益仍为 `NOT PROVEN`。
> T6 保持 `BLOCKED`。
>
> 随后的只读 gate 复核确认 bounded correction 是已有的、最多一次的 same-session
> 可靠性机制，不应被等同为功能质量失败。修订 gate 允许最多一次 correction，
> 但要求额外 prompt occurrence 的 token、cache 与 duration 全部计入；primary
> benefit metric 是六个 generated context package 的 estimated tokens。修订 gate
> 已通过结构校验，并在明确授权后执行一次。
>
> Attempt 2 的 fresh build 与 probes 均通过，前两个 Agent node correction=0；
> 但 Codex 发现 Claude proposal 把“非对象”擅自扩成“数组也必须拒绝”，返回 typed
> `NEEDS_REVISION`。ledger 记录 `revisionCount=1` 后，v2 stop-loss 中断运行。
> 该 revision 有真实 contract evidence，但与 T4 没有已证明的因果关系。两次 T5
> 均未形成可比较的完整样本，因此 T4 实际 token/cache/latency benefit 仍为
> `NOT PROVEN`，禁止第三次自动重跑，T6 继续 `BLOCKED`。
>
> Attempt 2 暴露的 interruption-report projection gap 已由 `f04cdedb` 修复。
> 中断路径现在先复用既有 `parse_started_workstream()` 恢复 captured start
> identity，再收集 ledger evidence 和生成 report；synthetic interruption test
> 证明不再错误投影 `workstream=unknown / 0 runs`。
>
> T5 v3 已在 clean SHA `1b235288` 上按一次性授权完成。报告：
> `docs/testing/real-case-reports/20260729T131141Z/report.md`；workstream：
> `ws-20260729T131421Z-BX54`。结果为 `COMPLETED / ACCEPT`、10 Agent runs、
> 0 revisions、0 protocol corrections、change scope PASS、独立 `npm test`
> 9/9 PASS。10/10 strict result 与 usage 均有效并完成对账。
>
> 六个 active context package 均不含 requirement，10 个最终 Agent prompt
> manifest 均只含一个 authoritative requirement section。Estimated context
> 从 T1 的 `17,348` 降至 `15,582`，下降 `1,766`（10.18%）；全部
> provider-reported total tokens 从 `577,528` 降至 `533,404`，下降 `44,124`
> （7.64%）；Agent duration 从 `462.467s` 降至 `398.284s`，下降 13.88%。
> 因此 T5 v3=`PASS`，T4 的同一 A3 observed benefit 已证明。
>
> 该结论只覆盖这一次同 baseline A3 样本，不外推为跨任务、跨时段或跨 provider
> 的普遍因果收益；金额继续 `DEFERRED / NOT_PROVEN`。一次性授权已消耗，禁止
> 自动重跑。T6 只解锁只读 readiness assessment，尚未激活中等复杂度 real-case。
>
> T6 readiness assessment 已选择 pinned cancer-clinic medium case
> `ed82f57`。Detached baseline clean，7 文件 change boundary 已固定；ephemeral
> Python 3.13.1 环境、独立 `cancer_clinic_test` PostgreSQL、full backend pytest
> `191 passed` 与 miniprogram build 均已通过，验证后 target 仍 clean。
>
> Target `backend/requirements.txt` 漏声明 FastAPI `File/Form` 所需的
> `python-multipart`。本次只在临时环境显式固定为 `0.0.32`，没有修改 target，
> 也不允许隐式 fallback。T6 在 provider 运行前的 readiness 状态是
> `READY_WITH_EXPLICIT_SUPPLEMENTAL_PIN / REAL-CASE NOT ACTIVE`；必须先固化
> executable gate、fresh build、probes，并重新获得一次 provider 授权。
>
> T6 assessment：
> `docs/plans/post-phase-d-t6-medium-case-readiness-20260729.zh-CN.md`。
>
> T6 已在一次性授权下执行。报告：
> `docs/testing/real-case-reports/20260730T000136Z/report.md`；workstream：
> `ws-20260730T000417Z-5PXU`。Fresh build、191-test backend baseline、
> miniprogram build、Claude/Codex probes、runner 与 usage reconciliation 均通过，
> 因此结果是有效 `FAIL`，不是 provider `INVALID`。
>
> 运行在 7 个 Agent runs、12 分 03 秒时停止。intake 与 design review 各产生一次
> 有 evidence 的 semantic revision，`revisionCount=2` 触发当时 gate 的
> stop-loss。第一次 design 在该 Agent run 内执行一次 bounded correction。第二次
> design revision 遭 Terminal permission denial，ACP 进程非零退出，因此按既有
> fail-closed 规则跳过 correction；这不是跨 Agent run 的 correction allowance
> 被耗尽。workflow 最终为 `WAITING_FOR_RECOVERY`，尚未进入 implementation；
> target 与 Agent worktree 均 clean。
>
> T6 当前状态：
> `REAL-CASE FAIL / STOP-LOSS ENFORCED / NO RETRY AUTHORIZED`。禁止自动重跑，
> 禁止未经独立只读归因就修改 prompt、parser、workflow 或 production。
> 下一版 gate：
> `docs/testing/verification-gates/20260730-t6-medium-case-v2.md`，状态为
> `READY_AFTER_CHECKPOINT / NOT AUTHORIZED`。
>
> 当前架构：
> `docs/design/dual-orchestrator-current-architecture.zh-CN.md`。
>
> 后续优化计划与任务门：
> `docs/plans/post-phase-d-integrated-verification-and-efficiency-plan.zh-CN.md`。
>
> T2 baseline：
> `docs/plans/post-phase-d-t2-efficiency-baseline-20260728.zh-CN.md`。
>
> T3 value review：
> `docs/plans/post-phase-d-t3-bottleneck-selection-20260729.zh-CN.md`。
>
> T4 deterministic closure：
> `docs/plans/post-phase-d-t4-context-requirement-subtraction-20260729.zh-CN.md`。
>
> T5 comparison：
> `docs/plans/post-phase-d-t5-a3-comparison-20260729.zh-CN.md`。
>
> Full workflow 的节点、Agent 调用、gate、context 与 revision 价值证明：
> `docs/plans/full-workflow-complexity-value-proof.zh-CN.md`。
>
> 禁止新增 workflow/policy/context/artifact/evidence/capability/metrics 平台；
> 禁止因 handler LOC 较大继续拆分；禁止自动重跑 T5、调
> prompt/model/timeout，或自动重跑 T6；禁止把 deferred monetary cost 或单次 A3
> observed benefit 外推为普遍收益。

> B0 Rebase Assessment: **COMMITTED** (`86af5ddb`)。
> B1 Subtraction: **COMMITTED** (`3f2ee81f`)。
> Strict change-scope paths: **COMMITTED** (`f2410e97`)。
>
> **B1 subtraction checkpoint 已完成；B4 删除收敛的 deterministic 验收已通过。**
> - handler、validator 与 metadata writer 不再把 path-level read policy 映射为
>   node failure、contract violation、recovery 或 workflow status。
> - `ExplorationPlan`、`ExplorationPolicyEvaluator`、`ExplorationReadKindPolicy`
>   及其 production projection/protocol 已删除，不再保留
>   `explorationPolicyResult` 或 `scope_expansion_request` 兼容字段。
> - production sibling sweep 对 `readScope`、`allowedDirectReadFiles`、
>   `ExplorationPlan`、`ExplorationPolicy`、`scope_expansion_request`、
>   `READ_OUTSIDE_EXPLORATION_SCOPE` 与 `DIRECT_READ_BUDGET_EXCEEDED` 为 0。
> - recommendation、actual read/write/tool-usage telemetry 与 read-reuse guidance
>   保留；重复读取只进入 metrics，不拥有 node failure、contract violation 或 recovery
>   权力。
>
> **B2 small slice = ENFORCED + PROVEN，尚未提交。**
> - `TaskContract.scope` 声明 `CREATE` / `MODIFY` 变更意图。
> - workspace `baseCommit` 到当前 worktree 的 Git `--name-status -z` 产生 typed
>   actual changes；NUL framing 保留 Unicode、Tab 与换行路径。
> - `artifact.validate` 在返回成功前执行唯一的 change-boundary comparison。
> - unexpected change、operation mismatch、DELETE/RENAME/UNKNOWN、缺失 contract、
>   Git 不可用或缺失可信 base commit 均 fail closed。
> - `approve_all` implementation agent 运行前以内存 fingerprint 固定当前
>   `task-contract.json`、`workstream.json` 与 `workspaces/`；运行中篡改或新增这些
>   change-boundary control facts 会在 node success 前失败。
> - 声明但未发生的变更仅记录为 observation，不导致失败。
> - ignored files 不属于 Git deliverable change set，因此不进入 B2 actual changes；
>   B2 是 workflow change governance，不声明 OS/filesystem containment。
> - 未新增 phase、module、registry、state machine 或 policy engine。
> - handler cannot-bypass 与 workflow/ledger cannot-bypass 均证明 violation
>   无法激活 acceptance context；strict fixture sibling smoke 通过。
>
> **Controlled README real-case = PASS。**
> - 报告：`docs/testing/real-case-reports/20260726T032753Z/report.md`；
>   workstream：`ws-20260726T033037Z-YW6K`。
> - fresh Maven package 与 provider probe 均通过；full workflow 最终状态
>   `COMPLETED`，acceptance=`ACCEPT`，benchmark grade=`PASS`，
>   acceptance reject=0，repair loop=0。
> - 12 个 agent run 均产生 strict `.contract.json` sidecar；最终 report、
>   handoff、next-stage prompt 与 delivery PR body 完整。
> - `change-scope-evaluation.json` 为 `PASS`；typed actual change 只有
>   `README.md/MODIFY`，missing declared 与 violations 均为空；
>   `artifact.validate` ledger result 消费同一事实并记录
>   `change_scope_status=PASS`。
> - 目标 worktree 仅修改 `README.md`，`node --test` 通过。
> - implementation actual report 仍记录 `actualReadFiles=[README.md]`、
>   `actualWriteFiles=[README.md]` 及 typed tool usage；workstream 中不存在
>   legacy exploration plan/policy artifact。
>
> **Phase B = PASS / ENFORCED + PROVEN。Checkpoint `e1c094b`。**
>
> **Phase C = PASS / ENFORCED + PROVEN。**
> - full reactor：9/9 modules PASS（runtime 439、adapter 300、CLI 68、server 53）；
> - independent boundary review：PASS；
> - workspace scan、requirement/filename scoring、seed content digest 与 raw input-ref
>   expansion 已删除；
> - validated agent sidecar 内容由 node + execution ledger 中的 SHA-256 绑定；
> - unknown refs 仅保留 pointer，canonical context/direct artifact 在读取前执行
>   exact path、real-path containment 与 symlink 检查；
> - README 报告：`docs/testing/real-case-reports/20260726T111504Z/report.md`；
>   workstream：`ws-20260726T111801Z-CM6B`；
> - 失败前 7 个 Agent 节点的 sidecar 当前 hash、node occurrence hash 与
>   node execution hash 全部一致；prompt 中未发现 workspace/filename heuristic
>   或 seed content digest，未知 transcript/stderr 仅保留 pointer；
> - 历史首次 README 运行在 `implement.claude_self_test_report` 停止：Claude 返回可解码的纯 JSON，
>   ACP `end_turn` 且 exit code=0，但缺少必需字段 `result.missing_evidence`；
> - 当时 bounded correction 仅接受 `AGENT_RESULT_PARSE_FAILED`，因此
>   `contractDecodable=true` 的 schema contract violation 未触发 correction；
> - 该次失败当时不能作为 Phase C `PROVEN`；此中间状态已由后续
>   schema-level bounded correction、README rerun 和 A3 PASS 取代，不是当前限制。
> - 独立 owner assessment：**PASS / IMPLEMENTATION READY**。Schema truth 保持在
>   `AgentResultEnvelopeValidator`；`AcpxAgentResultAcquirer` 只执行最多一次同 session
>   correction；`ArtifactContractValidator` / `AgentWorker` 保持最终唯一准入。
> - assessment：
>   `docs/plans/__CHECKPOINT__schema-level-bounded-correction-assessment.zh-CN.md`。
> - schema-level bounded correction：**PASS / ENFORCED + DETERMINISTICALLY PROVEN**。
>   Strict parser 未放宽；validator 返回 typed correction targets；acquirer 最多执行一次
>   same-session、deny-all correction；第二份结果改变 decision/summary/evidence 或其他
>   非 target 字段时直接拒绝。Targeted tests 与完整 reactor 均通过。
> - schema-correction checkpoint：`542187a7`；
> - README rerun 报告：
>   `docs/testing/real-case-reports/20260726T143936Z/report.md`；workstream：
>   `ws-20260726T144218Z-SP43`；
> - rerun 达到 `COMPLETED + ACCEPT + benchmark PASS`，10 次 Agent run、0 repair
>   loop；目标 worktree 仅修改 `README.md`，`npm test` 4/4；
> - `implement.claude_self_test_report` 首份 occurrence 为
>   `contractDecodable=true + contractSchemaValid=true`，因此本次未触发 correction；
>   缺字段 correction 的 cannot-bypass 证据仍来自 deterministic integration tests；
> - Phase C README 边界达到 `ENFORCED + PROVEN`；
> - A3 首次运行报告：
>   `docs/testing/real-case-reports/20260726T144933Z/report.md`；workstream：
>   `ws-20260726T145216Z-QKJJ`。该运行暴露 `ProcessUsability` 在 handler 与
>   metadata writer 间重复推断，属于 execution fact owner 漂移；
> - `bacd51dd` 收敛 authoritative `ProcessUsability` 后，同一 A3 重跑报告：
>   `docs/testing/real-case-reports/20260726T153645Z/report.md`；workstream：
>   `ws-20260726T153926Z-2H4Q`；
> - A3 重跑达到 `COMPLETED + ACCEPT + benchmark PASS`，20 次 Agent run，
>   目标 worktree 只修改声明的三个文件，最终 report、handoff 与 delivery
>   artifacts 完整；
> - A3 证明多文件 typed context、lineage、strict result、change boundary 与
>   workflow routing 可以完整闭环。该运行没有实际触发“非零 ACP process exit
>   但 strict typed result 完整”的特殊分支，因此该特殊分支只达到
>   `ENFORCED + DETERMINISTICALLY PROVEN`，不得描述为 real-case proven；
> - Phase D 仅激活只读 assessment。完成 assessment review 前，不得修改
>   production handler。
> 评估结论与单次减法范围见
> `docs/plans/__CHECKPOINT__phase-c-context-boundary-assessment.zh-CN.md`。

## 执行纪律

每个 real-case 失败都先按以下顺序处理：

```text
typed failure fact
  -> 定位唯一 owner
  -> 区分 local defect / missing domain owner
  -> 最小修复或抽取 domain service
  -> 重跑同一个 case
```

不得因为单个失败立即增加通用 framework、policy engine 或新的兼容解析器。只有同一规则被多个 executor 消费、且现有 owner 无法承载时，才允许抽取 domain service。

每个 phase 必须独立完成 Analyze -> Modify -> Review -> Reflect -> Analyze；未达到本 phase 的 PASS，不激活下一 phase。

## Phase A：完整小案例

### A1 README Full Lifecycle（PASS-WARN，等待 checkpoint）

目标：在 `full` variant 中证明：

```text
intake -> contract -> design -> plan -> implement
-> self-test -> acceptance -> delivery
```

PASS：

- 最终状态 `COMPLETED`；
- acceptance 最终决策为 `ACCEPT` 或 `ACCEPT_WITH_NOTES`；
- Claude/Codex 最终消息均通过 strict `AgentResultEnvelope`；
- 无 Markdown/自然语言控制路由；
- 无手工修改 ledger、artifact 或 worktree；
- 目标仓库只发生 contract 允许的 README 变更；
- fresh build、provider probe 和运行工件完整。

FAIL：任何 workflow/recovery/contract/scope/acceptance/delivery 边界失败。

INVALID：provider、配置、旧 jar、探针或外部运行时导致本轮无法证明代码目标。

Stop-loss：首次出现 recovery 即停止；不调预算、不切模型、不顺手修 Scope/Context/handler。

当前证据：

- `20260722T075423Z` 首次 A1 运行在 `intake.codex_product_review` 连续两次返回 `NEEDS_REVISION`，随后 Claude session reconnect；主因是 review context 只携带 proposal reference/summary，完整 typed proposal 未进入 prompt。
- 直接边界修复：workflow context 节点声明 `required_input_artifact=task_contract_proposal`；context builder 在 reviewer 启动前验证 reference/hash/lineage，并投影完整 proposal；完整 context package 不再被 adapter 的 6000 字符 preview 截断。
- deterministic closure：篡改 reference/sidecar 时 context 节点失败且 reviewer 启动数为零；`full`/`contract` sibling definitions 一致；full reactor 绿色。
- `20260722T083641Z` A1 重跑已通过 intake、contract 和 human requirement review，在 `design.claude_design` 停止。ACP prompt 正常完成（exit 0），但 final assistant message 为 prose + JSON，唯一 strict `AgentResultEnvelopeParser` 正确拒绝并产生 `AGENT_RESULT_PARSE_FAILED`。
- 当前 checkpoint：只实现一次有界 agent-result contract correction。ACP adapter 负责同一 session 的第二次 prompt；唯一 strict parser 决定 wire contract 是否可解码；`ArtifactContractValidator` 继续唯一负责 lineage、node contract 和 semantic validation。不得提取 prose 中的 JSON，不得增加通用 execution pipeline、`TaskHandler.reinvoke()` 或 workflow retry。
- `20260722T134655Z` controlled real-case（workstream `ws-20260722T134943Z-Z5FK`）完成 `full` lifecycle：最终状态 `COMPLETED`，最终 acceptance `ACCEPT`，16 个 Agent run 全部 `SUCCESS`，目标 worktree 仅修改 `README.md`，`node --test` 通过，fresh build 与 provider probe 均通过。
- 同一 real-case 真实触发一次 bounded correction：`design.claude_design` occurrence 1 为 `AGENT_RESULT_PARSE_FAILED`，occurrence 2 在相同 `claude-main-ws-20260722T134943Z-Z5FK` session 中 strict decode 成功；没有第三次调用，最终 usage 为两个 prompt occurrence 的显式聚合。
- 运行中未出现 `RECOVERABLE_RATE_LIMIT`、`PROCESS_STALLED` 或 reconnect。此前将 session UUID 中的 `429b` 误判为 HTTP 429 的 matcher 已改为仅接受显式 429 形态，并由 catalog、health cannot-bypass tests 与本次 replay 证明。
- 本次成熟度为 `PASS-WARN`：A1 的 typed result、lineage 与 full lifecycle 边界达到 `ENFORCED + PROVEN`；两次 acceptance `REJECT_CONTEXT_INSUFFICIENT` 经既有 repair loop 后收敛为 `ACCEPT`。

#### A1.1 Acceptance Verification Evidence Lineage（PASS）

目标：收口 A1 暴露的 acceptance evidence 路由与失败分类缺口，不扩张为通用 Context Engine。

Authoritative fact：已通过 `ArtifactContractValidator` 且绑定 durable node/execution result 的 self-test contract sidecar，其中 `node_id=implement.claude_self_test_report`、`source_artifact_type=self_test_report`、`decision=SELF_TEST_COMPLETE`，并包含 `checks_run`、`evidence` 与 `verification_summary`。

唯一消费链：

```text
self-test validated sidecar
  -> ArtifactValidateHandler（与 Git evidence 聚合并转发）
  -> ContextBuildPackageHandler（启动 reviewer 前要求 SELF_TEST_COMPLETE）
  -> acceptance prompt typed projection
```

分类规则：当 acceptance review 同时声明 `missing_tests` 与泛化 `context_gaps`，`FailureArbiter` 必须优先分类为 `VERIFICATION_INSUFFICIENT`，并由既有 workflow edge 路由回 `implement.claude_self_test_report`，不得回到 implementation。

Cannot-bypass：

- 已验证 self-test sidecar 经 artifact validation 后仍存在于 output refs，并进入 acceptance context；
- 缺少 `SELF_TEST_COMPLETE` 时 context node 失败，acceptance reviewer 启动数为零；
- `missing_tests + context_gaps` 选择 `REJECT_VERIFICATION_INSUFFICIENT`；
- `MISSING_TEST_EVIDENCE` 是精确 typed alias，不依赖 summary 正文；
- full workflow 的 verification edge 仍只路由到 self-test report。

完成条件：targeted tests、`dual-runtime` full suite、`dual-adapter-acpx` full suite、`git diff --check` 与独立架构 review 全部 PASS。通过后提交 A1.1 checkpoint，再评估 A2；A2 未满足条件时不得启动 real-case。

关闭证据：

- `ArtifactValidateHandler` 只从显式 input refs 转发通过 `AgentOutputLineageVerifier` 的 sidecar；不扫描 runs。
- `AgentOutputLineageVerifier` 将 sidecar decision 同时绑定 node 与 node-execution durable result；发布后篡改 cannot-bypass test 通过。
- acceptance requirement 绑定 `implement.claude_self_test_report + self_test_report + SELF_TEST_COMPLETE`，错误 producer/type/decision 均不能满足。
- evidence 缺失在 reviewer 前产生 typed `REQUIRED_AGENT_EVIDENCE_MISSING`；显式缺测优先分类为 `VERIFICATION_INSUFFICIENT` 并路由回 self-test。
- targeted tests、`dual-runtime` full suite、`dual-adapter-acpx` full suite、`git diff --check` 均通过；第二次独立架构 review 为 `PASS`。

### A2 小 Bug（PASS）

目标：证明 source-code 修改、定向测试、A1.1 self-test evidence lineage 和 acceptance 闭环。

当前 case：`issue-flow-app` 的 `summarizeIssues` 对非数组输入泄漏迭代器 `TypeError`。限定只修改 `src/dashboard.js` 与 `test/issueWorkflow.test.js`，要求在函数边界抛出精确的 `issues must be an array`，并保持现有行为与 `node --test` 全绿。

Readiness：A1.1 为 `ENFORCED + PROVEN` 且已提交为 `16fe713`；目标仓库干净、现有测试通过、缺陷由失败的精确断言复现；Verification Gate 已锁定 source of truth、PASS/FAIL/INVALID 与 stop-loss。允许启动一次 controlled `full` real-case。

关闭证据：

- controlled run `ws-20260722T150030Z-MP3G`，报告 `docs/testing/real-case-reports/20260722T145746Z/report.md`；fresh build 与 provider probe 均通过。
- workflow 一次完成 `COMPLETED`，final acceptance=`ACCEPT`，acceptance reject=0，repair loop=0，benchmark grade=`PASS`。
- `ArtifactValidateHandler` output refs 保留 ledger-bound self-test sidecar；acceptance context 明确投影 `SELF_TEST_COMPLETE`、`node --test` 5/5 与 verification summary。
- 目标 worktree 只修改 `src/dashboard.js` 和 `test/issueWorkflow.test.js`；新增边界校验与 focused test，`node --test` 5/5 通过。
- 未触发 `REQUIRED_AGENT_EVIDENCE_MISSING`、自然语言控制 fallback、manual ledger/worktree repair 或范围外修改。

### A3 简单应用 API（PASS）

目标：使用外部 `issue-flow-app` JavaScript 测试项目，证明 Dual 能组织 Claude Code 与 Codex 完成一个有限多文件、跨模块的应用 API 任务，并守住 contract、scope、self-test evidence 和 acceptance 闭环。该 JavaScript 项目只是受控 real-case 目标，不是 Dual 的运行时组件或依赖。

当前 case：保持 `src/issueWorkflow.js` 中的 `ALLOWED_TRANSITIONS` 为唯一状态迁移 SoT；新增 `availableTransitions(status)` 查询和 `src/issueApi.js` 的 `getIssueState(issue)` 稳定 DTO projection，并在独立测试中验证各状态、非法状态及返回数组隔离。限定只修改 `src/issueWorkflow.js`、`src/issueApi.js` 与 `test/issueApi.test.js`；不引入 HTTP server、网络、第三方依赖或第二份迁移规则。

Readiness：A2 controlled real-case 已达到 `COMPLETED + ACCEPT + PASS`；目标仓库干净、基线测试通过，且 precheck 已证明 `issueApi.js` 与 `availableTransitions` 尚不存在。允许使用固化的 Verification Gate 启动一次 controlled `full` real-case。

当前证据：

- `20260723T024557Z` 首次 A3 运行在 `intake.claude_brainstorm` 停止。严格 `AgentResultEnvelope` 正确拒绝了无法表达“修改现有文件”和“创建新文件”区别的 proposal；旧模型只能依赖 `new:` 字符串前缀，CREATE/MODIFY 语义没有进入 typed contract。
- `TaskContract.ScopeEntry(path, operation)` 现为 proposal 与 canonical TaskContract 共享的唯一 scope value object，`operation` 仅允许 `MODIFY|CREATE`。`MODIFY` 必须已存在，`CREATE` 必须尚不存在；旧 `new:` 前缀被显式拒绝。
- `task_contract_proposal` schema 已升级为 v2。旧 v1 sidecar 与“schema v2 但 included 仍为 string[]”均 fail closed，不提供 compatibility fallback，也不会生成 `task-contract.json`。
- proposal -> review -> human approval -> contract.compile -> context/memory/governance/report 的 production consumers 已迁移到 typed `ScopeEntry`；mechanical proposal failure 保留 valid AgentResult，并以 artifact contract failure 路由，不伪装成 output missing。
- controlled lifecycle smoke 以真实 ACP JSON-RPC 形态注入 strict `AgentResultEnvelope`，31 个节点、6 个 gate、11 个 agent run 全部完成；显式证明 intake proposal schema v2、最终 scope typed shape，以及 `main.py/MODIFY + README.md/MODIFY` 精确保真。
- semantic smoke 的 missing plan body、review prose noise、cross-stage reread 三个场景均通过；正式 smoke 不再生成 `dual-artifact` / `dual-control` Markdown。
- `20260723T074957Z` A3 重跑通过 intake、contract.compile 与 design，在 `plan.codex_plan_review` 返回 typed `NEEDS_REVISION` 后按 stop-loss 中止。Claude 生成的 `plan-claude.plan.json` 内容完整，但 review context 只信任 `plan-claude.contract.json` 的摘要；renderer 将同一 validated `PlanResult` 的派生 plan projection 误标为 `unvalidated agent result omitted from context`，导致 Codex 缺少实施步骤与验收映射。
- 修复不新增 evidence module 或 artifact state machine：`AgentResultDocumentProjector` 将已经由 `AgentResultEnvelopeValidator` 验证的 typed plan body 写入现有 `agent_output_contract` sidecar；`AgentEvidenceIndex` 将 payload 完整性纳入 required-evidence 判断；summary/full context renderer 从同一 validated entry 渲染并去重，伪造 derived plan 不能覆盖它。
- reviewer 启动门禁已收口：`source_artifact_type=plan_result` 但 typed plan body 缺失或不完整时，context build 返回 `REQUIRED_AGENT_EVIDENCE_MISSING` 且不发布 reviewer context。characterization/cannot-bypass tests、full Maven reactor（9/9 模块）与 `git diff --check` 均通过。
- `20260723T100647Z` A3 重跑证明 typed proposal -> Codex review lineage 正常，但首轮 proposal 被 typed `NEEDS_REVISION` 拒绝：它遗漏了用户明确要求的 strict `AgentResultEnvelope` 执行协议，并把 JavaScript `object` 的数组边界自行收紧为“必须拒绝”。该结果是有效的合同质量拒绝，不是 evidence 丢失；运行按零 revision-loop stop-loss 中止。
- intake producer 与 Codex reviewer 现共享同一保真语义：所有用户明确的业务目标、scope、行为、验收和验证要求必须进入 binding proposal fields；`risk_areas`、`open_questions`、`evidence_refs` 不能代替绑定要求；agent 不得基于语言边界或保守偏好擅自扩大/收紧可观察行为。旧的“choose the conservative default”冲突规则已删除。
- `AgentResultEnvelope`、JSON-only output 与 workflow routing 是 harness node Output Contract 的职责，不属于业务 task proposal。它们由 prompt contract、strict parser 与 validator 独立强制；producer 不复制、reviewer 不因 proposal 未重复这些协议而拒绝，避免形成第二个协议 SoT。
- `20260723T112922Z-8P57` 进一步暴露确定性 projection bug：absolute-path sanitizer 的 ASCII `\p{Alnum}` 负向判断把“缺失/非对象”“修改/新增”等中文 slash phrase 当作 POSIX 绝对路径，导致 reviewer context 出现 `[outside-workspace path removed]`。sanitizer 改为 Unicode character class；真实外部绝对路径仍脱敏，中文合同原文保持不变。
- 该边界不新增 requirement registry、evidence registry 或状态机。机械 schema validator 继续负责 shape；独立 Codex review 负责语义保真并 fail closed。prompt contract tests、adapter full suite 与 sibling rule sweep 已通过。
- `20260723T114300Z-TMHV` 使用同一 A3 Verification Gate 完整通过：workflow=`COMPLETED`、final acceptance=`ACCEPT`、acceptance reject=0、repair loop=0、benchmark grade=`PASS`。fresh Maven build、provider probe 与 runner-kind 检查均通过。
- A3 目标 worktree 仅修改 `src/issueWorkflow.js`、`src/issueApi.js` 与 `test/issueApi.test.js`；`ALLOWED_TRANSITIONS` 保持唯一迁移规则，`issueApi.js` 复用 `availableTransitions`，`npm test` 通过。
- intake review 只发现并收敛了 proposal 内真实的字段拼写冲突；此前错误的 harness protocol 重复要求与中文 slash phrase 脱敏污染均未复现。self-test 以 typed `SELF_TEST_COMPLETE` sidecar 进入 acceptance context，Codex 一次返回 `ACCEPT`。
- 关闭报告：`docs/testing/real-case-reports/20260723T114021Z/report.md`。

Phase A 完成条件：A1/A2/A3 各有一个可复现的 `COMPLETED + ACCEPT` real-case，且失败修复没有引入自然语言控制 fallback。

## Phase B：Scope Boundary

仅在 Phase A 暴露 scope owner 缺口或 A3 PASS 后显式激活。

### B0 Scope Boundary Characterization（COMPLETE）

目标：不修改 production，先证明当前 `RepoScopeGovernance` 中每一类 scope 规则的来源、owner、消费者和准入影响，建立 Phase B 的删除基线。

本 checkpoint 只允许：

- 区分 typed `TaskContract.scope`、通用路径安全规则、语言/框架 convention、文件名或目录结构猜测；
- 追踪哪些规则真正影响 read admission、scope expansion 或 agent 启动；
- 固化现有越界读取 cannot-bypass 测试及其命令；
- 给出保留清单、删除清单、目标 owner 和 B1 readiness 结论。

本 checkpoint 禁止：

- 修改 `RepoScopeGovernance` 或其他 production 代码；
- 新增 Scope Engine、policy DSL、repository index framework 或通用 capability framework；
- 运行 provider / real-case；
- 提前启动 Phase C Context Boundary 或 Phase D Handler Convergence。

关闭结论：

- canonical `TaskContract.scope` 是业务 scope SoT，但当前 effective read scope 还混入 input refs、evidence、git changed-files、requirement tokenization 和 repo-map filename scoring；
- heuristic/observed candidates 会进入 `ExplorationPlan.allowedDirectReadFiles`，因此会影响运行后节点验收，不只是 prompt 建议；
- 当前 enforcement 能阻止违规 run 成为成功节点，不能在 tool call 前物理阻止读取；
- fixed extension allowlist、acceptance prose path extraction、filename scoring 和重复 stage policy owner 均进入 B1 删除清单；
- deterministic baseline：runtime scope tests 30/0、adapter boundary tests 9/0、heuristic characterization 1/0；
- 完整分析、删除清单、目标 owner 与 B1 tests：`docs/plans/phase-b0-scope-boundary-characterization.zh-CN.md`。

### B1 Typed Scope Subtraction — **SUPERSEDED**

> ⚠️ **SUPERSEDED by Direction B.** 旧 B1 描述的是 read-authorization 语义
> （canonical scope 作为 read authorization，ExplorationPolicyEvaluator 作为 read
> decision owner）。Direction B 已决定 read governance 降级为 telemetry。
>
> - 旧 B1 的 PASS / `ENFORCED + PROVEN` 结论**已失效**。
> - 旧 B1 的目标结构、测试数字、验收条件和”允许提交 checkpoint”许可**均不再有效**。
> - 历史内容（实施细节、关闭证据、cannot-bypass test 清单）见：
>   `docs/plans/phase-b0-scope-boundary-characterization.zh-CN.md`、
>   `docs/plans/__CHECKPOINT__direction-b-b0-rebase-assessment.md`、
>   Git history（branch `codex/recovery-origin-requirements`）。
> - **不得作为 Direction B / B1 的实现或验收依据。**

## Phase C：Context Boundary

Assessment、subtraction implementation、deterministic gates、README 与 A3
controlled real-case 均已完成并通过。

目标结构：

```text
ledger facts + typed artifact refs + verified lineage
-> bounded ArtifactIndex / LineageQuery
-> ContextPackage
```

filesystem 只验证已知 artifact 的存在和 hash，不通过目录扫描猜测业务 evidence。不得建设通用 graph platform。

Assessment 结论：

- `AgentEvidenceIndex` + `AgentOutputLineageVerifier` 已是 routed typed evidence 的正确内核；
- 当前缺口是 `RepoScopeGovernance` 在 context build 中扫描 workspace、按 requirement
  与文件名猜测候选、读取 seed content 并注入 prompt；
- 下一步只允许一次 subtraction implementation：删除这条未路由 context source，
  收紧 generic raw ref 展开，同时保留 ledger facts、typed artifacts、actual telemetry
  与 read-reuse contract；
- 不新增 Artifact Graph、Evidence Registry、Context Engine 模块、query DSL 或状态机；
- implementation 已完成，deterministic gates 与独立 review 均 PASS；
- README 首次运行暴露 schema-level result 缺口；bounded correction 保持 strict
  parser、禁止默认补字段和语义 retry，并已由 deterministic tests 证明；
- README 重跑与 A3 多文件 controlled real-case 均达到
  `COMPLETED + ACCEPT + benchmark PASS`；
- Phase C context boundary 为 `PASS / ENFORCED + PROVEN`。

## Phase D：Handler 收敛

Phase C 已 PASS。Phase D 已完成只读 assessment、四个 bounded subtraction slice、
历史真实 REJECT sidecar replay 和全部 12 个 concrete handler 复核。

目标不是机械地把“68 个类”改成固定数量 executor，而是：

- node executor 只取 typed input、调用 domain service、写 typed result；
- contract、scope、context、review/gate、validation、delivery 各有唯一 owner；
- handler package 不再承载大块 policy、filesystem discovery 或 projection；
- 删除的旧逻辑多于新增 glue code；
- workflow/contract/handler consistency tests 和 full reactor 保持绿色。

Assessment 与首切片：

- checkpoint：
  `docs/plans/__CHECKPOINT__phase-d-handler-convergence-assessment.zh-CN.md`；
- 当前有 12 个 concrete handler；assessment 不预设最终数量；
- 首个已证明重复 owner 是 `HumanReviewGateHandler` 的本地 decision polarity
  集合与既有 `StageOutcome`；
- 本地 positive/negative 集合已删除，handler 只消费
  `StageOutcome.fromDecision`；
- `PASS -> READY` 与详细拒绝 `REJECT_CONTEXT_INSUFFICIENT -> revise`
  均有 regression/cannot-bypass test；
- targeted tests 63/0、`dual-runtime` 443/0、full reactor 9/9 modules /
  1,131 tests / 0 failures；
- production 净减少 4 LOC；
- 首个 slice 已提交为 `437b6e2`。

第二个 bounded subtraction slice：

- `ReviewDecisionHandler` 不再自行扫描、解析和校验 routed contract sidecar；
- `AgentEvidenceIndex.latestDecisionFromRefs` 成为 routed decision evidence 的唯一
  read owner，并返回 typed missing/parse/lineage/missing-decision failure；
- invalid latest routed sidecar fail closed，不回退较旧 sidecar；
- `ReviewDecisionHandler` production LOC 减少 53 行；
- targeted routing tests 75/0，full reactor 9/9 modules /
  1,132 tests / 0 failures；
- 第二个 slice 已提交为 `2f26cf5`；
- README controlled real-case
  `docs/testing/real-case-reports/20260727T140143Z/report.md`
  在干净 SHA 上达到 `COMPLETED + ACCEPT + benchmark PASS`；
- real-case 中 `design.decision` 从 strict sidecar 路由为
  `READY_FOR_ARCH_REVIEW`，`plan.decision` 实际覆盖
  `NEEDS_REVISION -> NEEDS_REVISION -> READY_TO_IMPLEMENT`，
  `acceptance.decision` 为 `ACCEPT`；所有结果均记录
  `source=contract-sidecar`，无 `CONTRACT_SIDECAR_*` failure；
- target diff 仅 `README.md`，复验 `npm test` 通过；
- 第二个 slice 达到 `ENFORCED + PROVEN`。不得据此自动启动第三个 slice。

第三个 bounded subtraction slice 已达到 deterministic PASS：

- 目标 handler：`FailureClassifyHandler`；
- acceptance failure facts 的唯一来源必须是 ledger-routed strict contract
  sidecar，经 `AgentEvidenceIndex.latestDecisionFromRefs` 解析和 lineage 校验；
- 删除 `NodeInstance.result` 中 `decision`、`summary`、`contract_gaps`、
  `out_of_scope_files`、`missing_tests`、`context_gaps` 与 `category_hint`
  对分类的 authority；
- 缺失或无效 sidecar 必须 fail closed，不能默认成 `REJECT`；
- `FailureArbiter`、workflow definition、classification 规则和 artifact
  writer 均不修改；
- cannot-bypass tests 已证明：
  - legacy result map 不能在缺少 sidecar 时驱动 classification；
  - legacy result map 不能覆盖有效 sidecar；
  - sidecar 缺少 decision 时返回 `INVALID_CONTRACT_SIDECAR`，且不会调用
    classification artifact writer；
- `FailureClassifyHandler` production LOC 从 308 降至 297；
- targeted tests 43/0，full reactor 9/9 modules / 1,135 tests / 0 failures；
- deterministic maturity 为 `ENFORCED`：缺失或无效 typed evidence 会在
  `FailureArbiter` 与 workflow classification route 前失败；
- 历史真实 REJECT sidecar
  `acceptance-reject-20260709.contract.json` 已通过当前 ledger publication、
  `ReviewDecisionHandler`、`FailureClassifyHandler` 与 workflow edge 重放；
- replay 产生 typed `VERIFICATION_INSUFFICIENT`，激活
  `implement.claude_self_test_report` / `iter-002`，第三个 slice 达到
  `ENFORCED + PROVEN`；未为概率制造 REJECT 消耗 provider token。

第四个也是最后一个 bounded subtraction slice：

- `HumanReviewGateHandler` 的 input artifact summary 不再直接 parse 和验证
  routed sidecar，而是消费 `AgentEvidenceIndex.validatedDocument`；
- tampered published sidecar 在 review packet/recommendation 写入前 fail closed；
- targeted tests 93/0，full reactor 9/9 modules / 1,137 tests / 0 failures；
- production 净减少 5 LOC，未新增 module、registry、engine 或 framework。

最终 12-handler 复核：

- `ArtifactValidateHandler`、三个 delivery handler、`FailureClassifyHandler`、
  `HumanReviewGateHandler`、`ReviewDecisionHandler` 未发现残余重复 authority；
- `ContextBuildPackageHandler`、`ContractCompileHandler`、
  `PromptGenerateNextStageHandler`、`ReportGenerateFinalHandler` 与
  `AcpxAgentTaskHandler` 仍有规模或专用读取复杂度，但没有证据证明存在第二控制
  owner，不因 LOC 或“可以统一”继续拆分；
- handler 数量保持 12。Phase D 的目标是 owner 和职责收敛，不是达到预设数量；
- Phase D 状态：`PASS / ENFORCED + PROVEN`。当前未激活新的 phase。

## 历史锁定范围（旧 B1 checkpoint 时代的许可，现已失效）

> ⚠️ **SUPERSEDED**: 以下内容是旧 B1 checkpoint（read-authorization 语义）时代的
> 许可范围。**该许可现已全部失效。** 不得据此启动或提交 Direction B/B1。
>
> 当时的许可：审查并提交旧 B1 checkpoint（read-authorization 语义下的）；独立评估下一 phase readiness。
>
> 当时的禁止：在独立 readiness assessment 前启动 Phase C Context Boundary 或 Phase D handler
> 重构，以及多轮 review、多 Agent proposal merge、自动 rollback、policy engine、预算/provider
> 优化、HTTP/runtime 扩张、tool-call 前 broker 或 write-scope lifecycle。
>
> 以上仅为历史许可记录；当前 objective 以本文顶部的 Phase C assessment
> 状态为准。

## 2026-08-18 Captcha-Auth Full Workstream 暂停点

当前主目标仍是先完成首个完整端到端产品 workstream，不启动新的优化 phase。

- workstream：`ws-20260818T011340Z-WA53`；
- effective worktree：`/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260818T011340Z-WA53`；
- workflow 当前仍为 `WAITING_FOR_RECOVERY`，active node 为
  `node-contract-compile-100`；ledger 未被手工改写；
- acceptance typed contract 已确认真实实现缺陷：生产默认非零 OTP resend interval
  下，新 `otp_send_state` 记录把 `last_sent_at` 初始化为当前时间，首次发送随即被
  resend 检查拒绝；
- 产品修复已落在 worktree：以 `INSERT ... ON CONFLICT DO NOTHING` 的更新行数判定
  首次准入，首次插入跳过 resend 检查，冲突路径继续在行锁下检查；新增 unit 与真实
  PostgreSQL 非零间隔回归；
- 产品全量测试：`98 tests / 0 failures / 0 errors / 0 skipped`；
- Dual 未提交修复：acceptance `task_contract` section soft budget
  `2000 -> 2500`；`FailureArbiter` 以 closed typed
  `verification_remediation=TEST_CODE_CHANGE` 优先于自由文本 `contract_gaps`
  路由 implementation repair；focused Dual tests `42/0/0`；
- 旧 classifier 已把同一 acceptance REJECT 误分为 `CONTRACT_GAP`，继而进入
  `contract.compile` 并以 `TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING` 失败；
- 一次手工 ledger 补偿尝试被安全边界拒绝，命令未执行、ledger 未变化。恢复时不得
  绕过该边界；应实现一个严格限定、带事件记录且由 ledger owner 执行的
  deterministic-classification replay/compensation 命令，或经独立边界评审选择其他
  正式恢复路径；
- 暂停期间不得调用 provider、不得创建 fresh workstream、不得 resume 当前错误节点。

恢复后的下一步：先闭合正式 reclassification recovery 边界并做 focused test；随后用
新 CLI 对当前 classifier 重放，让 RoutingEngine 正常生成 implementation repair iteration，
再继续 self-test -> artifact validation -> acceptance，目标为 workflow `COMPLETED`。

### 2026-08-18 恢复与完成结果

上述暂停点已关闭，首个 captcha-auth 完整端到端 workstream 已完成：

- 新增 ledger-owned `recovery replay-classification`，仅接受 lineage-bound
  `acceptance.classify_failure -> contract.compile` 且 compile 以
  `TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING` 失败的精确误路由；旧 compile node
  保留为 `SKIPPED` 并记录 compensation evidence，recovery gate 被显式 resolved；
- 当前 captured workstream 使用该命令产生 event
  `deterministic_classification_replay_requested`，corrected classifier 输出
  `IMPLEMENTATION_BUG / matched_rule=4`，RoutingEngine 正常创建 `iter-011`；
- DeepSeek implementation 输出 `IMPLEMENTED`，随后 self-test 输出
  `SELF_TEST_COMPLETE`；真实 PostgreSQL regression 同时覆盖 email 与 phone 的
  非零 resend interval 首发成功和立即重发节流；
- self-test 与 Codex acceptance 均记录 `99 tests / 0 failures / 0 errors / 0 skipped`；
- artifact validation：`PASS`，`changed_file_count=61`、scope violations `0`、
  missing declared `0`、hygiene warnings `0`；
- Codex acceptance：`ACCEPT_WITH_NOTES`、`blocking=false`、`must_fix=[]`、
  `missing_evidence=[]`、`verification_remediation=NONE`；
- human acceptance 通过明确 action `approve` 完成；workflow 最终
  `COMPLETED`，completion audit 为 `ready`、`lifecycleState=ENDED`、`gaps=[]`；
- 同次运行暴露并修复 CLI shortcut 漂移：`ReviewApproveCommand` 现在提交用户动作
  `approve`，canonicalization 仍唯一归 ledger；terminal approve-without-edge 与
  approved-edge 两种 sibling path 均有测试；
- 独立 diff review 发现 server/UI sibling 仍预先发送 `approved`；现已收敛为发送
  `approve`，由同一 ledger owner 决定 terminal completion 或 legacy `approved`
  edge 映射；terminal `approve + run` 在 ledger 已完成时直接返回完成边界，不再
  resume 已结束 workflow。`WorkstreamActionServiceTest` 为 `11/0/0`；
- Dual 受影响切片最终结果：ledger `41/0/0`、runtime `42/0/0`、CLI `4/0/0`，
  `git diff --check` clean。

主目标状态：`PROVEN`（真实 workflow `COMPLETED` + 非退化 `ACCEPT_WITH_NOTES` +
产品 PostgreSQL-backed tests 全绿）。下一步不是再制造 real-case，而是独立审查并选择性
提交本轮 Dual checkpoint；产品 worktree 可进入交付/提交整理。

### 2026-08-18 E2E Harness 边界复盘

首个完整 E2E 的架构结论已固化到
`docs/design/e2e-harness-boundary-retrospective.zh-CN.md`。本轮确认并关闭了 acceptance
context projection、typed failure classification、deterministic compensation、gate action
ownership 与 terminal approve command composition 五类 harness 缺陷。

当前最高的 ledger correctness risk 是多文件状态转换的崩溃一致性。下一步仅授权 bounded
architecture assessment：清点 sibling transitions、定义 commit point、建立 failpoint/restart
证据并比较最小协议；未授权直接引入 transaction framework、database、event store、新
module 或新的 provider real-case。

### 2026-08-18 Model-Strength Harness Subtraction 对照

参考《模型越来越强，harness 该留下什么？》后，架构复盘末尾新增 L0-L3 对照与删除
候选。优先级进一步区分为：R1 仍是最高 ledger correctness risk；prompt/rule subtraction
是短期成本更低的 optimization track。下一步先做只读 Prompt/Rule Ownership Inventory，
覆盖 10 个 prompt、`AgentResultPromptContract` 和已有 validators；随后只选择一个有
deterministic owner 的删除 checkpoint。A8 deterministic self-test replacement、small
profile 和 R1 crash protocol 均需各自独立 Gate，不混成一次改造。
