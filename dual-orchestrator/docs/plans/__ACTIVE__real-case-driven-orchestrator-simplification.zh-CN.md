# __ACTIVE__ Real-Case-Driven Orchestrator Simplification

## 总体目标

1. 简化 Dual Orchestrator。
2. 让 node executor/handler 只负责编排，领域规则归属唯一 domain owner。
3. LLM workflow 结果只通过 strict `AgentResultEnvelope` 进入状态流转；Markdown 仅供人阅读，不参与控制。

## 当前唯一优先级：先关闭已知正确性残余，再做删除优化

captcha-auth workstream `ws-20260818T011340Z-WA53` 已达到 workflow `COMPLETED`，首个完整 E2E 主目标已经
`PROVEN`。当前不再恢复该 workstream，也不再制造同类 provider 样本。

综合 E2E 复盘中 H1、H2、H4 的代码级核实，执行顺序调整为：

1. H1：制定并评审“qualified task contract 全文必达、soft budget 只告警、hard capacity 独立归属”的
   implementation-ready plan，随后实施单一 deterministic checkpoint；
2. H4：制定并评审独立的 workflow action visibility consistency 计划，随后只强制 outgoing edge
   必须在 `allowed_actions` 中可见；保留显式 no-edge terminal action，不再增加 CLI/UI/action mapper；
3. H2：保持冻结。typed remediation 到 implementation repair routing 已 `ENFORCED + PROVEN`，无新 bypass
   证据不得继续改 prompt/schema/parser/routing；
4. 完成 H1/H4 后再进行 R1 multi-file mutation bounded assessment；
5. 最后恢复 Prompt/Rule Ownership Inventory、纯删除 checkpoint 和 A8/small-profile 实验。

H1/H4 均不得调用 provider 或新增 workflow node、schema、module、registry、engine。H1 终版计划已获 CC PASS 并进入
deterministic implementation checkpoint；仍未授权 provider/real-case，也未授权 R1 事务方案或 harness subtraction。

当前成熟度：Phase A 的 strict agent-result、typed lineage 与完整小案例已达到 `ENFORCED + PROVEN`；Phase B 的 read-governance subtraction 与 typed change-boundary enforcement 已由 deterministic tests 和 controlled real-case 达到 `ENFORCED + PROVEN`；Phase C 的 typed context boundary 已由 deterministic tests、README 与 A3 controlled real-case 达到 `ENFORCED + PROVEN`；Phase D 已通过四个删除式 slice 收敛已证明的 handler 重复 owner，并达到 `ENFORCED + PROVEN`。

## Current Active Step: OKX S2 RECOVERY AND TERMINAL COMPLETION

2026-09-03：S2 checkpoint `d43f4351` 已在 OKX 同一 workstream 的新 self-test occurrence
`run-1788411416526` 中得到真实证明：policy ref/SHA 可重验且 `allow_local_binding=true`，sandbox 内本地 listener
与 HTTP stub 成功运行。S2 结论提升为 `IMPLEMENT PROFILE LOCAL BINDING ENFORCED + REAL-CASE PROVEN`。

该运行随后暴露真实产品缺陷：`44 tests / 0 failures / 18 errors`，共同根因为 `CandleSyncService` 在
`@PostConstruct` 中获取自身 Spring proxy，触发 `BeanCurrentlyInCreationException`；添加 Byte Buddy javaagent 后
Mockito 环境错误消失，产品根因不变。typed self-test 正确返回 `SELF_TEST_INCOMPLETE`。

workflow 当前停在 `WAITING_FOR_RECOVERY / node-implement-self_test_decision-042`。这不是 S2 或 provider 失败：
revision-limit 拦截器在路由前创建 synthetic `retry/abort` gate，遮蔽了 frozen definition 已声明的
`BLOCKED -> gate.human_implementation_repair`。`retryActiveNode` 只会重跑同一 decision，不能创建 repair iteration，
因此 blind retry 被禁止。当前唯一下一步是实施
`revision-limit-declared-human-repair-routing-plan-20260903.zh-CN.md` 的最小通用修复；不新增 recovery action、
不重置预算、不修改 OKX 代码、不调用 provider。修复通过独立 diff review 后，才用 deterministic retry 把当前
decision 路由到既有人工 repair gate。

2026-09-03：S2 SDK capability Gate 已证明 SDK `0.3.232` + embedded Claude Code `2.1.232` 在
`Options.sandbox.network.allowLocalBinding=true` 时 self bind/connect PASS、host loopback reachable、external direct
connect denied；operator 明确接受将该能力限定给现有 `implement` profile。实施 checkpoint 已完成：
profile 是唯一 capability owner，occurrence policy 是 durable fact，reviewed fork 只投影到 SDK option，
managed settings 对 true/false 均省略该 permissive key。零 workflow/profile/ledger/schema 扩张。reviewed fork
full suite `986/0/0`、Dual reactor `1298/0/0/0`、installer/configure/fresh Doctor 全部 PASS；patch identity 更新为
`41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c`。独立 diff review 已
`PASS（P0=0 / P1=0）`，并独立复现 fork 两次直跑全绿、Java focused `50/0/0/0`、六方 identity 与
reverse-apply。S2 checkpoint 已选择性提交为 `d43f4351`，工作树 clean。当前唯一下一步是按
`20260903-okx-s2-recovery-and-terminal-completion.md` 恢复既有 OKX workstream；不新建样本、不运行 E1 probe。

2026-09-01：E1 R2 Gate 独立评审发现 classifier execution failure 与 external proxy 共用 `exit 74`，会把环境
`INVALID` 错投影为能力 `FAIL`。delta 将 Node 自检及非 `0/1` 退出统一归为 `exit 76 / INVALID`，并补 `126/127`、
IPv4 `127/8`、`NO_PROXY` 尾逗号测试；同时记录 r1 fixture 修订链和历史样本的显式 runtime command 来源。此阶段仍只修
capability observation，不修改 production network policy，也不调用 provider。

E1 R2 delta 独立复审已 `PASS / EXECUTION READY（P0=0 / P1=0）`，确认上一轮 P1/P2 全部关闭；仅保留 stdin
读取异常的 nano 级理论观察，不再改动冻结 fixture。下一步按顺序提交 docs/fixture/runtime-pin checkpoint，再执行 standing
授权内 non-tool preflight；Bash/WebSearch capability prompts 仍需一次性明确授权。

E1 R2 checkpoint 已提交为 `05f864ba`；fresh package、Doctor、fixture SHA、launcher compile、离线测试与一次 fresh minimal
health 全部 PASS。用户随后明确授权两个 capability prompts。本轮 Bash session 精确执行一个 `./e1-network-probe.sh` tool call：
三个 Anthropic credential 均 absent，但严格分类器判定 `HTTP_PROXY=external`，脚本在首个网络请求前以
`exit 74 / E1_UNSAFE_PROXY_PROVENANCE` fail-closed。按 Gate 立即 stop-loss：未运行 WebSearch、session-conflict 或第二样本；
Bash session 已关闭，sentinel 已停止。

2026-09-01 对照 Anthropic 官方 sandbox/settings/secure-deployment 文档后纠正判定：Claude Code 的公开安全契约是“沙箱外代理
+ OS 级阻断直连 + 域名策略”，并未承诺 Bash 子进程的 proxy URL 必须呈现 loopback hostname。未设置 `httpProxyPort` /
`socksProxyPort` 时，Claude Code 可使用内建代理。因此本轮不是能力 `FAIL`，而是
`INVALID_OBSERVATION_MODEL / STOP-LOSS EXECUTED`：Gate 把实现投影的地址形态误当成安全 authority，真正的 network deny
effect 仍 `NOT PROVEN`。下一步仅允许按官方契约重写 Gate 与独立评审；不得消耗第二样本、实施 `PUBLIC_NETWORK_DOMAIN`
或恢复 OKX。

R3 documented-contract Gate 已起草于
`docs/testing/verification-gates/20260901-e1-claude-sandbox-documented-contract-r3.md`，并新增去除 proxy URL 形态判断的冻结
fixture。新版只验证官方契约效果：credential stripping、deny-all proxy domain rejection、raw-socket OS no-direct-connect、
strict no-unsandboxed fallback 与 WebSearch 共存。离线 fixture test 已 PASS；当前为
`DRAFT / EXECUTION AUTHORIZED AFTER INDEPENDENT REVIEW PASS`，必须先完成独立同行评审与 clean docs checkpoint，不运行第二
provider 样本。

R3 独立评审结论为 `PASS / EXECUTION READY AFTER GATE-SIDE CONDITIONS（P0=0 / P1=1）`。唯一 P1 是网络拒绝正向归因仍留给
operator 解释。Gate 已改为 frozen-runtime 签名白名单：proxy path 只接受 network allowlist/sandbox block 签名，direct path
只接受这些签名或 `Operation not permitted`；普通 HTTP/DNS/TLS/timeout/connection failure 一律不能 PASS，缺少或歧义签名为
`INVALID_OBSERVATION_SIGNATURE`。同时吸收 P2：72/75 归 INVALID、73 按 typed reason 归 FAIL，precheck/sentinel 原始输出入 SHA，
复验 `allowManagedDomainsOnly=true`、空 excludedCommands 与 Claude Code 最低版本，并要求净化环境运行离线 test。为避免
direct-path 的 `EPERM` 被 curl 抹平成普通连接错误，fixture 进一步改为 raw Python socket，并让 proxy curl 保留拒绝响应正文；
因此 SHA 已更新，必须做一次 delta 复审后才能执行。

R3 delta 复审已 `PASS / EXECUTION READY（P0=0 / P1=0）`；四个拒绝签名在实际 embedded `2.1.232` binary 中独立命中，
更新后的 fixture SHA、净化环境离线 test 与 Bash 语法均复现通过。首个 clean fixture/Gate checkpoint 为 `5c26c0df`；Gate 已冻结
acpx fork、ACP/SDK/embedded Claude identity 与 binary SHA。下一步为 docs-only freeze commit 后的 fresh package/Doctor/preflight；
任一身份或签名漂移即 INVALID，不调用 provider。

R3 结果随后证明原观察模型仍有 owner 冲突：Dual 将八个 proxy 名作为 Bash credential deny，而 Claude sandbox 按官方契约
可用同名变量承载自身 network transport。bounded comparison 推荐固定 `network_transport_owner=claude_sandbox`、只把三个
Anthropic provider 名作为 credential deny，并将 ambient proxy scrub 下沉到 reviewed fork 的 E1-bound Claude child spawn；
wrapper/Doctor 仅保留 defense in depth。独立评审 `PASS / IMPLEMENTATION READY（P0=0 / P1=0 / P2=5）`，五项 P2 已写回
comparison，`resolveSettings(settingSources=[])` 的 presence-only 证据也已归档。

当前只读 sweep 已完成并形成
`docs/plans/e1-claude-sandbox-network-transport-owner-implementation-plan-20260901.zh-CN.md`。首轮独立计划评审发现
`P0-1`：固定 `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1` 会按 Anthropic 官方 settings reference 关闭
`autoAllowBashIfSandboxed`，使 env 成为第二 policy owner，并重新阻断 Bash capability probe。delta 已按最小方向反转：
只在 Claude + 完整 E1 binding 下、晚于 process/auth/session env merge 删除八个 proxy 名和 ambient SCRUB 开关；provider
credential 的 sandboxed Bash 隔离仍由 policy v2 `sandbox.credentials.envVars` 唯一拥有；finalizer 挂在
`ensureLaunchSupport` 之后、spawn 之前，并补 production command-shape 回归与证据文件 SHA。下一步仅允许独立 delta review；
PASS 前不修改 production/fork patch、不调用 provider、不恢复 OKX。

P0 delta 独立复审已 `PASS / IMPLEMENTATION READY（P0=0 / P1=0 / P2=3）`，确认删除 ambient SCRUB 恢复
policy 对 auto-allow 的唯一 ownership，且不改变 R2/R3 已验证配置。三项 P2 已同步：Boundary Map 删除旧“固定安全开关”
措辞、引用改为 official settings-reference、测试 5 覆盖 SCRUB 大小写变体。当前进入 §6 单一 deterministic
implementation checkpoint；仍禁止 provider、real-case、domain approval、自建 proxy 与 workflow/ledger/UI 改动。

checkpoint 首轮独立 diff review 为 `WARN（P0=0 / P1=2）`：stamped build identity 测试仍固定期待 `UNBUILT`，且
finalizer 仅有纯函数测试、未从 `AcpClient.start()` 证明最终 child env。两项均已最小关闭：build-info test 改为消费 generated
identity 常量；真实 child harness 证明完整 E1 binding 删除八个 proxy 名与 ambient SCRUB、保留 executable，并以 unbound
Claude/non-Claude sibling 证明不越界。patch identity 更新为
`fc2783a8b5aa2a450007b9e5516ca473d9a5e5785d8760b342e230ea180c60cc`；fork pre-stamp 与 stamped
full suite 均为 `980/0/0`，Dual reactor `1294/0/0/0`，wrapper、patch apply/reverse、fresh Doctor 全部 PASS。
`AcpxAgentTaskHandler`、`AcpxAgentTransport`、`AgentRunMetadataWriter` 保持零 diff 是因为其既有 ref/SHA binding 与持久化
不依赖 policy schema 字段，已由现有测试覆盖。当前只允许独立 delta review；PASS 前不提交、不进入 capability Gate。

delta diff review 已 `PASS / READY TO COMMIT（P0=0 / P1=0 / P2=3 非阻塞）`，独立复跑交付树
`980/0/0`、wrapper、Java focused、fresh Doctor 与 reverse-apply 均通过；两个 P1 正式关闭。当前先选择性提交 E1
Network Transport Owner checkpoint，随后另起 sandbox-effect capability Verification Gate。用户已预授权该 Gate 所需的
provider tool turns，不再重复询问；授权不包含绕过 Gate/stop-loss、恢复 OKX real-case、domain approval 或 custom proxy。

E1 Network Transport Owner checkpoint 已提交为 `5018790`，工作树随后 clean。新的 capability Gate 已写入
`docs/testing/verification-gates/20260902-e1-claude-sandbox-effect-after-transport-owner.md`：复用 R3 原冻结 fixture，唯一改变是
执行基线升级为 policy v2 与新 fork identity，用于直接验证 owner 修复后的 Bash domain deny、OS no-direct-connect、filesystem/
credential 不退化及 WebSearch 共存。当前只允许独立 Gate review；review PASS 与 clean docs checkpoint 后按 standing
authorization 直接执行 provider turns，不再重复询问。

