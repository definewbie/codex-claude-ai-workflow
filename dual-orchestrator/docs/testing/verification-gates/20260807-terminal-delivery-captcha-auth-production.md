# Verification Gate: Terminal-Delivery Captcha-Auth Production

日期：2026-08-07

状态：`FAIL / SAMPLE CONSUMED / ROOT-CAUSE ATTRIBUTED`。provider probes 已通过，
唯一授权的新 workstream 已运行并在第一次 intake semantic revision 被 strict
revision-delta validator 阻断。不得 retry/resume 或创建替代样本。

## 1. Target classification

- Target phase: `P0` integrated workflow proof.
- Existing production behavior: bundled `terminal-delivery` profile, no new
  workflow or Agent capability.
- Required checkpoints: `4212b960`, `114d64be`, `331119f8`, `36f18840`,
  `6fb51608`, and revision-delta evidence checkpoint `41e0ea70`.
- This Gate does not modify prompt, parser, contract schema, workflow routing,
  model configuration, retry policy, or human-gate semantics.

## 2. Natural task and applicability

Task repository:
`/Users/kriswang/work/projects/ai/captcha-auth-app`.

The user requested a new Java application with mobile/email registration and
login, SMS/email registration OTP, and an image Captcha before password login.
The task has no declared successor stage after final product acceptance.
Therefore the terminal profile's missing handoff and next-stage nodes are
`NOT_APPLICABLE` by task semantics, not an experiment-only deletion.

Frozen target baseline:

