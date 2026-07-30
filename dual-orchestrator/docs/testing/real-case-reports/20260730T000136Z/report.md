# Real Lifecycle Case Report

## Metadata

- Started At: 2026-07-30T00:01:36Z
- Finished At: 2026-07-30T00:13:39Z
- Final Status: WAITING_FOR_RECOVERY
- Variant: full
- Prompt Language: zh-CN
- Repo: /private/tmp/dual-t6-cancer-clinic-readiness
- Workstream: ws-20260730T000417Z-5PXU
- Effective Workdir: /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU
- Ledger: /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU
- Task File: /private/tmp/dual-t6-cancer-clinic-task.md

## Verification Gate

# Verification Gate: T6 Cancer-Clinic Medium Case

- Target: One full workflow closes the pinned cancer-clinic plan-scoped recent-completed-cycle gap while preserving strict typed results, the seven-file change boundary, target quality, and business safety.
- Source of truth: workflow/current.json; every runs/*/run.json and strict result sidecar; task-contract.json; change-scope-evaluation.json; acceptance contract; backend pytest and miniprogram build logs; metrics/workstream-metrics.json; pinned target Git HEAD and diff.
- Trigger precondition: Dual clean SHA 3c23a8fd; pinned target ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927 and clean status; Python 3.13.1 ephemeral environment with requirements SHA-256 412637f581208f7a64fac57a705c1c4a2ef868d954c16c7924bddb1a4cc1a391 plus python-multipart==0.0.32; healthy cancer_clinic_test PostgreSQL; backend 191-test baseline PASS; miniprogram build PASS; fresh Dual reactor PASS; Claude and Codex probes PASS.
- How precondition is checked before run: Validate this gate; record Dual and target SHA/status, pip freeze, database identity/health, backend pytest count, frontend build result, fresh package evidence, runner kind, and both provider probes before workstream creation; create no workstream on any mismatch.
- PASS: Workflow COMPLETED; acceptance ACCEPT or non-regressive ACCEPT_WITH_NOTES; strict results valid; change-scope PASS; backend full pytest and miniprogram build PASS after change; no prohibited business behavior or wording; protocol correction at most one; semantic revision at most one; all usage reconciled; at most 15 Agent runs and 35 minutes elapsed.
- FAIL: Workflow, acceptance, strict contract, target tests/build, seven-file change boundary, business behavior, usage reconciliation, Agent-run cap, elapsed-time cap, correction bound, or revision bound fails.
- INVALID: Target or Dual SHA, dependency pin, database, baseline, build freshness, provider/model, runner, probe, usage source, or test environment is absent or not comparable; INVALID is not partial success.
- Stop-loss: Stop on a second protocol correction; a second semantic revision; more than 15 Agent runs; more than 35 minutes elapsed; out-of-scope change; prohibited medical conclusion or state mutation; typed fatal, reconnect, or rate-limit failure; missing usage; or target worktree contamination.
- Run artifact freshness: Require a fresh Maven package from clean SHA 3c23a8fd, jar/source mtime proof, artifactFreshnessProven=true, expected acpx runner kind, and providerProbePathMatchesExpected=true before workstream creation.

## Metrics

- Gate Rounds: 2
- Agent Runs: 7
- Timeline Nodes: 17
- Iteration Starts: 2
- Design Revisions: 2
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
- Benchmark Reason: status=WAITING_FOR_RECOVERY

## Preflight Evidence

```json
{
  "schemaVersion": 1,
  "repo": "/private/tmp/dual-t6-cancer-clinic-readiness",
  "taskFile": "/private/tmp/dual-t6-cancer-clinic-task.md",
  "verificationGate": "/private/tmp/dual-t6-cancer-clinic-verification-gate.md",
  "variant": "full",
  "promptLanguage": "zh-CN",
  "acpxAvailable": true,
  "buildMode": "maven_package",
  "buildStartedEpoch": 1785369696,
  "buildFinishedEpoch": 1785369842,
  "jarPath": "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar",
  "jarMtimeEpoch": 1785306552,
  "latestJvmInputMtimeEpoch": 1785287654,
  "artifactFreshnessStatus": "fresh_existing_artifact",
  "artifactFreshnessReason": "jar_mtime_covers_project_inputs",
  "artifactFreshnessProven": true,
  "providerProbeEnabled": "1",
  "providerProbeStatus": "passed",
  "providerProbeReason": "provider_probe_completed",
  "providerProbeExitCode": 0,
  "providerProbeTimeoutSeconds": 180,
  "providerProbeResultPath": "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260730T000136Z/provider-probe-result.json",
  "providerProbeStderrPath": "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260730T000136Z/provider-probe-stderr.log",
  "expectedRunnerKind": "acpx_claude_session",
  "providerProbeRunnerKind": "acpx_claude_session",
  "providerProbePathMatchesExpected": true
}
```

## Summary JSON

```text
/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260730T000136Z/summary.json
```

## Final Status Snapshot

```text
Workstream: ws-20260730T000417Z-5PXU
Workspace: /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU
Branch: ai/ws-20260730T000417Z-5PXU
Ledger: /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU

Workflow:
  Definition: agent-lifecycle-full@6
  Status: WAITING_FOR_RECOVERY
  Active nodes: [node-design-claude_design-018]
  Open gates: [recovery-node-design-claude_design-018]
  Active node definition: design.claude_design
  Active node status: FAILED
  Active iteration: iter-003
  Recovery: Workflow needs inspection
  Recovery detail: The final ACP assistant message did not contain a valid AgentResultEnvelope.
  Next action: Inspect recovery evidence before deciding whether retry is safe.
  Command: dualctl retry --workdir /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU ws-20260730T000417Z-5PXU
```

## Artifacts

| Artifact | Status | Path |
| --- | --- | --- |
| Final Report | missing | /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU/reports/latest-final-report.md |
| Workstream Summary | present | /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU/reports/workstream-summary.md |
| Handoff JSON | missing | /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU/artifacts/next-stage/handoff.json |
| Next-stage Prompt | missing | /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU/artifacts/next-stage/next-stage-prompt.md |
| Delivery PR Body | missing | /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU/delivery/pr-body.md |
| Delivery Commit Message | missing | /private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU/.agent/workstreams/ws-20260730T000417Z-5PXU/delivery/commit-message.txt |

## Git Status

```text
```

## Git Diff Stat

```text
```

## Useful Commands

```bash
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" status --workdir "/private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU" "ws-20260730T000417Z-5PXU"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" summary --workdir "/private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU" "ws-20260730T000417Z-5PXU"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" timeline --workdir "/private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU" "ws-20260730T000417Z-5PXU"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" runs --workdir "/private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU" "ws-20260730T000417Z-5PXU"
java -jar "/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar" recovery show --workdir "/private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU" "ws-20260730T000417Z-5PXU"
cd "/private/tmp/dual-t6-cancer-clinic-readiness-agent-runs/ws-20260730T000417Z-5PXU"
git status --short
git diff --stat
```

## Logs

All command logs are under:

```text
/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/docs/testing/real-case-reports/20260730T000136Z/logs
```
