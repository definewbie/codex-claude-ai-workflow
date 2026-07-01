# P0 Agent Execution Boundary Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AgentExecutionAttempt -> AgentExecutionOutcome` the single execution fact boundary for acpx/Claude/Codex runs, so P0 can distinguish provider/process wait, adapter failure, contract failure, policy failure, stale running, and successful completion without projection guesses.

**Architecture:** Keep workflow definitions unchanged. Adapter and artifact layers only produce typed evidence; `AgentExecutionFinalizer` / `AgentExecutionOutcomeResolver` produce the final outcome. `run.json`, recovery, metrics, and trace consume the same outcome read model. P0.5 governance remains in place but is not the target of this plan.

**Tech Stack:** Java 21, Maven, dual-core execution model, dual-runtime finalizer/validator, dual-adapter-acpx transport/metadata writer, dual-ledger-file metrics/recovery projections.

---

## Scope Boundary

This plan is P0 only.

Allowed:
- Fix execution attempt/outcome ownership.
- Fix acpx native/session process evidence and timeline event persistence.
- Fix adapter failure classification and recovery projection consistency.
- Add tests and replay/smoke gates that prove execution boundary behavior.

Not allowed:
- Provider-cache-aware/session compaction strategy changes.
- Memory-loop or repo-scope feature expansion.
- Complex real-case token benefit claims.
- Workflow definition changes unless a consistency test proves a current definition mismatch.

## Verification Gate For Any Real Provider Run

```text
Verification Gate:
- Target: P0 execution boundary produces consistent attempt/outcome and explains dominant latency phase.
- Source of truth: run.json.executionAttempt, run.json.executionOutcome, run.json.executionTimeline, metrics.providerMetrics.executionConsistencyCounts, metrics.tokenAttribution.perRun[].executionLatency.
- Trigger precondition: fresh jar/server includes this branch; current configured provider can return one simple response; P0.5 governance remains non-empty but is not the target.
- How precondition is checked before run: mvn package without --skip-build; inspect jar mtime; run one provider probe or stop as INVALID on overload/quota; confirm `git rev-parse HEAD` in report.
- PASS: no stale RUNNING, no attempt/outcome conflict, no false PROCESS_STALLED on successful output, terminal/provider/process/post-process latency visible in timeline, recovery reason is typed when failure occurs.
- FAIL: missing executionAttempt/outcome, contradictory attempt/outcome, PROCESS_STALLED with valid success outcome, missing timeline for a long run, recovery reason generic or invented by projection.
- INVALID: old jar/server, provider overload/quota before target node, missing run.json, projection refresh mutates source ledger, P0.5 policy blocks before execution boundary can be observed.
- Stop-loss: stop after first unexplained >180s single agent run, first WAITING_FOR_RECOVERY without typed adapter/contract reason, or first missing run.json after process exit.
- Run artifact freshness: mvn package timestamp and report path recorded before run; no --skip-build unless jar timestamp is explicitly verified.
```

---

### Task 1: Define Execution Evidence Audit Tests

