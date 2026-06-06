# Contract Boundary Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make structured agent artifacts the machine-readable boundary between LLMs and the orchestrator.

**Architecture:** LLMs produce Markdown for humans plus `dual-control` status and typed `<dual-artifact>` JSON for the harness. The orchestrator injects prompt contracts from `ContractRegistry`, validates structured artifacts, writes sidecars, routes workflow state, and fails strictly when required artifacts are missing or invalid.

**Tech Stack:** Java 21, Maven, Spring Boot CLI/server modules, file ledger, acpx prompt templates.

---

## File Map

- Modify: `dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AgentPromptRenderer.java`
  - Inject `allowed_decisions`, `required_control_keys`, and artifact contract guidance into prompt templates.
- Modify: `dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AgentPromptRendererTest.java`
  - Verify prompt contract variables are rendered from `ContractRegistry`.
- Modify: `dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/PromptTemplateConsistencyTest.java`
  - Allow new template variables and reject hardcoded decision lists.
- Modify: `dual-adapter-acpx/src/main/resources/dev/dual/adapter/acpx/prompts/*.md`
  - Replace hardcoded decision lists with injected contract variables.
- Create or modify runtime artifact classes under `dual-runtime/src/main/java/dev/dual/runtime/handler/`
  - Parse typed `dual-artifact` JSON blocks and validate self-test / acceptance / handoff artifacts.
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/ArtifactContractValidator.java`
  - Require structured artifact for nodes that declare one; stop using Markdown sections as machine facts.
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractCompileHandler.java`
  - Fail when `task_contract_proposal` is missing instead of compiling from Markdown heuristics.
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/FailureArbiter.java`
  - Classify from structured fields, not `reviewSummary` keyword matching.
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/HumanReviewGateHandler.java`
  - Generate operator guidance from structured fields, not Markdown section strings.
- Update docs:
  - `dual-orchestrator/docs/features.md`
  - `dual-orchestrator/docs/architecture-and-execution-flow.md`

## Task 1: Prompt Contract Single Source

- [ ] **Step 1: Write failing renderer test**

Add a test in `AgentPromptRendererTest` that renders `acceptance-codex-review` for `acceptance.codex_verifier_review` and asserts:

```java
assertTrue(rendered.contains("ACCEPT"));
assertTrue(rendered.contains("ACCEPT_WITH_NOTES"));
assertTrue(rendered.contains("REJECT"));
assertFalse(rendered.contains("ACCEPT|ACCEPT_WITH_NOTES|REJECT"));
```

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
mvn -pl dual-adapter-acpx -Dtest=AgentPromptRendererTest test
```

Expected: FAIL because `{{allowed_decisions}}` is not rendered yet or templates still hardcode pipe lists.

- [ ] **Step 3: Implement renderer variables**

Update `AgentPromptRenderer` to read `node_id`, call `ContractRegistry.forNode(nodeId)` when available, and inject:

```text
{{allowed_decisions}}
{{required_control_keys}}
```

- [ ] **Step 4: Update prompt templates**

Replace hardcoded decision lists in agent prompts with injected allowed decisions and required control keys.

- [ ] **Step 5: Run prompt tests**

Run:

```bash
mvn -pl dual-adapter-acpx -am -Dtest=AgentPromptRendererTest,PromptTemplateConsistencyTest test
```

Expected: PASS.

## Task 2: Structured Artifact Validator

- [ ] **Step 1: Write failing validator tests**

Add tests in `ArtifactContractValidatorTest`:

- completed `implement.claude_self_test_report` without `self_test_report` artifact fails with `STRUCTURED_ARTIFACT_MISSING`.
- completed `acceptance.codex_verifier_review` with artifact decision different from `dual-control` fails with `CONTROL_ARTIFACT_DECISION_MISMATCH`.
- valid acceptance artifact writes sidecar fields from JSON, not Markdown headings.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
mvn -pl dual-runtime -Dtest=ArtifactContractValidatorTest test
```

Expected: FAIL because structured artifact validation is not implemented.

- [ ] **Step 3: Implement typed artifact parsing**

Create focused parser / validator code that:

