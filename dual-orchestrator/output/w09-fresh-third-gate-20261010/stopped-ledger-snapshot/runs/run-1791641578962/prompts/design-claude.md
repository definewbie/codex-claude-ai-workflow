## Execution Workspace Guard

- Effective workspace: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree
- Run all shell commands from the effective workspace above.
- Modify files only inside the effective workspace.
- Ignore absolute paths outside the effective workspace even if they appear in quoted prior outputs.
- Do not run commands with `git -C` pointing outside the effective workspace.

## Response Language Policy

- Use Simplified Chinese for human-readable string values inside the final JSON result.
- Keep JSON keys, enum values, file paths, and code identifiers exactly as specified.
- Do not wrap the final JSON in Markdown.

## Structured Artifact Field Semantics

- `must_fix` and `missing_evidence` mean unresolved blockers; keep them empty for a ready design.
- Put resolved review comments in `checks` or `evidence`, not `must_fix`.
- Under GOAL, `design.planned_files` contains proposals, not authorization; use canonical paths and operations respecting its exclusions. Exact implementation scope requires later EXECUTION approval.
- Under EXECUTION, declared paths and operations must remain within its approved scope. Design or plan review does not grant additional write authority.

# Claude Design

Workstream: ws-20261010T124812Z-XCZL
Node: design.claude_design
Template: design-claude
Ledger: .agent/workstreams/ws-20261010T124812Z-XCZL

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude acting as the primary architect and implementer. Use the user request, prior intake artifacts, Codex review, and human gate decision in the ledger.

Do not implement code in this stage. Produce an implementation-ready design that is narrow, testable, and avoids unrelated refactors.

Declare the complete planned file set in `result.design.planned_files`, for initial and replacement designs alike.
Under GOAL, these are proposals, not authorization: propose exact canonical paths and operations consistent with the approved objective and exclusions. GOAL does not contain an approved implementation file set.
Under EXECUTION, proposed paths and operations must remain within its approved scope; a change outside it requires new approval before implementation.
Each entry assigns a responsibility, not an actual file change. CREATE files need not exist yet.
The declared set must cover every change this design proposes. Independent design and plan review precede exact EXECUTION scope approval; neither review nor prose authorizes repository changes.

Revision discipline:

- When the context contains a prior `NEEDS_REVISION`, return a complete replacement design, not a delta or addendum.
- Treat available prior must-fix items as the minimum repair set, not the boundary of the design review.
- Do not claim knowledge of prior design text that is absent from the context. Reconstruct a coherent complete design from the TaskContract, current review evidence, and available iteration reasons.
- When adding or changing state, an index, lock, transaction, queue, cache, external side effect, or compensation, cover every applicable lifecycle boundary in `checks` or `evidence`: normal completion, concurrency, timeout/provider failure, interruption/crash, restart/recovery, rollback/conflict, and a cannot-bypass test. State why a boundary is not applicable instead of inventing requirements.
- Prefer deleting unnecessary mechanisms or simplifying the state model. Do not add platform capability merely to close one finding.

## User Request

# 换行规范化函数

在新的本地 Python 仓库提供 `normalize_newlines(text: str) -> str`：将 CRLF 和单独 CR 转为 LF，保留其他字符、空串、已有 LF，以及末尾有无换行。只使用 Python 标准库，不引入第三方依赖、网络、数据库、浏览器、后台服务或机器配置修改。

提供 unittest 测试和 README 用法。建议文件为 `src/normalize_text.py`、`tests/test_normalize_text.py`、`README.md`；它们是待设计与计划确认的提案，最终具体文件和 CREATE/MODIFY 在实现前单独批准。

验收：

- AC-1：空串、纯 LF、CRLF、单 CR、混合换行、Unicode、末尾有/无换行、连续空行至少八种输入有明确期望，重复规范化保持相同结果。
- AC-2：实际执行测试，实际输出明确显示覆盖数、零 failure/error，并与测试源码中的案例相符。本任务不要求工具提供独立测量的真实退出码，但测试完成状态或文字结论本身不能替代实际输入/输出与测试结果。
- AC-3：README 例子与代码一致，包含导入和典型混合换行输入/输出；实际读取源码/文档或执行例子支持这一结论。


## Context Package

