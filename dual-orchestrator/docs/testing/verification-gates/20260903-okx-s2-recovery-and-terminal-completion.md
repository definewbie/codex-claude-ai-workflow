# OKX S2 Recovery And Terminal Completion Verification Gate

日期：2026-09-03  
状态：`S2 PASS / WAITING FOR HUMAN IMPLEMENTATION REPAIR`  
样本：`ws-20260829T122452Z-9BZG`

## Verification Gate

- **Target**：使用 S2 checkpoint 恢复当前 `implement.claude_self_test_report` occurrence，证明本地
  listener 集成测试可在 Claude implement policy 下执行，并继续同一 workstream 直至下一 human gate
  或 terminal。
- **Source of truth**：`workflow/current.json`、当轮 node/run `run.json`、其
  `executionAuthority.policyRef/policySha256`、该 policy 文档、typed self-test contract 与 Maven/Surefire 证据。
- **Trigger precondition**：Dual HEAD 包含 `d43f4351`；reviewed acpx identity 为
  `fd173f04 / 41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c / v2`；
  current 为 `RUNNING / evt-000041`，唯一 active node 为
  `node-implement-claude_self_test_report-041 / READY / iter-003`；persisted definition 对该 node 声明
  `permission_profile=implement`；无 open gate。
- **How precondition is checked before run**：fresh package + Doctor；直读 current/node/definition；运行前复核
  reviewed acpx `build-info --json`。
- **PASS（S2 子目标）**：新 self-test run 的 policy ref/SHA 可重验且 `allow_local_binding=true`；本地 HTTP
  listener 与 stub 请求成功执行，不再出现 bind/listener 环境阻塞。
- **PASS（完整产品目标）**：typed decision 为 `SELF_TEST_COMPLETE`；声明的自测实际执行且零
  failure/error；workflow 进入后续 acceptance/human gate 或 terminal，无 adapter/provider fatal。
- **FAIL（S2 子目标）**：新 policy 不是 true，或 self-test 仍因 bind/listener 环境失败。
- **FAIL（产品目标）**：S2 子目标通过，但测试暴露真实产品缺陷并返回 `SELF_TEST_INCOMPLETE`。该结果必须
  进入 workflow 已声明的 implementation repair 路径，不得归因为网络环境，也不得冒充 S2 失败。
- **INVALID**：未使用 `d43f4351` 后的 JAR/reviewed binary，current/node/definition 前置漂移，
  policy/run 证据缺失，或 operator 绕过 persisted workflow transition。
- **Stop-loss**：一旦确认 policy 不是 true、同一 listener blocker 再现或 provider/adapter fatal，立即停止；
  若得到真实产品失败，则只允许沿现有 typed implementation repair 路径继续，不做 blind retry、不修 prompt、
  不新增网络机制。
- **Run artifact freshness**：fresh JAR Doctor PASS；新 run id/startedAt 必须晚于 `d43f4351`；policy ref
  必须落在该新 run 目录并通过 SHA/lineage 重验。

## 执行边界

本 Gate 恢复既有 workstream，不新建样本、不运行 E1 capability probe、不开放 external domain、
不修改 target 需求或 workflow。如到达 human gate，仅展示 authoritative evidence 并等待人工决定；
不自动代替 acceptance authority。

## 执行结果

- 新 occurrence `run-1788411416526` 的 policy SHA 已重验，`allow_local_binding=true`。
- sandbox 内本地 listener 与 HTTP stub 测试成功，S2 子目标 `PASS / REAL-CASE PROVEN`。
- Maven 执行 `44 tests / 0 failures / 18 errors`；18 个 error 的共同产品根因是
  `CandleSyncService` 在 `@PostConstruct` 中通过 `ApplicationContext.getBean` 获取自身代理，触发
  `BeanCurrentlyInCreationException`。附加 `byte-buddy-agent` 后 Mockito 环境错误消失，产品根因保持不变。
- typed self-test 正确返回 `SELF_TEST_INCOMPLETE`。随后 `implement.self_test_decision` 因既有一次 repair 已耗尽
  `max_revisions=1`，被通用 revision-limit 拦截器置为 `WAITING_FOR_RECOVERY`；它没有进入 frozen workflow 已声明的
  `BLOCKED -> gate.human_implementation_repair` 路径。
- 当前 `retrySafe=false`。`dualctl retry` 只会重跑同一 deterministic decision，并再次命中同一预算，不得执行。

## Revision-limit 路由恢复结果

- checkpoint `a44532d` 已修复通用路由顺序。对原 recovery 执行一次 deterministic retry 后，decision node
  正确完成为 `reason=revision_limit_reached / original=SELF_TEST_INCOMPLETE / max=1 / current=1`，并进入
  frozen workflow 已声明的 `gate.human_implementation_repair`；current 为 `WAITING_FOR_HUMAN`，无 provider 调用。
- Gate packet 随后被旧 consumer 拒绝，因为它错误要求 latest Agent sidecar 也必须写成 `BLOCKED`。该下游缺口已在
  `revision-limit-human-gate-source-disposition-followup-20260903.zh-CN.md` 中按同一边界修复并通过
  `1304/0/0/0` 回归。
- 当前 Gate 是 follow-up 前已创建的在途实例，不含新 Gate-node disposition。不得改写历史 ledger；人工可直接依据
  前驱 decision result、latest self-test sidecar 与 revision-limit event 作 `revise` 决定。下一次同类 Gate 将在 packet
  发布前机械重验两类事实。
