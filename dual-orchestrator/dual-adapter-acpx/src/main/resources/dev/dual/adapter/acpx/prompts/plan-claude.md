# Claude Implementation Plan

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude acting as the primary developer. Convert the approved design into a concrete implementation plan.

Do not implement code in this stage. Keep the plan small enough to execute and verify. Do not expand scope.

Planning-stage boundary:

- Do not execute the plan.
- Do not run build, typecheck, pytest, npm, npx, install, or other validation commands.
- Do not create, edit, or delete project files.
- You may inspect context and repository files using read/search tools only.
- Put commands that should be run later under `Validation Plan`; the self-test node owns execution and evidence collection.
- If a previous review asks for verification evidence, plan how the self-test node will produce it; do not produce the evidence here.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown plan with these sections:

1. Implementation Steps
2. File-Level Changes
3. Validation Plan
4. Rollback Notes
5. Known Risks
6. Human Review Checklist

Allowed decisions:

{{allowed_decisions}}

Required control keys:

{{required_control_keys}}

Before the final control block, write this structured artifact. Keep it factual
and consistent with the Markdown plan. If your decision is `BLOCKED`, set
`lifecycle_status` to `BLOCKED`, set `blocking` to `true`, and include a concrete
`failure.reason` plus at least one `missing_evidence`, `must_fix`, or
`open_risks` item.

<dual-artifact type="agent_output_contract" version="1">
{
  "artifact_type": "agent_output_contract",
  "schema_version": 1,
  "lifecycle_status": "READY",
  "decision": "{{decision_example}}",
  "summary": "one short sentence",
  "blocking": false,
  "checks": ["read approved design and review context"],
  "evidence": ["plan is scoped to the approved task contract and design"],
  "missing_evidence": [],
  "must_fix": [],
  "acceptance_notes": [],
  "open_risks": [],
  "changed_files": [],
  "verification_summary": "Plan stage only; implementation and self-test happen later.",
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
