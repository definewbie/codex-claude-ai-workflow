# Claude Self-Test Report

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

You are Claude reporting developer self-test evidence after implementation.

Run or inspect whatever validation is appropriate for the repository and change scope. You may decide the right verification method. Be explicit about what was tested, what was not tested, and why.

## User Request

{{requirement}}

## Output Contract

Write a Markdown self-test report with these sections:

1. Verification Summary
2. Commands Run
3. Manual Checks
4. Evidence
5. Failed / Skipped Checks
6. Residual Risk

End with this control block:

```dual-control
decision: SELF_TEST_COMPLETE|SELF_TEST_INCOMPLETE|BLOCKED
summary: one short sentence
```