- target HEAD: `7c7e35e89a66b14c29a335958ad90c8a92779fa0`;
- target tree: `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`;
- requirement SHA-256:
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`;
- target verification SHA-256:
  `0dc1ee67b5979fdc43d0d6e250ab2eb825516fa216e75ea25dab5a1761d7a9d7`;
- baseline contains only `REQUIREMENT.md` and `VERIFICATION-GATE.md`; no prior
  implementation exists.

Historical captcha-auth workstreams are failure evidence only. They must not be
retried, resumed, or used as the execution target for this Gate.

## 3. Verification Gate

- **Target:** Run one fresh captcha-auth workstream with the bundled production
  `terminal-delivery` profile and prove that it can reach governed terminal
  completion without A10/handoff, while preserving contract obligations,
  independent reviews, acceptance, tests, typed change scope and lineage. Prove
  that the profile has nine nominal Agent nodes instead of full's ten and that
  every actual provider occurrence beyond those nine is explained by a
  persisted semantic revision or the existing single bounded correction.

- **Source of truth:** The frozen task files and target Git tree; persisted
  `workflow/definition.json` and instance state; `iterations/*.json`;
  `nodes/*.json`; every `runs/*/run.json` execution outcome, occurrence and
  correction disposition; verified task-contract proposal/reference and
  compiled contract; design/plan/implementation/acceptance artifacts;
  `change-scope-evaluation.json`; lineage validation; completion audit;
  workstream metrics; final Git diff; README; and post-run project test results.

- **Trigger precondition:** Dual worktree is clean and its HEAD contains all
  required checkpoints above plus this reviewed Gate. The bundled definition
  resolves as `agent-lifecycle-terminal-delivery@1`, has SHA-256
  `5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479`,
  contains 29 nodes, 45 edges and exactly nine `acpx.agent_run` definitions,
  and contains no handoff/next-stage nodes. The frozen target HEAD/tree and task
  hashes match, the target is clean and has no implementation. A fresh CLI jar
  must be built after the latest JVM input. The configured Claude and Codex
  runner kinds must resolve and one minimal probe for each must pass. Model names
  are recorded as adapter evidence but are not orchestration preconditions.

- **How precondition is checked before run:** Recompute Dual ancestry, target
  HEAD/tree and all hashes. Validate and inspect the bundled definition using the
  fresh jar. Record package start/end, jar path/mtime/hash, latest JVM source
  mtime and `artifactFreshnessProven=true`. Run deterministic workflow/contract
  tests only if tracked production or test code changed after their last clean
  checkpoint; documentation-only changes do not justify repeating the full
  reactor. Run minimal provider probes only after every deterministic preflight
  field passes. Before the first Agent call, inspect the newly persisted
  workflow id/version/hash and stop if it differs.

- **PASS:** All of the following hold:
  1. the persisted production definition is exactly
     `agent-lifecycle-terminal-delivery@1`, with the frozen source hash and no
     A10/handoff/next-stage node;
  2. workflow reaches `COMPLETED` through the terminal human acceptance gate;
  3. final acceptance is `ACCEPT`, or `ACCEPT_WITH_NOTES` with no unresolved
     must-fix item and no quality or scope regression;
  4. strict task contract, design, plan, implementation and acceptance artifacts
     are valid, resolve through verified lineage and preserve all contract
     obligations required by the delivered behavior;
  5. change-scope evaluation is `PASS`, and the final Git diff contains no
     undeclared or unexplained file change;
  6. `mvn test` passes after delivery, including PostgreSQL-backed registration,
     authentication, Captcha and OTP coverage required by the task; README
     documents the reproducible PostgreSQL and delivery-adapter setup;
  7. the persisted definition does not declare handoff or next-stage work;
     `CompletionAuditWriter` therefore does not require those artifacts, the
     completion audit is `ready` with no gaps, and no handoff or next-stage
     artifact is generated. `NOT_APPLICABLE` is the Gate's classification of
     these combined typed facts, not a field invented in the audit document;
  8. the nominal Agent definition set is exactly the established A1-A9 set.
     Actual Agent executions and provider occurrences are reported separately:
     every execution beyond the nine nominal executions must resolve to a typed
     semantic iteration, and every additional occurrence within an execution
     must resolve to its persisted bounded-correction disposition. No runtime
     retry or unexplained call is allowed.

  If semantic revision and correction are both zero, this sample additionally
  proves an actual `10 -> 9` natural-task call result. If they are nonzero but
  fully typed and the workflow still passes, production terminal delivery is
  proven for the task and the fixed-call subtraction remains proven, but this
  sample does not claim an actual total of nine calls.

- **FAIL:** The correct, fresh profile runs but workflow does not complete;
  acceptance rejects or leaves unresolved must-fix work; a required obligation,
  strict artifact, lineage, test, PostgreSQL verification, change-scope rule or
  completion audit fails; A10/handoff/next-stage executes or is required; an
  unexplained Agent execution/provider occurrence exists; a runtime retry is
  used; failed correction, non-zero provider process, permission denial,
  terminal-state contradiction, or historical-workstream mutation occurs.

- **INVALID:** Dual or target is dirty; required checkpoint ancestry, target
  tree, task hash, workflow id/version/hash, fresh jar, runner kind, probe or
  required ledger source is absent or mismatched; an old jar/server/worker is
  used; the run starts with `full`, `contract`, or an external experiment
  workflow; the target already contains implementation; or the new workstream
  cannot be distinguished from historical captcha-auth workstreams. `INVALID`
  cannot be reported as partial success and does not authorize a replacement
  sample.

- **Stop-loss:** Do not retry/resume either historical workstream. For the new
  workstream, use the production runner and existing human gates. Stop and
  quiesce immediately on profile mismatch, stale artifact, permission denial,
  non-zero provider process, failed bounded correction, scope/lineage bypass,
  terminal-state contradiction, or an unexplained call. A single typed
  `NEEDS_REVISION` at a review stage is normal semantic iteration; stop if that
  same stage produces a second consecutive typed `NEEDS_REVISION`, regardless
  of natural-language similarity, or if total elapsed time exceeds 90 minutes.
  Do not change prompt/parser/model, issue a runtime retry, or create a second
  workstream to rescue the result.

- **Run artifact freshness:** The Gate report must record the clean Dual SHA and
  checkpoint ancestry, package times, jar path/mtime/hash, target HEAD/tree and
  task hashes, persisted workflow identity/hash, configured runner kinds, actual
  provider/model identities, probe results, new workstream id and cutover time.
  Any missing freshness field makes the result `INVALID`.

## 4. Call accounting

The Gate must report three different values and must not collapse them:

```text
nominal production Agent definitions = 9
actual Agent executions = 9 + typed semantic-revision executions
actual provider occurrences = actual Agent executions + bounded-correction occurrences
```

The full profile's nominal tenth call is A10 `stage.claude_handoff`. This Gate
proves its non-applicability through the persisted workflow definition and
completion audit, not by subtracting one from an observed total after the run.

One natural sample cannot prove statistical stability across all task types.
Together with the existing A3 same-case proof, a PASS supplies two-task evidence
that the production profile preserves quality while removing the fixed A10 call.

## 5. Maturity discipline

- Current bundled profile wiring: `ENFORCED`.
- A3 same-case terminal result: `PROVEN FOR ONE A3 SAMPLE`.
- Captcha-auth production task: `NOT RUN` until this Gate passes CC review and
  preflight/probes are separately authorized.
- Valid revision-to-review PASS B remains `ENFORCED / NOT PROVEN` unless it
  occurs naturally and its exact typed chain is independently reviewed.
- No result from this Gate may claim universal model obedience, universal
  `10 -> 9` actual calls, or statistical stability across task classes.

## 6. Independent review

Independent read-only review: `PASS`.

- no P0/P1 findings;
- production definition identity, 29/45 graph shape, A1-A9 Agent set, absence
  of handoff nodes, full-profile A10 preservation and target hashes were
  independently verified;
- call accounting and quality sources were accepted;
- review P2 concerning a presumed persisted `NOT_APPLICABLE` field was corrected
  to the actual owner: workflow definition plus completion-audit evaluation;
- review P2 concerning materially unchanged obligations was replaced by a fully
  typed stop-loss: second consecutive `NEEDS_REVISION` at the same stage.

## 7. Executed sample

- Dual SHA: `46ba22417884e17b246b3148a273c3b0848b3bfe`.
- CLI jar SHA-256:
  `3f927e9dd3b2f8b3cacf5429cad8e193fbf440dea8a1f66b5fb12269699f4f7c`.
- Workstream: `ws-20260807T134039Z-R47H`.
- Persisted workflow: `agent-lifecycle-terminal-delivery@1`, 29 nodes, 45 edges,
  nine nominal Agent nodes, no handoff/next-stage node.
- Bundled source SHA-256:
  `5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479`.
- Persisted JSON SHA-256:
  `53ff9473de296685523451f4c604ced7a351fa3d36386db6f95f5049ed879e6c`.
  The byte hashes intentionally differ because persistence adds the definition
  `schemaVersion`; an object-level comparison found no other difference. Gate
  identity is therefore the bundled source hash plus the loaded id/version and
  29/45/9/no-handoff semantic shape, not equality of formatting-dependent file
  bytes.
- Provider probes: Claude PASS; Codex PASS.
- Actual execution before stop: three Agent executions, three provider
  occurrences, one semantic revision, zero bounded corrections.
- Provider identities: Claude adapter used `zhipu/glm-5.2`; Codex adapter used
  `openai/GPT-5.6-Sol`.
- Final workflow state: `WAITING_FOR_RECOVERY`; active failed node
  `node-intake-claude_brainstorm-006`; no implementation occurred.

The initial proposal and Codex product review completed successfully. The review
created typed obligations `CO-1..CO-4`. The revision returned a strict-valid
envelope and referenced all four obligations, but changed many clauses outside
those declared deltas, added `NG-6`, `NG-7`, `C-19`, `TR-19`, and `TR-20`, and
deleted `R-7`. Artifact validation therefore emitted:

```text
TASK_CONTRACT_REVISION_DELTA_INVALID
UNDECLARED_CONTRACT_CLAUSE_CHANGE
UNDECLARED_CONTRACT_CLAUSE_ADDITION
CONTRACT_CLAUSE_DELETION_NOT_ALLOWED
```

No revised proposal reference or second Codex product-review occurrence was
created. The persisted `AGENT_RECONNECT_REQUIRED` signal is
`superseded=true`; the authoritative failure is `CONTRACT_FAILED /
required_artifact_contract_failed`, not transport recovery.

## 8. Root-cause attribution

The immediate invalid artifact was produced by the Claude/model execution: it
rewrote clauses beyond its declared `revision_responses`. The validator behaved
correctly and prevented publication and downstream review.

The run also proves a Dual-owned projection gap. The revision prompt included:

- the verified previous proposal reference and SHA-256;
- the complete Codex review and typed obligations `CO-1..CO-4`;
- the rule to preserve every existing clause not named by the current revision.

It did not include the previous proposal clause body: the projected review
document carried `taskContractProposal=null`, while the referenced proposal was
not rendered into the prompt. Exact preservation therefore depended on implicit
same-session memory. That is insufficient for a strict delta contract.

Boundary result:

- **Declared by:** verified previous proposal ref plus typed review obligations.
- **Proven by:** persisted prompt, revision output and typed delta diagnostic.
- **Consumed by:** artifact validation before proposal-reference publication.
- **Enforced before:** second Codex review and contract compilation.
- **Failure route:** node `FAILED`, outcome `CONTRACT_FAILED`, workflow
  `WAITING_FOR_RECOVERY`.
- **Cannot-bypass:** no new proposal ref and no second reviewer occurrence.

The next change must project the verified baseline proposal exactly into the
revision prompt. It must not loosen validation, auto-merge model output, rely on
filesystem guessing, add retry, or create a new platform abstraction. No second
real-case is authorized before that boundary has deterministic proof and review.

Full report:
`../real-case-reports/20260807T135202Z/report.md`.

## 9. Follow-up checkpoint

Independent read-only review returned `WARN` with one P1 and one P2:

- P1 confirmed the verified-baseline projection gap and agreed with the dual
  attribution: model-owned invalid output plus Dual-owned missing baseline;
- P2 found the source/persisted byte-hash ambiguity documented above and agreed
  that the run remains `FAIL`, not `INVALID`.

The bounded P1 fix is implemented but not committed. It preserves the owners:

- `TaskContractProposalReference` verifies reference identity, hash and lineage;
- `TypedContextPayloadBoundary` serializes the verified proposal exactly once;
- `AgentPromptRenderer` consumes that payload only for a validated intake
  `NEEDS_REVISION` review and fails before provider dispatch if verification
  fails;
- `TaskContractProposalValidator` remains the only revision-delta acceptance
  owner.

No schema, workflow, validator, retry or correction behavior changed. The
independent code-level review returned `PASS` with no P0/P1. Focused tests are
`53/0`; the full reactor is `1194/0/0`. The verified-baseline projection is
therefore deterministic `ENFORCED`, but it is not real-case `PROVEN` and does
not authorize a second real-case.

The review recorded one non-blocking pre-existing boundary: human requirement
`revise -> intake.claude_brainstorm` does not carry an
`intake.codex_product_review / NEEDS_REVISION` document and therefore does not
trigger this baseline guard. That path is a separate follow-up item; it is not
silently claimed by or added to this checkpoint.
