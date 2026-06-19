# Technical Debt

This file tracks deliberate MVP trade-offs. Items here are acceptable for the
current development stage, but should be revisited before the project becomes a
stable open-source release.

## Runtime Failure Signal Catalog Configuration

Status: accepted MVP debt

Current state:

- `AcpxFailureSignalCatalog` keeps provider / adapter failure signal patterns in
  Java code.
- This is acceptable for the MVP because the catalog belongs to the acpx adapter
  compatibility layer, is tested, and keeps workflow / ledger / UI code free of
  provider-specific string matching.

Why it is debt:

- From a 12-factor perspective, provider-specific runtime differences are
  deployment configuration, not permanent application code.
- Claude, GLM, Codex, Cursor, and future acpx-compatible tools may emit
  different quota, reconnect, context-window, auth, or timeout diagnostics.
- Requiring a code change for every provider wording change will slow down
  field calibration.

Future direction:

- Keep the current model boundary:
  - `AcpxFailureEvidence`: stdout, stderr, exit code.
  - `AcpxFailureSignalCatalog`: provider / adapter signal definitions.
  - `AcpxFailureClassifier`: evidence-to-signal classification and fallback.
- Move built-in signal definitions to a versioned resource file, for example
  `acpx-failure-signals.json`.
- Allow optional external overrides through environment or workspace config, for
  example `DUAL_ACPX_FAILURE_SIGNALS=/path/to/signals.json`.
- Keep strict validation and tests for loaded signal definitions.

Non-goal for now:

- Do not introduce a general rules engine. The catalog is a small adapter
  compatibility mechanism, not a workflow decision engine.

Mitigated in current code:

- Transport retry for missing acpx sessions now reuses the same runtime failure
  classifier instead of maintaining a separate stderr-only string check.
- Runtime failure evidence now includes stdout, stderr, and exit code.

## Agent Terminal Contract Configuration

Status: mitigated, still MVP debt

Current state:

- `AgentTerminalContract` centralizes the terminal completion contract:
  `[done] end_turn` plus the terminal `dual-control` block with required fields.

Why it is debt:

- The marker and control-block requirements are still built-in Java constants.
- Future ACP-compatible agents may expose a more structured completion signal.

Future direction:

- Keep the centralized contract object.
- Allow adapter-specific terminal contract definitions if acpx or future tools
  expose different completion markers.

## Contract Compiler Heuristics

Status: removed from runtime

Current state:

- `ContractCompileHandler` no longer derives canonical TaskContract fields from
  ordinary Markdown prose during normal execution.
- The canonical path requires a trusted
  `<dual-artifact type="task_contract_proposal" version="1">` from the Claude
  intake output, followed by deterministic validation.
- The old Markdown extraction logic and compatibility entry point have been
  removed from runtime code and tests.

Why it is debt:

- This is no longer an active runtime debt.
- Future natural-language-to-contract support would be a new feature, not a
  hidden fallback path.

Future direction:

- Keep structured contract proposals emitted by the intake Agent as the default
  canonical path.
- If profile/template-based extraction is ever reintroduced, implement it as an
  explicit feature with a separate contract, strict validation, and tests that
  prove it cannot silently replace the canonical proposal path.

## Review Noise Fallback

Status: mitigated, still MVP debt

Current state:

- `FailureArbiter` prioritizes structured acceptance fields such as
  `contract_gaps`, `out_of_scope_files`, `missing_tests`, and `context_gaps`.
- Text fallback is centralized in `ReviewNoiseSemantics`.

Why it is debt:

- The fallback still recognizes a few English phrases such as non-blocking
  review noise.

Future direction:

- Make acceptance artifacts carry sufficient structured fields so text fallback
  becomes rare or removable.

## Gate And Iteration Semantics

Status: mitigated

Current state:

- Gate guidance reads `gate_purpose` and `terminal_gate` metadata from node
  definitions instead of inferring gate type from node-id substrings.
- Revision iteration detection is centralized in `IterationSemantics`.
- Timeline display overrides are centralized in `TimelineDisplaySemantics`.
- Recovery failure reason fallback is centralized in `FailureReasonSemantics`.

Remaining risk:

- Edge-level `iterationEffect` metadata is not yet part of the workflow model.
  If workflow variants become more complex, this should move from centralized
  outcome semantics into edge metadata.

## Managed Tool Cleanup Policy

Status: accepted MVP debt

Current state:

- `AcpxProcessCleanupPolicy` contains a small, tool-specific cleanup rule for
  managed Serena MCP processes started under Codex/acpx.

Why it is debt:

- This is environment/tool specific.

Future direction:

- Keep cleanup policy centralized.
- Consider a small managed-process pattern catalog if more adapter-spawned tools
  need cleanup.