**Files:**
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-core/src/test/java/dev/dual/core/execution/AgentExecutionOutcomeResolverTest.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/AgentRunMetadataFinalizerTest.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentTaskHandlerTest.java`

- [x] **Step 1: Add resolver tests for allowed terminal combinations**

Add tests proving:
- exit 0 + complete terminal + required artifact valid => `SUCCESS`.
- exit 0 + complete terminal + missing required artifact => `CONTRACT_FAILED`.
- exit non-zero + complete terminal + valid artifact => `SUCCESS_WITH_WARNINGS` only when process usability says complete output is usable.
- adapter signal `RATE_LIMITED` / reconnect / overloaded => `RECOVERABLE_ADAPTER_FAILURE`, never `CONTRACT_FAILED`.

Run:

```bash
rtk mvn -pl dual-core -Dtest=AgentExecutionOutcomeResolverTest test
```

Expected before implementation fixes, if a gap exists: a targeted failure naming the conflicting outcome.

- [x] **Step 2: Add finalizer tests for source precedence**

Add tests proving `AgentRunMetadataFinalizer` uses persisted `run.json.processResult` and `run.json.adapterSignals` before legacy `NodeResult` facts.

Run:

```bash
rtk mvn -pl dual-runtime -Dtest=AgentRunMetadataFinalizerTest test
```

Expected: all existing cases pass; new cases fail only if finalizer still derives outcome from legacy status.

- [x] **Step 3: Add handler tests for successful process with harness failure**

Add/keep tests proving P0.5 `EXPLORATION_POLICY_VIOLATION` changes node result and top-level run status, but does not rewrite process/attempt into adapter failure.

Run:

```bash
rtk mvn -pl dual-adapter-acpx -am -Dtest=AcpxAgentTaskHandlerTest#completedRunEmbedsExplorationPolicyResultInRunMetadata test
```

Expected: PASS after P0.5; this protects against P0 refactor regressing policy-vs-adapter separation.

---

### Task 2: Centralize Terminal Outcome Finalization

**Files:**
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-core/src/main/java/dev/dual/core/execution/AgentExecutionFinalizer.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-core/src/main/java/dev/dual/core/execution/AgentExecutionOutcomeResolver.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/AgentRunMetadataFinalizer.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AgentRunMetadataWriter.java`

- [x] **Step 1: Make finalizer the only writer of terminal attempt/outcome**

Ensure terminal run metadata is generated by one finalizer call that receives:
- `ProcessResult` / process usability.
- terminal control result.
- typed artifact contract result.
- adapter signals.
- usage visibility.
- harness policy failure, if any.

Do not let artifact validator or projection rewrite `executionOutcome`.

- [x] **Step 2: Preserve boundary separation**

Implement or verify these mappings:
- Adapter failure => `executionOutcome.status=RECOVERABLE_ADAPTER_FAILURE` or `RUNTIME_FAILED`.
- Artifact/contract failure after process success => `CONTRACT_FAILED`.
- Harness policy failure after process success => top-level run failed with policy reason, while execution attempt remains process-completed.
- Successful native/session output => no `PROCESS_STALLED`.

- [x] **Step 3: Run focused tests**

```bash
rtk mvn -pl dual-core,dual-runtime,dual-adapter-acpx -am \
  -Dtest=AgentExecutionOutcomeResolverTest,AgentRunMetadataFinalizerTest,AcpxAgentTaskHandlerTest \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS with no attempt/outcome conflict regressions.

---

### Task 3: Close Native Claude And Session Transport Evidence Gaps

**Files:**
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTransport.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxRunResult.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/NativeClaudeTerminal.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxFailureClassifier.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxFailureClassifierTest.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AgentFinalArtifactExtractorTest.java`

- [x] **Step 1: Verify native terminal success requires process success**

Keep invariant:

```text
native success = exitCode == 0 && nativeResult.parseable && !is_error && (stop_reason=end_turn || subtype=success)
```

`NativeClaudeTerminal` may remain exit-code agnostic, but classifier must require exit code as a conjunct.

- [x] **Step 2: Preserve native artifact extraction boundary**

Native output may extract a typed `<dual-artifact>` block, but plain markdown native output must produce empty artifact extraction and become contract failure if artifact is required.

Do not add markdown heading parsing or natural-language routing.

- [x] **Step 3: Run transport tests**

```bash
rtk mvn -pl dual-adapter-acpx -Dtest=AcpxFailureClassifierTest,AgentFinalArtifactExtractorTest,AcpxAgentTransportTest test
```

Expected: native/session mode tests pass; success-shaped JSON with non-zero exit remains adapter failure.

---

### Task 4: Persist Execution Timeline During Run

**Files:**
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxRunRequest.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxExecutionEventSink.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AgentRunMetadataWriter.java`
- Test: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentTaskHandlerTest.java`

- [x] **Step 1: Ensure transport emits event segments**

Required events:
- `process_started`
- `terminal_control_detected`
- `terminal_control_grace_elapsed`
- `recoverable_signal_detected`
- `process_wait_completed`
- `process_exited`
- `artifact_extracted`
- `contract_validated`

- [x] **Step 2: Ensure handler persists event segments before final result**