Gate 首轮独立评审发现 `P0-1`：我错误复用了 R3 的 proxy userinfo 分类与 exit 73 前闸，它属于已废弃的 v1 owner 契约，
会在当前 runtime 上确定性阻断网络矩阵并重新把 Claude-owned transport URL 当成 Dual authority。delta 已按批准计划 §3.5
修正：复制新 fixture，proxy 只记录名称 presence、不读取值；URL-shaped canary 必须完整完成矩阵且证据不泄漏；exit 73 仅保留
给三个 Anthropic credential。同步冻结 launcher 源码，并补 WebSearch profile/stream 结果与 Bash permission-request 判读。
offline 双场景 test、shell syntax 与 production classpath launcher compile 已 PASS；fixture SHA 已冻结。当前预授权不激活，
只允许独立 fixture/Gate delta review。

fixture/Gate 首轮 delta review 确认原 P0 已关闭，但发现 `P1-1`：Gate 把 WebSearch profile 描述成“只批准 WebSearch、
retry=0”，冻结 launcher 实际使用 production review profile，确定性参数为 allowed tools `Read,Grep,Glob,WebSearch`、
autoApprove kinds `read/search`、fetch escalate，且继承 retry=1。仅改文字接受 retry=1 会破坏单次样本纪律，因此 launcher
现在保留完整 production 权限字段，只把该验证 occurrence 的 retry 收紧为 0；Gate 同步冻结完整 CLI/policy JSON，并明确
WebFetch request/deny 是预期证明路径。当前仍只允许 SHA 重算、离线验证与独立 delta 复审，预授权不激活。

P1 alignment delta 独立复审已 `PASS / EXECUTION READY（P0=0 / P1=0）`：launcher 逐字段保留 production review
profile，仅收紧 retry=0；Gate 的 allowed-tools、permission-policy 与 flag absence 判据和实际装配完全一致；launcher
`cdaa7bf6...5142fb`、fixture `5/5` SHA、compile、offline test、bash syntax 均复现通过。当前先提交 clean Gate/fixture
checkpoint；全部 non-provider preflight PASS 后，用户预授权自动生效，按 Gate 恰一次 Bash、条件式恰一次 WebSearch。

Gate/fixture 已提交为 `3831b6d4`，全部 preflight 与 minimal health PASS 后执行了唯一 Bash occurrence。policy v2、credential
absence、workspace/tmp、三条 raw socket `Operation not permitted` 与零 permission request 均实证成立；但 inherited-proxy 的
curl/child/git 只返回普通 `CONNECT tunnel failed, response 403`，stderr/tool result/stream 均无 Gate 冻结的 domain-policy
归因信号。按 Gate 判 `INVALID_OBSERVATION_SIGNATURE` 并立即 stop-loss：WebSearch occurrence 为零、Bash/health session 均
closed、未运行第二样本。另发现 host raw TCP before 与 sentinel console 原始输出未持久化，证据卫生也不足。结果落在
`docs/testing/capability-proofs/20260902-e1-sandbox-effect-after-owner-result/`。下一步只允许只读归因与结果独立评审，禁止调
fixture 重跑、OKX resume、real-case、domain approval 或 custom proxy。

用户随后补充本次运行期间宿主开启 Shadowsocks VPN；只读核对显示 macOS system proxy 字典为空，但外部 IPv4 路由由
`utun4`/`198.18.0/15` 接管，符合 TUN/透明路由形态。因此原 Gate 的 `host direct` 只能解释为
`host-path reachability control`，不能证明宿主无代理直连。sandbox 内 raw socket 的 `EPERM` 仍是 connect 前 OS 拒绝事实，
但 inherited-proxy 的 403 可能来自 Claude sandbox proxy、Shadowsocks/宿主代理链或上游，进一步确认
`INVALID_OBSERVATION_SIGNATURE` 而非 PASS/FAIL。该拓扑为运行后补充事实，已单独标注，不冒充 frozen preflight evidence。

用户随后明确要求先用 shell/acpx 做不改 production 的 sandbox 连通性诊断。一次性 launcher 复用 production policy writer、
transport 与已验签 E1 fork，只在 `/private/tmp` 将 `allowed_domains` 改为 `["example.com"]` 并重算 binding SHA；同一 Bash
tool call 中 `https://example.com/` exit 0，而未列入 allowlist 的 Cloudflare URL exit 22/HTTP 403。两条请求共享同一
Claude sandbox 与宿主 Shadowsocks/TUN 下游，因此差分证明 sandbox 可联网且 strict allowlist 产生 allow/deny effect；
它不证明 403 内部 producer、完整绕过矩阵或 `PUBLIC_NETWORK_DOMAIN` 已实现。有效 provider 样本恰一个 prompt/一个 Bash、
零 permission request、session closed。原 Gate 仍为 INVALID，不追溯改写；独立证据位于
`docs/testing/capability-proofs/20260902-e1-sandbox-allowlist-differential-result/`。

随后按用户授权继续执行一次冻结 WebSearch coexistence probe。CLI/policy 装配与预期一致，恰一个 prompt，WebSearch 与
WebFetch 各调用一次、无 Bash、session closed；但两者均以同一个 provider/tool-backend
`API Error: 400 [1214][modelCode：不存在]` 失败，且 WebFetch 未出现预期 permission request/deny。adapter exit 0 只代表
turn 完成，不能冒充工具 PASS。本轮判 `INVALID_PROVIDER_TOOL_BACKEND`：WebSearch coexistence 与 exact WebFetch denial 均
`NOT PROVEN`，但不能归因 sandbox 网络失败。下一步只能只读核实 provider tool backend 与 permission event 顺序，backend
preflight 可机械通过前不得重复样本或通过开放 fetch 制造 PASS。证据位于
`docs/testing/capability-proofs/20260902-e1-websearch-coexistence-result/`。

只读根因定位确认两个 owner 被混淆：当前 ACP provider 为 `open.bigmodel.cn + glm-5.3`，文本模型可用但 Anthropic native
WebSearch/WebFetch backend 对该 modelCode 不兼容；同时 `--allowed-tools`/`allowedTools` 只是免确认集合，SDK 真正的工具
可见性 owner 是 `tools`/`disallowedTools`，Dual 当前没有传递。故 ledger WebSearch approval transport 虽 deterministic
`ENFORCED`，provider side effect 前的 exact-tool cannot-bypass 仍 `NOT MODELED/NOT PROVEN`。E1 sandbox network 与 WebSearch
必须拆开：前者以 Bash allow/deny differential 达到 bounded `PROVEN`；后者另起最小 visibility-authority 设计。GLM 如需联网，
应评审其官方 Search/Reader MCP exact-tool 边界，不把 native server tool、MCP 与 Bash fetch 合并。完整根因见同目录
`root-cause.md`。

独立根因评审随后 `PASS（P0=0 / P1=1 / P2=4）`，但发现 reviewed acpx fork 的 persistent policy binding
guard 只在 `shouldReuseExistingRecord(...)` 成功后执行。captured differential 的有效 occurrence 由 launcher、policy 文件与
`session/new` `_meta` 共同证明使用 `ce796a1b...`，而归档 record 仍残留早期 invocation 的 `ea47a3ed...`；因此行为差分
观察成立，record durability 不成立。当前唯一 active step 改为评审
`docs/plans/e1-acpx-persistent-policy-binding-replacement-guard-plan-20260902.zh-CN.md`：persistent 同名 record 必须在任何
reuse/replacement/client 副作用前全等校验 binding；只有 binding 相同且 backend session 失效时才允许重建并完整刷新
options。差分目录新增 `ERRATUM.md`，不改原始工件。该 plan/delta review PASS 前不修改 fork、不调用 provider、不进入
新版 E1 closure Gate 或 WebSearch visibility 实施。

首个完整 Mailpit real-case 已完成并通过产品 diff 审查，target commit 为 `c2e74f9`。后续共性边界计划已提交为
`bd2be53`；root-closure 与 exact WebSearch human authority checkpoint 已提交为 `4401149d`，其 deterministic
边界达到 `ENFORCED`，root closure 另有两个自然 occurrence 支持 `PROVEN`。

Persistent policy binding replacement guard 计划独立评审已 `PASS / IMPLEMENTATION READY（P0=0 / P1=0 / P2=4）`，
checkpoint 随后完成。manager replacement、direct prompt、queue owner 与 resource-not-found fresh fallback 现在共用唯一
binding guard；不一致或单侧缺失均在 client/lease/prompt/record 副作用前拒绝，same-binding fresh fallback 会完整刷新并
checkpoint 新 options。fork Node 24 full suite `985/0/0`、Dual reactor `1294/0/0/0`、clean apply/reverse、installer、
configure、fresh package 与 Doctor 全部 PASS；reviewed identity 更新为
`fd173f04 / 81acbb7491a17aedfb48b1a73f077b59e06f1d5eceb7cd617e59df86aa5c60ab / claude_execution_policy.v2`。
captured differential 的 record durability 已通过 ERRATUM 明确降为 `INVALID`，不改写原始工件。当前只允许独立 diff review；
provider、real-case、OKX resume、domain approval 与 WebSearch visibility 实施仍未授权。

D1 历史执行记录：producer/consumer/sibling sweep 已确认 human acceptance 当时只绑定
commit/PR Markdown，CLI/server 各自扫描 runs 并重读当前 worktree；此外 `delivery commit/create-pr` 仍可绕过 gate
直接 add/commit/push。实施计划已落在
`docs/plans/accepted-delivery-candidate-implementation-plan-20260828.zh-CN.md`，选择 content-addressed candidate、
直接消费 authoritative gate history 的 deterministic resolver、runtime 单一 application service、manual commit finalize，并删除 legacy delivery
bypass。独立架构评审已 `PASS（P0=0 / P1=0）`，checkpoint 已按计划完成：immutable candidate、event-ordered
resolver、共享 apply/finalize、CLI/API/UI 委托与 legacy sibling subtraction 全部落地；workflow JSON 零修改。
focused boundary slice 全绿，非沙箱 full Maven reactor 为 `1268/0/0/0`。deterministic 证据支持 D1
`ENFORCED / NOT REAL-CASE PROVEN`；该 checkpoint 后续已通过独立 diff review 并提交。

D1 独立 diff review 已 `PASS（P0=0 / P1=0 / P2=2 知会）`，并独立复现 full reactor
`1268/0/0/0` 与 clean diff-check。两个 P2 已结转 D2：历史 `delivery.json` 只读投影残迹，以及稳定
commit-message/PR-body 只能保持 presentation 身份。D1 已满足选择性提交条件；提交后才允许进入 D2 只读
producer/consumer/sibling analysis，仍不授权 provider/real-case。

D1 已选择性提交为 `d04d0a58`，工作树恢复 clean 后完成 D2 只读 producer-consumer sweep，结果见
`docs/plans/d2-completion-delivery-producer-consumer-sweep-20260829.zh-CN.md`。sweep 证明现有 persisted node
input/output refs 已足够从 final acceptance sidecar 反向重建 acceptance context 与 self-test occurrence lineage，因此
拒绝新增 durable `VerificationEvidenceSelection`；同时 D1 的 `DeliveryApplicationService.View` 已是唯一 delivery
read model，不再建立包揽 workflow/completion/evidence/delivery 的通用模型。D2 收缩为两个有序 checkpoint：D2-A
新增只读 completion evidence lineage resolver 并删除 final-report/server 的 runs+mtime 猜测；D2-B 复用 D1 view，删除
`delivery.json`、raw merge-plan、legacy status 与 UI fallback 的重复解释。当前成熟度为 `ASSESSED / NOT IMPLEMENTED`；
该 sweep 随后进入独立架构评审，仍未调用 provider/real-case。

D2 sweep 独立评审结论为 `PASS（P0=0 / P1=0 / P2=2）`，确认 existing routed refs 足以机械重建
report -> acceptance -> acceptance context -> self-test occurrence lineage，且 D2-A/D2-B 的拆分与顺序最小。P2-1 要求
实现时不能只凭 context package 文件名定位 node：定位后必须验证该 completed context node 的 outputRefs 确实包含 package
ref，并核对 workflow definition 的 acceptance profile/target/required evidence；P2-2 将 generic artifact-contract/artifacts
mtime scan 登记为 presentation residual，不冒充本轮已修。基于该结论已形成
`docs/plans/d2-a-completion-evidence-lineage-implementation-plan-20260829.zh-CN.md`。当前只允许对 D2-A 计划做一次独立
架构评审；PASS 后实施单一 D2-A checkpoint，D2-B 必须等待 D2-A clean commit。

D2-A 计划独立评审确认所有 producer/consumer 事实与逐跳 lineage 均成立，文件范围最小且不产生第二 SoT，但发现一个
P1 计划语义冲突：合法 intermediate goal 会由 `NEEDS_REVISION/BLOCKED` 写入，不能归为 corruption。计划已增加
`COMPLETION_GOAL_NOT_CONFIGURED` 与 `COMPLETION_GOAL_NOT_FINAL`，API 分别投影 `not_configured/not_final`；report node
仍对所有非 `AVAILABLE` 状态 fail closed，但保留原始分类。另修正 production bundled variants 为三个，将 small-plan
experiment 明示为 test-only，并把 `TraceCommand.acceptance()` 与 `artifacts().contextPackageMetadata` mtime 选择登记为
D2-B/presentation residual。delta 复审确认上一轮 P1/P2 全部关闭，结论为 `READY（P0=0 / P1=0）`；新增 P2 仅要求
report 入口补 `NOT_CONFIGURED/NOT_FINAL` 对偶测试与修正文档指针，已并入测试矩阵。当前进入单一 D2-A deterministic
implementation checkpoint；仍不调用 provider、不启动 real-case，D2-B 等待 D2-A clean commit。

D2-A 已按该边界实施。新增只读 resolver 只从 canonical goal 与 persisted routed refs 重建
decision -> acceptance sidecar -> acceptance context producer -> required self-test sidecar，并逐跳重验 workflow identity、
iteration、node/execution/sidecar SHA、context outputRefs 与 definition config。final report 在任何写入前消费同一
resolution，非 AVAILABLE 保留 `NOT_CONFIGURED/NOT_FINAL/INVALID` 原分类并 fail closed；server status/overview/attention
使用同一四态投影，旧 summary/runs acceptance 与 report filename/mtime recent-output authority 已删除。

攻击测试覆盖 sidecar SHA 篡改、goal/decision refs 漂移、双 acceptance、forged context package、缺 routed self-test、
cross-iteration、旧 report bytes 保留和 bundled/test-only sibling consistency。首轮 focused resolver/report `17/0/0/0`，
相关 report fixture slice `77/0/0/0`，server controller `22/0/0/0`；首轮非沙箱 full reactor
`1277/0/0/0`，`git diff --check` clean。当前成熟度候选为 completion evidence lineage
`ENFORCED / NOT REAL-CASE PROVEN`、final report `VERIFIED PRE-HUMAN SNAPSHOT`；等待一次独立代码级 diff review，
PASS 前不提交、不进入 D2-B、不调用 provider/real-case。

