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

- `must_fix`, `missing_evidence`, `open_risks`, and `failure.reason` mean unresolved blockers for the current decision.
- If the decision is positive, ready, or accepted, keep blocking fields empty: `blocking=false`, `must_fix=[]`, `missing_evidence=[]`, and `failure.reason=""`.
- Do not use `must_fix` to list review comments that you already resolved. Put resolved review comments in `checks`, `evidence`, or `acceptance_notes` instead.

# Codex Intake Product Review

Workstream: ws-20261010T124812Z-XCZL
Node: intake.codex_product_review
Template: intake-codex-product-review
Ledger: .agent/workstreams/ws-20261010T124812Z-XCZL

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Codex acting as an external reviewer. Review the intake artifact from product manager, architect, and final-user perspectives.

Do not modify files. Do not perform line-by-line code review. Focus on product completeness, architectural fit, missing assumptions, and whether the workflow can safely move forward.

Treat the typed `task_contract_proposal` result in the intake context as the candidate shared task contract. Review it by principles, not by formatting nitpicks.

The current node's `AgentResultEnvelope`, JSON-only output, and workflow-routing protocol are enforced independently by the orchestrator's Output Contract. Do not require the task proposal to duplicate those harness protocols, and do not return `NEEDS_REVISION` solely because they are absent from proposal fields.

## Contract Review Principles

# Contract Review Principles

- Intent fidelity: the proposal must preserve the user's original intent.
- Explicit-requirement coverage: every user-stated objective, scope boundary, behavior constraint, acceptance criterion, and verification requirement must appear in a binding proposal field; `risk_areas`, `open_questions`, and `evidence_refs` do not preserve requirements.
- Protocol ownership: harness output and routing protocols belong to the orchestrator's node Output Contract and must not be duplicated into the product task proposal.
- Semantic neutrality: the proposal must not broaden or narrow accepted input, output, scope, or observable behavior based on an unstated implementation preference or language-specific edge case.
- Typed scope authority: intake file entries are proposals, not authorization. GOAL approval fixes the objective, acceptance and exclusions, not an implementation file set. Design and plan review produce the candidate for later exact EXECUTION approval. Only the approved EXECUTION `scope.included` is the exhaustive authorization set for permitted repository changes; each entry names one exact repository-relative path and `CREATE` or `MODIFY`. It does not require every declared path to be changed. Acceptance criteria, constraints, risks, and other prose cannot authorize an undeclared path. Never request an illustrative or non-exhaustive EXECUTION scope. A scope gap must request concrete typed path-and-operation changes.
- Non-goal clarity: boundaries must prevent unrelated refactors and scope creep.
- Verifiability: acceptance criteria must be observable by self-test, reviewer inspection, or user acceptance.
- Evidence grounding: important claims should point to user request, repo files, or prior artifacts.
- Risk coverage: risk areas should expose product, architecture, user, and data-safety risks relevant to the task.
- Human boundary: ask for human input only for real product/risk decisions, not JSON formatting or process validation.

If the proposal is missing, polluted by ledger metadata, materially inconsistent with the user request, or too vague to guide implementation, return `NEEDS_REVISION`. If the task has real product ambiguity that an agent should not decide, return `NEEDS_REVISION` or `BLOCKED` with the specific question for the next gate.

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

- .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/task-contract-proposals/node-intake-claude_brainstorm-006-320e15b65a9c523b.ref.json
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261010T124812Z-XCZL/context/node-context-intake_codex_product_review-008.md

