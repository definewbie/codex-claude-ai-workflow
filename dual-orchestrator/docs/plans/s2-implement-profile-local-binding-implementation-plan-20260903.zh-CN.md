# S2 Implement Profile Local Binding 实施计划

日期：2026-09-03  
状态：`IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS`  
目标阶段：`P0 DIRECT REAL-CASE BLOCKER`

## 1. 目标

让现有 `implement` permission profile 下的 Claude execution 获得 SDK
`Options.sandbox.network.allowLocalBinding=true`，从而允许实现和自测节点运行需要本地 listener 的集成测试。

本计划只关闭 OKX real-case 已实证的 self-test execution-environment 缺口。不实现通用网络授权、
domain approval、宿主服务发现、网络安全证明平台或 deterministic host executor。

## 2. 已证实前提

1. `implement.claude_implement` 与 `implement.claude_self_test_report` 在全部 bundled workflow 中都使用
   `permission_profile=implement`。
2. `contract_discovery`、`observe` 和 `review` 使用独立 profile，不需要 local binding。
3. S2 SDK Gate checkpoint `a88a53b3` 的唯一真实 query 已证明：SDK `0.3.232` + embedded Claude Code
   `2.1.232` 在 `Options.sandbox.network.allowLocalBinding=true` 时，self bind/connect PASS、host loopback reachable、
   external direct connect denied。
4. Operator 已明确接受 `implement` profile 可访问宿主 loopback 服务的代价。
5. SDK `managedSettings` 会过滤 `allowLocalBinding=true` 这类 permissive key；因此 fork 必须将它只投影到
   `Options.sandbox`，不能放入 `managedSettings`。

## 3. Owner 与闭环

| 问题 | 结论 |
|---|---|
| Declared by | workflow 已有 `permission_profile=implement` |
| Policy owner | `AcpxPermissionProfile` 对 `implement` profile 声明 local-binding capability |
| Durable fact | occurrence-scoped `claude-execution-policy.json` 中的 `sandbox.network.allow_local_binding` |
| Proven by | policy ref/SHA/lineage 校验 + reviewed fork parser/build identity |
| Consumed by | fork 将该值投影到 Claude Agent SDK `Options.sandbox.network.allowLocalBinding` |
| Enforced before | Claude session 创建和 Bash tool execution |
| Failure route | 缺失、篡改、非布尔值、profile/value 不一致或 fork identity 不符时，provider 前 fail closed |
| Cannot bypass | profile sibling tests + policy parser/projection tests + handler-to-transport binding test |

不新增领域对象、ledger 字段或 workflow 节点。该能力是 execution adapter policy，不是业务状态。

## 4. 最小生产变更

### 4.1 Dual policy producer

1. 在 `AcpxPermissionProfile` 中提供唯一的 `allowsLocalBinding()` 能力判定：只有现有
   `implement` profile 为 `true`，其他 profile 全部为 `false`。
2. `AcpxAgentTaskHandler` 把已解析的 effective permission profile 传给 `ClaudeExecutionPolicy.write()`。
3. `ClaudeExecutionPolicy.write()` 从 profile capability 写出 `allow_local_binding`，其他 sandbox/network/filesystem/
   credential 值不变，schema 仍为 v2。
4. 保留现有五参 `write()` 重载并固定写出 `false`，仅供已冻结的历史 E1 fixture 继续编译；
   production handler 必须调用带 profile 的新签名，不得使用兼容重载。
5. `approveAll` 与 legacy unnamed profile 的现有 fail-closed 边界不变。

### 4.2 Reviewed acpx fork

1. parser 继续要求 `allow_local_binding` 存在且为 boolean，不再把它固定为 `false`。
2. 将该值只投影到 SDK `sandbox.network.allowLocalBinding`。
3. `managedSettings.sandbox.network` 只保留 strict allowlist、空 allowed domains 和空 unix sockets 等限制性设置，
   不得包含 `allowLocalBinding`；无论 policy 值为 `true` 还是 `false`，managed settings 都必须省略该键。
4. 现有 SHA、exact-key、duplicate-key、lineage、secret-like 和 session binding guard 全部保持。

### 4.3 Fork identity 同步

按现有 E1 流程重生 exact patch，更新 `tools/acpx-e1/manifest.json`、installer/build-info、
`scripts/configure-acpx.sh` 和 `DoctorCommand` 的 patch SHA；使用 installer 运行 fork 全量测试并重建 reviewed binary。
这是现有 supply-chain identity 的必要机械同步，不是新机制。

