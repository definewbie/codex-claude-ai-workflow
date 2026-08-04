# Verification Gate: TaskContractProposal v3 Revision Chain

Status: `SAMPLE CONSUMED / TARGET CHAIN OBSERVED / PROMOTION WITHHELD`

- Target: Prove one real provider-driven intake chain executes
  `NEEDS_REVISION -> revised proposal -> READY -> human approval -> contract.compile`
  using TaskContractProposal schema v3. This gate proves obligation coverage and
  evidence preservation; it does not re-evaluate unrelated T6 remediation routing.
- Source of truth: Clean Dual Git SHA containing `83b01da`; pinned target and task
  hashes; workflow iteration documents; validated proposal and review sidecars;
  `TaskContractProposalReference`; decision-node input refs; human-gate result;
  compiled `TaskContract`; every relevant `run.json`; and the generated real-case
  report/summary.
- Trigger precondition: Dual is built from a clean SHA containing `83b01da`; the
  target is clean at `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`; the task SHA-256
  is `776750d5ed1d044d1414da5351159238edeeba409f4a9e57584ae771a3518af3`;
  deterministic v3 tests and full reactor pass; fresh package and actual Claude
  and Codex runner probes pass. The workflow definition must route intake
  `NEEDS_REVISION` to a child iteration and route `READY` through the requirement
  gate to `contract.compile`.
- How precondition is checked before run: Verify Git and task hashes and target
  cleanliness; inspect the bundled workflow edges; run focused v3 tests plus the
  full reactor; package from the clean SHA; require fresh-artifact proof; run one
  minimal probe through each actual acpx runner. Do not create a workstream if any
  deterministic or provider precondition fails.
- PASS: The first Codex product review returns `NEEDS_REVISION` with non-empty,
  unique typed `contract_obligations`; the child-iteration Claude proposal uses
  schema v3 and contains exactly one `revision_response` per obligation, with no
  unknown/duplicate obligation ID and only existing clause IDs; the second Codex
  product review returns `READY`; the current-iteration requirement gate is
  approved; `contract.compile` completes from that same iteration; the compiled
  contract preserves objective, scope, clause text and `evidence_refs`; all
  sidecars, references, lineage and execution facts are valid and terminal.
- FAIL: Once `NEEDS_REVISION` is triggered, any missing/duplicate/unknown
  obligation response, response to a non-existent clause, v1/v2 proposal,
  historical-review fallback, cross-iteration gate approval, failed compile,
  malformed sidecar, invalid lineage, non-zero provider process, failed bounded
  correction, permission denial, or non-terminal execution is FAIL. A second
  intake semantic revision is also FAIL for this sample.
- INVALID: The first Codex product review returns `READY` or `BLOCKED`, so the
  target revision chain is not triggered; any identity/freshness/probe field is
  absent; the target is dirty; or required ledger artifacts are missing. A direct
  `READY` is not a product defect, but it cannot prove this gate.
- Stop-loss: Stop at the first FAIL, second intake revision, provider/permission
  failure, or 35 minutes. Do not retry, resume, alter prompts/parsers/models,
  weaken validation, or run a second sample. If the trigger is not reached, mark
  the sample `INVALID/NOT_TRIGGERED`; do not reinterpret it as PASS.
- Run artifact freshness: The run must execute from a clean worktree at the gate
  commit, Maven-package after the latest JVM source mtime, and persist matching
  Git SHA, task hash, expected runner kind, passed provider probes and
  `artifactFreshnessProven=true` in `preflight.json`.

Maturity before this run is `ENFORCED` by deterministic cannot-bypass tests. Only
a PASS sample under this gate may promote the real revision chain to `PROVEN`.

## Actual Result

- Clean Gate SHA: `d446125`; v3 checkpoint: `83b01da`.
- Workstream: `ws-20260804T051509Z-CUVU`.
- Freshness build: 9/9 modules, 1175 tests, 0 failures/errors. Claude and Codex
  probes passed before workstream creation.
- The target chain was genuinely exercised. Iteration 1 Codex returned
  `NEEDS_REVISION` with exactly `CO-1` and `CO-2`. Iteration 2 Claude emitted
  schema v3 with exactly one response for each obligation (`CO-1 -> NG-5`,
  `CO-2 -> C-1,C-9`). The second Codex review returned
  `READY_FOR_PRODUCT_REVIEW`; the iter-002 requirement gate was approved; and
  iter-002 `contract.compile` completed.
- The compiled contract equals the revised proposal for objective, scope, all
  five narrative clause collections and `evidence_refs`. All four target Agent
  runs exited zero, decoded strict contracts, required no bounded correction and
  ended with typed `SUCCESS`.
- The overall runner sample is not Gate PASS. After the target chain had already
  completed, the operator incorrectly inferred that revision had not occurred
  from the active design process and interrupted the runner. The design node was
  subsequently closed as `FAILED` and the workflow reached
  `WAITING_FOR_RECOVERY`; the generated report also captured an earlier double-
  trap `INTERRUPTION_RECOVERY_FAILED` snapshot.

Verdict: the v3 path has strong real execution evidence, but this gate does not
promote it to `PROVEN`, because its own rule permits promotion only from an
overall PASS sample. The one-sample authorization is consumed. No retry, resume
or second sample is authorized.
