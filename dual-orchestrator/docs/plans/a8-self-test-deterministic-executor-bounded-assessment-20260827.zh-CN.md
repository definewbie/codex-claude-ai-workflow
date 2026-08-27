# A8 Self-Test Deterministic Executor Bounded Assessment

日期：2026-08-27
状态：`INDEPENDENT REVIEW PASS / NO-GO FOR REPLACEMENT / VALIDATED`

## 1. 唯一问题

能否在不降低验证质量和路由可靠性的前提下，把 `implement.claude_self_test_report` 从一次 Agent 调用替换为确定性命令执行，
从而稳定减少一次模型调用？

本 assessment 只判断 replacement readiness，不授权 production 改动、workflow/schema 扩张、provider 或 real-case。

## 2. 当前边界与 owner

当前 self-test 不是纯命令节点：

1. workflow 把它声明为 `acpx.agent_run`，产出 `self_test_report`，允许 READY、INSUFFICIENT_EVIDENCE、BLOCKED
   三类 stage outcome（`agent-lifecycle.terminal-delivery.json:269-285`）；
2. `SelfTestResult` 同时承载 `checks_run`、`checks_skipped`、`evidence`、`missing_evidence`、
   `acceptance_notes` 与 `verification_summary`（`AgentResultEnvelope.java:190-201`）；
3. projector 把上述字段写入 durable typed artifact（`AgentResultDocumentProjector.java:80-89`）；
4. `implement.self_test_decision` 消费该 artifact：COMPLETE 才进入 artifact validation，INCOMPLETE 回到 bounded
   implementation repair，BLOCKED 进入 human gate（workflow edges `:790-840`）；
5. acceptance context 要求同一 validated self-test ref 且 decision=SELF_TEST_COMPLETE，缺失不能 dispatch reviewer
   （workflow node config `:314-325`）。

因此 authoritative fact 不是“命令 exit=0”，而是带 lineage 的 self-test evidence artifact；routing owner 仍是
`ReviewDecisionHandler` / `RoutingEngine`，不能由 shell exit code 直接替代。

## 3. Canonical command inventory

全 production 搜索 `canonical command`、`test_command`、`build_command`、`validation_command`、
`commands_to_run` 后，没有找到可由 orchestrator 权威消费的 repository verification entry：

- `PlanArtifactDocument.validation_plan` 是 Agent 规划内容，不是可信可执行命令；
- `AgentCapabilityCatalog` 只声明 Read、rtk、codegraph/gitnexus 等能力，不声明项目测试命令
  （`dual-runtime/src/main/java/dev/dual/runtime/governance/AgentCapabilityCatalog.java:65-130`）；
- workflow node config 没有 test command；TaskContract 也没有 executable-command owner；
- 根据 `pom.xml`、`package.json`、Gradle 文件名猜命令会建立新的 heuristic owner，并错误处理 profiles、容器、服务、
  多模块参数和用户要求的专项验证。

结论：当前不存在 deterministic executor 可以安全执行的 authoritative command input。

## 4. Captured captcha-auth evidence

只读重建：

`/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260818T011340Z-WA53/.agent/workstreams/ws-20260818T011340Z-WA53`

该 workstream 的 self-test 节点共有 8 个 Agent runs：

- 总 duration：2,311,286 ms，约 38.5 分钟；
- 3 个 SUCCESS，5 个 adapter/output failure；
- typed decisions：2 次 SELF_TEST_COMPLETE、1 次 SELF_TEST_INCOMPLETE；
- 首个有效 INCOMPLETE 样本运行 `mvn test` 后，从 96 tests 的失败堆栈识别出模板属性和 Testcontainers 配置两类
  确定性实现缺陷，并触发 implementation repair；
- 后续 COMPLETE 样本不仅读取 exit code，还核对 96/99 tests、关键并发/OTP 修复、Testcontainers/PostgreSQL 证据、
  skipped/missing evidence 与 repository hygiene。

