# Acceptance Review

## Decision

Rejected because acceptance evidence is incomplete.

<dual-artifact type="acceptance_review" version="1">
{
  "artifact_type": "acceptance_review",
  "schema_version": 1,
  "lifecycle_status": "REJECTED",
  "decision": "REJECT",
  "summary": "The implementation cannot be accepted because required verification evidence is missing.",
  "blocking": true,
  "checks": ["inspected git diff", "read self-test report"],
  "evidence": ["The changed file list matches the intended scope."],
  "missing_evidence": ["No build, typecheck, or equivalent validation evidence was provided for the code change."],
  "must_fix": ["Provide appropriate validation evidence before final acceptance."],
  "acceptance_notes": [],
  "open_risks": ["The change may fail at runtime without validation."],
  "changed_files": ["src/example/Feature.java"],
  "verification_summary": "Verification is insufficient for acceptance.",
  "failure": {
    "category_hint": "CONTEXT_INSUFFICIENT",
    "contract_gaps": [],
    "out_of_scope_files": [],
    "missing_tests": ["Build/typecheck evidence is missing."],
    "context_gaps": [],
    "reason": "The verifier cannot accept the change without validation evidence."
  }
}
</dual-artifact>

```dual-control
decision: REJECT
summary: Rejected because required verification evidence is missing.
```
