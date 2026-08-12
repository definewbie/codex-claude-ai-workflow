# Verification Gate: Terminal-Delivery Captcha-Auth Completion v7

日期：2026-08-12

状态：`INDEPENDENT REVIEW PASS / COMMIT AUTHORIZED / INFRASTRUCTURE PREFLIGHT AUTHORIZED / PROVIDER NOT AUTHORIZED`

本 Gate 只验证两个相互依赖的目标：完成 captcha-auth 产品；若 typed review 自然产生 `BLOCKED`，
证明 checkpoint `99f827d5` 能把它路由到 human gate，由人类通过 UI 或 CLI 提交明确决定，并在同一
workstream 中继续到 contract compile。不得人为制造 `BLOCKED`，也不得复用或 resume v6 workstream。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@2` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。业务范围保持 `REQUIREMENT.md` 原文：Java 21、
  Spring Boot 3、Thymeleaf、PostgreSQL/Flyway；手机号或邮箱加密码登录；登录前 image Captcha；
  注册时完成短信或邮件 OTP；完成测试、README、change-scope、lineage、completion audit 与
  terminal acceptance。若 review 自然 `BLOCKED`，还必须完成
  `BLOCKED -> WAITING_FOR_HUMAN -> human note -> compile`。
- Source of truth: target `REQUIREMENT.md` 与 Git baseline/final diff；persisted workflow
  definition/instance；review sidecar；open/resolved `GateDocument` 的 `source_outcome`、
  `subject_input_refs`、action、note；review packet；compiled contract 与 contract-resolution；
  proposal/revision lineage；run.json；change-scope evaluation；completion audit；terminal acceptance；
  PostgreSQL-backed verification、`mvn test` 和 README。UI 文案、Markdown 自述或进程退出码不能单独
  证明 PASS。
- Trigger precondition: Dual 必须来自 clean SHA，ancestry 包含 `99f827d5` 和本 Gate 的已审查提交；
  target 必须保持 HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且无实现文件；fresh package、
  terminal-delivery workflow identity/shape、隔离 PostgreSQL 与 Claude/Codex minimal probes 全部 PASS。
- How precondition is checked before run: 记录 Dual/target Git identity 和 clean status、checkpoint
  ancestry、requirement hash、workflow id/version/source hash/node/edge/Agent/handoff shape、build 起止、
  CLI JAR path/hash/mtime、PostgreSQL health/query 与两个 provider probe。任一证据缺失时不得创建
  workstream。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed tests、`mvn test`、
  README、change-scope、lineage 和 completion audit 全部通过，handoff 不存在。若自然出现
  `BLOCKED`，还必须同时满足：decision node execution=`COMPLETED` 且 outcome=`BLOCKED`；打开的
  GateDocument `source_outcome=BLOCKED` 并引用当前 reviewer sidecar；workflow=`WAITING_FOR_HUMAN`
  而非 recovery；UI 或 CLI 提交非空 human note；resolved gate 逐字保存 note；compiler 从同一 sidecar
  的 `must_fix/missing_evidence` 生成 contract-resolution 并继续执行。
- FAIL: workflow 未完成、terminal acceptance 拒绝/留下 must-fix、产品或验证条件失败；`BLOCKED`
  进入 recovery、被 autorun 自动批准、允许空 note、gate/reviewer lineage 不一致、human note 未进入
  resolution、compile 拒绝合法 current lineage；或出现 scope/lineage bypass、permission denial、
  non-zero provider process、failed correction、无法解释的调用或 terminal contradiction。
- INVALID: Dual/target dirty，baseline/hash/workflow/fresh JAR/probe 不符，复用旧 session/workstream，
  使用非 terminal-delivery workflow，启动前 target 已有实现，或 operator 在运行中修改代码、prompt、
  parser、workflow、revision limit、provider/model 配置。若没有自然 `BLOCKED`，产品 Gate 仍可判
  PASS/FAIL，但 human-decision checkpoint 只能保持 `ENFORCED / NOT PROVEN`，不得伪造证明。
- Stop-loss: freshness/identity/probe 不符时在 provider 前停止；runtime typed failure、scope/lineage
  violation、failed correction 或 non-zero process 出现后不得继续。`WAITING_FOR_HUMAN` 是本 Gate 的
  合法暂停点，不触发 quiesce/recovery；等待人类查看 packet 并提交决定。wall-clock 达 90 分钟、
  ledger 与副作用矛盾或同类技术失败重复两次时终止并执行既有 terminate/reconcile/session-reap。
  不 retry、不创建第二 workstream 救场。
- Run artifact freshness: Gate review PASS 后提交本文与 active plan，得到 clean execution SHA；从该
  SHA fresh package 并记录 JAR 与 workflow hashes。所有运行工件必须晚于 checkpoint，persisted
  definition 创建后须与 frozen identity 精确一致。

## Human Interaction Procedure

现有 `run-real-case.sh` 会把 autorun 在 `WAITING_FOR_HUMAN` 的有意停止当作失败并执行收尾，因此本轮
不修改 harness，也不使用它跨越 human gate。operator 使用现有 CLI/UI 驱动同一 workstream：

1. `dualctl start --run` 创建唯一 fresh workstream并运行到第一个稳定边界；
2. 使用 `dualctl autorun` 驱动普通 gate；若输出表明需要 human decision，应得到
   `WAITING_FOR_HUMAN`，此非 FAIL；
3. 首选 management UI/console 阅读完整 review packet，在 note 中写入明确产品决定后 approve 或
   revise；CLI 等价后备为
   `dualctl review decide --action approve --note <decision> --workdir <workspace> <workstream>`；
4. 使用 `dualctl resume` / `dualctl autorun` 继续同一 workstream，直至下一个 human decision、
   recovery 或 terminal completion；
5. 最终从 ledger 生成 status、summary、timeline、runs、gate、contract-resolution、compile、scope、
   completion 与 acceptance 证据。UI 和 CLI 只是 command 入口，不是事实 owner。

本 Gate 不预先替人类选择手机号格式范围。若 reviewer 再次提出该问题，operator 必须根据产品意图
给出明确决定；note 的内容属于真实 human evidence，不能由 Dual 自动生成。

## Frozen Workflow

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@2
source: dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json
source sha256: 3b13d6c64a42870cc0a4c9c921a8315a6cc879b83527de20f8ed16a79906717e
nodes: 29
edges: 48
nominal acpx.agent_run nodes: 9
handoff nodes: 0
note: 9 个 TASK 节点实际调度 ACPX agent；其余 TASK 节点负责 context、compile、validation、report 或 delivery
```