首轮独立 diff review 为 `WARN（P0=0 / P1=1 / P2=4）`。唯一阻塞项是 server 未测试
NOT_CONFIGURED/NOT_FINAL/INVALID、`finalDecision=none`、critical/warning attention、成功 attention 门控与 decoy runs
no-scan。delta 已新增四条 controller tests 完整覆盖这些分支；另补 acceptance/context 跨 iteration 与 future-mtime
decoy tests，并将计划校验顺序修正为 goal identity corruption 优先于 finality。delta focused resolver/report
`19/0/0/0`、server controller `26/0/0/0`，最终非沙箱 full reactor `1283/0/0/0`，`git diff --check`
clean。当前等待独立 delta review；PASS 前仍不提交、不进入 D2-B。

第二轮独立 delta review 已 `PASS（P0=0 / P1=0 / P2=2 知会）`，独立复现 focused 与 full reactor 数字，确认
原 P1-1、identity/finality 文档顺序及三腿跨 iteration/mtime 反证全部 CLOSED。两个残留 P2 仅为 acceptance/report
id 的既有硬编码耦合和 status 高频完整只读 resolution 的性能观察，不构成 authority fallback；分别以 workflow 泛化和
真实轮询成本证据作为 revisit trigger。D2-A 选择性提交条件满足，成熟度正式记录为 completion evidence lineage
`ENFORCED / NOT REAL-CASE PROVEN`、final report `VERIFIED PRE-HUMAN SNAPSHOT`。下一步仅允许在 clean checkpoint 后
制定 D2-B projection convergence 实施计划，不直接编码、不调用 provider/real-case。

D2-A 已选择性提交为 `83d840ed`，工作树 clean 后完成 D2-B producer/consumer 的代码级复核。D2-B 不新增
delivery 状态或 read model：workflow current 继续拥有当前 workflow 状态，D1 `DeliveryApplicationService.View`
继续拥有 delivery status/reason/capability；CLI/server 只格式化 view，ledger attention 删除 delivery 解释。
实施计划已写入
`docs/plans/d2-b-delivery-projection-convergence-implementation-plan-20260829.zh-CN.md`，同时纳入两个容易遗漏的
边界：`SummaryCommand` 的 action-bearing workflow 判断必须从 timeline 切换到 current，以及 list API 将
`status/workflowStatus` 双解释收敛为 canonical `status + statusSource + legacyInitialStatus`。当前只允许独立架构
评审该计划；PASS 前不改 production、不调用 provider/real-case。

D2-B 计划首轮独立架构评审为 `WARN（P0=0 / P1=1 / P2=3）`。唯一 P1 是计划漏掉运行中 workstream 的
主导 delivery eligibility：`DeliveryApplicationService.view()` 会投影
`BLOCKED / WORKFLOW_NOT_DELIVERABLE`，但原测试矩阵没有规定其展示和 command 边界。delta 已将其定义为
“当前尚不可交付”的可见资格状态，要求 CLI/API/两个 UI 保留 reason、零 mutation command、零 legacy/raw
fallback；同时明确放弃 legacy PR URL/时间戳展示、补 `workflowStatus` reader 零命中 sweep，并把 UI 测试对准真实
fallback delta。当前只允许一次独立 delta 复审；PASS 前仍不实施 production。

D2-B delta 复审已 `PASS / READY（P0=0 / P1=0）`，随后进入单一 deterministic implementation checkpoint。
CLI status/summary 与 server status/overview/list 已收敛到 workflow current + runtime delivery view；两个 UI 删除
raw delivery 与 `workflowStatus` fallback；ledger attention 删除 delivery plan 解释。实施期通过反转旧 Summary fixture
发现 D1 view 仍会接受 candidateRef 为空的非 FAILED raw plan，已在唯一 runtime owner 中 fail closed，并以 unbound
APPLIED 反证测试钉住。后续 sibling sweep 发现 `SchedulerService` 仍以 list-item `workflowStatus`
触发副作，已改为仅消费 `statusSource=workflow_current` 的 canonical `status`，并以 legacy-only fixture 证明零
runtime action。

controlled lifecycle smoke 又暴露两个只有完整序列才可见的缺口：smoke gate loop 从 CLI 文本搜
`BLOCKED`会把 delivery eligibility 误当 workflow status；D1 candidate resolver 对异构 event id 全局字典序
校验，会把真实 ledger 中交错的 `agent-task-*`/`domain-*` 事件误判为乱序。前者已改为直接
消费 `workflow/current.json.status`；后者改为仅对 workflow-owned `evt-NNNNNN` 做数值递增校验，
非 workflow event 不参与排序，workflow event id 按生成器的“最少 6 位”契约解析，但
`node_routed` 缺 workflow event id 仍 fail closed。反证 fixture 已加
interleaved agent-task event。

最终 controlled smoke 到达 `COMPLETED`（6 gate rounds / 112 events / 32 nodes / 11 runs / 0 warnings），
delivery view 为 `READY` 且 `canMerge=true`；full reactor 独立报告聚合为 151 files，
`1286/0/0/0`，`git diff --check` 与 smoke shell syntax 均通过。production sweep 确认旧
`delivery/delivery.json`、raw `deliveryMerge`、list/task-row `workflowStatus` 已不再作为状态或命令能力
reader。当前成熟度候选为 `ENFORCED / NOT REAL-CASE PROVEN`，正在等待独立代码级 diff review；
PASS 前不选择性提交。

D2-B 独立 diff review 已 `PASS（P0=0 / P1=0 / P2=2 知会）`，并独立复现 full reactor
`1286/0/0/0` 与 `git diff --check` clean。评审确认 workflow current + delivery view 的单一 authority、
mutation command capability 门控、scheduler 零 legacy action、混合 event ledger resolver 与 controlled smoke 边界均
成立。P2-1 的三个实施期 production 文件已回填计划 §7；P2-2 仅要求后续 sweep 区分
`NotificationService` 自有 payload 与 list-item fallback reader。成熟度正式为
`DELIVERY/CURRENT PROJECTION CONSISTENCY ENFORCED / NOT REAL-CASE PROVEN`，当前允许选择性提交该单一
checkpoint。

D2-B 已选择性提交为 `fdd8a49b`，工作树恢复 clean。D1/D2-A/D2-B 至此完成
deterministic enforcement，下一主线是制定一次自然 real-case Verification Gate 证明 accepted candidate、
completion evidence 与多端 projection 的端到端价值。

E1 原定位为 `IMPORTANT / NOT URGENT SECURITY TODO / NOT MODELED`。OKX fresh real-case
`ws-20260829T122452Z-9BZG` 已自然触发 revisit 条件：implement retry 的 ACP stream 对 Bash/curl 产生
permission request，但 `autoApprove execute` 自动选择 `allow`；随后 npm registry 下载成功，且 tarball 被错误保存为
`klinecharts.min.js`。operator 已中断 run、执行 stale recovery 并关闭 persistent session，workstream 保留在
`WAITING_FOR_RECOVERY`，不得直接 retry。

该 incident 证明“受控网络入口”与“Agent tool 子进程默认禁网”必须合并：A 单独存在时仍可被 Bash 绕过。
实施计划已写入
`docs/plans/e1-controlled-network-access-and-agent-egress-enforcement-plan-20260829.zh-CN.md`，当前成熟度为
`NATURAL INCIDENT PROVEN / ENFORCEMENT NOT IMPLEMENTED`。下一步仅允许独立架构评审该计划；评审 PASS 后先做
frozen adapter capability proof，proof PASS 才实施 A + mini-B 原子 checkpoint。评审与 deterministic enforcement
完成前不恢复 OKX workstream、不创建第二样本。

E1 首轮独立架构评审为 `WARN（P0=0 / P1=2 / P2=6）`。总体设计与 A+mini-B 原子性成立，但 capability proof
漏了两个会改变 enforcement 声明的边界：generated sandbox 下既有 WebSearch 必须继续可用而 WebFetch 仍拒绝；网络
sandbox 不能静默破坏 worktree/`/tmp` scratch 的常规 filesystem 语义。计划 delta 已补齐两项 P1，并将 loopback/LAN、
IP/DNS、proxy env、legacy approveAll 与 scheduler manual-only 纳入 proof/cannot-bypass 矩阵；同时修正 incident 工件事实：
当前 vendored 文件已被 run 内解压为正确 min.js，但来源未经授权，需要 provenance 处置而不是按 `.tgz` 删除。当前等待一次
独立 delta 复审；PASS 前不做 capability probe、不改 production、不恢复 workstream。

E1 delta 复审已 `PASS / READY（P0=0 / P1=0 / P2=2）`，确认上轮 8 项 finding 全部由 proof 问题、测试断言与
FAIL 门真实关闭。P2 已随执行吸收：incident permission request 数修正为 4/4；capability proof 的命令、原始输出 SHA、
逐问题结论和 residual 统一落在
`docs/testing/capability-proofs/20260829-e1-claude-tool-egress-capability-proof/`。non-provider capability proof 已执行并判定 `FAIL`：Claude SDK/adapter 支持 session-bound sandbox options，但 frozen `acpx@0.11.0` 没有把 `settings/managedSettings/sandbox` 传入 `session/new._meta` 的入口，且现有 wrapper 尚未分离 provider env 与 tool subprocess env。按 stop-loss 未启动真实 Claude tool turn、未改 production、未恢复 OKX workstream。

E1 policy transport bounded comparison 已经独立评审 `PASS（P0=0 / P1=0 / P2=3）`，确认 frozen client 白名单缺口真实、候选 A 最小且不存在可行 settings 文件替代。三项 P2 已写回：拒绝 `CLAUDE_CONFIG_DIR + user settings` 与宿主 managed-settings；env 分层明确依赖 wrapper 收窄和 managed deny；fork 采用 exact source commit/patch/manifest/独立 review。

实施级 reflection 进一步确认：当前 adapter 0.46 固定 Claude Code 2.1.178，早于 credential env deny 与 strict allowlist 的能力版本；只补 acpx 仍会让 provider secret 暴露给 Bash ambient env，不能进入生产。新的最小计划为 `docs/plans/e1-policy-transport-checkpoint-implementation-plan-20260829.zh-CN.md`：固定 acpx source patch + adapter 0.70/SDK 0.3.232 capability baseline，仅证明 fixed policy transport，不实现 network approval。当前只允许独立评审该计划；PASS 前零 production/provider/real-case。不得扩张为通用 adapter/settings 平台，也不得以 target settings、Bash parser 或直接 provider probe 绕过该缺口。

E1 policy transport 实施计划独立评审已 `PASS / READY（P0=0 / P1=0）`。三项 P2 已写回：修正
upstream parser 路径；明确 adapter ambient env、wrapper provider allowlist、policy credential deny 与真实 Bash env
四层断言；枚举固定 provider env allowlist。按计划 stop-point，当前只完成 acpx fork checkpoint：固定
`v0.13.2@fd173f04`，生成 patch SHA
`304577dc136c87b055b5873b0562929cd5c16b1b0d18321e27f851209b3ab811` 与 manifest；focused
`147/0/0/0`，合规 Node 24 下 upstream full suite `972/0/0/0`，build/lint/format/diff-check 全部 PASS。
结果见 `docs/plans/e1-policy-transport-acpx-fork-checkpoint-result-20260829.zh-CN.md`。当前只允许独立 review
fork patch；PASS 前不得接线 Dual、调用 provider、恢复 OKX workstream或创建第二样本。

fork 独立 diff review 为 `WARN（P0=0 / P1=1 / P2=3）`，确认 fixed-policy 输入、真实 `_meta` 消费、session
binding 与成熟度边界成立。阻塞项已关闭：manifest repository 修正为公开权威 `openclaw/acpx`；同时补 tag object、
npm tarball SHA，并将 queue-owner 半绑定从静默跳过收紧为立即拒绝，delta focused `89/0/0/0`。当前允许进入
Dual fake tests -> installer -> policy writer -> transport binding；仍不得调用 provider 或恢复 real-case。

下一次 generalization real-case 已选定 fresh 项目 `okx-btc-k-line-demo`，目标路径为
`/Users/kriswang/work/projects/ai/okx-btc-k-line-demo`。需求是使用 Java 25、Spring Boot 4.1.0、
MyBatis-Plus 3.5.17 与 SQLite 构建 BTC-USDT 1m K 线采集、定时同步和 Web 展示应用。
官方文档已确认 Spring Boot 4.1.0 GA 与 `mybatis-plus-spring-boot4-starter:3.5.17`
组合有效。

target baseline 已以 `c848079` 提交，README 与 requirement 均为中文，仅含
`REQUIREMENT.md`、README、`.gitignore` 与
target-owned workspace check；无 `pom.xml`、无 `src/`，用于真实验证 cold-start 实现。Gate 已写入
`docs/testing/verification-gates/20260829-small-workflow-okx-btc-k-line-fresh-project.md`。主 PASS 要求产品
完整性与 D1 accepted-candidate/apply/finalize、D2 completion/delivery 多端投影同时成立；8-call
仅是次级效率结果。用户已显式授权唯一 fresh real-case；不再把 CC 需求评审设为前置审批层。
当前先选择性提交 Gate，随后执行 non-provider preflight 与 standing-authorized minimal probes；
所有前置机械 PASS 后才创建唯一 workstream 并启动 provider。

H1 mandatory TaskContract projection checkpoint 已选择性提交为 `e1b6e9e5`。qualified full projection 与 soft
warning 达到 `ENFORCED`；provider hard-capacity 保持 `NOT MODELED`；real-case value 保持 `NOT PROVEN`。H1 不再扩张，
其 bounded reason guard-matrix 仅作为测试覆盖候选记录，不阻塞主线。

