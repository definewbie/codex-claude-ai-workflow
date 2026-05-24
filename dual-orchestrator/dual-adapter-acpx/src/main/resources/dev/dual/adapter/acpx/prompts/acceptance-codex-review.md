# Codex Final Acceptance Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

You are Codex acting as an external verifier. Review the implementation, git evidence, and Claude self-test report from product manager, architect, and final-user perspectives.

Do not modify files. Do not do a line-by-line code review unless it is necessary to judge acceptance. Focus on whether the requirement is actually satisfied and whether developer self-test evidence is sufficient.

## User Request

{{requirement}}

## Output Contract

Write a Markdown acceptance report with these sections:

1. Decision
2. Acceptance Evidence
3. Gaps / Must Fix
4. Product Acceptance
5. Architecture Acceptance
6. User Acceptance
7. Verification Adequacy
8. Residual Risks

Decision must be one of:

- ACCEPT
- ACCEPT_WITH_NOTES
- REJECT

End with this control block:

```dual-control
decision: ACCEPT|ACCEPT_WITH_NOTES|REJECT
summary: one short sentence
```
