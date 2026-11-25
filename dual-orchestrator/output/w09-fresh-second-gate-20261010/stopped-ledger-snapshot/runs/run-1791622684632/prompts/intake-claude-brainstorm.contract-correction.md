Typed correction reason: PARSE_FAILURE
Syntax diagnostic:
- kind: INVALID_JSON

Re-emit the complete AgentResultEnvelope as one strictly valid JSON object.

Your preceding final assistant message failed strict AgentResultEnvelope validation.
Preserve its semantic decision, evidence, and conclusion using only fields declared by the contract.
Remove every unsupported key. Do not preserve invalid object structure.
Do not re-run the task, use tools, explain, or use Markdown.
This is the only contract-correction attempt. Return exactly the JSON object required below.

Return exactly one JSON object as the final assistant message.
Do not use Markdown fences, XML tags, headings, commentary, or text before/after the JSON.
Preserve the exact key names and JSON primitive types shown below. Replace example values with factual values.
Every JSON object is closed. Do not add keys that are not declared for that object.
Allowed `result.decision` values: [READY_FOR_PRODUCT_REVIEW]



Intake `result.scope.included` contains candidate paths, not write authorization. GOAL approval fixes objective, acceptance and exclusions; exact implementation scope is approved in EXECUTION after design and plan review. Only approved EXECUTION `scope.included` is the exhaustive authorization set for permitted repository changes; each entry names one exact repository-relative path. Each scope path must name one exact file, not a directory; it must be repository-relative, must not end with `/`, and must already be canonical (no `./`, `..`, repeated separators, backslashes, leading/trailing whitespace, `.git`, or `.agent` segments). Dual derives CREATE/MODIFY from the effective worktree. It does not require every declared path to be changed. Acceptance criteria, constraints, risks, and other prose cannot authorize an undeclared path. Never describe the typed scope as illustrative or non-exhaustive. When a revision obligation identifies a scope gap, update the typed path entries directly and map the obligation to one or more genuinely relevant clause IDs. Each task-contract clause must contain stable string fields `id` and `text`. On an initial intake, `result.revision_responses` must be `[]`. On a revision, copy every routed Contract Obligation ID exactly once into `review_obligation_id` and list one or more clause IDs in `resolved_by_clause_ids`. Preserve every existing clause not named by the current `revision_responses` by copying its `id` and `text` character-for-character from the verified previous proposal, including whitespace, punctuation, capitalization, and Markdown backticks. Formatting normalization is a modification and is prohibited unless that clause ID is declared in `resolved_by_clause_ids`. List every added or modified clause ID in at least one `resolved_by_clause_ids` entry, and do not delete existing clause IDs. Each `result.revision_responses` item is a closed object with exactly these fields: [review_obligation_id, resolved_by_clause_ids]. No other keys are permitted.

{
  "schema_version" : 1,
  "workstream_id" : "ws-20261010T085617Z-8SVF",
  "run_id" : "run-1791622684632",
  "node_id" : "intake.claude_brainstorm",
  "result" : {
    "artifact_type" : "task_contract_proposal",
    "decision" : "READY_FOR_PRODUCT_REVIEW",
    "summary" : "one short sentence",
    "needs_human_input" : false,
    "objective" : "one clear objective",
    "scope" : {
      "included" : [ {
        "path" : "repo/relative/existing.ext"
      }, {
        "path" : "repo/relative/new.ext"
      } ],
      "excluded" : [ "explicit non-goal" ]
    },
    "acceptance_criteria" : [ {
      "id" : "AC-1",
      "text" : "observable acceptance criterion"
    }, {
      "id" : "AC-2",
      "text" : "second observable criterion"
    } ],
    "test_requirements" : [ {
      "id" : "TR-1",
      "text" : "required verification"
    } ],
    "constraints" : [ {
      "id" : "C-1",
      "text" : "technical or product constraint"
    } ],
    "non_goals" : [ {
      "id" : "NG-1",
      "text" : "explicit non-goal"
    } ],
    "risk_areas" : [ {
      "id" : "R-1",
      "text" : "risk for review"
    } ],
    "evidence_refs" : [ "repo/relative/evidence.ext" ],
    "open_questions" : [ ],
    "revision_responses" : [ ]
  }
}