H4 implementation plan 已落在
`docs/plans/h4-workflow-action-visibility-consistency-plan-20260826.zh-CN.md`。计划把 ledger 现有 directional
`approve -> approved` canonicalization 与 ledger/handler 两份 fallback 集中为 core semantic owner，并由
`WorkflowDefinition` 在 bundled/custom/frozen definition 的共同构造边界强制“每条 gate edge 都能由 visible action
触发”。allowed-only no-edge action继续作为显式 terminal command，覆盖 contract `reject`、terminal acceptance
`approve` 与 next-stage `complete`；不增加 terminal registry 或 node-id exception。独立架构评审已
`PASS（P0=0 / P1=0 / P2=2 非阻塞）`，确认 single core
semantic owner、directional canonical rule、三类 no-edge terminal command 与共同 Definition guard 均成立。
P2-1 的 `FullWorkflowDefinitionTest` 已补入 direct-test 清单。checkpoint 已按计划完成实现：新增单一 core
`HumanGateActionSemantics` owner，`WorkflowDefinition` 在 bundled/custom/frozen definition 的共同构造边界拒绝
hidden edge；ledger gate document、runtime canonicalization 与 human review packet 全部消费该 owner，三条旧解释路径
已删除。focused H4 slice 为 `167/0/0/0`；非沙箱 full Maven reactor 9/9 modules、`1280/0/0/0`；
`git diff --check` clean。deterministic 证据支持 action visibility `ENFORCED`；全 UI action 动态投影仍
`NOT MODELED`，所有 entry/action 组合仍 `NOT PROVEN`。当前等待一次独立代码级 diff review，PASS 前不提交；
H4 不需要 provider/real-case。独立代码级 diff review 已
`PASS（P0=0 / P1=0 / P2=0）`，并独立复现 focused 超集 `178/0/0/0`、full reactor
`1280/0/0/0` 与 clean diff-check；确认 13 个文件满足单一原子 checkpoint 的选择性提交条件。

H1/H4 correctness residual 已完成。当前进入 R1 multi-file mutation bounded assessment：先只读清点一次 ledger
mutation 涉及的文件集合、可观察 commit point、失败窗口与 restart/recovery 行为，再用最小 failpoint/restart 证据判断
是否需要修复。此阶段不预设 WAL、transaction manager、event store 或通用事务框架，也不调用 provider/real-case。

assessment plan 已落在
`docs/plans/r1-multi-file-mutation-bounded-assessment-plan-20260826.zh-CN.md`。代码清点确认至少六类 sibling mutation；
第一轮只选择 initialize、human gate resolution、stale recovery、normal routing 四个 restart slice，逐个持久化真实写序
前缀并用新 ledger 实例检查引用一致性、原命令幂等重放和 second restart。已有代码证据显示 gate resolved 先于
node/current，node FAILED 先于 recovery current，均可能让原命令失去重放资格；但在 S1-S4 结果出来前不选择共享
协议。独立架构评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`。评审建议的 writer decorator 经复核不可直接使用：
`AtomicJsonWriter` 为 `final` 且 `write()` 不可覆写；本轮改用目标 `.tmp` 占位和 event append 阻断，在不改 production
的前提下驱动真实写序。S1-S4 共 14 个 prefix 已完成：`PASS=2 / FAIL=12 / INVALID=0`。S1、S2、S3、S4 均至少
存在一个 stuck prefix；S4 在 event append 前可安全 finish-forward，event append 后会重复 event id，current commit 后
会与 instance 漂移。M6 agent-run/execution/agent-task 写点清单已同步落盘。结果见
`docs/plans/r1-multi-file-mutation-bounded-assessment-result-20260826.zh-CN.md`。当前只允许独立代码级 diff review 该
test-only evidence checkpoint；PASS 前不设计或实现 production mutation protocol。

test-only evidence checkpoint 已通过独立 diff review：`PASS（P0=0 / P1=0 / P2=0）`，focused `4/0/0/0`、full
reactor `1284/0/0/0` 与 diff-check 均独立复现，并选择性提交为 `3ecef0c4`。后续 bounded comparison 已落在
`docs/plans/r1-multi-file-mutation-design-comparison-20260826.zh-CN.md`：generation pointer 因约 31 个文件、84 个
reader 调用点、event/layout migration 与 legacy 双读成本被拒绝；prepared manifest 仍需 mutation plan/apply、event-id
幂等、pending-aware reader 与 M6 owner 收敛。独立架构评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`，并指出原比较
遗漏 transition-specific reconciliation。comparison 已补入第三候选：event-id 幂等、instance 从 current 重派生，
以及 S2/S3 两个精确 stuck-shape finish-forward，可覆盖 11/12 个 FAIL 形态但不能恢复 S1；它会扩大守卫接受空间，
因此在零自然事故条件下同样 `DEFERRED`。reader 成本口径已补为四个 main source roots 中指定四类 symbol 的
`rg -l=31`、`rg -n=84`。当前结论为 `RISK VALIDATED / IMPLEMENTATION DEFERRED`；评审已授权选择性提交两份文档，
关闭 R1 implementation 并返回删除式优化。

Prompt/Rule Ownership Inventory 已落在
`docs/plans/prompt-rule-ownership-inventory-20260826.zh-CN.md`。10 个 prompt + 1 个 shared template 共 428 行；
`AgentResultPromptContract`、parser、validator 与 permission/change-boundary owner 已逐类对账。结论是 D2/D3 暂无满足
直接删除条件的规则组：validator 只负责拒绝，不能替代 producer interface；implementation/self-test 也没有完整
`.agent/` filesystem guard。D1 复核则发现原候选范围过窄：不只是 `approveDesign()`，`FileLedgerStore` 的 11 个
legacy design-loop prepare/complete/fail/approve/revise public methods 全部 production-unreachable，仅由旧测试消费。
继续沿引用闭包核对后，`PreparedRunView`、`RunDocument`、`ArtifactManifest`、`CodexReviewControl`、
`HumanGateDocument` 也完全没有正式 workflow consumer；若只删 11 个入口会留下 dead type 与 legacy document factories。
下一步只允许独立评审“删除整个 legacy design-loop writer family”的原子 checkpoint；PASS 前不改 production，
不删除 prompt，不调用 provider/real-case。

独立架构评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`，并补出唯一 sibling：
`WorkstreamPaths.currentHumanGateJson()` 的全部消费者同属 dead family。checkpoint 已按评审边界实施纯删除：移除 11 个
legacy public writer、5 个 dead DTO/control type、仅由旧流程消费的 factory/mutator、human-gate path helper 及专属测试；
保留 initialization/status/timeline compatibility record shape 与正式 workflow human gate owner。机械 sibling sweep 已确认
11 个旧入口、5 个 dead type、legacy factories/mutators 与 `currentHumanGateJson()` 在 main/test 中全部零命中；focused
初始化、状态与正式 workflow gate slice 为 `53/0/0/0`。非沙箱 full Maven reactor 9/9 modules、
`1236/0/0/0`，`git diff --check` clean；沙箱内第一次 full run 的两项错误均来自 macOS 拒绝
`ProcessHandle.descendants()` 所需的 `sysctl`，在非沙箱同一代码上通过，不属于删除回归。当前只允许独立代码级 diff
review。独立 diff review 已 `PASS（P0=0 / P1=0 / P2=0）`，并独立复现核心 focused `51/0/0/0`、full
reactor `1236/0/0/0` 与 clean diff-check；确认 D1 成熟度为 `VALIDATED`，正式 human/design transition owner 继续为
`ENFORCED`。D1 已满足选择性提交条件。下一步仅评估一个 D2/D3 subtraction 候选；在完成字段级信息增量 diff、
deterministic counterfactual 与独立评审前，不删除 prompt，也不调用 provider/real-case。

D2/D3 单一候选筛选未发现值得 production 实验的删除项：intake deny-all 已机械覆盖的工具禁令虽可局部删除，但 token
收益远低于一次 full real-case 成本；D3 的 validator 仍不能替代 producer interface。因此没有为了兑现 subtraction 而
制造低价值 prompt checkpoint。随后按 retrospective 的高价值候选进入 A8 bounded assessment，结果见
`docs/plans/a8-self-test-deterministic-executor-bounded-assessment-20260827.zh-CN.md`。captured captcha-auth 证明 A8
成本真实（8 calls、约 38.5 分钟），但也证明有效 self-test 曾从失败堆栈发现真实实现缺陷；当前 production 没有
authoritative canonical verification command，直接 deterministic replacement 会新增 heuristic owner 并丢失 evidence
sufficiency/routing 语义。当前判定 `NO-GO FOR DIRECT REPLACEMENT`，等待一次独立只读架构评审；PASS 后关闭 A8，
转入 small workflow profile assessment，不改 production、不调用 provider/real-case。

A8 独立评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`。成本拆分后，5 个 adapter/output failures 占总时长约
69.5%，3 个有效语义调用约 11.8 分钟；failure-rate 是独立 execution reliability 轴，不能当作 replacement 收益。
评审确认现行 self-test evidence boundary 为 `ENFORCED + PROVEN`，A8 assessment 为 `VALIDATED`，deterministic
replacement 为 `NOT MODELED`。A8 就此关闭；当前进入 small workflow profile bounded assessment，只读判断哪些自然
小任务可以删除完整 Agent node，而不替换或削弱 typed contract、human authority 与 verification kernel。

small profile assessment 已落在
`docs/plans/small-workflow-profile-bounded-assessment-20260827.zh-CN.md`。A2 因 `ContractCompileHandler` 的 validated
product-review identity 要求保留；A4 因 captcha-auth 已证明真实 defect yield，不作为首个实验。唯一候选为 A6 plan
review：以 terminal-delivery@5 为基线，在 test-only external definition 中删除 plan-review context、A6 和 plan decision，
让 A5 typed plan 直接进入既有 human plan gate。预期 31/51/9/6 -> 28/46/8/6；A1-A5、A7-A9、所有 human gates、
TaskContract、自测、acceptance 与 delivery kernel 不变。当前只允许独立架构评审；PASS 前不制作 definition、不改
production、不调用 provider/real-case。该架构评审现已完成，下面记录已授权的 deterministic checkpoint 结果。

