## Execution Workspace Guard

- Effective workspace: /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree
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

# Claude Implementation Plan

Workstream: ws-20261023T005431Z-9YYF
Node: plan.claude_plan
Template: plan-claude
Ledger: .agent/workstreams/ws-20261023T005431Z-9YYF

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude acting as the primary developer. Convert the independently reviewed design into a concrete implementation plan.

Do not implement code in this stage. Keep the plan small enough to execute and verify. Do not expand scope.

Under GOAL, planned files are proposals, not authorization: derive canonical paths from the reviewed design while respecting the approved objective and exclusions. Exact EXECUTION scope approval follows design and plan review. Under EXECUTION, the plan must remain within approved paths and operations; a change outside them requires new approval before implementation.

Planning-stage boundary:

- Do not execute the plan.
- Do not run build, typecheck, pytest, npm, npx, install, or other validation commands.
- Do not create, edit, or delete project files.
- You may inspect context and repository files using read/search tools only.
- Put commands that should be run later under `Validation Plan`; the self-test node owns execution and evidence collection.
- If a previous review asks for verification evidence, plan how the self-test node will produce it; do not produce the evidence here.

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

- .agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718961414/outputs/design-codex-review.stderr.log
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718961414/outputs/design-codex-review.transcript.log
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718961414/outputs/design-codex-review.json

```json
{
  "workstreamId" : "ws-20261023T005431Z-9YYF",
  "runId" : "run-1792718961414",
  "nodeId" : "design.codex_arch_review",
  "nodeInstanceId" : "node-design-codex_arch_review-015",
  "stageOutcome" : "READY",
  "lifecycleStatus" : "READY",
  "decision" : "READY_FOR_ARCH_REVIEW",
  "summary" : "完整修订设计与需求一致，AR-1、AR-2 已在设计层面解决，可以进入后续计划评审。",
  "sourceArtifactType" : "agent_output_contract",
  "sourceOutputRef" : ".agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718961414/outputs/design-codex-review.json",
  "blocking" : false,
  "checks" : [ "已先审阅 Review Evidence Brief，再核对完整当前设计、三个计划文件职责、验收映射及两项修订义务，未仅依赖 ADDRESSED 声明。", "AR-1 已解决：当前 CASES、混合换行测试设计和 README 示例统一使用正确期望 \"a\\nb\\nc\\nd\"，与两步替换算法一致。", "AR-2 已解决：当前设计明确合并采集 stdout 与 stderr，或分别完整保留两者，并要求实际报告与源码案例核对。", "语义与覆盖完整：先替换 CRLF、再替换单独 CR，保留其他字符及末尾换行存在性；九类输入均有明确期望，并安排全表幂等性断言。", "文件职责完整：src/normalize_text.py、tests/test_normalize_text.py、README.md 分别承担实现、测试和使用说明，导入方案可在这三个候选文件内完成。", "已复核生命周期适用性：函数无共享可变状态、I/O 或外部副作用；测试导入路径设置仅限进程内，无需新增并发协调、超时恢复、事务、回滚或补偿机制。", "范围与授权一致：仅使用标准库，保持用户规定的排除项；当前 GOAL 批准不授权产品文件写入。" ],
  "evidence" : [ "当前设计的测试文件职责与 README 职责均明确 \"a\\r\\nb\\rc\\nd\" → \"a\\nb\\nc\\nd\"；逐步替换推导也得到同一结果，支持关闭 AR-1。", "当前设计明确使用 python -m unittest discover -s tests -v 2>&1，并禁止只保存 stdout 或以预期成功文本替代实际结果；README 职责同步注明 stderr 采集，支持关闭 AR-2。", "当前 CASES 列出空串、纯 LF、CRLF、单 CR、混合换行、Unicode、末尾有换行、末尾无换行和连续空行；11 个计划测试方法包含全表无 CR 与幂等性检查。", "AC-3 验证方案包含实际读取源码与 README，或在指定导入路径下运行 doctest；当前提供的是验证方案，尚无实现测试结果。" ],
  "mustFix" : [ ],
  "contractObligations" : [ ],
  "missingEvidence" : [ ],
  "acceptanceNotes" : [ "两项既有义务在本次设计审查中通过核对；实现正确性与测试取证仍须在 EXECUTION 阶段实际验证。", "Ran 11 tests 表示计划测试方法数，输入类别覆盖仍须结合 CASES 核对；实际数量变化应据实记录。", "实施前仍须单独批准精确路径及 CREATE/MODIFY 操作组成的穷尽 EXECUTION 授权集合。" ],
  "openRisks" : [ ],
  "changedFiles" : [ ],
  "verificationSummary" : "依据所提供完整当前设计完成静态审查，两项既有阻断均已修正，未发现新的设计阻断；未修改文件或执行测试。",
  "completedWork" : [ ],
  "importantDecisions" : [ ],
  "recommendedNextStage" : "",
  "suggestedNextPrompt" : "",
  "control" : {
    "summary" : "完整修订设计与需求一致，AR-1、AR-2 已在设计层面解决，可以进入后续计划评审。",
    "decision" : "READY_FOR_ARCH_REVIEW"
  },
  "failure" : {
    "categoryHint" : "",
    "verificationRemediation" : "NONE",
    "contractGaps" : [ ],
    "outOfScopeFiles" : [ ],
    "missingTests" : [ ],
    "contextGaps" : [ ],
    "reason" : ""
  },
  "rereadReasons" : [ ],
  "reviewSubject" : null,
  "taskContractProposal" : null,
  "repositoryFactBinding" : null,
  "proposalDerivation" : null,
  "planArtifact" : null,
  "design" : null,
  "authoredRevisionResponses" : null,
  "authoredContext" : {
    "ref" : ".agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718961414/prompts/design-codex-review.md.manifest.json",
    "sha256" : "a444550a9df9e624641f034e1f524f57a42929d4c85783675af416ad0338550b"
  },
  "verificationChecks" : [ ]
}
```