## External Preflight

在 fresh package 后、workstream 创建前，将一次性证据写入 Git ignored 的
`dual-orchestrator/target/verification-preflight/captcha-auth-v7/`：

1. 新短生命周期 Codex probe；
2. runner-equivalent Claude probe；
3. 清理旧 v5/v6 probe 容器；若存在先前残留的 v7 容器也一并清理。确认
   `127.0.0.1:55432` 空闲后启动
   `postgres:16-alpine` 容器 `dual-captcha-auth-v7-postgres`，以 `pg_isready` 和 `SELECT 1` 验证；
4. 保存 Dual/target/workflow/JAR identity 与所有 exit code；最终复制到 case report 并核对 SHA-256。
5. 使用 `git check-ignore` 确认 preflight 输出目录被 Git 忽略；未被忽略时不得写入运行证据或提交。

probe 只验证 provider/ACP 可用，不验证模型会遵守业务合同。不得根据 provider 背后的模型名称改变
Gate 判定。

## Call Accounting And Maturity

- nominal Agent nodes：9；semantic revision 和 bounded correction 均作为额外真实 execution 计入；
- 不设 token 金额门槛，但必须记录调用数、input/output/cache token 与 wall-clock；
- checkpoint `99f827d5` 当前为 `ENFORCED / NOT PROVEN`；只有自然 human-decision 链继续到 compile
  才能升级为 `PROVEN`；
- captcha-auth 只有最终业务、测试与 acceptance 全部通过才算完成；
- 不新增 runner monitor、human-decision schema、module、registry、engine 或平台层。

## Result

`REVIEW PASS / INFRASTRUCTURE PREFLIGHT AUTHORIZED`。允许提交 Gate、active plan 与 review，并在 clean
SHA 上执行 Git/workflow identity、fresh package 和隔离 PostgreSQL preflight。Claude/Codex probes、
provider 与 real-case 尚未授权。
