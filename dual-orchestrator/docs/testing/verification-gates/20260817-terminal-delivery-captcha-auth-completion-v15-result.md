# Captcha-Auth Completion v15 Result

Date: 2026-08-17  
Verdict: `FAIL / CONSUMED`  
Product: `NOT ACHIEVED`  
Target uniqueness checkpoint: `ENFORCED / NOT PROVEN`

## Run Boundary

- Dual SHA: `070ad84d64f58d442308cb5995c1e842b1a1d018`.
- Workstream: `ws-20260817T141747Z-HUAX`.
- Persisted workflow: `agent-lifecycle-terminal-delivery@5`.
- Failed run: `run-1786976617178` at `intake.claude_brainstorm`, iteration `iter-002`.
- Report: `docs/testing/real-case-reports/20260817T141322Z/report.md`.
- Fresh package (`1253/0/0/0`), isolated PostgreSQL, Claude/Codex probes, workflow identity, and durable external-preflight evidence all passed before workstream creation.
- No retry, resume, third correction occurrence, or second sample was run. Scoped sessions were closed and no provider process remained.

## Typed Failure

- Both occurrences ended with process exit `0` and ACP `end_turn`; neither timed out or emitted a current permission denial.
- Occurrence 1 SHA-256: `a21143f6c4eb27c8f5665ccbfc10d3e0ed14dabb1fcbb018ba027a4e8bdac5ff`.
- Occurrence 2 SHA-256: `b520d585693da459398c6be52256d4572c48a0ff03580f7f8bef3c6f0c5271d7`.
- Each candidate was plain JSON with `presentationDisposition=NOT_NEEDED` and was missing only the root object's final `}`. Appending exactly one `}` makes each candidate grammatically valid.
- The first failure produced `PARSE_FAILURE / UNEXPECTED_END_OF_INPUT` at line 1, column 3326. The single bounded correction prompt carried that typed diagnostic and explicitly required a complete re-emission with every object and array closed.
- The correction ended in the same EOF shape at line 1, column 3262. The strict parser rejected it; no sidecar or proposal reference was published.
- Final execution outcome: `OUTPUT_INCOMPLETE / MANUAL_ONLY`, primary reason `agent_result_missing_or_invalid`.
- Workflow state: `WAITING_FOR_RECOVERY`, with the failed intake node and a recovery gate.

## Independent Attribution

### Confirmed

1. The production ACP stream is the authoritative raw assistant input. Its two turn messages match the two persisted candidate files byte for byte; Dual did not truncate either candidate.
2. `AcpxSessionStreamProjector` selected the terminal assistant message and `AcpxAgentResultAcquirer` passed it unchanged to the strict parser because no presentation fence was present.
3. The bounded correction owner behaved correctly: one typed correction, no third occurrence, no automatic node retry, and fail-closed routing after the second parse failure.
4. Target uniqueness was not bypassed. Parsing failed before envelope validation, merger, durable sidecar publication, or reviewer invocation. The checkpoint remains `ENFORCED / NOT PROVEN`; for this sample the conditional proof is `TRIGGERED BUT NOT REACHED (parse boundary)`.
5. The run, attempt, process, outcome, workflow, and recovery projections agree.

### Inference And Unknowns

- The acpx session record's history view contains duplicated/near-duplicated assistant text, while the wire stream contains only the terminal message consumed by Dual. This is an adapter/provider history-presentation observation, not a control-path input.
- The exact producer of the extra history copy cannot be established from the captured evidence. It remains `UNKNOWN`; it must not be attributed conclusively to reconnect, the provider, or Dual.

## Cross-Sample EOF Evidence Correction

The confirmed lower bound for this exact root-only EOF morphology is **1 run / 2 occurrences**, both from captcha-auth v15.

A subsequent independent review reconstructed the surviving authoritative ACP wire streams for T6 v5 (`ws-20260802T152626Z-QN2R`). Those messages contain fenced or prefixed presentation, balanced multi-document content, and depth-2 truncation. None is recoverable by appending one root-closing brace. The T6 v5 ledger no longer exists, so its full parser/validator/merger replay is `NOT VERIFIABLE`.

The earlier classification of T6 v5 as the same root-only morphology was an unsupported extrapolation from a historical summary and is withdrawn. Other retained strict failures such as fences and invalid enums are different classes and are not counted.

## Boundary Review

| Dimension | Result |
|---|---|
| Declared by | Terminal assistant message in the ACP stream |
| Proven by | Stream/candidate byte equality and Jackson EOF location |
| Consumed by | Presentation normalization, then the strict parser |
| Enforced before | No valid envelope means no sidecar, reference, reviewer, or successor |
| Failure route | Typed EOF diagnostic, one correction, second EOF, `OUTPUT_INCOMPLETE`, recovery gate |
| Cannot bypass | Parser precedes schema, semantic validation, projection, and merge |
| Residual raw path | Transcript and acpx record history are investigation material only |

Independent peer review: `PASS`, with no Dual-side P0/P1. The strict parser, correction bound, terminal classification, and recovery route behaved correctly.

## Decision

Do not resume this workstream, create a second v15 sample, auto-append `}`, relax the parser, or add another correction/retry.

Proceed first with one bounded **prompt-projection** checkpoint: replace the relevant one-line JSON output example with a structurally readable multi-line example and explicit final-root closure check, without changing the accepted schema or parser. This is a model-facing risk reduction, not a correctness guarantee.

Root-closure normalization remains deferred and the current strict rejection remains in force. Reconsider normalization only if the exact morphology reappears in an independent natural run after prompt projection, or if a third historical sample is proven by original wire/ledger artifacts. The prompt checkpoint requires architecture review and deterministic prompt tests before any future real-case authorization.
