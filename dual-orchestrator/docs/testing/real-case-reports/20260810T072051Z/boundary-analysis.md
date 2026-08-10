# Captcha-Auth Completion v4 Boundary Analysis

## Verdict

```text
Gate: FAIL / CONSUMED
Product completion: NOT ACHIEVED
Primary owner: model revision output
Harness boundary: PASS / fail closed
```

## Evidence

- Execution SHA: `918b15be0ebee45cfdd03c73883afcf26d99c02f`
- Workstream: `ws-20260810T072433Z-J75J`
- Pre-run workflow source: SHA-256 `5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479`,
  29 nodes / 45 edges / 9 Agent nodes / 0 handoff nodes
- Persisted workflow: `agent-lifecycle-terminal-delivery@1`
- External Claude and Codex minimal probes: PASS; runner-owned first-provider probe: PASS
- Failed node: `intake.claude_brainstorm`, iteration `iter-002`
- Run: `run-1786346955946`
- Process: exit `0`, usability `EXITED_ZERO`
- Presentation: `SINGLE_JSON_FENCE_REMOVED`
- Strict parse/schema: valid
- Bounded correction: `NOT_NEEDED`
- Artifact result: `TASK_CONTRACT_REVISION_DELTA_INVALID`
- Exact error: `UNDECLARED_CONTRACT_CLAUSE_CHANGE: AC-6`

The reviewer obligation `CO-6` requested an exact typed scope addition for
`src/test/java/com/captcha/auth/service/UserServiceTest.java` with operation `CREATE`.
The revision did add that typed entry directly, rather than attempting prose-based scope widening.

The same revision also changed `AC-6`, although no routed obligation named `AC-6` as a landing
point. The prompt required every unlisted clause to be copied character-for-character. The
revision-delta validator therefore rejected the candidate before a second reviewer call, compile,
or implementation side effect.

## Boundary Invariant

```text
Declared by: verified previous TaskContractProposal + typed review obligations
Proven by: character-level revision delta comparison
Consumed by: TaskContractProposalValidator before sidecar publication/compile
Enforced before: reviewer continuation, contract compile, design, and implementation
Failure route: contract diagnostic -> node FAILED -> workflow WAITING_FOR_RECOVERY
Cannot bypass: undeclared AC-6 change rejected despite valid JSON/schema and exit 0
Residual path: none observed; target Git remained clean
```

No parser, prompt, model configuration, workflow, retry, or schema change was made during the run.
The sample must not be retried or resumed under this consumed Gate.