Prepared `run.json.executionTimeline[]` must update while transport runs. Final `writeCompleted` may replace/normalize with complete result timeline, but it cannot be the first time events exist.

Integrity constraints:
- Transport must not write `run.json` directly. It may only emit typed `AcpxExecutionEvent` values.
- `AgentRunMetadataWriter` is the only writer for `run.json` during an agent run.
- Running updates are append-only timeline updates. They may append to `executionTimeline[]`; they must not write terminal `status`, `failureReason`, `executionAttempt`, or `executionOutcome`.
- Each persisted timeline event must carry stable ordering data: `seq` or an equivalent monotonic append order, `kind`, `observedAt`, `elapsedMs`, and `details`.
- Every run.json update must use a safe write path: read current JSON, merge the new event, write a temporary JSON file, then atomically move it into place.
- If a run is interrupted before finalization, partial `run.json` must remain valid JSON with `status=prepared` and a partial timeline. It must not pretend to be completed.
- `AgentExecutionFinalizer` is the only terminal writer for `executionAttempt`, `executionOutcome`, legacy terminal status fields, and usage visibility.
- Any future watchdog or poller must publish events through the same writer API or a separate event file consumed by the writer. It must not become a second run.json writer.
- Projection may mark incomplete timelines as `timeline_incomplete`; projection must not repair source run state.

- [x] **Step 3: Add or verify test**

Test should simulate a transport that emits events before returning, then assert prepared run.json contains at least `process_started` and one later event before completion.

Run:

```bash
rtk mvn -pl dual-adapter-acpx -Dtest=AcpxAgentTaskHandlerTest test
```

Expected: PASS; no stale prepared-only state when transport throws after emitting events.

---

### Task 5: Align Recovery And Metrics Projections With Outcome

**Files:**
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/MetricsProjectionWriter.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/RecoveryAssessment.java`
- Modify: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/TraceCommand.java`
- Test: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-ledger-file/src/test/java/dev/dual/ledger/file/MetricsProjectionWriterTest.java`
- Test: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-ledger-file/src/test/java/dev/dual/ledger/file/RecoveryAssessmentTest.java`
- Test: `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/src/test/java/dev/dual/cli/TraceCommandTest.java`

- [x] **Step 1: Make metrics consume outcome first**

Metrics should report:
- `executionConsistencyCounts`
- `outcomeStatusCounts`
- `usageVisibility.gaps[].reason`
- `executionLatency.dominantPhase`

from `executionAttempt` / `executionOutcome`, not legacy status guesses.

- [x] **Step 2: Make recovery consume typed failure reason**

Recovery should distinguish:
- reconnect/session missing
- rate limit/overload/quota
- context window
- transport failure
- artifact contract failure
- harness policy failure

No generic “external failure” if typed reason exists.

- [x] **Step 3: Run projection tests**