## 5. 明确不改

- 不改 bundled/custom workflow JSON、node id 或 routing。
- 不新增 permission profile，不按 OKX、Mailpit、Maven 或测试框架特判。
- 不改 allowed domains、proxy、filesystem、unix socket、credential deny 或 unsandboxed-command 策略。
- 不开放 `contract_discovery`/`observe`/`review` profile 的 local binding。
- 不修改 ledger、artifact schema、CLI/API/UI、scheduler、recovery 或 human gate。
- 不调用 provider，不恢复 OKX real-case，直到 deterministic checkpoint 独立 diff review PASS。

## 6. 文件范围

Dual production/tests：

- `dual-adapter-acpx/.../AcpxPermissionProfile.java`
- `dual-adapter-acpx/.../ClaudeExecutionPolicy.java`
- `dual-adapter-acpx/.../AcpxAgentTaskHandler.java`
- 对应 profile/policy/handler tests
- `dual-cli/.../DoctorCommand.java` 及 test（仅 patch identity）
- `scripts/configure-acpx.sh`（仅 patch identity）

Fork source/tests 与交付 identity：

- `src/acp/claude-execution-policy.ts`
- `test/claude-execution-policy.test.ts`
- 若 SDK option 装配行为第二处必需改动，仅允许修改该直接消费点及对应 test；不允许新增通用 option passthrough。
- `tools/acpx-e1/patches/...`
- `tools/acpx-e1/manifest.json`
- installer 生成的 reviewed build identity/audit 工件

若实施需要超出上述 owner/consumer/identity 文件，必须停止并重评，不得顺手扩大。

## 7. 必须测试

1. `implement` profile 产生 `allow_local_binding=true`。
2. `contract_discovery`、`observe`、`review` 产生 `false`；三个当前 bundled workflow 的实现/自测 sibling 一致。
3. handler 生成的 occurrence policy ref/SHA/lineage 仍能在 transport 调用前重验。
4. fork 对 `true`/`false` 均严格解析，非 boolean、缺字段、篡改 SHA 均 fail closed。
5. `true` 只出现在 SDK `Options.sandbox.network.allowLocalBinding`，不出现在 `managedSettings`。
   同时扩展现有 `AcpClient createSession forwards a bound Claude execution policy in _meta` 测试，直接断言
   真实 `_meta.claudeCode.options` 中 option 为 `true` 且 managed settings 无该键。
6. 其他 sandbox/network/filesystem/credential 字段与旧 checkpoint 逐项相同。
7. 非 `implement` profile 的正向 sibling 反例保持 local binding 关闭。
8. fork focused/full tests、Dual focused tests、full reactor、Doctor、configure shell test 与 `git diff --check` 全部 PASS。
9. regenerated patch SHA 在 patch/manifest/installer/build-info/Doctor/configure 六方一致。

## 8. 实施顺序

1. 先添加 profile 与 fork projection 的失败测试。
2. 修改 Dual policy producer，不动 workflow。
3. 修改 fork parser/projection，确保 `Options.sandbox` 与 `managedSettings` 职责分离。
4. 运行 fork focused/full tests，重生 patch 与全部 identity。
5. 重建 reviewed acpx，运行 Doctor/configure 验证。
6. 运行 Dual focused/full reactor 和 diff-check。
7. 开展一次独立代码级 diff review。
8. diff review PASS 后选择性提交，再决定是否恢复当前 OKX workstream。

## 9. 成熟度与退出条件

实施与 deterministic tests PASS 后最多声明：

- `IMPLEMENT PROFILE LOCAL BINDING: ENFORCED`
- `SDK LOCAL BINDING EFFECT: PROVEN`（来自本次已完成 S2 Gate）
- `OKX SELF-TEST RECOVERY: NOT PROVEN`（须恢复 real-case 后证明）
- `COMPLETE E1 NETWORK SECURITY: NOT PROVEN`

后续若重启 E1 closure Gate，必须先同步旧判据：`implement` occurrence 中 loopback 可达是已接受的
预期能力，只有 LAN/external 越界才是网络拒绝边界的失败。不得继续用“loopback 必须 EPERM”
的旧判据误报 S2。

若必须新增 profile、workflow node、第二网络 owner、通用 SDK option passthrough 或放宽 external domain，立即停止。
这些都不是本轮实现 S2 所必需的条件。
