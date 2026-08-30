# E1 Execution Policy Injection Bounded Comparison

## 1. 决策问题

E1 capability proof 已 `FAIL`：Claude Code/SDK/adapter 支持 sandbox policy，但 frozen `acpx` client 无法把 occurrence-bound `settings/managedSettings/sandbox/settingSources` 发送到 `session/new._meta`。

本比较只决定如何让 **E1 的固定 execution policy 到达 Claude sandbox**。它不是独立 adapter roadmap，不提供任意 Claude settings 注入，不实现 domain approval，不恢复 OKX workstream，也不调用 provider。

## 2. Owner 与硬约束

- policy owner：Dual 的 execution authority；
- enforcement owner：Claude Code OS sandbox；
- transport owner：ACP client 必须原样、可审计地把 policy 送入当前 session；
- project/user settings 不是 authority，不能通过合并普通 settings 模拟 managed restriction；
- provider credentials 与 tool subprocess env 必须分层；
- policy 改变必须创建 fresh effective session；
- 不解析 Bash、不代理业务 HTTP、不新增网络规则 DSL。
- 通用性只覆盖所有 Claude execution profile 对同一固定 policy contract 的复用；不得扩张为 provider-neutral adapter framework、通用 settings API 或任意 `_meta` passthrough。

## 3. 候选

### A. 扩展并固定 acpx session options（推荐）

为 acpx 增加受类型约束的 E1 execution-policy 输入，由 client 根据固定 schema 构造 `settingSources`、`settings`、`managedSettings`、`sandbox` 与净化后的 `env`，再放入 `session/new._meta.claudeCode.options`；session record 只保存 policy SHA，变化即拒绝 resume/rebuild。调用方不得直接提供任意 Claude options 或 `_meta`。

优点：修复发生在真正缺失 capability 的 ACP client owner；不复制 Claude sandbox；可形成上游贡献；Dual 只负责生成和验证 policy。

成本：需要维护 pinned fork/commit，直到上游正式发布等价接口；必须给 acpx 自身补协议与持久化测试。

### B. Dual 自有 ACP JSON-RPC metadata shim

在 acpx 与 claude-agent-acp 之间拦截 `session/new` 并注入 `_meta`。

优点：不修改第三方包。

拒绝作为首选：新增一个协议中间层，必须正确转发 streaming、cancel、permission、session failure 和未来 ACP 扩展；它会扩大 605 秒 timeout/session closure 等既有边界的故障面。长期成本高于一个受限的 client option。

### C. 在 target/worktree 生成 `.claude/settings.local.json`

拒绝。它污染业务 workspace，project/local settings 会合并而非形成 Dual 独占 restriction，Agent 还可能在 execution 内修改它；无法满足 immutable occurrence binding。

### D. Dual 直接替代 acpx，调用 Claude Agent SDK

拒绝。会复制 session queue、ACP lifecycle、permission projection、cancel/closure 等成熟能力，远超 E1 缺口。

### E. 等待上游

可作为时间换成本选项，但会让已自然证明的 Bash 网络旁路持续阻塞复杂 real-case。当前收益低于候选 A。

### F. `CLAUDE_CONFIG_DIR` + `ACPX_CLAUDE_INCLUDE_USER_SETTINGS=1`

拒绝。它虽不污染 target，但 effective sources 仍为 `user/project/local` 合并；project/local 可扩张普通 settings，Agent 也可在 execution 内改写 `.claude/settings.local.json`。同时该目录今天还承担 provider env 来源，会继续混合 policy 与凭据 owner。

### G. 写宿主机 managed-settings 路径

拒绝。managed-settings 是宿主全局 authority，不是 workstream/execution occurrence；写入会影响同机其他 Claude 进程，无法满足一次 execution、一次 SHA 和 session-bound lifetime。

## 4. 推荐边界

选择 A，但先做一个独立、可回滚的 **E1 policy transport checkpoint**，不与 E1 domain authority 混写，也不把它升级为通用 acpx 配置能力。