```bash
rtk mvn -pl dual-ledger-file,dual-cli -am \
  -Dtest=MetricsProjectionWriterTest,RecoveryAssessmentTest,TraceCommandTest \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS; historical replay fixtures do not mutate ledger source files.

---

### Task 6: Historical Replay Proof

**Files:**
- No production code unless replay exposes a contradiction.
- Use existing `dualctl projections refresh`.

- [x] **Step 1: Select historical problematic workstreams**

Use at least:
- one stale RUNNING sample;
- one recoverable adapter failure sample;
- one slow process/provider wait sample.

Copy each workstream to `/private/tmp/dual-p0-replay-<ws>`.

- [x] **Step 2: Record source hashes before replay**

Run:

```bash
rtk find "run.json" /private/tmp/dual-p0-replay-<ws>/.agent/workstreams/<ws>/runs
rtk git --no-pager hash-object /private/tmp/dual-p0-replay-<ws>/.agent/workstreams/<ws>/workflow/current.json
```

If not in a git repo, use:

```bash
shasum /private/tmp/dual-p0-replay-<ws>/.agent/workstreams/<ws>/workflow/current.json
```

- [x] **Step 3: Refresh projections**

Run:

```bash
rtk java -jar dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar projections refresh --workdir /private/tmp/dual-p0-replay-<ws> <ws>
```

- [x] **Step 4: Verify source hashes unchanged**

Re-run the same hash commands. PASS only if workflow/current.json, nodes, and runs are unchanged.

- [x] **Step 5: Inspect generated metrics**

Required evidence:
- stale sample has execution consistency warning.
- recoverable sample has typed recoverable outcome.
- slow sample has `dominantPhase=process_wait` or another concrete phase, not unknown.

---

#### Task 6 Replay Evidence (2026-07-01)

Replayed three historical cancer-clinic workstreams against the current `dual-cli` jar after `mvn -pl dual-cli -am package -DskipTests`:

| Workstream | Purpose | Source hash result | Projection evidence |
| --- | --- | --- | --- |
| `ws-20260629T212347Z-86TZ` | output-incomplete recovery sample | `workflow/current.json` and combined `nodes+runs` hashes unchanged | `outcomeStatusCounts={OUTPUT_INCOMPLETE=1}`, `executionConsistencyCounts={consistent=1}` |
| `ws-20260630T112054Z-N4YR` | slow process/provider wait sample | `workflow/current.json` and combined `nodes+runs` hashes unchanged | `outcomeStatusCounts={SUCCESS=1, SUCCESS_WITH_WARNINGS=1}`, per-run `dominantPhase=process_wait` |
| `ws-20260701T001207Z-WALF` | recoverable adapter failure sample | `workflow/current.json` and combined `nodes+runs` hashes unchanged | `outcomeStatusCounts={RECOVERABLE_ADAPTER_FAILURE=1}`, per-run `dominantPhase=process_wait` |

Replay command shape:

```bash
java -jar dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar projections refresh --workdir /private/tmp/dual-p0-replay-<ws> <ws>
```

Conclusion: projection refresh is read-model-only for workflow/nodes/runs source files, and the refreshed metrics expose typed execution outcome plus latency phase without mutating ledger source.

---

### Task 7: Controlled Real-Case Smoke

**Files:**
- No code changes expected.
- Use existing real-case script.

- [ ] **Step 1: Write the Verification Gate into the run note**

Use the exact gate from this plan. Do not start provider if artifact freshness or provider precondition fails.

- [ ] **Step 2: Build/package without skip**

Run:

```bash
rtk mvn -pl dual-cli -am package
```

Expected: BUILD SUCCESS. Record jar mtime.

- [ ] **Step 3: Run a bounded cancer-clinic smoke**

Stop-loss:
- first single agent run >180s without timeline explaining dominant phase;
- first WAITING_FOR_RECOVERY without typed reason;
- first missing run.json after process exit;
- more than 3M provider tokens;
- more than one semantic repair loop.

- [ ] **Step 4: Classify result**

PASS only if:
- every run has `executionAttempt` and `executionOutcome`;
- no stale RUNNING;
- no false `PROCESS_STALLED`;
- P0.5 governance context remains non-empty;
- latency dominant phase is visible for slow runs.

FAIL if any execution boundary invariant breaks.

INVALID if provider overload/quota, old jar, or P0.5 policy blocks before P0 target can be observed.

---

## Commit Plan

1. `test(p0): lock execution outcome boundary cases`
2. `fix(p0): centralize agent execution finalization`
3. `fix(acpx): normalize native/session terminal evidence`
4. `fix(acpx): persist execution timeline during transport`
5. `fix(projection): align recovery and metrics with execution outcome`
6. `docs(p0): record replay and real-case verification gate`

Do not squash P0.5 changes into these commits unless they are already staged separately. If current worktree contains uncommitted P0.5 closure, commit that first before starting Task 1.

## Self-Review

Spec coverage:
- Execution source of truth: Tasks 1-4.
- Adapter/native/session evidence: Task 3.
- Stale/latency observability: Tasks 4-6.
- Projection/recovery consistency: Task 5.
- Real-case validation: Task 7.

No planned task changes workflow definition or token optimization strategy.

Residual risk:
- If provider is overloaded, real-case is `INVALID`, not `FAIL`.
- If P0.5 blocks before P0 target can be observed, run is `INVALID`; either adjust the smoke case or inspect policy evidence, but do not disable P0.5 globally.