独立架构评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`；P2 已分别落实为 A5 BLOCKED ledger parity 与
gate-owned revision budget 测试。test-only external definition 已生成，SHA-256 为
`d41b835e4d8a8347b64984e085385030246976bbc485a866f215dfafc97faaec`，精确为 terminal-delivery@5 删除 3 nodes、
6 incident edges并新增 A5 -> human plan gate 一条 edge。focused tests `7/0/0/0`，full reactor
`1243/0/0/0`，diff-check clean；production 与 bundled definitions 零修改。当前 profile graph/ledger boundary 为
`VALIDATED`，production variant `NOT MODELED`，real-case `NOT PROVEN`。下一步仅允许独立代码级 diff review；PASS 后
选择性提交并制定一次 natural small-task Verification Gate，不直接启动 provider。

独立代码级 diff review 已 `PASS（P0=0 / P1=0 / P2=0）`，并独立复现 focused `7/0/0/0`、full reactor
`1243/0/0/0` 与 clean diff-check。评审确认六文件构成单一 test-only checkpoint、零 production 变化，满足选择性
提交条件；checkpoint 已选择性提交为 `e369a6e7`。

natural small-task Gate 草案已落在
`docs/testing/verification-gates/20260827-small-workflow-darwin-list-json.md`，冻结任务为
`docs/testing/real-case-tasks/20260827-darwin-list-json.md`。目标是在 clean
`darwin-skill-optimizer` 上为 `darwin list` 增加 opt-in `--json` 输出：默认文本行为不变、JSON shape/排序/空列表/坏条目
可见性/无写入均可机械验收。该任务有真实脚本消费价值，范围仅为 CLI、focused tests 与 README，不需要数据库、网络或
新依赖。

Gate 同时把质量与收益钉死：A5 typed plan 必须直接进入用户 human plan gate；A6 三节点不得存在或执行；Agent runs 与
provider occurrences 必须恰好 `8`，correction 与 semantic revision 必须均为 `0`。首次 revision 已使 9 -> 8 目标失效，
因此立即记录 FAIL 并止损，不继续消耗 provider 来追求产品完成。`run-real-case.sh` 会 autorun/auto-approve human gate，
不适用于本 Gate；执行必须使用 fresh JAR 分段推进并由用户本人处理 gate。当前只允许独立同行评审 Gate、task 与 active
plan；评审 PASS 前不运行 package/probe，不创建 workstream。

Gate 独立评审为 `WARN（P0=0 / P1=1 / P2=2）`：冻结 target 的三个模块依赖不存在的
`/Users/krisdwang/.hermes/skills/...` 绝对路径，完整 pytest 在 collection 阶段固定产生 3 个
`FileNotFoundError`，原“full tests PASS”前置不可执行。修订没有把这些错误伪装成 PASS：task 与 Gate 现在分别冻结完整
collection 的三个文件/异常/缺失路径签名，并单独要求其余 runnable suite 全绿。独立复核得到
`234 passed / 0 failed / 0 errors`；完整 collection 恰为预登记的 3 errors。pytest executable/runtime/version 也必须在
preflight 记录，缺失或漂移为 INVALID。有效样本中的 provider/adapter fatal 已明确归为 execution FAIL。P1/P2 文字缺口
现已关闭；task SHA 更新为 `5c0d9f8d9507b75ed72eecd1d4740133ec9e20f67a9420856d9f87ef9e0b2362`。当前只允许
选择性提交 Gate、task、active plan 与独立 review；提交后仅授权 fresh package 和 non-provider preflight，provider probes
与 fresh workstream 仍未授权。

该 natural real-case 已执行并按预定义 stop-loss 收口。small profile 确实删除 A6，前 8 个 provider occurrences 无
correction；但 acceptance 在实际 execution worktree 发现 `238 passed / 2 failed` 并 typed `REJECT`，workflow 创建
revision 与第 9 个 run，因此 `9 -> 8 且质量不退化` 为 `FAIL / NOT PROVEN`。第 9 个 run 已终止并 stale recovery，最终
workflow 为 `WAITING_FOR_RECOVERY`，不 resume、不重跑同一样本。

只读对照证明失败不是 `list --json` 局部实现：同一 target HEAD 的 source checkout runnable baseline 为
`234 passed / 0 failed`，fresh worktree 在还原三个任务文件后仍有两项 snapshot failure。根因是目标测试以 tracked
fixture 的 `st_mtime` 选择“最新”工件，而 Git worktree checkout 改写 mtime；这暴露了共性 verification 缺口：source
checkout baseline 不能证明实际 execution workspace eligibility。

候选比较与独立评审已提交为 `fddb356a`，结论为 `PROCEED_WITH_GUARDS`。implementation plan 随后通过独立架构评审
`PASS（P0=0 / P1=0 / P2=2 非阻塞）`，两项 P2 已落实为含空格路径测试和 `StartCommand.java:77-79` locator
漂移 fail-visible 说明。harness-only checkpoint 已实施：runner 时序为
`start(no --run) -> persisted workspace capture -> pre-run verify -> run -> autorun`；configured check 的 non-zero、identity
漂移、tracked dirty 或 evidence 缺失均在首次业务 Agent 前 INVALID，legacy 无 check 明确投影
`not_configured/false`。pre-run interrupt 不再伪造 stale recovery/session reap。focused 三份测试与全部 8 个 shell tests
均 PASS，syntax 与 diff-check clean；Java/workflow/schema/provider 零变更。独立代码级 diff review 已
`PASS（P0=0 / P1=0）`，checkpoint 已选择性提交。下一步不制造第二个 Darwin 样本，也不恢复
`ws-20260827T051220Z-2U4R`；等待下一次自然且明确配置 workspace check 的真实任务，用完整完成结果评估 `PROVEN`。

该自然任务已从 captcha-auth-app 的真实未完成可用性能力中选定：为本地开发加入 Mailpit-backed email OTP 注册链路。
已验收的 captcha-auth 实现先以 `de59e98` 合入 target master；任务说明与 target-owned workspace check 以 `dc0ea59`
提交。workspace check 将 Docker 29/Testcontainers 的既有 API 兼容条件局部限定为 `-Dapi.version=1.44`，未修改产品依赖，
并独立复现 `99/0/0/0`。新的 small-workflow Gate 已冻结 Dual、workflow、target、task 与 check identity；当前只允许独立
Gate review，不调用 provider、不创建 workstream。

Gate 独立评审已 `PASS（P0=0 / P1=0）`。唯一 P2 的授权顺序歧义已消除：本轮 non-provider preflight 只允许
fresh package、workflow validation 与 source-side identity/hash 复算；workspace capture/verify 明确归入后续
fresh-workstream 授权。non-provider preflight 已从 Gate clean SHA `26070ca6` 完成：clean package 与 fresh CLI/server
JAR、external workflow validation、Dual checkpoint ancestry、target HEAD/tree/cleanliness 及 task/check SHA 全部 PASS；
typed evidence 保存于 ignored `target/verification-preflight/captcha-auth-mailpit-small/non-provider-preflight.json`。
该时点尚未调用 provider 或创建 workstream，后续双 probes 与 fresh workstream 结果如下。

双 provider probes 随后均 PASS 并闭合 session；唯一 fresh workstream `ws-20260827T142330Z-FX2Y` 完成 persisted
workflow identity 与 execution-workspace capture/verify。首轮 Claude intake strict output PASS，但 prompt 禁止全部工具且
投影的 `repo-map.files`、`contractScopeCandidates` 均为空；Codex reviewer 因 scope 无仓库事实与 Mailpit 测试隔离不足返回
`NEEDS_REVISION`。Gate 首次 revision stop-loss 因而触发；Dual 创建了 revision execution，但 captured run 仍停在
prepared 边界（无 command/PID/process-start/provider occurrence），随后由 stale reconciler 分类为 `TIMEOUT`。sessions
已关闭，ledger 已收口到 `WAITING_FOR_RECOVERY`。small-profile `8 calls / 0 revision` 为 FAIL，产品未完成。

根因不是模型格式或环境，而是 initial contract 的事实获取循环：首次 TaskContract 不存在，context projection 又从已有
TaskContract scope 派生 candidates，同时 seed-only prompt 禁止 Agent 获取新事实。通用修复计划已落在
`docs/plans/initial-contract-fact-acquisition-plan-20260827.zh-CN.md`。首轮独立架构评审 `PASS（P0=0/P1=0）`，其三个
P2 已通过代码事实核实：execution 与 provider turn 的边界已改正；`AgentCapabilityCatalog` 纳入一致性边界；acpx policy
优先级在本仓与已公开 adapter surface 中无法证明，因此实现不再依赖 `--deny-all` 与 policy 的组合优先级，而是使用
terminal-disabled、无 write/execute approval 的明确 read/search profile。进一步核实发现 initial semantic `BLOCKED` 并非合法 contract shape，
且当前只有 read telemetry、没有可复用的 pre-call 数字预算 owner；修订计划因此明确不扩 schema/graph、不用 post-run hard
count 再次牺牲正确性，只强制 read/search-only workspace boundary，并让 reviewer/human 继续拥有 scope 完整性裁决。
delta 架构复审已 `PASS（P0=0 / P1=0）`。checkpoint 已实现 Git tracked-path inventory、identity 重验、bounded
read candidates、actual-read SHA evidence、read/search-only permission 与 capability/prompt 一致性；proposal 在非空 inventory
下必须至少引用一个 tracked fact，其他既有 durable evidence refs 保持合法。当前仅进行 deterministic verification 与独立
代码级 diff review。focused runtime + adapter 为 `116/0/0/0`，revision/compile 补充切片为 `41/0/0/0`；非沙箱 full
Maven reactor 9/9 modules、`1246/0/0/0`，`git diff --check` clean。成熟度为
`FACT ACQUISITION ENFORCED / REAL-CASE NOT PROVEN`。独立代码级 diff review 已
`PASS（P0=0 / P1=0 / P2=2 知会）`，并独立复现 focused `180/0/0/0`、full reactor
`1246/0/0/0` 与 clean diff-check；checkpoint 满足选择性提交条件。不 resume captured workstream、不调用 provider。

checkpoint 已选择性提交为 `cca00827`。新的 cold-start Gate 草案已落在
`docs/testing/verification-gates/20260828-small-workflow-captcha-auth-mailpit-fact-acquisition.md`。它不再把 inventory 存在
等同于事实闭环，而是同时要求：initial Read/Search execution fact、actualReadEvidence path/SHA、proposal evidence 与
repo-map 交集、typed product review READY，以及最终 8 calls / 0 correction / 0 revision / workflow COMPLETED / Mailpit 产品
验收全部成立。旧 `FX2Y` 只保留为 defect evidence；因 initial occurrence 已由旧 checkpoint 执行，不得 resume 来冒充
cold-start proof。当前只允许独立只读评审 Gate 与 active plan；不执行 package、probe 或 provider。

Gate 独立评审已 `PASS（P0=0 / P1=0 / P2=2 知会）`。inventory SHA 的强制判定 owner 已明确为
`TaskContractProposalValidator` 的 production re-capture；operator 等价命令仅作报告交叉核对。Gate 具备选择性提交条件；
提交后下一步为 fresh package、external workflow validation 与 source-side frozen identity/hash 复算，仍不创建 workstream、
不调用 provider。

Gate 与 active plan 已提交为 `f69a161c`，fresh package、workflow/identity preflight 与 Claude/Codex minimal probes 均
PASS。用户授权后创建唯一 fresh workstream `ws-20260827T232026Z-EFCB`；execution-workspace preflight 为
`99/0/0/0`。首个 Claude intake occurrence 实际产生 `17` 次 read/tool usage、`16` 条 path/SHA read evidence，proposal
通过 strict parser、inventory re-capture 与 evidence traceability，Codex product review 返回
`READY_FOR_PRODUCT_REVIEW / blocking=false / must_fix=[]`，随后合法停在 human requirement gate。

early boundary audit 发现该样本不能继续：contract-discovery 使用的 Git inventory 写入历史共享路径
`memory/repo-map.json`，后续 product-review context build 又无条件覆盖同一路径。validator 在当时消费的是正确 inventory，
但 proposal lineage 没有绑定该 occurrence 的 repo-map ref/SHA，最终 audit 无法从 durable ledger 重建判定依据；actual
governance writer 同时还以 mtime latest 选择 plan/policy/catalog。样本因此判为
`INVALID_EVIDENCE_DURABILITY`，不是模型/provider/product FAIL。workstream-scoped sessions 已关闭；未批准 gate、未 resume、
未创建第二样本。

通用修复与问题发现方案已落在
`docs/plans/repository-fact-occurrence-ownership-and-boundary-discovery-plan-20260828.zh-CN.md`。目标是把 repository fact、
plan/policy/catalog 与相关 governance projections 绑定到不可变 run occurrence；validator、actual report、proposal sidecar
lineage 与 final audit 消费同一 ref/SHA；workstream memory 只保留显式 current/cumulative projection。验证纪律从单步字段
检查升级为 producer -> consumer -> sibling writer -> pause/restart -> cold audit 的 temporal sequence，并在 real-case human
gate 与 terminal completion 前设置 typed boundary sentinels。当前只允许独立只读架构评审该计划；不授权编码、provider、
resume EFCB 或新的 workstream。

独立架构评审已 `PASS（P0=0 / P1=0 / P2=3 非阻塞）`。decision 名已修正为
`READY_FOR_PRODUCT_REVIEW`，并补充 captured plan 只残留 inventory HEAD/SHA、无法重建完整路径事实的精确边界。
评审要求的 reader/writer/sibling sweep 已关闭 binding 唯一落点：immutable binding JSON 是唯一 authority；
`PreparedContext` 仅 transient 传递；adapter 把 binding ref 加入 `NodeResult.outputRefs`；proposal sidecar 只保存 ref/SHA
作为 lineage anchor；actual report 只投影同一 ref/SHA；`run.json` 不重复保存 binding。`TaskContractProposalReference`
read path 负责冷启动全链重验，现有 ledger `CompletionAuditWriter` 不被扩张为 runtime repository semantic owner。
计划现为 `IMPLEMENTATION READY`，下一步只允许实施该单一 deterministic checkpoint 与 sequence tests；仍不调用 provider、
不 resume/批准 EFCB、不创建新 workstream。

occurrence ownership checkpoint 已按该原子边界实施。repository fact、plan/policy/catalog、working-set、file summaries、
read-reuse contract 与 planned report 现在写入 `nodeInstanceId + runId` 唯一目录，并由 immutable binding JSON 记录每个
artifact 的 ref/SHA 与 Git inventory identity；累计 `workstream-memory.json` 仅保存 latest projection refs。adapter 在
prompt 前持有 typed binding，并把其 ref/SHA 作为 machine-owned result facts 传给 validator；proposal sidecar 在写盘和
计算 reference SHA 之前写入 binding anchor，`TaskContractProposalReference.readVerified` 冷读取时重验 binding 与全部
artifact。actual report 只消费传入 binding，旧 fixed-global 与 mtime-latest authority 已从 production 删除。

cannot-bypass 测试在实施中实际抓出并关闭两处顺序缺口：binding ref 曾被旧“倒序最后 JSON”逻辑误当 Agent envelope；
sidecar 曾在 binding 字段注入前写盘，导致字段只存在于内存 map。现在前者按 adapter-owned binding ref 排除候选，后者在
typed semantic validation、写盘与 reference SHA 前完成注入。sequence tests 证明同 node 不同 run 不覆盖、后续 sibling
build 不改变旧 occurrence 字节、global repo-map 不能替代缺失 binding、artifact 篡改 fail closed、cold proposal reference
会重验 binding。focused runtime `128/0/0/0`、adapter `47/0/0/0`；非沙箱 clean full reactor
`1248/0/0/0`；`git diff --check` clean。当前成熟度为 repository fact occurrence ownership `ENFORCED / NOT PROVEN`；
等待一次独立代码级 diff review，PASS 前不提交、不调用 provider、不 resume/批准 EFCB、不创建新 workstream。

独立代码级 diff review 已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`，并独立复现 full reactor
`1248/0/0/0` 与 clean diff-check。P2-1 记录 binding 非原子直写的 fail-closed availability 残余，归入既有 R1
revisit trigger，不在本 checkpoint 扩张写协议；P2-2 确认旧 workstream 新校验 fail-closed 符合 no-fallback/no-resume
政策。checkpoint 已选择性提交为 `b25d211`，repository fact occurrence ownership 达到 `ENFORCED / NOT PROVEN`。

