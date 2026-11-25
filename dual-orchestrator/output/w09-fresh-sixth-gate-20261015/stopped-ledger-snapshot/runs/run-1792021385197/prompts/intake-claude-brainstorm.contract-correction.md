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



evidence_refs are canonical product-tree file paths, not program artifact references. An empty tracked-path inventory permits evidence_refs=[]; do not create files to populate it. For a non-empty inventory include at least one existing tracked product file supported by its repo-map fact. Never copy .agent/ or .git/ references into evidence_refs or evidence_ref_additions. Program repo-map/binding/context materials are carried by RepositoryFactBinding; reading them does not make them product evidence. Use task_contract_revision_patch for ordinary changes. Before first GOAL approval only, an actual objective or scope.excluded change may use task_contract_proposal with base_proposal_id. Preserve scope.included/evidence_refs (retain or ADD), open_questions, all clause IDs/sections (ADD/REPLACE only); revision_responses must cover each obligation using actual relevant clause IDs. Full proposal without a goal/exclusion change is rejected. Full result shape: {"artifact_type":"task_contract_proposal","decision":"READY_FOR_PRODUCT_REVIEW","summary":"one short sentence","needs_human_input":false,"objective":"one clear objective","scope":{"included":[{"path":"repo/relative/existing.ext"},{"path":"repo/relative/new.ext"}],"excluded":["explicit non-goal"]},"acceptance_criteria":[{"id":"AC-1","text":"observable acceptance criterion"},{"id":"AC-2","text":"second observable criterion"}],"test_requirements":[{"id":"TR-1","text":"required verification"}],"constraints":[{"id":"C-1","text":"technical or product constraint"}],"non_goals":[{"id":"NG-1","text":"explicit non-goal"}],"risk_areas":[{"id":"R-1","text":"risk for review"}],"evidence_refs":["repo/relative/evidence.ext"],"open_questions":[],"revision_responses":[],"base_proposal_id":null} Replace this illustrative content with the full revised baseline, set base_proposal_id to the exact current ID and supply all revision_responses. The patch is bound to base proposal id `node-intake-claude_brainstorm-002:10e582f4007c97e2`. Full content identity is fixed by Dual; never emit base_proposal_sha256. Cover each authoritative routed obligation exactly once. Preserve its id and implement its text: [{"id":"CO-1","text":"三个建议文件必须保持待设计与计划确认的提案性质，不能通过 GOAL 阶段的排除项提前固定实现文件集合；实现前须单独批准精确、穷尽的 EXECUTION scope.included，每项包含一个仓库相对路径及 CREATE 或 MODIFY，任何未声明路径均不得变更。"}]. For task_contract_revision_patch only: clause ADD/REPLACE, exact scope ADD, and evidence-ref ADD are supported. No DELETE, section move, scope removal, objective/open-question change, JSON Pointer, or arbitrary field patch is allowed. `scope_additions` uses exact repository-relative paths. Each scope path must name one exact file, not a directory; it must be repository-relative, must not end with `/`, and must already be canonical (no `./`, `..`, repeated separators, backslashes, leading/trailing whitespace, `.git`, or `.agent` segments). Dual derives CREATE/MODIFY from the effective worktree. `section` is authoritative; clause IDs are opaque and their prefixes do not determine section. Across the entire `result.obligation_patches` array, each `clause_changes[].id`, `scope_additions[].path`, and `evidence_ref_additions[]` target must appear at most once. When one evidence ref supports multiple obligations, add it in one most relevant patch only; use each obligation's `resolved_by_clause_ids` to express coverage instead of adding the ref again. Each `result.obligation_patches` item is a closed object with exactly these fields: [review_obligation_id, resolved_by_clause_ids, clause_changes, scope_additions, evidence_ref_additions]. No other keys are permitted.

{
  "schema_version" : 1,
  "workstream_id" : "ws-20261014T234009Z-RN9X",
  "run_id" : "run-1792021385197",
  "node_id" : "intake.claude_brainstorm",
  "result" : {
    "artifact_type" : "task_contract_revision_patch",
    "decision" : "READY_FOR_PRODUCT_REVIEW",
    "summary" : "one short sentence",
    "base_proposal_id" : "node-intake-claude_brainstorm-002:10e582f4007c97e2",
    "obligation_patches" : [ {
      "review_obligation_id" : "CO-1",
      "resolved_by_clause_ids" : [ "AC-1" ],
      "clause_changes" : [ {
        "operation" : "REPLACE",
        "section" : "ACCEPTANCE_CRITERIA",
        "id" : "AC-1",
        "text" : "replacement text"
      } ],
      "scope_additions" : [ {
        "path" : "repo/relative/new.ext"
      } ],
      "evidence_ref_additions" : [ "repo/relative/evidence.ext" ]
    } ]
  }
}