版本 sweep 进一步确认：当前 `claude-agent-acp@0.46.0` 固定的 Claude Code `2.1.178` 早于 credential env deny 与 strict allowlist 所需版本。仅补 acpx 白名单仍会让 provider secret 进入 Bash ambient env，不能形成 E1 前置能力。因此该 checkpoint 必须同时冻结到已核实具备这些能力的最小 adapter/SDK 组合；当前候选为 `claude-agent-acp@0.70.0` + SDK `0.3.232` + Claude Code `2.1.232`。这属于 E1 capability baseline，不授权 adapter 其他新功能。

1. session option 只接受 E1 固定 schema 的 execution policy 文件/ref，不接受任意命令行 JSON、Claude options 或 `_meta`；
2. client 在创建 session 前校验 schema 与 SHA；
3. `_meta` 只携带 policy、setting sources 与净化 env，不携带 ledger/path authority；
4. session record 保存 policy SHA；同名 session SHA 不同即 fail closed，调用方必须 close/rebuild；
5. fake ACP server 精确断言 `session/new._meta`，删除任一限制字段必须测试失败；
6. wrapper 只导出明确列举的 provider route 变量，不再导出 settings.env 全集；provider secret 与 proxy 变量名必须进入 managed credential deny。acpx 的显式 SDK env 不含 proxy/secret，且不得误称 overlay 为删除；
7. 先独立评审和 deterministic tests，再回到原 E1 proof 问题 1-3；仍不直接调用 provider。

checkpoint 的退出条件仅为：fake ACP server 证明 frozen client 能把同一份 SHA-bound E1 policy 交给当前 session；session reuse、env 分层和缺失 policy 均 fail closed；显式 SDK env 中 proxy/secret 零出现，managed credential deny 精确包含需要从 Bash 移除的变量名。实际 tool subprocess env 为零仍属于 E1 proof Q6，必须由后续 Verification Gate 证明，deterministic test 不得冒充 provider runtime。checkpoint 不声明网络 enforcement 已完成，也不顺带增加其他 adapter capability。

fork/pin 采用可删除的 source patch 形式：基线锁定 acpx 官方 `v0.13.2` tag object `a5db336c1f886fb722b6ba5a4a9898181fe1a92a`、commit `fd173f04aa1b56f9e3f5ca5190c034ddcae28792`；patch、构建输入与产物 SHA 写入 manifest，fork diff 单独评审。不得修改用户全局已安装文件，也不得依赖浮动 Git branch/npm range。上游发布等价接口后删除 patch 与安装路径，恢复官方 pin。

## 5. Stop-Loss

出现以下任一情况即停止候选 A，重新比较：

- 必须 fork 大量 acpx lifecycle/queue/permission 代码；
- session record 无法绑定 policy SHA；
- 无法避免 arbitrary settings passthrough；
- provider env 与 tool env 只能共享完整 process env；
- 需要修改 workflow JSON、AgentResultEnvelope 或业务 target；
- 上游已发布等价、可验证且更小的接口。
- 开始出现与 E1 policy transport 无关的通用 ACP option、provider abstraction 或配置管理需求。

## 6. 成熟度

- Claude sandbox capability：`MODELED BY UPSTREAM`；
- Dual execution policy injection：`VALIDATED DEFECT / NOT ENFORCED`；
- E1 controlled network authority：`NATURAL INCIDENT PROVEN / ENFORCEMENT NOT IMPLEMENTED`；
- OKX recovery：`PAUSED / NOT AUTHORIZED BY THIS COMPARISON`。

候选比较的独立架构评审已 `PASS / READY（P0=0 / P1=0 / P2=3）`。P2 已吸收：显式拒绝两个 settings 文件变体；env 分层拆成 wrapper allowlist、managed credential deny 与后续 runtime proof；fork pin、manifest 与独立 fork review 已写实。

后续 boundary reflection 又发现当前 Claude Code `2.1.178` 早于 E1 所需的 credential env deny/strict allowlist，故候选 A 不能只补 acpx 白名单。修订后的实施计划位于 `e1-policy-transport-checkpoint-implementation-plan-20260829.zh-CN.md`，将 adapter/SDK capability baseline 与 acpx fixed-policy transport 作为同一最小前置 checkpoint。该计划再次独立评审 PASS 前不修改 production、不调用 provider、不恢复 real-case。