新的 cold-start Gate 草案已落在
`docs/testing/verification-gates/20260828-small-workflow-captcha-auth-mailpit-occurrence-durability.md`。它把主 PASS 定义为
occurrence durability 与 Mailpit 产品 E2E 同时完成，并在每个 human gate 前与 terminal acceptance 前执行 durable cold
audit；`8 calls / 0 revision` 降为独立 efficiency 子结果，合法 revision/correction 不再自动使可完成的产品任务失败。
独立 Gate 评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`，确认 occurrence durability、产品完成与 efficiency 子结果已
正确解耦，typed/durable SoT、FAIL/INVALID、stop-loss、cold audit 与授权边界均可执行。P2-1 已吸收为 case-report 侧的
精确只读命令清单：每个 proposal/run 元组从 routed `.ref.json` 冷定位 sidecar 与 binding，逐项复算八个 artifact SHA、
inventory identity、actual report 与 current projection refs；该清单只保存 Gate evidence，不成为 production validator 或
路由 owner。P2-2 的长样本风险由 90 分钟 active-runtime 上限、human pause 不计时和同 blocker 二击止损继续约束。
当前仅授权选择性提交 Gate 与 active plan；提交后 package、workflow validation 与 source identity 复算仍需按 Gate 边界
执行，provider probes 与唯一 fresh workstream 分别等待独立授权。不调用 provider、不 resume/批准 EFCB、不创建新
workstream。

### Completed H1 Checkpoint

权威说明见 `docs/design/e2e-harness-boundary-retrospective.zh-CN.md` 第 3.1、3.4 和 10 节。当前先把 H1 的
qualified contract owner、完整投影、soft warning、token accounting、hard-capacity ownership 与 cannot-bypass
tests 写成一个原子实施计划；计划草案已落在
`docs/plans/h1-mandatory-task-contract-projection-plan-20260826.zh-CN.md`。首轮 CC 评审为 WARN：指出 Phase 3
未定义 `PRESERVE_FULL`、EvidenceManifest 漏出单快照边界。修订已闭合两项 P1，并额外把 acceptance agent
dispatch 时的 governance 重建纳入同一 SHA identity。第二轮 delta review 再次为 WARN，确认设计正确但指出 metadata
当前不在 output refs；终版修订明确只由 acceptance context 路由 metadata，prompt projector 将其作为 machine-only ref
过滤，其他 profile 的 input/output refs 不变。最终 delta review 已 PASS，单一 checkpoint 已完成实现。focused runtime +
adapter 为 `83/0/0/0`；full Maven reactor 9/9 modules、`1270/0/0/0`；`git diff --check` clean。测试已证明
compile SHA、latest compile qualification、qualified snapshot 四消费者、>2500 token 全文保留、Phase 3/Phase 5
不可降级或删除、override fail-before-write、acceptance-only metadata routing、metadata 不泄漏及
dispatch-before-transport typed 拒绝。deterministic PASS 不冒充 real-case `PROVEN`。

独立代码级 diff review 已 `PASS（P0=0 / P1=0 / P2=3 非阻塞）`，并独立复现 full reactor
`1270/0/0/0` 与 diff-check。评审确认无第二 owner、TOCTOU、raw metadata 泄漏或 budget bypass，满足单一原子
checkpoint 的选择性提交条件。成熟度口径为 qualified projection `ENFORCED`、provider hard-capacity
`NOT MODELED`、real-case value `NOT PROVEN`；提交后进入 H4 计划，不为 H1 制造 provider 样本。

## 历史执行记录：Human Revise Revision-Context Boundary / v16 FAIL, CONSUMED

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

## 2026-08-30 E1 Policy Transport Checkpoint

OKX real-case 暴露的通用 execution-policy transport 缺口已完成 deterministic 实施：

- acpx fork 固定于 upstream `fd173f04...`，reviewed patch SHA 为 `304577dc...`；manifest 已修正
  权威仓库 URL，并新增 annotated tag object 与 npm tarball SHA；
- installer 使用 Node >=22.13 复跑 upstream `973/0/0/0`，build identity 与 audit evidence 落盘；
- Dual 对 named-profile Claude occurrence 生成固定 policy，ensure/new/prompt/correction 绑定同一 ref/SHA，
  删除、篡改、越界或 lineage 漂移在 provider side effect 前拒绝；
- legacy `approve_all` 保持可运行，但 `run.json.executionAuthority` 明确为
  `NETWORK_AUTHORITY_NOT_CONFIGURED`，不继承 E1 成熟度；
- wrapper 收窄为固定 provider env allowlist，Doctor/configure 对 fork identity fail closed；
- Dual full reactor `1294/0/0/0`，fork `973/0/0/0`，shell tests 与 `git diff --check` clean。

成熟度仅为 `E1 POLICY TRANSPORT ENFORCED / SANDBOX EFFECT NOT PROVEN`。独立 Dual diff review 已
`PASS（P0=0 / P1=0 / P2=4 知会）`，并独立复现 focused `95/0/0`、full reactor
`1294/0/0/0`、fork installer `973/0/0/0` 与 clean diff-check。四项 P2 已登记为 capability proof
输入：system tmpdir scratch 语义、official acpx 参数硬失败、ignored installer audit 与 operator-trusted
`DUAL_ACPX_EXECUTABLE`。离线 capability proof Q1-Q3 已完成：Q1 `PASS`；Q2 transport/env separation
`PASS`、provider connection `NOT PROVEN`；Q3 option consumption `VALIDATED`、OS sandbox behavior
`NOT PROVEN`。直接裸跑 upstream suite 因绕开 installer identity 注入并受当前进程权限限制而判
`INVALID`，相关进程已全部终止，正式 installer 已重跑并恢复 `973/0/0/0`。证据位于
`docs/testing/capability-proofs/20260830-e1-policy-transport-offline-replay/`。下一 active step 只能是制定
Q3 behavioral effect + Q4-Q15 的独立 Verification Gate；provider、旧 OKX workstream resume 与新 real-case
均未授权。

E1 真实能力 Gate 已起草为
`docs/testing/verification-gates/20260831-e1-claude-sandbox-effect-capability.md`，并冻结 package-local launcher、
network matrix script、Bash prompt 与 WebSearch prompt 四个 fixture。Gate 将原 Q3-Q15 拆为 provider capability、既有
deterministic enforcement 与 implementation-deferred 三类，避免用尚未实现的 Q10/Q14 形成循环前提。当前只允许独立只读
评审 Gate/fixtures；两个带工具的 Claude prompts 不属于 standing minimal health probe，尚未授权。

Gate 独立评审已 `PASS / READY（P0=0 / P1=0 / P2=5）`，fixture SHA、编译配方、production writer/transport 与
WebSearch 启用形态均经独立复现。五项 P2 已作为小 delta 吸收：npm/Maven 改为已知存在工件形成 hard discriminator；
tool env 任一 credential/proxy presence 立即 exit 73；session conflict 补精确 ensure 命令与 prompt-count 反证；sentinel
固定在空专用目录；case report 强制登记 WebSearch 为 Gate operator opt-in、未执行 production human authority。当前进入
文档 checkpoint 提交与 non-provider preflight，两个带工具 prompts 仍需一次性明确授权。

Gate checkpoint 已提交为 `49495dbe`；non-provider preflight 通过：fresh reactor `1294/0/0/0`、E1 focused
`95/0/0`、fork installer `973/0/0`，Doctor/build identity/fixture SHA/launcher compile 均 PASS。用户随后明确授权一次
minimal health 与两个 tool prompts。minimal health fresh+closed 且回答 `OK`。Bash probe 在 workspace/system tmpdir 写读成功后，
发现 tool subprocess 可见 `ANTHROPIC_AUTH_TOKEN`、`ANTHROPIC_BASE_URL` 与 8 个大小写 proxy 变量，按冻结脚本
`exit 73 / E1_TOOL_ENV_LEAK` 触发首个 stop-loss。WebSearch、session conflict 与任何后续 tool prompt 均未执行；Bash session
已关闭，sentinel 已停止。Gate 结论为 `FAIL`，不是 `INVALID`；E1 sandbox effect 与 WebSearch coexistence 仍 `NOT PROVEN`，
禁止实施 `PUBLIC_NETWORK_DOMAIN` 或恢复 OKX。当前唯一动作是 env propagation owner 的 bounded comparison。

用户升级 standalone Claude Code 到 `2.1.236` 后，核实 Dual 实际执行链仍由 acpx 配置固定在
`claude-agent-acp 0.46.0 / embedded Claude Code 2.1.178`，standalone CLI 不拥有 ACP runtime。现已把用户级 acpx
配置与 wrapper fallback 同步到 `claude-agent-acp 0.70.0`；fresh handshake 证明实际依赖
`claude-agent-sdk 0.3.232 / embedded Claude Code 2.1.232`，Doctor 与 wrapper test PASS。

随后在 detached clean source `49495dbe` 上重跑同一 frozen Bash fixture。fresh health PASS；Bash tool 中
`ANTHROPIC_API_KEY`、`ANTHROPIC_AUTH_TOKEN`、`ANTHROPIC_BASE_URL` 已全部 absent；但八个大小写 proxy 变量仍 present，
冻结脚本按原规则 `exit 73`，故程序性记录为 `FAIL / STOP-LOSS EXECUTED`，未运行网络矩阵、WebSearch 或 session conflict。
后续核实发现新版 Claude sandbox runtime 包含为 policy-enforcing local proxy 注入 HTTP(S)_PROXY 等变量的实现，而
presence-only fixture 既把空变量算 present，也无法区分受控内部代理与宿主泄漏。因此 proxy/network capability 的语义结论修正为
`INVALID_OBSERVATION_MODEL`；三个 Anthropic 名称不可见仅保留为 captured fact，不以未做消融对照的单样本宣称机制 PROVEN。
证据位于 `docs/testing/capability-proofs/20260831-e1-claude-sandbox-effect-49495db-r2/`。下一步只允许修订 proxy provenance
观测与 network-denial Gate；不得删除受控 sandbox proxy、不得恢复 OKX 或实施 `PUBLIC_NETWORK_DOMAIN`。

R2 Gate 与独立 fixture 已新增：proxy URL 只输出 `absent/empty/controlled_loopback/external`，`NO_PROXY` 只输出固定分类，
不落原始值；新增 unset 全部 proxy 后的 direct/loopback/LAN 三条绕过测试，最终 PASS 必须同时依赖完整网络矩阵与 structured
denial evidence。离线分类测试覆盖 absent、loopback、empty、external 与 wildcard bypass，当前 PASS。R2 独立评审与 clean
checkpoint 前不调用 provider。

## 2026-09-01 E1 R3 Documented Contract Result

R3 Gate/fixture 已经独立评审 PASS，并分别以 `5c26c0df`、`0d70d3f5` 冻结。fresh package、Doctor、
launcher compile、离线 fixture、runtime identity、宿主 loopback/LAN/external/direct-socket precheck 与 minimal
health 均通过。唯一 Bash capability occurrence 恰好执行一次冻结命令，随后在任何网络请求之前返回
`exit 73 / E1_PROXY_CREDENTIAL_LEAK HTTP_PROXY`；三个 Anthropic credential 名称均 absent，但
`HTTP_PROXY` 分类为 `present_with_userinfo`。按 Gate 立即 stop-loss；WebSearch、网络矩阵、retry、correction 与
第二样本均未执行，两个 session 已关闭，sentinel 已停止。

程序结论为 `FAIL / STOP-LOSS EXECUTED`。这证明 Dual policy 中的 `subprocess_denied_env_names` 没有成为
最终 effective Bash environment 的完整 enforcement，但不能证明宿主凭据泄漏或 Claude sandbox 不安全：当前无值探针无法区分
宿主 proxy credential 与 Claude 内建 sandbox proxy 注入的 session-scoped capability。下一步仅允许 bounded provenance
comparison，明确 value owner、注入阶段、scope/lifetime 与 exec 前唯一验证点；不得清空受控 proxy、放宽 Gate、重跑 provider、
实施 `PUBLIC_NETWORK_DOMAIN` 或恢复 OKX。

R3 result 独立评审 `PASS（P0=0 / P1=0 / P2=4）`，结果与证据已提交为 `fc0b1efe`。四项 P2 已如实
登记：sentinel closure receipt 与显式时间戳缺失、operator 语境 Python EPERM 不得跨语境归因、tool 等值仅以
`command` 字段判定。随后完成 bounded comparison：wrapper 已在 adapter spawn 前清除全部宿主 proxy，captured
session 又使用 `settingSources=[]`，因此不再为已有 deterministic 边界建设 HMAC provenance 平台。推荐 Candidate C：
固定 `network_transport_owner=claude_sandbox`，sensitive-env deny 仅拥有 provider runtime inputs，wrapper scrub 成为该 owner 的
ambient-env precondition；不检查 Claude 内部 proxy URL，不设置 custom proxy port。当前只达到 `ASSESSED / NOT IMPLEMENTED`，下一步
仅允许独立架构评审 comparison 与实施边界，不授权 production 修改或 provider。

## 2026-09-01 E1 Network Transport Owner Checkpoint

bounded comparison、实施计划评审与 P0 delta 复审均已 PASS，production checkpoint 已按单一边界实施：

- `ClaudeExecutionPolicy` 升级为 `claude_execution_policy.v2`，固定
  `network_transport_owner=claude_sandbox`；provider credential deny 仅保留三个 Anthropic 名称；
- reviewed acpx fork 在 Claude E1 child 的全部 env merge 与 executable resolution 完成后、spawn 前删除八个
  ambient proxy 名及 ambient `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`；非 E1、非 Claude sibling 不变；
- fork loader 继续以 exact keys、SHA、lineage 与固定值校验 policy；半绑定或非 Claude binding 在 child spawn 前拒绝；
- patch、manifest、configure、Doctor 与安装 build identity 统一为
  `fd173f04 / fc2783a8b5aa2a450007b9e5516ca473d9a5e5785d8760b342e230ea180c60cc / claude_execution_policy.v2`；
- installer 在 Node 22.21 正常权限环境复跑 fork `978/0/0`；Dual focused `95/0/0`，正常权限 full reactor
  `1294/0/0/0`，wrapper test 与 `git diff --check` PASS。Codex 沙箱内首次 reactor 的两个 ProcessHandle
  `sysctl EPERM` 属验证环境 INVALID，正常权限重跑已消除。

准确成熟度为 `NETWORK TRANSPORT OWNERSHIP ENFORCED / SANDBOX EFFECT NOT PROVEN`。当前只允许独立代码级 diff
review；review PASS 并 clean commit 后，才制定新的 capability Verification Gate。不得在本 checkpoint 中调用 provider、
恢复 OKX、实施 domain approval 或宣称网络效果已证明。

## 2026-09-02 E1 acpx Persistent Policy Binding Replacement Guard

独立 diff review 已 `PASS / READY TO COMMIT（P0=0 / P1=0 / P2=4）`。manager replacement、direct prompt、
queue owner 与 fresh-session fallback 四条绕过路径均在 client/record/prompt/lease 副作前由唯一
binding comparator fail closed。reviewed patch identity 为
`81acbb7491a17aedfb48b1a73f077b59e06f1d5eceb7cd617e59df86aa5c60ab`。

提交前在受支持 Node `22.21.0` 与正常 Unix socket 权限下复跑 fork full suite：`985/0/0`。
高负载下曾有一次不可复现的 upstream timing 失败；处置纪律是提交必须有完整 clean pass，同一失败可连续复现则阻断并单独治理，
不用无限重跑挑选结果。其余 P2（参数化 replacement 矩阵、resume 保留 options 显式断言、Doctor/manifest 机器交叉断言）
在下次触及对应文件时作为 sibling test 要求，不为它们改变已评审 patch identity。

当前成熟度：`PERSISTENT POLICY BINDING REPLACEMENT GUARD ENFORCED / E1 FULL CLOSURE NOT PROVEN`。
下一步是选择性提交本 checkpoint，然后起草新版 E1 closure Verification Gate；继续禁止 OKX resume、
real-case、`PUBLIC_NETWORK_DOMAIN`、custom proxy、第二样本与 WebSearch visibility 实施。

## 2026-09-02 E1 Closure R4 文档与 Verification Gate

E1 从 OKX 个案中抽离出的通用目标、owner 分层、历次失败根因与成熟度边界，已统一写入
`docs/design/e1-controlled-network-access-convergence-retrospective.zh-CN.md`。结论不再把 WebSearch、宿主代理或
单个 HTTP 错误字符串混入 Claude Bash egress 的证明：Dual 声明 occurrence policy，reviewed acpx 负责不可变绑定与
child-spawn 收敛，embedded Claude sandbox 负责 OS/network enforcement，执行证据负责证明效果。

新版 `20260902-e1-claude-bash-egress-closure-r4.md` 已起草并冻结 fixture。它使用一个 fresh occurrence、一个 prompt、
一个 Bash call，在同一 policy 中合取验证 allowlisted `example.com` 成功、未列入域失败、child/Git 不可绕过、三类 IPv4 TCP
socket 返回 `EPERM`、credential/filesystem 不退化，以及 policy→SDK options→stream 与 record ref/SHA 的链式一致性。宿主开启
Shadowsocks/TUN 时只记录 `host-path reachability`，不再声称宿主直连；WebSearch、real-case、domain approval、Codex 与
容器明确排除。

初版 fixture offline test、shell syntax 与 production-class launcher compile 已 PASS，fixture identity 已冻结。当前 active step
仅为独立同行评审 Gate/fixture；评审 PASS 且 non-provider preflight 全部满足后，用户既有 provider-turn standing
authorization 才生效。当前不得执行 capability occurrence、恢复 OKX、启动 real-case 或创建第二样本。

独立子代理评审判 `FAIL / NOT READY（P0=0 / P1=4 / P2=1）`：原稿缺统一 runner/evaluator；implement profile 使用
`sessions ensure` 且成功路径不自动 close；误把 `session/new._meta` 描述成携带原始 binding 字段；任意 non-zero 不能单独
证明 domain causal enforcement；raw-socket 范围实际仅为 IPv4 TCP。修订版已吸收这些发现：增加 R4 专用
runner/evaluator（明确禁止平台化），launcher 使用字段等价的 fresh diagnostic profile 强制 `sessions new` 并在 `finally`
关闭，binding 改为 policy→canonical SDK options→stream 加 record ref/SHA 的链式验证，domain 结论降为 bounded
observation，最终声明收窄为 IPv4 TCP direct-connect boundary。该修订完成时仍只允许独立 delta 评审，provider 预授权尚未激活。

用户随后明确激活 R4 的 provider 预授权。修订后的独立 delta review 与 non-provider preflight 全部 PASS 后，可直接执行一次
fresh minimal health 和唯一 Bash capability occurrence，不再重复申请授权。该授权不包含第二样本、real-case、OKX resume、
WebSearch、`PUBLIC_NETWORK_DOMAIN` 或 custom proxy；FAIL/INVALID 仍必须立即停止。

## 2026-09-02 R4 Subtraction And Real-Case Restoration

用户质疑后重新检查主目标，确认此前把三个不同边界混在了一起：目标 Java 应用访问 OKX API、构建工具下载依赖、Agent Bash
自主联网。只有第三项属于 E1 安全治理；它没有阻止目标应用通过正常 HTTP client 完成功能，因此不应继续作为 OKX real-case
的前置条件。

本轮执行减法：删除尚未提交的 R4 Gate、runner、evaluator 与 fixture，不调用其 provider prompt；此前激活但未消费的 R4
预授权随已删除 Gate 失效。保留已经独立评审通过的 deterministic production checkpoint，包括 policy transport、network
transport owner、child env finalizer 和 persistent policy binding replacement guard。准确成熟度仍为
`NETWORK TRANSPORT OWNERSHIP ENFORCED / SANDBOX EFFECT NOT PROVEN`。

E1 完整网络安全收敛降为重要但不紧急的 TODO。未来若真实任务需要 Agent Bash 主动联网，优先采用 typed
`NETWORK_ACCESS_REQUIRED(domain, purpose)` 加 human `deny/allow_once/allow_session/allow_global` 的最小授权模型；不恢复
R4 证明平台，不把第三方 sandbox 内部实现证明设为产品交付条件。

本次范围扩大的根因已写入 E1 复盘：未验证是否阻塞主目标便提升安全观察项、混淆 transport enforcement 与第三方内部因果
证明、把 review 观察项全部视为同轮必修、让 Gate 自身平台化、违背“先有后优”。今后同一主题连续两轮 review 仍扩张时，
必须选择缩小、延期或停止；`grill-me` 仅用于高成本架构或该止损点，不作为日常小修改的固定流程。

当前 active step：读取 `ws-20260829T122452Z-9BZG` 的 authoritative ledger 状态并按现有 workflow transition 恢复 OKX
real-case。不得为恢复该 workstream 新增 E1 provider probe、网络验证平台或业务特判。

### OKX 恢复实证与 Stop-Loss

恢复过程得到以下权威事实：

- 首次 resume 在 provider 前失败，因为进程未设置 `DUAL_ACPX_EXECUTABLE`，PATH 上的官方 acpx 不认识
  `--claude-execution-policy-ref`；改用 Doctor 校验通过的 reviewed fork `81acbb74...` 后 bootstrap 正常。
- implement occurrence `run-1788344338152` 执行约 608 秒、51 次工具调用并写入大量实现，但未产出最终
  `AgentResultEnvelope`；用户明确授权一次 `retrySafe=false` 的同节点 retry。
- 冲突旧 session 关闭后，`run-1788346607263` 产出合法 `IMPLEMENTED`。独立宿主环境 `mvn test` 证明该结论不充分：
  `44 tests / 0 failures / 18 errors`，共同根因为 `CandleSyncService` 初始化时自引用 Spring proxy，触发
  `BeanCurrentlyInCreationException`。
- self-test occurrence `run-1788347257826` 在 Claude sandbox 内只能执行 22 个测试；本地 HTTP stub 因 loopback bind
  `EPERM` 无法启动，因此它如实返回 `SELF_TEST_INCOMPLETE`，但把缺口归为环境限制。该结果自动路由 repair iteration。
- repair occurrence `run-1788347523147` 完成 30 次工具调用但零写入，沿用“环境限制”结论返回 `IMPLEMENTED`；宿主环境
  `mvn test` 仍为 `44 / 0 / 18`，证明产品缺陷未修复。

本轮暴露两个新的通用 harness 缺陷：

1. **Persistent session lifecycle 与 occurrence policy binding 冲突**：workstream-scoped session 名稳定，但每个
   occurrence 的 policy ref/SHA 不同；相邻 agent 节点会在 provider 前触发
   `CLAUDE_EXECUTION_POLICY_CONFLICT`，目前每次都需 operator 手工关闭 session。binding guard 本身正确，缺的是
   workflow/adapter 对合法 occurrence rollover 的 session lifecycle 协调。
2. **Self-test execution environment 不等价**：Claude sandbox 禁止本地回环端口绑定，使常见 HTTP integration test
   无法运行；模型只能报告缺证据，后续 repair 又没有获得宿主侧真实失败。`SELF_TEST_INCOMPLETE` 被路由了，但 repair 输入
   不能得到可执行的产品错误，形成无修改循环。

已执行 stop-loss：关闭开放 Claude session，不再运行第三轮 self-test/repair，不以 prompt 或更多 provider retry 掩盖这两个
边界。当前 workstream 保持 `RUNNING`，active node 为新一轮 `implement.claude_self_test_report` READY；产品测试未通过，
不得进入 acceptance 或声称 real-case 完成。

下一步应先做一个最小 bounded comparison，只比较两类既有方向：让 self-test profile 明确允许 loopback-only 测试，或由
harness-owned deterministic executor 在 Agent sandbox 外运行声明的测试命令并把 typed 结果投影给 self-test/repair。比较必须
同时处理 occurrence rollover 的自动 session close/new；禁止恢复 R4、构建网络证明平台或继续 provider 试错。

### 2026-09-02 Self-Test Environment 与 Occurrence Session Bounded Comparison

比较已落在
`docs/plans/self-test-environment-and-occurrence-session-bounded-comparison-20260902.zh-CN.md`。结论不是增加 executor 或
冲突后自动补偿，而是关闭两个现有模型矛盾：

1. occurrence-scoped Claude execution policy 必须使用既有 `RUN` physical session scope；同一 run 的 correction/reconnect
   仍复用，相邻 run 不再共享不兼容 binding。该规则属于 adapter execution invariant，不能被 frozen workflow definition 或
   workstream scope env 绕过；
2. E1 sandbox policy 使用 Anthropic 已有 `allowLocalBinding=true`，只恢复 localhost listener 能力，外部 domain、filesystem、
   credential 与 no-unsandboxed 边界保持不变。

A8 deterministic executor 继续 `NO-GO`：本次 real-case 证明了 execution environment 缺口，但没有产生可信 canonical test
command owner。宿主 `mvn test` 只作为 stop-loss 诊断，不成为 workflow SoT。

实施拆成两个原子 checkpoint：先 S1 session lifetime（纯 deterministic），再 S2 localhost binding（policy/fork identity + 一个
最小 bind/外网拒绝 capability slice）。两者独立 diff review PASS 后才恢复当前 OKX workstream，不创建第二样本。

独立 comparison 评审随后确认 S1 `READY`，但指出 S2 未规格化 sandbox 到宿主既有 localhost listener 的 connect 方向。
comparison 已修订：S2 降为 `ASSESSED / NOT READY`，必须先用最小 fixture 区分 self-owned loopback round-trip 与 host-owned
loopback access；若后者也被放开，必须显式重分类并由 operator 裁定，不能藏入 residual。R4 已删除，不同步或恢复旧 Gate。

S1 已作为独立 deterministic checkpoint 实施，结果见
`docs/plans/occurrence-policy-session-lifetime-checkpoint-result-20260902.zh-CN.md`。E1-bound Claude execution 现在强制使用既有
`RUN` physical session scope；env/node-config 与 compaction experiment 均不能降回共享 session；同一 run 的 correction/reconnect
仍共享，终态 cleanup 继续按 run.json 实际名称关闭。独立 diff review 已 `PASS（P0=0 / P1=0 / P2=4）`；随行补齐 env
override 与相邻 execute no-conflict 两条回归测试，并明示 compaction seed 不再跨 occurrence 连续。focused `61/0/0/0`，full
reactor `1297/0/0/0`，diff-check clean，未调用 provider。当前允许选择性提交 S1；S2 与 OKX resume 继续等待。

### 2026-09-02 S2 Local Binding Semantics Gate

S1 已由 checkpoint `08229040` 选择性提交。S2 不直接修改 production：先用
`docs/testing/verification-gates/20260902-s2-claude-local-binding-semantics.md` 与冻结 fixture 测量
`allowLocalBinding=true` 的两个方向。self-owned listener 必须 bind + round-trip 成功；sandbox client 对预先存在的宿主
loopback sentinel 原样分类为 `HOST_DENIED` 或 `HOST_REACHABLE`；未授权 external HTTPS 必须失败。

首次方案直接调用 standalone Claude `2.1.236`。独立评审指出这同时偏离 production 的 embedded `2.1.232` 版本和 SDK
`managedSettings` 投递路径，结果不能直接支撑 S2 决策，并会迫使同一能力重复消耗 provider 样本。该方向已停止，未执行 provider。

修订后的 Gate 复用 production `ClaudeExecutionPolicy.write -> reviewed AcpxAgentTransport/fork -> claude-agent-acp 0.70.0 ->
SDK 0.3.232 / embedded Claude 2.1.232` 链。fixture launcher 只在 disposable occurrence policy 中把
`allow_local_binding=false` 翻为 `true` 并重算 binding SHA；production 默认值不变。probe 仍只测 self-owned loopback、
host-owned loopback 和未授权 external HTTPS 三项，宿主 sentinel 运行前后双检，curl 使用 `--noproxy '*'` 排除 Shadowsocks
proxy 中介。standalone settings/flag 机器已删除，不新增网络框架或通用 evaluator。

当前只允许 production-chain Gate/fixture 的 delta review 与 non-provider 离线校验；provider 尚未执行。用户既有 provider
tool-turn 预授权仅在 delta review PASS 后激活，且只覆盖唯一一次 S2 Bash occurrence；不恢复 OKX、不创建第二样本。

production-chain delta review 确认方向正确，但发现冻结 offline test 自身断言错误、固定 session 名会在 prompt 前失败后与新
policy SHA 冲突、runner 的离线端到端仿真被误删。三项均已在 fixture 内最小修复：断言改为 fixed-string、session 名按运行时
唯一生成并落 evidence、fake java/acpx/curl 重新跑通 runner 的 sentinel/watchdog/SHA 管线。未采纳可选 IP/DNS 扩展；当前仍只
允许重新冻结 SHA、离线复验和简短 delta review。

重冻结后，fixture SHA `5/5`、package-local launcher compile 与宿主侧 offline end-to-end 均 PASS；Codex 自身受限沙箱内的
loopback bind 失败仅用于确认测试必须在 Gate 规定的宿主环境执行，不作为 S2 测量结果。provider 仍未调用。

最新 fixture delta review 已 `PASS / EXECUTION READY（P0=0 / P1=0 / P2=1）`。独立评审复现 offline test、5/5 SHA 与
launcher production-classpath 编译；三项阻塞全部关闭。当前允许选择性提交 Gate/fixture/active plan，再执行 non-provider
preflight；全部通过后只运行唯一一次 production-chain Bash occurrence。可选 IP literal 探针不纳入本轮。

Gate checkpoint `fb0cf9c` 的 non-provider preflight 全绿后启动唯一 execution attempt，但在 provider 前被 reviewed fork 拒绝：
`allow_local_binding must equal false`。`launcher.json` 记录 transport `exitCode=4 / SESSION_BOOTSTRAP_FAILED`；无 ACP session、
无 provider prompt、无 tool call、无 probe TSV，故结论为 `INVALID_PRE_PROVIDER / NOT MEASURED`，不是 S2 FAIL。根因是计划错误
类比 allowlist differential：`allowed_domains` 是 fork 允许变化的输入，而 `allow_local_binding` 在 parser 与 SDK projection 两处
都被固定为 false。runner 还遗漏了 transport exitCode 的 fail-fast。证据已归档到
`docs/testing/capability-proofs/20260902-s2-local-binding-fb0cf9c-invalid/`；不修改冻结 fixture、不原地重跑、不消费第二样本。

下一步采用更小且无循环依赖的 SDK capability Gate：直接以本地 SDK `0.3.232` 的 `managedSettings` 驱动同一 embedded Claude
`2.1.232`，测量 self loopback、host loopback 与 external deny。该 Gate 不经过 acpx fork guard、不修改 production、不创建
session，`persistSession=false`；目的仅是确定第三方 runtime 对 `allowLocalBinding=true` 的真实语义。Gate 与 fixture 位于
`docs/testing/verification-gates/20260903-s2-sdk-sandbox-option-local-binding.md` 及同名 fixture 目录。当前只允许独立只读评审，
provider 尚未调用。

在新 Gate 的 provider 前置核验中，SDK `resolveSettings()` 实测将 `managedSettings` 内的 `allowLocalBinding=true` 过滤掉，证明
managed policy tier 只能承载 restrictive policy，不能承载该 permissive runtime capability。draft 已纠正为唯一职责划分：
`Options.sandbox` 投递 local-binding runtime 开关，`managedSettings` 继续拥有 strict allowlist、空 domains、禁止 unsandboxed
command 与 credential deny。该发现未消耗 provider，也未修改 production。

新 Gate 独立评审已 `PASS / EXECUTION READY（P0=0 / P1=0 / P2=3）`，并复现 offline test、fixture/SDK/binary SHA 与
SDK 类型契约。按 P2 补充了失败解释边界：self bind FAIL 只能证明 option+managed 合并路径不可用，不能外推 runtime 完全不
支持。用户既有 provider tool-turn 预授权明确覆盖本次唯一 SDK query；不增加通用 adjudicator 或 IP/DNS 探针。

### 2026-09-03 OKX Recovery 与 Revision-limit Gate Source

S2 implement local binding 已在 OKX self-test occurrence 中自然证明：本地 listener 与 HTTP stub 可用。测试随后暴露
`CandleSyncService` 自代理初始化导致的真实产品缺陷，typed outcome 为 `SELF_TEST_INCOMPLETE`。

checkpoint `a44532d` 已让 revision limit 优先消费 workflow 声明的唯一 `BLOCKED -> human gate` route。真实恢复运行证明
路由成功进入 `gate.human_implementation_repair` 且未调用 provider，但 Gate packet consumer 仍把 runtime 提升后的
`BLOCKED` 错当成 Agent 必须原样输出的 decision，导致 packet 生成失败。

follow-up 仅闭合该同一事实链：RoutingEngine 将 revision-limit disposition 写入新 Gate node 的已有 result；
HumanReviewGateHandler 在 packet 写入前把该 disposition 与原 Agent sidecar 严格互证。普通 BLOCKED 规则不变，无新 action、
schema、workflow node 或 recovery path。focused `115/0/0/0`，full reactor `1304/0/0/0`。独立 diff review 已
`PASS（P0=0 / P1=0 / P2=3）`，允许选择性提交。提交 fresh JAR 后，由 operator 对既有在途 Gate 直接作 `revise`
决定并继续 OKX 产品修复。

### 2026-09-03 S2 SDK Local Binding 实测结果

唯一 SDK query 已完成，机械判定为 `PASS / HOST_REACHABLE / OPERATOR DECISION REQUIRED`。冻结 SDK `0.3.232`
与 embedded Claude Code `2.1.232` 下，`Options.sandbox.network.allowLocalBinding=true` 同时允许 sandbox 内
self bind/connect 与访问宿主机已监听的 loopback sentinel；空 domain allowlist 下 external direct connect 仍被拒绝。
运行为一个 prompt、一个精确 Bash tool call、SDK success、零 permission denial，host pre/postcheck 均 PASS。

Operator 已接受 `HOST_REACHABLE` 语义。代码事实进一步确认实现与自测节点共用现有
`permission_profile=implement`；因此只对 self-test 做节点特判反而会新增漂移路径。已起草
`docs/plans/s2-implement-profile-local-binding-implementation-plan-20260903.zh-CN.md`，最小边界是对现有
`implement` profile 投影 SDK local-binding option，其他 profile 保持关闭。当前 active step 为该计划的独立架构评审；
评审 PASS 前不改 production、不恢复 OKX real-case、不运行第二 provider 样本。

### 2026-09-03 acpx Executable 与 Turn Deadline 边界

OKX 产品修复已在宿主环境通过 `mvn test`（44/0/0/0），但同一 implement 节点的两个 reviewed-acpx occurrence
分别运行 `608153ms` 与 `603998ms` 后，以 exit 0、无 `prompt_completed`、空 final assistant message 结束，随后被
误投影为 `AGENT_RESULT_MISSING`。另一次 provider 前失败证明未设置 `DUAL_ACPX_EXECUTABLE` 时会静默 fallback 到 PATH
中的 acpx 0.11.0，直到发送 E1 flags 才暴露不兼容。

这两个问题同属 adapter execution boundary，实施计划见
`docs/plans/acpx-executable-and-turn-deadline-boundary-plan-20260903.zh-CN.md`。修复只复用既有
`TIMED_OUT` 与 `SESSION_BOOTSTRAP_FAILED`：E1-bound request 在 session/provider 前校验 reviewed build identity；
无 completion 且到达声明 deadline 时，即使 exit 0 也必须分类为 typed timeout；总 turn deadline增加显式有界配置，
当前 OKX 恢复使用 1200 秒，600 秒 inactivity watchdog 不变。禁止新增 workflow/schema/recovery action/自动重试或配置平台。

当前 active step：先写 cannot-bypass tests，再实施单一 adapter checkpoint；full reactor 与独立 diff review PASS 后构建
fresh JAR，只恢复当前 OKX 节点一次。同类失败再次出现即停止，不创建第二样本。

实施已完成，result 见
`docs/plans/acpx-executable-and-turn-deadline-boundary-result-20260903.zh-CN.md`。边界自审补齐了 correction prompt 入口，
避免同一 occurrence 在初始 prompt 通过身份检查后从 correction 绕过。聚焦测试分别为 transport 42/0/0/0、Doctor
11/0/0/0、handler 46/0/0/0、finalizer 9/0/0/0；full reactor 1311/0/0/0，`git diff --check` clean。

当前 active step：独立代码级 diff review。review PASS 前不恢复 OKX；review 后选择性提交、构建 fresh JAR，再按既有
Gate 以 reviewed acpx 和 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=1200` 只恢复当前节点一次。

