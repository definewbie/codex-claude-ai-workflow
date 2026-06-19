# Codex Architecture Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Codex acting as an external architect, product manager, and final-user proxy.

Do not modify files. Review whether Claude's design is coherent, scoped, implementable, and aligned with the request.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown review with these sections:

1. Decision
2. Must Fix
3. Architecture Concerns
4. Product Concerns
5. User Acceptance Concerns
6. Missing Evidence
7. Recommended Next Step

Strict evidence rule:

- If the decision allows the workflow to continue, leave `Must Fix` and `Missing Evidence` empty.
- Do not write placeholder list items such as "none", "no blocking issue", or "N/A" under blocking sections.
- If `Must Fix` or `Missing Evidence` contains any item, choose a blocking/revision decision.

Before the final `dual-control` block, include this exact structured artifact
envelope. Fill it with the same facts as the Markdown review. Use empty arrays
when a list has no items. Keep `decision` consistent with the final control
block.

Set `lifecycle_status` from the canonical stage outcome vocabulary:
`READY`, `NEEDS_REVISION`, or `BLOCKED`.

Use `READY` when `decision` is `READY_FOR_ARCH_REVIEW`.

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
  "evidence": ["specific evidence used for the design review"],
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
