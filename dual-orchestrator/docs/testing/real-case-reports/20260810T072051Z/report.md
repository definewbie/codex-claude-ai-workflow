# Real Lifecycle Case Report

## Metadata

- Started At: 2026-08-10T07:20:51Z
- Finished At: 2026-08-10T07:31:10Z
- Final Status: START_FAILED
- Variant: terminal-delivery
- Workflow Path: bundled
- Workflow Source SHA-256: not_applicable
- Workflow: bundled@unknown
- Prompt Language: auto
- Repo: /Users/kriswang/work/projects/ai/captcha-auth-app
- Workstream: ws-20260810T072433Z-J75J
- Effective Workdir: /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J
- Ledger: /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J
- Task File: /Users/kriswang/work/projects/ai/captcha-auth-app/REQUIREMENT.md

## Verification Gate

# Verification Gate: Terminal-Delivery Captcha-Auth Completion v4

日期：2026-08-10

状态：`READY / NOT RUN`

本 Gate 的唯一目标是完成尚未交付的 captcha-auth 业务任务。旧 workstream
`ws-20260810T011244Z-8YVB` 只是历史证据，不 retry/resume；本轮使用最新
clean Dual 代码创建一个 fresh production `terminal-delivery` workstream。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@1` 完整交付 `/Users/kriswang/work/projects/ai/captcha-auth-app`，实现 Java 21 + Spring Boot 3 + Thymeleaf + PostgreSQL/Flyway 的手机号或邮箱注册登录，注册 OTP，登录前 image Captcha，并完成测试、README、change-scope、lineage 与 terminal acceptance。
- Source of truth: target `REQUIREMENT.md`、target baseline/final Git diff、persisted workflow definition/instance、typed proposal/contract/revision/review artifacts、run.json、change-scope evaluation、completion audit、terminal acceptance、PostgreSQL-backed verification、`mvn test` 和 README；workflow completion 单独不代表业务交付成功。
- Trigger precondition: Dual 必须为 clean SHA，且包含 typed-scope prompt checkpoint `07a11db3` 与 closure checkpoint `6370d9e7`；target 必须保持 clean baseline HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256 `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且没有任何实现文件；fresh package、workflow identity/shape、Claude 与 Codex minimal probes 全部 PASS。
- How precondition is checked before run: 记录 Dual HEAD/status/ancestry、target HEAD/tree/status/file list/hash、workflow source hash/id/version/node/edge/Agent/handoff shape、package start/end、CLI JAR hash/mtime、`runner-kind --variant terminal-delivery`和两个 ACPX provider probe 结果；`run-real-case.sh` 再次在创建 workstream 前强制 fresh artifact 与 first-runner probe，创建后核对 persisted workflow identity。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或无 unresolved must-fix、质量、scope 或 lineage 退化的 `ACCEPT_WITH_NOTES`；手机/邮箱注册登录、OTP、Captcha、安全、可访问性和所有 required tests 完整；PostgreSQL-backed verification 与 `mvn test` PASS；README 可复现；change-scope、lineage、completion audit PASS；handoff 不存在；超过 9 个 Agent executions 的每次调用均由 persisted semantic revision 解释，额外 provider occurrence 均由同一 run 内最多一次 bounded correction 解释并全量计入 token/耗时。
- FAIL: fresh profile 正确执行但 workflow 未完成，terminal acceptance 拒绝或留下 must-fix，业务功能、tests、PostgreSQL、README、contract、lineage、change-scope 或 completion audit 任一失败，handoff 被执行，或出现无法解释的 Agent/provider 调用、runtime retry、permission denial、non-zero provider process、failed correction 或 terminal contradiction。
- INVALID: Dual/target dirty，checkpoint ancestry、target baseline/hash、workflow identity/shape、fresh JAR、runner kind、任一 provider probe 或 ledger source 不符，使用旧 JAR/server/session/workstream，运行 `full`/external workflow，或 target 在启动前已有实现；这些情况不得解释为部分成功。
- Stop-loss: profile/freshness mismatch 必须在 provider 前停止；运行中出现 permission denial、non-zero provider process、failed correction、scope/lineage bypass、terminal contradiction 或 unexplained call 立即 quiesce；同一 review stage 连续第二次 `NEEDS_REVISION` 或 wall-clock 达到 90 分钟时中断并走 runner 的 terminate/reconcile/session-reap 路径；运行中不改 prompt/parser/model/workflow，不 retry/resume，不创建第二 workstream 救场。
- Run artifact freshness: Gate 提交后从该 clean SHA 执行 fresh Maven package，记录 build times、CLI JAR 绝对路径/mtime/SHA-256、workflow source SHA-256、Dual/target Git 身份、两个 probe 证据和 fresh workstream id；工件或证据不新鲜则本轮 `INVALID`。