独立 diff review 已 `PASS / ALLOWED（P0=0 / P1=0 / P2=4）`，并独立复跑 focused 108/0/0/0、full reactor
1311/0/0/0 与 clean diff-check。P2 仅记录 sibling/双 timer 测试强度、deadline 5 秒容差及本地 executable replacement
TOCTOU，不阻塞 captured 缺陷关闭，也不在本 checkpoint 扩成新机制。

当前 active step：选择性提交本 checkpoint（排除旧 OKX Gate）并构建 fresh JAR；随后为当前
`node-implement-claude_implement-049` 写一份短 recovery Gate，复核后只执行一次 recovery。

checkpoint 已提交为 `3bb87f3`。fresh JAR SHA256 为 `86beadc7a473d2cdec9fccaad9bffb4231502ba4130b2f2d98a61c72862fa8a8`，
Doctor 与 reviewed acpx identity 均 PASS；当前节点仍为 `node-implement-claude_implement-049 / WAITING_FOR_RECOVERY / evt-000054`，
目标工作区 `mvn test` 再次为 44/0/0/0。旧 S2 Gate 已过时且保持不动；当前 recovery Gate 见
`docs/testing/verification-gates/20260903-okx-acpx-identity-deadline-recovery.md`。

新 Gate 经独立只读核验后执行完成，结论为 `PASS`。唯一新增 run
`run-1788437649906` 使用 reviewed acpx、E1 policy binding 与精确 `--timeout 1200`；首次 provider
输出运行 `790110ms` 后产生 `prompt_completed`，证明旧 600 秒提前终止已消除。首次 JSON 解析失败由同一 run
的既有 correction 在 `25794ms` 内闭合，最终 AgentResult 合法、execution outcome=`SUCCESS/completed`，无
`AGENT_RESULT_MISSING`。节点 049 已完成并以 `evt-000056` 路由到 self-test 节点；`--max-steps 1`
在第二个 provider run 前停止。运行后 target source identity 未漂移，operator 复跑产品测试为 44/0/0/0，
原 44 个 testcase 零缺失。

