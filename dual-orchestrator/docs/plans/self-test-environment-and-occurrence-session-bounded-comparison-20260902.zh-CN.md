# Self-Test Environment 与 Occurrence Session Bounded Comparison

日期：2026-09-02
状态：`ASSESSED / RECOMMEND TWO SMALL CHECKPOINTS / NOT IMPLEMENTED`

## 1. 目标与边界

本比较只处理 OKX real-case 自然暴露的两个通用 harness 缺陷：

1. Claude self-test 所在 sandbox 禁止绑定 localhost，导致常见的 HTTP integration test 无法产生真实测试事实；
2. Dual 为每个 Agent occurrence 生成不同的 execution-policy ref/SHA，却默认复用 workstream-scoped Claude session，
   相邻节点会在 provider 前触发 `CLAUDE_EXECUTION_POLICY_CONFLICT`。

两者是独立边界，不合并成新平台，不恢复 R4，不增加业务特判、workflow node、通用命令发现器或网络证明框架。

## 2. 已确认事实

### 2.1 Self-test 环境不等价

- `ClaudeExecutionPolicy.write()` 固定写出 `sandbox.network.allow_local_binding=false`；reviewed acpx fork 也把该值固定校验为
  `false`，因此这是 Dual 当前执行策略，不是模型偶发行为。