```text
# Context Package

- Workstream: ws-20261010T124812Z-XCZL
- Builder Node: context.intake_codex_product_review
- Target Node: intake.codex_product_review
- Generated At: 2026-10-10T12:56:41.202190Z
- Profile: product_review
- Total: 1.8k / 4.0k tokens

## Context Governance

| Section | Level | Degraded | Original | Current |
|---|---|---|---|---|
| input_artifacts | full | no | 409 | 409 |
| review_evidence | full | no | 64 | 64 |
| recent_agent_outputs | full | no | 189 | 189 |
| ledger_pointers | pointer_only | yes | 139 | 153 |
| required_review_subject | full | no | 1.0k | 1.0k |

Degraded sections have full data at their original paths listed below.

## State & Evidence Boundary

This package separates deterministic harness facts from agent proposals.

### Trusted Harness Facts

- TaskContract qualification: not available
- Git Evidence: not generated yet

### Human Decisions

- Requirement Resolution: not available

### Agent Proposed States

- intake.claude_brainstorm: decision=READY_FOR_PRODUCT_REVIEW, stageOutcome=READY, evidenceState={blocking=false, evidence_count=1, missing_evidence_count=0, must_fix_count=0, acceptance_note_count=0, open_risk_count=0} (/private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.contract.json)

### Pending Contract/Evidence Gaps

- No contract failure diagnostics recorded

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: intake
- Governance refs: capabilityCatalog=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-008/agent-capability-catalog.json; readReuseContract=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-008/read-reuse-contract.json; repoMap=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-008/repo-map.json; repositoryFactBinding=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-008/binding.json

- Tracked inventory: count=0; head=; sha256=

- Available context capabilities: direct_file_read:Read

- Recommended read files: (none planned)
- Expected edit files: (none planned)
## Input Artifacts

### .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.stderr.log

(execution diagnostic omitted from context)

### .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.transcript.log

(execution transcript omitted from context; inspect raw file at /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.transcript.log)

### .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.json

- Contract: intake.claude_brainstorm
- Decision: READY_FOR_PRODUCT_REVIEW
- Summary: 已按 CO-1 修订 C-4 并新增 AC-5，将三个建议文件明确为候选范围，绑定实现前单独批准精确路径与 CREATE/MODIFY、仅以获批 EXECUTION scope.included 为穷尽授权。
- Verification: task contract proposal validated
- Contract Sidecar: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.contract.json
- Raw Output: .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.json

### .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-intake-claude_brainstorm-006-run-1791636803114/binding.json

(routed artifact has no typed context projection; content omitted)

### .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/task-contract-proposals/node-intake-claude_brainstorm-006-320e15b65a9c523b.ref.json

(routed artifact has no typed context projection; content omitted)

## Review Evidence Brief

TaskContract projection: unavailable

(no cumulative review verdict recorded; no open issues, changed files, or reusable memory recorded yet) Evidence: .agent/workstreams/ws-20261010T124812Z-XCZL/memory/workstream-memory.json.

## Recent Agent Outputs

### /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.contract.json

- Contract: intake.claude_brainstorm
- Decision: READY_FOR_PRODUCT_REVIEW
- Summary: 已按 CO-1 修订 C-4 并新增 AC-5，将三个建议文件明确为候选范围，绑定实现前单独批准精确路径与 CREATE/MODIFY、仅以获批 EXECUTION scope.included 为穷尽授权。
- Verification: task contract proposal validated
- Contract Sidecar: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.contract.json
- Raw Output: .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636803114/outputs/intake-claude-brainstorm.json

## Ledger Pointers

- Events: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/events.jsonl
- Workflow current: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/workflow/current.json
- Node state directory: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-2026101

[adaptively compressed; section degraded to stay within ~125 tokens; full source remains in ledger under /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL]

## Required Review Subject

- Proposal ID: node-intake-claude_brainstorm-006:320e15b65a9c523b
- Proposal Reference: .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/task-contract-proposals/node-intake-claude_brainstorm-006-320e15b65a9c523b.ref.json
- Proposal SHA-256: 320e15b65a9c523bd0b2f33ba01b5b296e3f4c23b04e82f74d128b161867fefe


```json
{
  "artifact_type" : "task_contract_proposal",
  "schema_version" : 3,
  "objective" : "在本地 Python 仓库中实现 normalize_newlines(text: str) -> str:将 CRLF 与单独 CR 统一转换为 LF,完整保留其他字符、空字符串、已有 LF 以及末尾有无换行的状态,并配套 unittest 测试与 README 用法说明。",
  "scope" : {
    "included" : [ {
      "path" : "src/normalize_text.py",
      "operation" : "CREATE"
    }, {
      "path" : "tests/test_normalize_text.py",
      "operation" : "CREATE"
    }, {
      "path" : "README.md",
      "operation" : "CREATE"
    } ],
    "excluded" : [ "第三方依赖及任何依赖声明文件(如 requirements.txt、pyproject.toml 的依赖项)", "网络、数据库、浏览器、后台服务相关的任何集成", "系统或机器配置的读取与修改", ".git/ 与 .agent/ 目录下的任何文件" ]
  },
  "acceptance_criteria" : [ {
    "id" : "AC-1",
    "text" : "tests/test_normalize_text.py 以 unittest 覆盖至少八类输入:空字符串、纯 LF 文本、含 CRLF 文本、含单独 CR 文本、混合换行文本、Unicode 内容、末尾有换行、末尾无换行、连续空行;每类用例断言与函数语义对应的明确期望输出,并断言对已规范化结果再次调用 normalize_newlines 输出不变(幂等)。"
  }, {
    "id" : "AC-2",
    "text" : "在仓库中实际执行 unittest(如 python -m unittest 或等价标准库方式),保留的真实输出明确显示运行的用例数且 failures=0、errors=0,输出所列用例与 tests/test_normalize_text.py 源码中的案例一一对应;本任务不要求工具独立测量的真实退出码,但仅凭“测试完成”之类的文字结论不满足本条。"
  }, {
    "id" : "AC-3",
    "text" : "README.md 提供与 src/normalize_text.py 实现一致的用法示例,至少包含导入语句和一段典型混合换行输入及其逐字符可核对的输出表示,并通过实际读取源码/文档或实际执行示例验证该一致性。"
  }, {
    "id" : "AC-4",
    "text" : "normalize_newlines 对任意字符串仅执行换行规范化:每个 CRLF(\\r\\n)与每个单独 CR(\\r)都恰好转换为一个 LF(\\n),其余所有字符(含空串、已有 \\n、末尾换行的有无)逐字符保持不变。"
  }, {
    "id" : "AC-5",
    "text" : "最终交付的实现改动严格限于获批 EXECUTION scope.included 列出的精确仓库相对路径，各路径的 CREATE/MODIFY 操作与获批内容一致；不出现授权集之外任何路径的改动，也不要求每个获批路径都必须发生改动。"
  } ],
  "test_requirements" : [ {
    "id" : "TR-1",
    "text" : "使用 Python 标准库 unittest 编写 tests/test_normalize_text.py,不引入 pytest 等第三方测试框架或依赖。"
  }, {
    "id" : "TR-2",
    "text" : "实际运行测试并在交付材料中记录真实命令与完整输出(含运行用例数、failures、errors),不得以结论文字替代实际输入/输出。"
  }, {
    "id" : "TR-3",
    "text" : "测试中显式包含幂等用例:对 AC-1 所列各类输入 x,断言 normalize_newlines(normalize_newlines(x)) == normalize_newlines(x)。"
  }, {
    "id" : "TR-4",
    "text" : "实际核对 README 示例与源码行为一致(读取比对或执行示例),确保示例的导入方式与输入/输出表示可复现。"
  } ],
  "constraints" : [ {
    "id" : "C-1",
    "text" : "仅使用 Python 标准库;不引入第三方依赖,也不创建依赖声明文件。"
  }, {
    "id" : "C-2",
    "text" : "不使用网络、数据库、浏览器或后台服务;不修改系统或机器配置。"
  }, {
    "id" : "C-3",
    "text" : "公开函数签名固定为 normalize_newlines(text: str) -> str,输入输出均为 str,不读写文件系统、不修改调用方传入的字符串。"
  }, {
    "id" : "C-4",
    "text" : "当前 scope.included 中的 src/normalize_text.py、tests/test_normalize_text.py、README.md 三个条目仅是候选建议，不构成已固定的实施文件集：GOAL 批准只确认目标、验收标准与排除范围，不锁定具体实施文件；须经设计与计划评审形成最终候选，并在实现开始前单独批准每个精确仓库相对路径及其 CREATE/MODIFY 操作；仅获批的 EXECUTION scope.included 构成穷尽的变更授权，本提案任何其他文字（含约束、验收、风险）均不得扩展该授权，且不要求每个获批路径都必须发生改动；在此授权边界内不改动 .git/ 与 .agent/ 下的任何内容。"
  } ],
  "non_goals" : [ {
    "id" : "NG-1",
    "text" : "不做换行规范化以外的文本处理:不去除首尾空白、不转换编码、不做 Unicode NFC/NFD 规范化、不改写制表符等其他控制字符。"
  }, {
    "id" : "NG-2",
    "text" : "不提供命令行入口、打包发布、CI 配置或其他工程化设施。"
  }, {
    "id" : "NG-3",
    "text" : "不支持 str 以外的输入类型,不新增换行转换相关的其他公开 API。"
  } ],
  "risk_areas" : [ {
    "id" : "R-1",
    "text" : "实现必须先处理 \\r\\n 再处理单独 \\r(或使用等价的一次性逻辑),否则 CRLF 会被替换成两个 LF;需由 CRLF 与混合换行测试用例防回归。"
  }, {
    "id" : "R-2",
    "text" : "README 示例中的回车/换行字符在 Markdown 渲染后不可见,应使用显式转义或 repr 风格表示输入/输出,确保示例可与实际执行逐字符核对。"
  }, {
    "id" : "R-3",
    "text" : "当前仓库产品树为空(repo-map 显示 tracked 文件数为 0),三个文件均为新建;测试导入 src 下的模块需以标准库方式处理 sys.path(如基于 __file__ 推导),避免引入安装步骤,具体方式在设计与计划阶段确定。"
  } ],
  "evidence_refs" : [ ],
  "open_questions" : [ ],
  "revision_responses" : [ {
    "review_obligation_id" : "CO-1",
    "resolved_by_clause_ids" : [ "C-4", "AC-5" ]
  } ]
}
```
```

