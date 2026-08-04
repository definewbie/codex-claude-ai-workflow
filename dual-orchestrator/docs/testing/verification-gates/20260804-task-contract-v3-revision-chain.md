# Verification Gate: TaskContractProposal v3 Revision Chain

Status: `AUTHORIZED AFTER PREFLIGHT / ONE SAMPLE ONLY`

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