当前 active step：停止自动推进，先以当前 READY 的 `implement.claude_self_test_report-056` 为边界制定最短继续策略；
不得把 adapter Gate PASS 外推为 OKX real-case 已完成，也不得再次修复身份、timeout、网络或 permission 机制。

self-test 单节点 Gate 已以 checkpoint `82fb49e` 冻结并执行，结论 `PASS`。唯一 run
`run-1788439283922` 使用 reviewed acpx、E1 binding 与 `--timeout 1200`，在 `598390ms` 后产出合法
`SELF_TEST_COMPLETE / READY` contract。沙箱内离线测试为 44/0/0/1，唯一 skip 来自 Playwright
无法写宿主 cache，contract 已明确记录该 missing evidence；没有把它冒充沙箱内全量通过。provider 结束后 operator
原样复跑 `mvn test` 为 44/0/0/0，fresh Surefire XML 的原 44 个 testcase 零缺失；target 四项 source identity
全部不变。node 056 已完成，`evt-000057` 只激活 deterministic `implement.self_test_decision-057 / READY`，
没有第二个 provider run。

当前 active step：先执行既有 deterministic self-test decision，不调用 provider；随后根据唯一 typed route 决定下一节点。
Playwright cache 差异只登记为 self-test 环境残余，本轮不扩成 executor、cache 投影或权限平台。

deterministic self-test decision 已把 `SELF_TEST_COMPLETE` 路由到 `implement.artifact_validation-058`；该节点随后以
`CHANGE_SCOPE_VIOLATION` 失败并进入 `WAITING_FOR_RECOVERY / evt-000059`，未调用 provider。evaluation 实证为
53 个 actual changes、16 个 unexpected actual、12 个 missing declared。主要差异是测试 package/fixture 路径偏离
TaskContract exact scope，以及 human repair 后新增的 `CandleCommitService.java` 未获 contract scope 授权。

validator 的拒绝本身正确；新发现的 harness 缺口是 typed scope violation 没有声明的 repair route，`retry` 只能重复失败，
operator 又不能绕开 ledger 手工改 workspace。bounded comparison 见
`docs/plans/change-scope-violation-repair-routing-bounded-comparison-20260903.zh-CN.md`。推荐只把 configured
`CHANGE_SCOPE_VIOLATION` 路由到既有 human implementation repair gate；TaskContract、human action 与 scope schema
均不变，evidence unavailable 仍 fail-closed。validation 提前到 self-test 前只记为效率候选，本轮不实施。

当前 active step：独立评审该 bounded comparison；PASS 前不 retry 当前节点、不改产品、不调用 provider。

独立评审判定 `WARN / NOT READY（P0=0 / P1=3）`：推荐 route 方向正确，但现有 stable git-evidence
会被 sibling 覆盖，Gate packet 又不展示 violations/missingDeclared；implementation repair 的 revise note 在非 BLOCKED
来源下并非必填；当前 workstream 的 repair revision budget 已于 `evt-000047` 消费，无法安全迁移。

comparison 已修订为最小完整链：node-instance-scoped git-evidence occurrence refs 随 route 进入 Gate；Gate 禁止 stable
fallback 并展示 violation 明细；所有 implementation-repair revise 均要求 note；三个 bundled definition 加一个 experiment
sibling 声明同一 route。当前 OKX 样本明确终止为 FAIL，不提高 revision limit、不手改 ledger/workspace、不 retry。

当前 active step：对修订 comparison 做 delta review；PASS 后制定单一 implementation plan。后续证明必须使用 fresh
workstream，当前样本仅作为 natural failure evidence 保留。

comparison delta review 已 `PASS（P0=0 / P1=0）`。P2 要求 implementation plan 明确 occurrence evidence
的防篡改结构，且将残留“四个 lifecycle”措辞改为“三个 bundled + 一个 experiment”；两项均已吸收。
实施计划见 `docs/plans/change-scope-violation-repair-routing-implementation-plan-20260903.zh-CN.md`：复用现有
change-scope evaluation、human repair gate 与 implementation loop，只新增 content-addressed occurrence ref/SHA、
definition-owned violation disposition、routed-ref Gate consumption 与 implementation-repair note guard。

当前 active step：独立架构评审实施计划。评审 PASS 前不改 production、不 retry/迁移当前 OKX ledger、不调用 provider。

实施计划首轮评审为 `WARN / NOT READY（P0=0 / P1=2）`：flag=true 但缺少 violation edge 时 custom
workflow 会被错误完成；Gate 仅从 ref 自身解析 producer node 会形成自证。计划已补 named production definition
invariant，要求 flag 为真布尔且恰一条 edge 指向 implementation-repair Gate；同时补 producer node 的
definition/handler/status/iteration/outputRefs/disposition 全链验证。artifact 与 definition 专属测试改为必需，note guard
补齐 sibling 反例。

当前 active step：实施计划 delta review；P0/P1 清零前不进入代码。

第二轮 delta review 仍为 `WARN / NOT READY（P1=2）`：violation edge 若带 false condition 仍可零后继完成；
semantic Gate 若允许 approve 或没有有效 revise loop 也可绕过 repair。计划选择更小的固定协议，不建设 custom gate
平台：flag=true 时必须是 unconditional violation edge 到 `gate.human_implementation_repair`；该 Gate 必须
revise-only，并恰有一条 unconditional loop 到 `context.implement_claude`。对应反例测试已列入 invariant matrix。

当前 active step：最终 implementation-plan delta review；通过后立即按 failing-tests-first 实施。

最终 implementation-plan delta review 已 `PASS / IMPLEMENTATION READY（P0=0 / P1=0）`。实现严格沿已评审边界完成：
`TaskContract.scope` 仍是唯一授权源；artifact validation 同时保留 stable latest projection，并发布按 validation node instance
隔离、content-addressed 的 occurrence evaluation；仅 definition 显式开启时，`FAIL` 以 typed
`CHANGE_SCOPE_VIOLATION` 完成并路由到既有 implementation-repair Gate，`UNAVAILABLE` 与未配置定义仍走 recovery。
Gate 在写 packet 前重验 producer definition/handler/status/iteration/outputRefs/result disposition 与 occurrence SHA，禁止
stable fallback；所有 implementation-repair `revise` 统一要求非空 note。三个 bundled workflow 与一个 experiment 已同步，
definition 构造期会拒绝字符串 flag、条件/缺失/多重 violation edge、错误 Gate 与无效 repair loop。

focused tests 159/0/0/0；full reactor 1320/0/0/0；`git diff --check` clean。当前 OKX workstream 仍保持
`WAITING_FOR_RECOVERY / evt-000059` 并按既定判断记为 FAIL，没有 retry、迁移或 provider 调用。

当前 active step：独立代码级 diff review；P0/P1 清零后选择性提交本 checkpoint，旧的
`20260903-okx-s2-recovery-and-terminal-completion.md` 继续排除。

首轮独立 diff review 为 `WARN / NO COMMIT（P0=0 / P1=2 / P2=1）`。P1-1 指出 core definition 允许任意
`artifact.validate` source id，但 Gate 固定验证 `implement.artifact_validation`，形成定义可通过、运行时 Gate 必拒的协议裂缝；
P1-2 指出 cannot-bypass matrix 未按计划完整落测。P2 指出 scope-violation packet 混合 occurrence authority 与可能被 sibling
覆盖的 stable latest 摘要。

delta 已统一为固定最小协议：definition invariant 同样要求 source id=`implement.artifact_validation`；补齐 violation edge、
repair Gate/loop 与 producer lineage 全负向矩阵；scope-violation Gate 只展示 occurrence-backed evaluation，不再混入 stable
changed-files/diff/status。delta focused 126/0/0/0，重新执行 full reactor 为 1323/0/0/0，diff-check clean。

当前 active step：独立 delta diff review；P0/P1 清零后才允许选择性提交。

delta diff review 已 `PASS / ALLOWED TO COMMIT（P0=0 / P1=0 / P2=1）`，并独立复跑 full reactor
1323/0/0/0 与 clean diff-check。首轮两个 P1 和 stable/occurrence 混合展示 P2 均确认 CLOSED；剩余 P2 仅为
Gate 测试字段级断言可更细，不影响 production 边界或提交。

当前 active step：选择性提交 change-scope violation repair checkpoint，明确排除旧 OKX recovery Gate；提交后不恢复
当前失败样本，下一步只允许评估是否需要为 fresh real-case 起草最小 Verification Gate。

change-scope violation repair checkpoint 已提交为 `22ae064`。当前 OKX 样本保持 FAIL，不迁移、不 retry；旧 recovery Gate
仍为未跟踪且明确排除。该修复当前成熟度为 `ENFORCED / NOT REAL-CASE PROVEN`。

当前 active step：停止继续扩展 harness。下一步应回到主目标，选择一个 fresh real-case，用最小 Gate 验证新路径能够在真实
scope violation 时进入人工 repair，并在 revise 后重新 validation PASS；若新任务没有自然触发 violation，不得人为制造问题，
只验证正常路径无回归。