- .agent/workstreams/ws-20261010T124812Z-XCZL/context/agent-governance-node-intake-codex_product_review-009-run-1791637010369.md

```text
# Agent Run Governance Context

- Workstream: ws-20261010T124812Z-XCZL
- Target Node: intake.codex_product_review
- Agent Node Instance: node-intake-codex_product_review-009
- Run Id: run-1791637010369
- Generated At: 2026-10-10T12:56:50.401152Z

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: intake
- Governance refs: capabilityCatalog=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-intake-codex_product_review-009-run-1791637010369/agent-capability-catalog.json; readReuseContract=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-intake-codex_product_review-009-run-1791637010369/read-reuse-contract.json; repoMap=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-intake-codex_product_review-009-run-1791637010369/repo-map.json; repositoryFactBinding=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-intake-codex_product_review-009-run-1791637010369/binding.json

- Tracked inventory: count=0; head=; sha256=

- Available context capabilities: direct_file_read:Read

- Recommended read files: (none planned)
- Expected edit files: (none planned)
```

Evidence use rule:

- If the package contains `Review Evidence Brief`, review that first.
- Use raw artifacts or repository reads only to spot-check high-risk claims or fill explicit evidence gaps.
- If the brief is insufficient, put concrete gaps in `missing_evidence` instead of broadening the review silently.

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
Allowed `result.decision` values: [BLOCKED, NEEDS_REVISION, READY_FOR_PRODUCT_REVIEW]
Allowed `result.failure.verification_remediation` values for this node: [NONE]
For this node, `result.reread_reasons` must remain exactly `[]`.
For this node, `result.failure.verification_remediation` must remain `NONE`.
evidence_refs are canonical product-tree file paths, not program artifact references. An empty tracked-path inventory permits evidence_refs=[]; do not create files to populate it. For a non-empty inventory include at least one existing tracked product file supported by its repo-map fact. Never copy .agent/ or .git/ references into evidence_refs or evidence_ref_additions. Program repo-map/binding/context materials are carried by RepositoryFactBinding; reading them does not make them product evidence. For NEEDS_REVISION with non-empty `result.failure.contract_gaps`, `result.contract_obligations` must contain one object per contract obligation with stable string fields `id` and `text`. Otherwise it must be `[]`. Each `result.contract_obligations` item is a closed object with exactly these fields: [id, text]. No other keys are permitted.

{
  "schema_version" : 1,
  "workstream_id" : "ws-20261010T124812Z-XCZL",
  "run_id" : "run-1791637010369",
  "node_id" : "intake.codex_product_review",
  "result" : {
    "artifact_type" : "agent_output_contract",
    "decision" : "<one allowed decision>",
    "summary" : "one short sentence",
    "lifecycle_status" : "READY",
    "blocking" : false,
    "checks" : [ "check or review performed" ],
    "evidence" : [ "specific evidence" ],
    "missing_evidence" : [ ],
    "must_fix" : [ ],
    "contract_obligations" : [ ],
    "acceptance_notes" : [ ],
    "open_risks" : [ ],
    "changed_files" : [ ],
    "verification_summary" : "short verification summary",
    "failure" : {
      "category_hint" : "",
      "verification_remediation" : "NONE",
      "contract_gaps" : [ ],
      "out_of_scope_files" : [ ],
      "missing_tests" : [ ],
      "context_gaps" : [ ],
      "reason" : ""
    },
    "reread_reasons" : [ ]
  }
}