成本必须拆分解释：5 个失败 runs 合计 1,605,875 ms，占总时长约 69.5%；3 个有效语义调用合计约 705 秒，约
11.8 分钟。因此 38.5 分钟不是 deterministic replacement 可节省的语义成本上限。降低 adapter/output failure rate
可能有更高 ROI，但它属于 execution reliability 边界，不能用来证明 replacement 输入边界已存在，也不在本 A8 scope。

这证明两件事同时成立：

1. A8 的调用/耗时成本真实且值得优化；
2. 当前 Agent 不只是格式化命令输出，直接替换会丢失已在真实 case 中产生过的 defect discovery 与 evidence sufficiency
   判断。

## 5. 候选比较

| 候选 | 判定 | 原因 |
|---|---|---|
| 固定执行 `mvn test` / `npm test` | REJECT | repository heuristic，不是 authoritative input；不通用 |
| 执行 plan 的 `validation_plan` | REJECT | Agent prose，不是 trusted executable contract，存在命令注入和语义漂移 |
| exit=0 自动合成 SELF_TEST_COMPLETE | REJECT | 跳过 skipped/missing evidence、专项测试、hygiene 与结果解释，绕过 typed owner |
| exit!=0 自动 SELF_TEST_INCOMPLETE | REJECT | 环境/工具失败与实现缺陷混淆，会错误触发 semantic repair |
| deterministic command 后始终再调用 Agent | REJECT | 不减少 Agent 调用，只增加一次重复执行和成本 |
| 有 authoritative command 时 PASS fast path，异常/歧义回 Agent | FUTURE CANDIDATE | 只有 command、evidence schema、timeout 和 failure classification 都由外部 typed owner 声明后才成立 |
| 保留当前 Agent self-test | CURRENT DECISION | 当前唯一不丢失真实语义的选择 |

## 6. 决策

`NO-GO FOR DIRECT REPLACEMENT`。

这不是证明 self-test Agent 永远必要，而是证明当前缺少 replacement 所需的输入边界。立即实现会新增 heuristic command
discovery、shell-to-artifact 推断和路由语义，复杂度增长与 subtraction 目标相反。

不修改 production，不跑 synthetic provider probe，不为该 NO-GO 结论制造 real-case。

## 7. Revisit triggers

只有满足以下任一条件，A8 才能重新进入设计：

1. 用户或 repository 以 typed、trusted、非 Agent prose 的方式声明 canonical verification command；
2. workflow/profile 已有可验证 command binding，包含 cwd、timeout、environment policy 与 expected evidence；
3. deterministic runner 能区分 implementation failure、tool/environment failure 与 insufficient evidence；
4. self-test artifact projector 能从真实 command facts 生成当前 contract，且 acceptance 不再依赖模型补写关键语义；
5. captured replay 证明 PASS fast path 与原 Agent artifact 在 checks/evidence/missing-evidence/routing 上不退化。

不得把“仓库里有 pom.xml”或“多数项目使用 mvn test”当 revisit trigger。

## 8. End-to-end invariant

| 维度 | 当前判定 |
|---|---|
| Declared by | workflow `self_test_report` contract + `SelfTestResult` typed shape |
| Proven by | parser/validator/projector、captured 3 个有效 artifacts |
| Consumed by | self-test decision、artifact validation、acceptance context、final report |
| Enforced before | SELF_TEST_COMPLETE validated ref 是 acceptance dispatch 前置条件 |
| Failure route | INCOMPLETE -> bounded implementation repair；BLOCKED -> human gate；adapter failure -> recovery |
| Cannot-bypass test | 既有 self-test semantic routing、required evidence、lineage/context tests |
| Residual gap | 无 authoritative canonical verification command，不能建立 deterministic replacement |

## 9. 下一步

独立只读架构评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`，逐项确认 canonical command owner 不存在、captured
defect discovery 真实、当前无安全 fast path。A8 以 `VALIDATED / REPLACEMENT NOT MODELED` 关闭，不写 production
code。下一步转入 `small workflow profile` bounded assessment，优先寻找能删除完整 Agent node、同时保留 typed
contract/human gate/verification kernel 的自然任务边界。
