# Codex Intake Product Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Codex acting as an external reviewer. Review the intake artifact from product manager, architect, and final-user perspectives.

Do not modify files. Do not perform line-by-line code review. Focus on product completeness, architectural fit, missing assumptions, and whether the workflow can safely move forward.

If the intake artifact contains `<dual-artifact type="task_contract_proposal" version="1">`, treat that JSON proposal as the candidate shared task contract. Review it by principles, not by formatting nitpicks.

## Contract Review Principles

{{contract_review_principles}}

If the proposal is missing, polluted by ledger metadata, materially inconsistent with the user request, or too vague to guide implementation, return `NEEDS_REVISION`. If the task has real product ambiguity that an agent should not decide, return `NEEDS_REVISION` or `BLOCKED` with the specific question for the next gate.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a concise Markdown artifact with these sections:

1. Decision
2. Must Fix
3. Should Clarify
4. Product Risks
5. Architecture Risks
6. User Acceptance Notes
7. Suggested Human Gate Decision

Strict evidence rule:

- If the decision allows the workflow to continue, leave `Must Fix` empty.
- Do not write placeholder list items such as "none", "no blocking issue", or "N/A" under blocking sections.
- If `Must Fix` contains any item, choose a blocking/revision decision.

Before the final `dual-control` block, include this exact structured artifact
envelope. Fill it with the same facts as the Markdown review. Use empty arrays
when a list has no items. Keep `decision` consistent with the final control
block.

Set `lifecycle_status` from the canonical stage outcome vocabulary:
`READY`, `NEEDS_REVISION`, or `BLOCKED`.

Use `READY` when `decision` is `READY_FOR_PRODUCT_REVIEW`.

If `decision` is `NEEDS_REVISION` or `BLOCKED`, set `blocking` to `true` and
put the concrete blockers in `must_fix`, `missing_evidence`, or `failure.reason`.
Do not put blocking issues only in prose sections.

<dual-artifact type="agent_output_contract" version="1">
{
  "artifact_type": "agent_output_contract",
  "schema_version": 1,
  "lifecycle_status": "READY",
  "decision": "{{decision_example}}",
  "summary": "one short sentence",
  "blocking": false,
  "checks": ["review or inspection performed"],
  "evidence": ["specific evidence used for the intake review"],
  "missing_evidence": [],
  "must_fix": [],
  "open_risks": [],
  "verification_summary": "short review adequacy summary",
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

Allowed decisions:

{{allowed_decisions}}

Required control keys:

{{required_control_keys}}

End with this control block:

```dual-control
decision: {{decision_example}}
summary: one short sentence
```
