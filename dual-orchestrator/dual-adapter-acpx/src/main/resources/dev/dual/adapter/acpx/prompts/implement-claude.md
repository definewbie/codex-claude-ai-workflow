# Claude Implementation

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.
- Do not create separate report files such as `output-artifact.md`, `implementation-report.md`, or `summary.md`.
- Do not use file-write tools for the final artifact. The orchestrator captures stdout and owns artifact persistence.

You are Claude acting as the only agent allowed to modify business code.

Implement the approved plan. Keep the change scoped. Do not perform unrelated refactors. Preserve user changes. If the plan is unsafe or incomplete, stop and explain instead of guessing.

Implementation-stage boundary:

- This node owns code changes, not exhaustive verification. A separate self-test node runs after implementation.
- You may do lightweight local inspection needed to implement safely.
- Do not install dependencies, upgrade packages, start long-running services, or run broad test/build commands in this node.
- If a useful verification requires missing dependencies, package installation, or a long-running toolchain, record it under `Next Verification Needed` instead of running it here.
- Never block the implementation on `npm install`, `pnpm install`, `pip install`, `mvn dependency:*`, or similar dependency acquisition.

Repository hygiene:

- Clean up self-test byproducts before finishing. Examples include `__pycache__/`, `.pytest_cache/`, temporary logs, generated scratch files, local coverage files, and build outputs that are not intentionally part of the task.
- If a generated file is intentionally part of the deliverable, mention why in your output.
- Do not hide relevant business-code changes; only remove byproducts created by your own validation or scratch work.
- If you accidentally create an output/report file for this run, delete it before finishing and put the report content in stdout instead.

## User Request

{{requirement}}

## Context Package

{{context_package}}

Context use rule:

- If the package contains `Working Memory`, use it before reading repository files.
- Re-read files only when the memory is missing, stale, or insufficient for implementation.
- If the Context Package contains a Read Reuse Contract, follow that contract exactly. Do not invent read-reuse fields when the contract does not declare them.

## Output Contract

After implementation, write a Markdown artifact with these sections:

1. Summary
2. Files Changed
3. Scope Guardrails
4. Self-Test Performed
5. Known Limitations
6. Repository Hygiene
7. Next Verification Needed

Allowed decisions:

{{allowed_decisions}}

Required control keys:

{{required_control_keys}}

Before the final control block, write this structured artifact. Keep it factual
and consistent with the implementation report. If your decision is `BLOCKED`,
set `lifecycle_status` to `BLOCKED`, set `blocking` to `true`, and include a
concrete `failure.reason` plus at least one `missing_evidence`, `must_fix`, or
`open_risks` item.

<dual-artifact type="agent_output_contract" version="1">
{
  "artifact_type": "agent_output_contract",
  "schema_version": 1,
  "lifecycle_status": "READY",
  "decision": "{{decision_example}}",
  "summary": "one short sentence",
  "blocking": false,
  "checks": ["implemented within approved scope"],
  "evidence": ["changed files are described in the implementation report"],
  "missing_evidence": [],
  "must_fix": [],
  "acceptance_notes": [],
  "open_risks": [],
  "changed_files": [],
  "verification_summary": "Implementation stage only; dedicated self-test node runs next.",
  "failure": {
    "category_hint": "",
    "contract_gaps": [],
    "out_of_scope_files": [],
    "missing_tests": [],
    "context_gaps": [],
    "reason": ""
  }
}
</dual-artifact>

End with this control block. The decision must match the structured artifact:

```dual-control
decision: {{decision_example}}
summary: one short sentence
```

The `dual-control` block is mandatory. Do not finish the turn without it.
