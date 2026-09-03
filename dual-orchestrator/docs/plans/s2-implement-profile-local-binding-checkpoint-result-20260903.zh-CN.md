# S2 Implement Profile Local Binding Checkpoint Result

日期：2026-09-03  
状态：`REVIEWED / READY TO COMMIT`  
基线：`a88a53b3`  
目标阶段：`P0 DIRECT REAL-CASE BLOCKER`

## 1. 结论

本 checkpoint 按计划完成最小收口：只有现有 `implement` permission profile 会在
occurrence-scoped Claude execution policy 中声明 `allow_local_binding=true`；`contract_discovery`、
`observe`、`review` 和 `contract_correction` 均保持 `false`。未新增 profile、workflow node、
ledger 字段或通用 SDK option passthrough。

reviewed acpx fork 将该值只投影到 SDK `Options.sandbox.network.allowLocalBinding`；
`managedSettings.sandbox.network` 在 true/false 两种情况下都省略该键，继续只承载限制性设置。

## 2. 变更边界

- `AcpxPermissionProfile` 成为 profile 到 local-binding capability 的唯一 owner。
- `AcpxAgentTaskHandler` 将已解析的 effective profile 交给 `ClaudeExecutionPolicy.write()`。
- 新签名按 profile 写入 durable policy；保留五参兼容重载并固定为 `false`，供历史冻结 fixture 编译。
- fork parser 继续对字段存在性和 boolean 类型 fail closed，并将 SDK option 与 managed settings 分离。
- E1 patch/manifest/installer/build-info/Doctor/configure identity 已机械同步。

## 3. 闭环证据

| 环节 | 结果 |
|---|---|
| Declared by | 现有 `permission_profile=implement` |
| Proven by | occurrence policy ref/SHA/lineage 与 reviewed fork identity |
| Consumed by | SDK `Options.sandbox.network.allowLocalBinding` |
| Enforced before | Claude session 创建与 Bash tool execution |
| Failure route | policy 缺失、篡改、非 boolean、identity 不符均在 provider 前拒绝 |
| Cannot bypass | profile sibling、handler policy、fork true/false、`_meta` 接线与 managed-settings 省略测试 |

## 4. 验证结果

- Java focused：`50/0/0/0`。
- reviewed fork focused：`63/0/0`。
- reviewed fork full：`986/0/0`。
- Dual full Maven reactor：`1298/0/0/0`，`BUILD SUCCESS`。
- fresh CLI package：`BUILD SUCCESS`。
- installer/configure：PASS；`bash -n` PASS。
- fresh Doctor：`Result: OK`。
- `git diff --check`：PASS。
- reviewed build identity：
  - upstream commit：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`
  - patch SHA：`41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c`
  - policy schema：`claude_execution_policy.v2`

实施中首次生成 patch 时未包含 fork 已有的 untracked 源文件，被 clean installer 的解析失败
暴露。已使用 intent-to-add 重生完整 patch，并保持 `UNBUILT` 两阶段盖章语义；文件集仍为原有
30 个 patch 文件，没有扩大生产范围。

## 5. Boundary Review

`PASS / INDEPENDENT DIFF REVIEW PASS`

- Source of truth：occurrence-scoped `claude-execution-policy.json`。
- 唯一 capability owner：`AcpxPermissionProfile`。
- 唯一外部消费路径：reviewed fork 投影到 Claude Agent SDK option。
- Sibling sweep：所有非 `implement` profile 保持关闭；workflow JSON 零修改。
- Residual：SDK local-binding effect 已由 S2 Gate 证明；OKX self-test 恢复仍须 real-case 验证。

## 6. 成熟度

- `IMPLEMENT PROFILE LOCAL BINDING: ENFORCED`
- `SDK LOCAL BINDING EFFECT: PROVEN`
- `OKX SELF-TEST RECOVERY: NOT PROVEN`
- `COMPLETE E1 NETWORK SECURITY: NOT PROVEN`

## 7. 独立 Diff Review

2026-09-03 独立代码级 diff review 结论：`PASS / 允许选择性提交`，`P0=0 / P1=0 / P2=4`。

四条 P2 均不阻塞：已知 queue timing flake 仅在高负载 `npm test` wrapper 中偶发，fork 直跑两次
全绿；profile-name 派生在封闭工厂集合下无旁路；最终 identity 版 full reactor 已复跑
`1298/0/0/0`；未来 E1 closure Gate 必须把 implement loopback 可达视为预期能力，继续以 LAN/external
作为拒绝边界。

下一步为选择性提交本 checkpoint。提交后才可单独决定是否恢复 OKX workstream；恢复仍是
real-case 验证，不属于本 deterministic checkpoint。