- .agent/workstreams/ws-20261023T005431Z-9YYF/artifacts/context-governance/occurrences/node-design-codex_arch_review-015-run-1792718961414/binding.json
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718961414/outputs/design-codex-review.contract.json
  (validated typed evidence already projected)

- .agent/workstreams/ws-20261023T005431Z-9YYF/context/agent-governance-node-plan-claude_plan-017-run-1792719040009.md

```text
# Agent Run Governance Context

- Workstream: ws-20261023T005431Z-9YYF
- Target Node: plan.claude_plan
- Agent Node Instance: node-plan-claude_plan-017
- Run Id: run-1792719040009
- Generated At: 2026-10-23T01:30:40.123138Z

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: plan
- Governance refs: capabilityCatalog=.agent/workstreams/ws-20261023T005431Z-9YYF/artifacts/context-governance/occurrences/node-plan-claude_plan-017-run-1792719040009/agent-capability-catalog.json; readReuseContract=.agent/workstreams/ws-20261023T005431Z-9YYF/artifacts/context-governance/occurrences/node-plan-claude_plan-017-run-1792719040009/read-reuse-contract.json; repoMap=.agent/workstreams/ws-20261023T005431Z-9YYF/artifacts/context-governance/occurrences/node-plan-claude_plan-017-run-1792719040009/repo-map.json; repositoryFactBinding=.agent/workstreams/ws-20261023T005431Z-9YYF/artifacts/context-governance/occurrences/node-plan-claude_plan-017-run-1792719040009/binding.json

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
- Re-read files only when the memory is missing, stale, or insufficient for planning.
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
Allowed `result.decision` values: [READY_FOR_PLAN_REVIEW]
Allowed `result.failure.verification_remediation` values for this node: [NONE]

For this node, `result.failure.verification_remediation` must remain `NONE`.

`result.revision_responses` is required: return [] when there are no active authored obligations. Otherwise cover every active obligation_id from Required Authored Context exactly once; do not add unknown IDs or omit unresolved obligations. Each entry has exactly obligation_id, disposition (ADDRESSED or NOT_ADDRESSED), and non-empty evidence. Use NOT_ADDRESSED when unresolved; ADDRESSED is a claim for review, not an approval or closure. Preserve all previously required behavior and the approved contract while making this revision.
edit_files must contain unique canonical proposed paths respecting the approved GOAL exclusions. Exact file operations require later EXECUTION approval. Each scope path must name one exact file, not a directory; it must be repository-relative, must not end with `/`, and must already be canonical (no `./`, `..`, repeated separators, backslashes, leading/trailing whitespace, `.git`, or `.agent` segments). A subset is allowed; reference_files do not authorize edits. Each `result.revision_responses` item is a closed object with exactly these fields: [obligation_id, disposition, evidence]. No other keys are permitted.
Each verification_binding must explicitly set actual_exit_required (boolean) and expected_exit_code (integer or null). Derive these from the approved acceptance requirements before execution; never weaken a required exit check. expected_exit_code requires actual_exit_required=true. Operator declarations cannot supply measured process exits.

{
  "schema_version" : 1,
  "workstream_id" : "ws-20261023T005431Z-9YYF",
  "run_id" : "run-1792719040009",
  "node_id" : "plan.claude_plan",
  "result" : {
    "artifact_type" : "plan_result",
    "decision" : "READY_FOR_PLAN_REVIEW",
    "summary" : "one short sentence",
    "lifecycle_status" : "READY",
    "blocking" : false,
    "checks" : [ "read approved design and review context" ],
    "evidence" : [ "plan is scoped to the approved contract" ],
    "missing_evidence" : [ ],
    "must_fix" : [ ],
    "acceptance_notes" : [ ],
    "open_risks" : [ ],
    "changed_files" : [ ],
    "verification_summary" : "Planning only; implementation and verification happen later.",
    "failure" : {
      "category_hint" : "",
      "verification_remediation" : "NONE",
      "contract_gaps" : [ ],
      "out_of_scope_files" : [ ],
      "missing_tests" : [ ],
      "context_gaps" : [ ],
      "reason" : ""
    },
    "plan" : {
      "implementation_steps" : [ "ordered implementation step" ],
      "edit_files" : [ "path/to/file.ext" ],
      "reference_files" : [ ],
      "file_level_changes" : [ "path/to/file.ext: intended change" ],
      "validation_plan" : [ "verification command or check" ],
      "rollback_notes" : "rollback approach",
      "known_risks" : [ "None identified." ],
      "human_review_checklist" : [ "review item" ],
      "files" : [ {
        "path" : "path/to/file.ext",
        "operation" : "MODIFY"
      } ],
      "verification_bindings" : [ {
        "criterion_id" : "approved-criterion-id",
        "method" : "observable assertion and execution method",
        "executor" : "AGENT",
        "environment_refs" : [ {
          "ref" : "replace/with/actual-environment-ref",
          "sha256" : "0000000000000000000000000000000000000000000000000000000000000000"
        } ],
        "authority_refs" : [ ],
        "evidence_kind" : "PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS",
        "feasibility_refs" : [ {
          "ref" : "replace/with/actual-feasibility-ref",
          "sha256" : "0000000000000000000000000000000000000000000000000000000000000000"
        } ],
        "detail_owner" : "implement.claude_self_test_report",
        "actual_exit_required" : false,
        "expected_exit_code" : null
      } ],
      "authority_refs" : [ ]
    },
    "revision_responses" : [ ]
  }
}


# Required Authored Context

The following verified inputs are mandatory. Respond to every active obligation; ADDRESSED does not close it. Use NOT_ADDRESSED honestly. Initial authors return [].

## Reviewed design

.agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718732583/outputs/design-claude.contract.json

{
  "artifact_type" : "agent_output_contract",
  "schema_version" : 1,
  "workstream_id" : "ws-20261023T005431Z-9YYF",
  "run_id" : "run-1792718732583",
  "node_id" : "design.claude_design",
  "node_instance_id" : "node-design-claude_design-012",
  "handler_type" : "acpx.agent_run",
  "decision" : "READY_FOR_ARCH_REVIEW",
  "summary" : "完整替换设计：保持两步 str.replace 纯函数、11 个 unittest 用例与三个 CREATE 候选文件，修正混合换行期望为 \"a\\nb\\nc\\nd\"，并将 AC-2 取证明确为合并采集 stdout 与 stderr。",
  "required_keys" : [ "summary", "decision" ],
  "allowed_decisions" : [ "READY_FOR_ARCH_REVIEW" ],
  "source_output_ref" : ".agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718732583/outputs/design-claude.json",
  "source_artifact_type" : "design_result",
  "generated_at" : "2026-10-23T01:28:55.051248Z",
  "control" : {
    "decision" : "READY_FOR_ARCH_REVIEW",
    "summary" : "完整替换设计：保持两步 str.replace 纯函数、11 个 unittest 用例与三个 CREATE 候选文件，修正混合换行期望为 \"a\\nb\\nc\\nd\"，并将 AC-2 取证明确为合并采集 stdout 与 stderr。"
  },
  "control_source" : {
    "kind" : "strict_agent_result_envelope"
  },
  "revision_responses" : [ {
    "obligation_id" : "sha256:ad1cb016896cb7cc87e8c05be6ee5ca5080ac1efb551cb458a3bfa83504a1e05#must_fix:0",
    "disposition" : "ADDRESSED",
    "evidence" : "本替换设计将共享 CASES 表中 \"a\\r\\nb\\rc\\nd\" 的期望修正为 \"a\\nb\\nc\\nd\"，并在设计说明、test_mixed_newlines_normalized 断言与 README doctest 示例三处使用同一期望值；算法推导为 replace(\"\\r\\n\", \"\\n\") 得 \"a\\nb\\rc\\nd\"、再 replace(\"\\r\", \"\\n\") 得 \"a\\nb\\nc\\nd\"，实现不得为该输入产生额外空行，test_crlf_becomes_single_lf 继续显式断言 \"a\\r\\nb\" → \"a\\nb\" 而非 \"a\\n\\nb\"。"
  }, {
    "obligation_id" : "sha256:ad1cb016896cb7cc87e8c05be6ee5ca5080ac1efb551cb458a3bfa83504a1e05#must_fix:1",
    "disposition" : "ADDRESSED",
    "evidence" : "AC-2 取证方案已明确为：EXECUTION 在仓库根执行 python -m unittest discover -s tests -v 2>&1 合并采集（或分别原样保留 stdout 与 stderr 两个流），因为 unittest 默认把逐用例结果、Ran 11 tests 与最终 OK/FAILED 报告写入 stderr；实际输出须保留逐用例名、覆盖数与零 failure/error，并与 tests/test_normalize_text.py 源码案例一一核对；设计明确禁止只保存 stdout 或以预期成功文本替代实际输入/输出。"
  } ],
  "lifecycle_status" : "READY",
  "checks" : [ "已实际读取已批准的 TaskContract 与 contract-resolution.md（Decision: APPROVE，human_gate 于 2026-10-23T01:19:39Z 批准），本替换设计的 Objective、Scope 排除项、AC/TR、Constraints、Non-Goals、Risk Areas 与契约逐条对齐，未扩大文件范围或引入新机制。", "已重新核对仓库现状：Glob 核实 src/**、tests/**、README.md 与 **/*.py 均无匹配，仓库可见文件仅位于 .git 与 .agent 之下，三个计划文件均为 CREATE，不覆盖现有文件，不创建或修改 .git/ 与 .agent/ 下任何内容。", "AR-1 已纳入：共享 CASES 表中混合换行案例修正为输入 \"a\\r\\nb\\rc\\nd\" 期望 \"a\\nb\\nc\\nd\"，与两步替换语义、test_mixed_newlines_normalized 断言及 README doctest 示例三方同值，消除上一版设计中 evidence 与 README 职责的内部冲突。", "AR-2 已纳入：测试取证方案明确为在仓库根执行 python -m unittest discover -s tests -v 2>&1 合并采集（或分别原样保留 stdout 与 stderr 两个流），因 unittest 默认将逐用例报告、Ran 11 tests 与 OK/FAILED 写入 stderr；实际输出须与测试源码案例一一核对，禁止只保存 stdout 或以预期成功文本、完成状态替代实测结果。", "替换顺序不变量保持：必须先 text.replace(\"\\r\\n\", \"\\n\") 再 text.replace(\"\\r\", \"\\n\")，否则 CRLF 会被拆成两个 LF；由 test_crlf_becomes_single_lf 断言 \"a\\r\\nb\" 结果为 \"a\\nb\" 而非 \"a\\n\\nb\"。", "状态与生命周期边界：本设计不新增索引、锁、事务、队列、缓存、外部副作用或补偿机制；函数为无 I/O 纯函数，正常完成即返回新字符串，并发、超时/提供方失败、中断/崩溃、重启/恢复、回滚/冲突及 cannot-bypass 各边界均不适用，无需为其设计防护或测试；测试内的 sys.path 设置仅存在于测试进程内存，不产生跨运行状态。", "当前仅有 GOAL 批准；planned_files 为提案而非实施授权，精确文件与 CREATE/MODIFY 操作须在后续设计与计划评审通过并经 EXECUTION 批准后实施。" ],
  "evidence" : [ "契约依据：.agent/workstreams/ws-20261023T005431Z-9YYF/artifacts/contracts/node-contract-compile-007/task-contract.md 的 Objective、Acceptance Criteria（AC-1/2/3）、Test Requirements（TR-1/2/3）、Constraints、Non-Goals、Risk Areas 已于本次运行实际读取并映射到本设计；本次修订仅修正错误期望值与取证方案，不改变契约映射。", "仓库现状实测：Glob 模式 src/**、tests/**、README.md、**/*.py 均无匹配，与契约 Risk Areas 所述 repo-map fileCount=0 一致，故三个计划文件均为新建（CREATE）。", "AC-1 覆盖依据：测试模块顶部定义共享 CASES 表，九类输入及修正后期望为——\"\"→\"\"；\"a\\nb\\n\"→\"a\\nb\\n\"；\"a\\r\\nb\"→\"a\\nb\"；\"a\\rb\"→\"a\\nb\"；\"a\\r\\nb\\rc\\nd\"→\"a\\nb\\nc\\nd\"（AR-1 修正值）；\"中文\\u2028😀\\r\\nx\"→\"中文\\u2028😀\\nx\"（U+2028 保留不转换）；\"end\\r\\n\"→\"end\\n\"；\"no newline\"→\"no newline\"；\"a\\r\\n\\r\\n\\rb\"→\"a\\n\\n\\nb\"；再由 test_idempotent_across_all_cases 以 subTest 遍历同一表断言 normalize_newlines(normalize_newlines(x)) == normalize_newlines(x)。", "AR-1 修正的算法推导：\"a\\r\\nb\\rc\\nd\".replace(\"\\r\\n\", \"\\n\") 得 \"a\\nb\\rc\\nd\"（CRLF 收敛为单个 LF），再 .replace(\"\\r\", \"\\n\") 得 \"a\\nb\\nc\\nd\"，共三次换行重编码、不新增空行；错误期望 \"a\\n\\nb\\nc\\nd\" 会使首处 CRLF 产生双 LF，违反契约 Constraints 的逐字符语义。", "测试结构依据：11 个测试方法——test_empty_string_returns_empty、test_pure_lf_unchanged、test_crlf_becomes_single_lf、test_lone_cr_becomes_lf、test_mixed_newlines_normalized、test_unicode_and_line_separators_preserved、test_trailing_newline_present_kept、test_trailing_newline_absent_kept、test_consecutive_blank_lines_normalized、test_no_cr_left_in_output、test_idempotent_across_all_cases——方法名与 verbose 输出逐条对应，支持 AC-2 的输出与源码核对；test_no_cr_left_in_output 断言全部 CASES 输出不含 \"\\r\"，与幂等性互为印证。", "AC-2 取证依据：python -m unittest discover -s tests -v 的逐用例行与结尾 Ran 11 tests、OK 默认写入 stderr，故 EXECUTION 须以 2>&1 合并采集或分别保留两个流；实际输出须呈现覆盖数与零 failure/error，并与 tests/test_normalize_text.py 源码案例一一对应，不以文字结论替代实测输出。", "AC-3 依据：README 采用 doctest 风格示例——导入语句 from normalize_text import normalize_newlines、典型混合换行 normalize_newlines(\"a\\r\\nb\\rc\\nd\") 期望 'a\\nb\\nc\\nd'（与 CASES 修正值逐字符一致），另含空串与末尾换行示例；可通过实际读取 src/normalize_text.py 与 README.md 逐字符比对，或执行 PYTHONPATH=src python -m doctest README.md 验证（TR-3 允许任一途径）。" ],
  "missing_evidence" : [ ],
  "must_fix" : [ ],
  "open_risks" : [ "src 布局的导入依赖测试文件内 sys.path 注入（或运行时 PYTHONPATH=src）：该机制是契约 Risk Areas 指定的 EXECUTION 计划确认项，若计划评审改为包结构或平铺布局，需同步调整 README 导入说明（仍在同一批准文件集内解决）。", "Ran 11 tests 中的 11 对应当前设计的测试方法数：若计划或实现阶段增删用例，须同步更新 README 与执行记录中的用例数，避免 AC-2 覆盖数与测试源码不符。" ],
  "verification_summary" : "仅完成替换设计的修订与核对（本次实际读取 TaskContract 与 contract-resolution.md、Glob 复查仓库现状、纳入 AR-1/AR-2 两项修正）；未编写代码、未执行测试、未创建或修改任何仓库文件。",
  "design" : {
    "planned_files" : [ {
      "operation" : "CREATE",
      "responsibility" : "实现 normalize_newlines(text: str) -> str：先 text.replace(\"\\r\\n\", \"\\n\") 再 text.replace(\"\\r\", \"\\n\")，将 CRLF 与孤立 CR 转为 LF；空串返回空串、已有 LF 原样保留、Unicode 字符（含 U+2028/U+2029）不变、末尾有/无换行状态原样保留（不新增、不删除）；纯函数、仅标准库、无 I/O、无状态、无外部副作用、重复调用幂等。",
      "path" : "src/normalize_text.py"
    }, {
      "operation" : "CREATE",
      "responsibility" : "unittest 测试：文件顶部以 pathlib 将仓库 src/ 目录插入 sys.path，支持从仓库根执行 python -m unittest discover -s tests -v；定义覆盖九类输入（空串、纯 LF、CRLF、单 CR、混合换行 \"a\\r\\nb\\rc\\nd\"→\"a\\nb\\nc\\nd\"、Unicode 含 U+2028 保留、末尾有换行 \"end\\r\\n\"→\"end\\n\"、末尾无换行、连续空行 \"a\\r\\n\\r\\n\\rb\"→\"a\\n\\n\\nb\"）精确期望的共享 CASES 表；共 11 个测试方法，含 CRLF 不拆为双 LF 的替换顺序断言、全表输出无残留 CR 断言、以及以 subTest 遍历 CASES 的幂等性断言。",
      "path" : "tests/test_normalize_text.py"
    }, {
      "operation" : "CREATE",
      "responsibility" : "中文用法文档：函数语义规则（含不转换 U+2028/U+2029、不新增或删除末尾换行）、导入方式（PYTHONPATH=src 或与测试一致的 sys.path 做法）、doctest 风格示例（from normalize_text import normalize_newlines 导入语句与 \"a\\r\\nb\\rc\\nd\" → 'a\\nb\\nc\\nd' 典型混合换行输入/输出，另含空串与末尾换行示例）、以及 python -m unittest discover -s tests -v 运行测试说明（注明 unittest 默认将报告写入 stderr，保存完整输出可用 2>&1 合并）；全部示例与 src/normalize_text.py 实现逐字符一致。",
      "path" : "README.md"
    } ]
  },
  "stage_outcome" : "READY",
  "stage_outcome_source" : "declared_status",
  "evidence_state" : {
    "blocking" : false,
    "evidence_count" : 7,
    "missing_evidence_count" : 0,
    "must_fix_count" : 0,
    "acceptance_note_count" : 0,
    "open_risk_count" : 2
  },
  "authored_context" : {
    "ref" : ".agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792718732583/prompts/design-claude.md.manifest.json",
    "sha256" : "bbab1b0065f2eb4251407f30d9eafcae40f1aa128aae3ba506ce07ae8d47d184"
  }
}


## Active obligations

[ ]