- locates the latest `<dual-artifact type="...">`.
- parses JSON with Jackson.
- validates required fields and enum decisions.
- rejects missing / invalid artifacts with stable error codes.

- [ ] **Step 4: Wire into `ArtifactContractValidator`**

Require structured artifacts for:

- `implement.claude_self_test_report` -> `self_test_report`
- `acceptance.codex_verifier_review` -> `acceptance_review`
- `stage.claude_handoff` -> `stage_handoff`

- [ ] **Step 5: Run runtime tests**

Run:

```bash
mvn -pl dual-runtime -Dtest=ArtifactContractValidatorTest,BuiltinHandlerTest test
```

Expected: PASS.

## Task 3: Contract Compile Strict Mode

- [ ] **Step 1: Write failing contract compile test**

Add or update `ContractCompileHandlerTest` so a missing `task_contract_proposal` returns `TASK_CONTRACT_PROPOSAL_MISSING` and does not write `task-contract.json`.

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
mvn -pl dual-runtime -Dtest=ContractCompileHandlerTest test
```

Expected: FAIL because Markdown fallback still generates a contract.

- [ ] **Step 3: Remove default semantic fallback**

Change `ContractCompileHandler` so `compileFromProposal(...)` is required by default. Keep old extraction helpers only if tests still cover a clearly named legacy mode; do not call them in strict mode.

- [ ] **Step 4: Run contract tests**

Run:

```bash
mvn -pl dual-runtime -Dtest=ContractCompileHandlerTest,TaskContractProposalValidatorTest test
```

Expected: PASS.

## Task 4: Failure Classification From Fields

- [ ] **Step 1: Write failing arbiter tests**

Add tests showing:

- `reviewSummary` containing `unclear` does not classify as `CONTEXT_INSUFFICIENT` when structured fields do not support it.
- non-empty `contract_gaps` classifies as `CONTRACT_GAP`.
- non-empty `out_of_scope_files` classifies as `IMPLEMENTATION_BUG`.

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
mvn -pl dual-runtime -Dtest=FailureArbiterTest,FailureClassifyHandlerTest test
```

Expected: FAIL for the summary keyword case.

- [ ] **Step 3: Update arbiter rules**

Remove summary keyword matching from primary classification. Keep summary in diagnostic output only.

- [ ] **Step 4: Run failure classification tests**

Run:

```bash
mvn -pl dual-runtime -Dtest=FailureArbiterTest,FailureClassifyHandlerTest test
```

Expected: PASS.

## Task 5: Human Guidance From Structured Fields

- [ ] **Step 1: Write failing review packet test**

Add or update `ReviewPacketCliHintsTest` / `BuiltinHandlerTest` so Markdown text containing `Must Fix` does not trigger `revise` unless structured sidecar has blocking fields.

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
mvn -pl dual-cli,dual-runtime -am -Dtest=ReviewPacketCliHintsTest,BuiltinHandlerTest test
```

Expected: FAIL because guidance still uses Markdown-derived fields.

- [ ] **Step 3: Update guidance source**

Read guidance from structured sidecar fields:

- `blocking`
- `must_fix`
- `missing_evidence`
- `open_risks`
- optional `recommended_human_action`

- [ ] **Step 4: Run CLI/runtime tests**

Run:

```bash
mvn -pl dual-cli,dual-runtime -am -Dtest=ReviewPacketCliHintsTest,BuiltinHandlerTest,TraceCommandTest test
```

Expected: PASS.

## Task 6: Full Verification and Real Case

- [ ] **Step 1: Run main module tests**

Run:

```bash
mvn -pl dual-cli,dual-server -am test
```

Expected: PASS.

- [ ] **Step 2: Run one short real case**

Run `scripts/run-real-case.sh` with a small `cancer-clinic` task and `--variant full`.

Expected:

- If it passes, record strict structured artifact compatibility.
- If it fails on missing structured artifact, fix prompt/schema first. Do not add Markdown fallback.

- [ ] **Step 3: Update docs**

Update `docs/features.md` and `docs/architecture-and-execution-flow.md` with the implemented strict structured artifact boundary.

- [ ] **Step 4: Commit**

Commit with:

```bash
git commit -m "feat: harden agent artifact contracts"
```