- Anthropic 官方设置与 Agent SDK 文档都把 `network.allowLocalBinding` 定义为允许进程绑定本地端口，默认值为 `false`；
  macOS 由 sandbox enforcement 实现。该能力不等于开放外部网络域名（取证：
  [Claude Code settings reference](https://code.claude.com/docs/en/settings#sandbox-network-allowlocalbinding)、
  [Agent SDK TypeScript reference](https://code.claude.com/docs/en/agent-sdk/typescript)，2026-09-02）。
- captured self-test `run-1788347257826` 在该环境内只运行到 22 个测试，5 个 integration test 因本地 HTTP stub
  bind `EPERM` 失败，typed 结论为 `SELF_TEST_INCOMPLETE`。
- 同一工作区在宿主执行完整 `mvn test` 得到 44 tests、18 errors，并暴露
  `CandleSyncService` self-proxy 引发的 `BeanCurrentlyInCreationException`。sandbox 内的 bind 失败先遮蔽了该产品缺陷。

因此现有 self-test artifact 对“它实际运行的环境”是诚实的，但该环境不足以承担 acceptance 所需的 integration-test fact。

### 2.2 Session scope 与 policy lifetime 冲突

- `SessionScopePolicy` 默认 scope 为 `workstream`，实际 session 为
  `claude-main-<workstreamId>`。
- `ClaudeExecutionPolicy` 的 lineage 包含 `workstream_id + node_instance_id + run_id`，因此 policy 是 occurrence-bound；
  相邻 run 的 ref/SHA 必然不同。
- reviewed acpx 的 persistent binding guard 正确拒绝同名 session 的 policy replacement；该 guard 不应放宽。
- 当前 real-case 已实证：self-test 路由到 repair 后，新 occurrence 在 provider 前冲突，必须由 operator 手工关闭旧 session。
- `SessionScopePolicy.Scope.RUN`、run.json session 记录和 `AcpxSessionLifecycle` 按实际 run ledger 关闭所有 session 的能力已经存在。
- 实验性 `SessionCompactionPolicy` 可在 handler 中覆盖基础 session name；其 Claude implement-boundary 名称按 node 而非 run
  生成。因此 S1 还必须规定 occurrence policy 优先于 compaction，不能让实验开关重新生成共享 physical session。

结论：不是 acpx guard 过严，而是 Dual 同时声明了互不相容的两个 lifetime。

## 3. Self-test 候选比较

| 候选 | 判定 | 理由 |
|---|---|---|
| A. 对所有 E1-bound Claude execution 允许 localhost bind | **条件推荐** | 复用官方 `allowLocalBinding`，不改变外部 domain allowlist；实现、self-test 与本地 dev server 都获得一致的通用能力；但需先实测它是否同时开放 sandbox 到宿主 loopback service 的连接 |
| B. 仅为 `implement.claude_self_test_report` 增加特殊 profile | 拒绝 | 把 sandbox capability 混入 ACP tool permission，并引入 node-id/profile 特判；其他需要本地 stub/dev server 的节点仍失败 |
| C. 在 Agent sandbox 外增加 harness-owned deterministic executor | 暂缓 | A8 已证明当前没有可信 canonical verification command、timeout、environment 与 failure-classification owner；现在实现会新增 heuristic owner，且仍不能替代 self-test artifact 的 skipped/missing evidence 判断 |
| D. 要求目标项目把本地 HTTP 测试改成 mock | 拒绝 | 点对点绕过；降低真实 integration coverage，不能修复 Dual 的执行环境边界 |
| E. 整体关闭 Claude sandbox | 拒绝 | 为获得 localhost bind 同时丢失 filesystem/network enforcement，收益与代价不成比例 |

### 3.1 推荐边界

候选改动是把 E1 policy 与 fork 的固定值从 `allow_local_binding=false` 改为 `true`。官方契约明确保证 localhost bind，
但未说明 macOS Seatbelt 是否同时放宽 sandbox 到宿主 localhost listener 的 connect。后者必须先测量，不能把“bind 开关”
直接写成“只允许自建 stub”。外部域名仍由既有 `allowed_domains`、OS sandbox 和 permission policy 控制。

这是通用开发能力，不是 OKX/Maven/Java 特判。已知风险包括 Agent 启动的本地服务可被宿主访问；待测风险是 Agent 是否
也能访问宿主已有的 localhost 服务。因此必须保留
`allow_unsandboxed_commands=false`、空外部 domain allowlist、credential 隔离，并用本地 bind 成功 + 未授权外网仍失败的
focused test 证明没有把 localhost capability 扩张成 external outbound grant。

## 4. Session 候选比较

| 候选 | 判定 | 理由 |
|---|---|---|
| A. E1 occurrence 强制使用既有 `RUN` scope | **推荐** | policy 与 physical session 同 lifetime；每个 run 名称天然唯一；contract correction、同 run reconnect 仍复用同一 session；无需解析错误文本或改 acpx guard |
| B. 冲突后自动 close 同名 session 再 ensure | 拒绝 | 先失败再补偿，需识别 conflict 并在 adapter 内增加 replacement transition；关闭后历史同样丢失，却比 run scope 多出副作用与竞态 |
| C. 允许相同语义但不同 ref/SHA 替换 | 拒绝 | 放宽 occurrence lineage，重新引入被 replacement guard 关闭的持久化漂移 |
| D. 把 execution policy 改成 session-scoped stable document | 拒绝 | policy 将不再精确绑定 node/run，需新 lineage 与审计模型，范围明显扩大 |
| E. 继续人工 `sessions close` | 拒绝 | operator 在正常相邻节点间承担内部 lifecycle 协调，无法形成可重复 E2E |

### 4.1 推荐边界

凡 Claude run 绑定 occurrence-scoped execution policy，`AcpxAgentTaskHandler` 必须把实际 physical session scope 收敛为
`RUN`，并在 run.json 如实记录原因。该规则属于 adapter execution invariant，不依赖 frozen workflow JSON，也不能被
`DUAL_SESSION_SCOPE_CLAUDE=workstream` 或 `DUAL_SESSION_COMPACTION_EXPERIMENT` 绕过。E1 occurrence 下 compaction 不得
改写 run-scoped physical session；若保留 seed，只能作为 prompt input，不能拥有 session lifetime。

长期连续性的 owner 保持为 context package、typed artifacts 与 ledger；acpx session 只保留同一 run 内 initial、contract
correction 和 reconnect 的短期记忆。终态 cleanup 已从 run.json 收集实际 session 名，因此 run-scoped session 不需要新 reaper。

## 5. 推荐实施顺序

### Checkpoint S1：Occurrence-policy session lifetime

1. 在 handler 的 policy/session 装配边界强制 E1-bound Claude 使用既有 `RUN` scope；
2. 保留非 E1 Claude 与 Codex 的现有 scope 行为；
3. 测试同一 workstream 两个相邻 Claude node 得到不同 session，同一 run correction/reconnect 保持同 session；
4. 测试 env/node config 与 compaction experiment 都不能把 occurrence-bound policy 降回共享 physical session；
5. 测试终态 cleanup 从多个 run.json 关闭全部实际 session。

### Checkpoint S2：Localhost binding capability

1. 先用一次性、非 production fixture 测量 `allowLocalBinding=true` 下两条方向：sandbox 内 server bind 后同 sandbox client
   round-trip 必须成功；sandbox client 对执行前已存在的宿主 loopback sentinel 是成功还是拒绝必须原样记录；
2. 若宿主 sentinel 仍拒绝，S2 可按窄边界实施；若可访问，则把它明确重分类为“允许 localhost bind/connect”，由 operator
   决定该本地服务暴露面是否可接受。不得把 connect 成功隐藏成 residual；
3. 只有边界裁定后，才同步修改 policy writer 与 reviewed fork loader，并更新 patch/manifest/Doctor/build identity；
4. deterministic tests 证明 policy→SDK options 为裁定值，其余 network/filesystem/credential 字段不变；
5. 最小 capability slice 还必须证明未授权 external domain 仍失败；不恢复 R4 矩阵。R4 Gate/fixture 已删除，不存在需要
   维持的 `direct_loopback=EPERM` 成熟度声明；若未来重启 E1 security Gate，必须消费本次新边界。

S1、S2 分成两个可独立回滚的 checkpoint。S1 不等待 provider；S2 当前 `NOT READY`，先完成上述最小方向测量与 operator
裁定，再进入 production implementation。两者完成后再恢复当前 OKX workstream 的 READY self-test 节点，不创建第二 real-case。

## 6. 不实施 deterministic executor 的原因

本次证据改变了“self-test 环境必须能跑 integration tests”的判断，没有改变 A8 的输入事实：Dual 仍没有可信的 canonical
test command owner。引入 executor 会同时新增命令声明、shell 安全、timeout、环境分类、artifact 合成和路由语义，复杂度远高于
把现有 sandbox 的官方 localhost 开关设为正确值。

宿主 `mvn test` 可继续作为 operator stop-loss 诊断，但在没有 typed command contract 前，不得冒充 workflow 的 authoritative
self-test artifact。

## 7. End-to-End Invariant

### S1

| 维度 | 判定 |
|---|---|
| Declared by | occurrence-scoped `ClaudeExecutionPolicy.Binding` |
| Proven by | policy lineage `workstream/node/run` + run-scoped session name |
| Consumed by | `AcpxAgentTransport` ensure/prompt/correction/reconnect |
| Enforced before | persistent session creation与 provider prompt |
| Failure route | 装配后 policy/session lifetime 仍不一致时，在 provider 前走既有 typed execution failure |
| Cannot-bypass test | adjacent runs distinct session；same-run correction same session；scope override 不生效 |

### S2

| 维度 | 判定 |
|---|---|
| Declared by | policy v2 `sandbox.network.allow_local_binding=true` |
| Proven by | ref/SHA-bound policy → reviewed fork → canonical SDK options |
| Consumed by | embedded Claude sandbox |
| Enforced before | sandboxed Bash 启动本地测试进程 |
| Failure route | policy/loader/identity 漂移在 child spawn 前拒绝 |
| Cannot-bypass test | self-owned localhost round-trip、host-owned loopback 方向有明确裁定，同时未授权 external domain 仍失败 |

## 8. 为什么此前测试没有发现

1. policy writer/fork 测试把固定 `allowLocalBinding=false` 当成安全不变量，只证明值被忠实传输，没有用真实 integration-test
   workload 验证它是否破坏 self-test 的职责；
2. persistent binding guard 测试证明“不同 binding 不得替换”，但没有覆盖 workflow 中两个合法相邻 Claude occurrence；
3. session scope 与 execution policy 分别通过了局部测试，却没有检查两者 lifetime 的可组合性；
4. captured real-case 同时提供了 transition sequence 与真实测试环境，才让两个局部正确、组合错误的边界显形。

预防规则不是增加更多通用 Gate，而是在 S1/S2 各加入一条代表性组合测试：相邻 occurrence lifecycle，以及 localhost-based
integration-test capability。以后新增 execution policy 字段时，必须同步检查 session lifetime 和 self-test workload 两个 sibling。

## 9. 结论

- Boundary Review：`PASS FOR BOUNDED COMPARISON`。
- 推荐：立即实施 `S1 RUN-SCOPED E1 SESSION`；`S2` 在 loopback connect 方向测量和裁定后再实施。
- deterministic executor：继续 `NO-GO / NOT MODELED`，直到出现可信 command owner。
- 不新增平台，不恢复 R4，不修改产品代码，不把宿主诊断当 workflow SoT。
- 当前成熟度：两个缺陷均为 `REAL-CASE VALIDATED / NOT ENFORCED`；S1 已具备实施边界，S2 为 `ASSESSED / NOT READY`。

## 10. Captured Evidence Pointers

以下 authoritative artifacts 保持在只读 real-case workspace；本 checkpoint 不复制或改写它们：

- `runs/run-1788347257826/run.json`：SHA-256
  `4711b143c455a03b9325d63737e4f1d8869d8da8e5474afec4a83313164749f4`；
- `runs/run-1788347257826/outputs/implement-claude-self-test.json`：SHA-256
  `3c876f259788a53f270126eff8393b4530d9cce473f8767ff600e75052082195`；
- workspace root：
  `/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG/.agent/workstreams/ws-20260829T122452Z-9BZG/`。

宿主 `44/0/18` 是当轮 operator stop-loss 诊断，未冻结原始 terminal output；不得把它单独作为 S1/S2 的 durable proof。