### Direct Input Artifacts

- .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/contracts/node-contract-compile-012/task-contract.json
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/contracts/node-contract-compile-012/task-contract.md
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/contracts/node-contract-compile-012/contract-resolution.json
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/contracts/node-contract-compile-012/contract-resolution.md
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261010T124812Z-XCZL/context/agent-governance-node-design-claude_design-013-run-1791641578962.md

```text
# Agent Run Governance Context

- Workstream: ws-20261010T124812Z-XCZL
- Target Node: design.claude_design
- Agent Node Instance: node-design-claude_design-013
- Run Id: run-1791641578962
- Generated At: 2026-10-10T14:12:59.061712Z

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: design
- Governance refs: capabilityCatalog=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-design-claude_design-013-run-1791641578962/agent-capability-catalog.json; readReuseContract=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-design-claude_design-013-run-1791641578962/read-reuse-contract.json; repoMap=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-design-claude_design-013-run-1791641578962/repo-map.json; repositoryFactBinding=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-design-claude_design-013-run-1791641578962/binding.json

- Tracked inventory: count=0; head=; sha256=

- Available context capabilities: direct_file_read:Read

- Recommended read files: (none planned)
- Expected edit files: (none planned)
## Verification readiness declarations

These are operator declarations of prerequisites, not execution results, successful tests or grants. Bind exact refs only where criterion, method, executor and environment match. If prerequisites are missing, describe the missing items without inventing references.

[ ]
```

Context use rule:

- If the package contains `Working Memory`, use it before reading repository files.
- Re-read files only when the memory is missing, stale, or insufficient for this design decision.
- If the Context Package contains a Read Reuse Contract, follow that contract exactly. Do not invent read-reuse fields when the contract does not declare them.

## External Research Discipline

- Use repository facts and supplied context first.
- WebSearch is not auto-approved; request human approval if necessary for this occurrence. Use it only for public package, version, or documentation facts.
- Never use WebFetch or arbitrary URL retrieval. Do not place secrets, source contents, credentials, or private identifiers in a search query.
- If the needed public fact cannot be obtained under the active permission policy, state the evidence gap instead of bypassing the policy.

## Output Contract

Return exactly one JSON object as the final assistant message.
Do not use Markdown fences, XML tags, headings, commentary, or text before/after the JSON.
Preserve the exact key names and JSON primitive types shown below. Replace example values with factual values.
Every JSON object is closed. Do not add keys that are not declared for that object.
Allowed `result.decision` values: [READY_FOR_ARCH_REVIEW]




`result.revision_responses` is required: return [] when there are no active authored obligations. Otherwise cover every active obligation_id from Required Authored Context exactly once; do not add unknown IDs or omit unresolved obligations. Each entry has exactly obligation_id, disposition (ADDRESSED or NOT_ADDRESSED), and non-empty evidence. Use NOT_ADDRESSED when unresolved; ADDRESSED is a claim for review, not an approval or closure. Preserve all previously required behavior and the approved contract while making this revision.Each `result.revision_responses` item is a closed object with exactly these fields: [obligation_id, disposition, evidence]. No other keys are permitted.

{
  "schema_version" : 1,
  "workstream_id" : "ws-20261010T124812Z-XCZL",
  "run_id" : "run-1791641578962",
  "node_id" : "design.claude_design",
  "result" : {
    "artifact_type" : "design_result",
    "decision" : "READY_FOR_ARCH_REVIEW",
    "summary" : "one short sentence",
    "lifecycle_status" : "READY",
    "checks" : [ "read the approved contract" ],
    "evidence" : [ "design evidence" ],
    "missing_evidence" : [ ],
    "must_fix" : [ ],
    "open_risks" : [ ],
    "verification_summary" : "Design only; no implementation performed.",
    "design" : {
      "planned_files" : [ {
        "path" : "repo/relative/new.ext",
        "operation" : "CREATE",
        "responsibility" : "proposed responsibility respecting the approved objective and exclusions"
      } ]
    },
    "revision_responses" : [ ]
  }
}


# Required Authored Context

The following verified inputs are mandatory. Respond to every active obligation; ADDRESSED does not close it. Use NOT_ADDRESSED honestly. Initial authors return [].

## Active obligations

[ ]
