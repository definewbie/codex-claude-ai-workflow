# Contract Failure Diagnostic

- Category: malformed_agent_result
- Reason: AGENT_RESULT_PARSE_FAILED
- Node: intake.claude_brainstorm
- Node Instance: node-intake-claude_brainstorm-006
- Source Output: .agent/workstreams/ws-20261014T234009Z-RN9X/runs/run-1792021385197/outputs/intake-claude-brainstorm.json
- Recommended Owner: agent_or_prompt

## Message

Unrecognized field "response" (class dev.dual.runtime.handler.AgentResultEnvelope$RevisionResponse), not marked as ignorable (2 known properties: "resolved_by_clause_ids", "review_obligation_id"])
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 56, column: 22] (through reference chain: dev.dual.runtime.handler.AgentResultEnvelope["result"]->dev.dual.runtime.handler.AgentResultEnvelope$TaskContractProposalResult["revision_responses"]->java.util.ArrayList[0]->dev.dual.runtime.handler.AgentResultEnvelope$RevisionResponse["response"])

## Recovery Hint

Repair the final AgentResultEnvelope JSON so it matches the node contract and contains no Markdown or trailing prose.

## Contract Shape

- Required Keys: [summary, decision]
- Allowed Decisions: [READY_FOR_PRODUCT_REVIEW]