## Frozen Workflow

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@1
source sha256: 5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479
nodes: 29
edges: 45
nominal acpx.agent_run nodes: 9
handoff nodes: 0
```

## Maturity Discipline

- captcha-auth 产品只有在本 Gate 的业务、测试和 terminal acceptance 全部通过时才可声明完成。
- typed-scope prompt semantics 当前仍为 `PROJECTED`。只有本轮自然出现 scope revision 且 provider 正确直接修改 typed entries 时，才记录额外 `PROVEN` 证据；未触发 revision 不影响产品 Gate PASS。
- 本 Gate 不授权新 schema、workflow、engine、registry、parser fallback 或 prompt 调试。


## Metrics

- Gate Rounds: 0
- Agent Runs: 3
- Timeline Nodes: 6
- Iteration Starts: 1
- Design Revisions: 1
- Acceptance Decisions: []
- Acceptance Reject Count: 0
- Final Acceptance Decision: none
- Had Acceptance Reject: false
- Repair Loop Count: 0
- Verification Confidence: UNKNOWN
- Verification Confidence Reason: no_final_acceptance_decision
- Original Repo Clean: true
- Invalid Reason: none
- Benchmark Grade: FAIL
- Benchmark Reason: status=START_FAILED

## Preflight Evidence

```json
{
  "schemaVersion": 1,
  "repo": "/Users/kriswang/work/projects/ai/captcha-auth-app",
  "taskFile": "/Users/kriswang/work/projects/ai/captcha-auth-app/REQUIREMENT.md",
  "verificationGate": "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/verification-gates/20260810-terminal-delivery-captcha-auth-completion-v4.md",
  "variant": "terminal-delivery",
  "workflowSelection": "variant",
  "workflowPath": "",
  "workflowSourceSha256": "",
  "workflowId": "",
  "workflowVersion": "",
  "workflowValidationStatus": "not_applicable",
  "workflowValidatedPath": "",
  "workflowPathMatchesValidated": false,
  "promptLanguage": "auto",
  "acpxAvailable": true,
  "buildMode": "maven_package",
  "buildStartedEpoch": 1786346451,
  "buildFinishedEpoch": 1786346660,
  "jarPath": "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar",
  "jarMtimeEpoch": 1786346244,
  "latestJvmInputMtimeEpoch": 1786343106,
  "artifactFreshnessStatus": "fresh_existing_artifact",
  "artifactFreshnessReason": "jar_mtime_covers_project_inputs",
  "artifactFreshnessProven": true,
  "providerProbeEnabled": "1",
  "providerProbeStatus": "passed",
  "providerProbeReason": "provider_probe_completed",
  "providerProbeExitCode": 0,
  "providerProbeTimeoutSeconds": 180,
  "providerProbeResultPath": "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260810T072051Z/provider-probe-result.json",
  "providerProbeStderrPath": "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260810T072051Z/provider-probe-stderr.log",
  "expectedRunnerKind": "acpx_claude_session",
  "providerProbeRunnerKind": "acpx_claude_session",
  "providerProbePathMatchesExpected": true
}
```

## Summary JSON

```text
/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260810T072051Z/summary.json
```

## Final Status Snapshot

```text
Workstream: ws-20260810T072433Z-J75J
Workspace: /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J
Branch: ai/ws-20260810T072433Z-J75J
Ledger: /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J

Workflow:
  Definition: agent-lifecycle-terminal-delivery@1
  Status: WAITING_FOR_RECOVERY
  Active nodes: [node-intake-claude_brainstorm-006]
  Open gates: [recovery-node-intake-claude_brainstorm-006]
  Active node definition: intake.claude_brainstorm
  Active node status: FAILED
  Active iteration: iter-002
  Recovery: Artifact contract failed
  Recovery detail: Inspect the diagnostic details, prompt contract, and source output before retrying the node.
  Next action: Inspect the contract diagnostic and fix the output contract before retrying.
  Command: dualctl retry --workdir /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J ws-20260810T072433Z-J75J
```

## Artifacts

| Artifact | Status | Path |
| --- | --- | --- |
| Final Report | missing | /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J/reports/latest-final-report.md |
| Workstream Summary | present | /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J/reports/workstream-summary.md |
| Handoff JSON | missing | /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J/artifacts/next-stage/handoff.json |
| Next-stage Prompt | missing | /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J/artifacts/next-stage/next-stage-prompt.md |
| Delivery PR Body | missing | /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J/delivery/pr-body.md |
| Delivery Commit Message | missing | /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J/.agent/workstreams/ws-20260810T072433Z-J75J/delivery/commit-message.txt |

## Git Status

```text
```

## Git Diff Stat

```text
```

## Useful Commands

```bash
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" status --workdir "/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J" "ws-20260810T072433Z-J75J"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" summary --workdir "/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J" "ws-20260810T072433Z-J75J"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" timeline --workdir "/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J" "ws-20260810T072433Z-J75J"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" runs --workdir "/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J" "ws-20260810T072433Z-J75J"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" recovery show --workdir "/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J" "ws-20260810T072433Z-J75J"
cd "/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260810T072433Z-J75J"
git status --short
git diff --stat
```

## Logs

All command logs are under:

```text
/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260810T072051Z/logs
```
