# Context Package

- Workstream: ws-20261011T141651Z-MBC2
- Builder Node: context.intake_codex_product_review
- Target Node: intake.codex_product_review
- Generated At: 2026-10-11T14:20:28.854520Z
- Profile: product_review
- Total: 1.7k / 4.0k tokens

## Context Governance

| Section | Level | Degraded | Original | Current |
|---|---|---|---|---|
| input_artifacts | full | no | 402 | 402 |
| review_evidence | full | no | 64 | 64 |
| recent_agent_outputs | full | no | 181 | 181 |
| ledger_pointers | pointer_only | yes | 139 | 153 |
| required_review_subject | full | no | 894 | 894 |

Degraded sections have full data at their original paths listed below.

## State & Evidence Boundary

This package separates deterministic harness facts from agent proposals.

### Trusted Harness Facts

- TaskContract qualification: not available
- Git Evidence: not generated yet

### Human Decisions

- Requirement Resolution: not available

### Agent Proposed States

- intake.claude_brainstorm: decision=READY_FOR_PRODUCT_REVIEW, stageOutcome=READY, evidenceState={blocking=false, evidence_count=1, missing_evidence_count=0, must_fix_count=0, acceptance_note_count=0, open_risk_count=0} (/private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.contract.json)

### Pending Contract/Evidence Gaps

- No contract failure diagnostics recorded

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: intake
- Governance refs: capabilityCatalog=.agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/agent-capability-catalog.json; readReuseContract=.agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/read-reuse-contract.json; repoMap=.agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/repo-map.json; repositoryFactBinding=.agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/binding.json

- Tracked inventory: count=0; head=; sha256=

- Available context capabilities: direct_file_read:Read

- Recommended read files: (none planned)
- Expected edit files: (none planned)
## Input Artifacts

### .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.stderr.log

(execution diagnostic omitted from context)

### .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.transcript.log

(execution transcript omitted from context; inspect raw file at /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.transcript.log)

### .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.json

- Contract: intake.claude_brainstorm
- Decision: READY_FOR_PRODUCT_REVIEW
- Summary: 在空的已跟踪产品树上以纯标准库实现 normalize_newlines 换行规范化函数，并配套 unittest 测试与 README 用法示例。
- Verification: task contract proposal validated
- Contract Sidecar: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.contract.json
- Raw Output: .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.json

### .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/context-governance/occurrences/node-intake-claude_brainstorm-002-run-1791728289467/binding.json

(routed artifact has no typed context projection; content omitted)

### .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/task-contract-proposals/node-intake-claude_brainstorm-002-517fc15b11bd1676.ref.json

(routed artifact has no typed context projection; content omitted)

## Review Evidence Brief

TaskContract projection: unavailable

(no cumulative review verdict recorded; no open issues, changed files, or reusable memory recorded yet) Evidence: .agent/workstreams/ws-20261011T141651Z-MBC2/memory/workstream-memory.json.

## Recent Agent Outputs

### /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.contract.json

- Contract: intake.claude_brainstorm
- Decision: READY_FOR_PRODUCT_REVIEW
- Summary: 在空的已跟踪产品树上以纯标准库实现 normalize_newlines 换行规范化函数，并配套 unittest 测试与 README 用法示例。
- Verification: task contract proposal validated
- Contract Sidecar: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.contract.json
- Raw Output: .agent/workstreams/ws-20261011T141651Z-MBC2/runs/run-1791728289467/outputs/intake-claude-brainstorm.json

## Ledger Pointers

- Events: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/events.jsonl
- Workflow current: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2/workflow/current.json
- Node state directory: /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-2026101

[adaptively compressed; section degraded to stay within ~125 tokens; full source remains in ledger under /private/tmp/dual-w09-newlines-20261011-fresh-05/worktree/.agent/workstreams/ws-20261011T141651Z-MBC2]

## Required Review Subject

- Proposal ID: node-intake-claude_brainstorm-002:517fc15b11bd1676
- Proposal Reference: .agent/workstreams/ws-20261011T141651Z-MBC2/artifacts/task-contract-proposals/node-intake-claude_brainstorm-002-517fc15b11bd1676.ref.json
- Proposal SHA-256: 517fc15b11bd16761e1c694d0625156981e8e515f239f6243186735ff00de94c

<!-- dual:typed-payload:start -->
```json
{
  "artifact_type" : "task_contract_proposal",
  "schema_version" : 3,
  "objective" : "在本仓库新增 src/normalize_text.py，提供 normalize_newlines(text: str) -> str，把 CRLF(\\r\\n) 与单独 CR(\\r) 统一转换为 LF(\\n)，其余字符、空串、已有 LF 及末尾换行的有无一律保持不变；同时新增 tests/test_normalize_text.py（unittest）与 README.md 用法说明。",
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
    "excluded" : [ "不修改 .git/ 与 .agent/ 目录下任何内容", "不改动上述三个提案文件之外的任何产品文件" ]
  },
  "acceptance_criteria" : [ {
    "id" : "AC-1",
    "text" : "tests/test_normalize_text.py 以显式期望值覆盖至少八类输入：空串、纯 LF、纯 CRLF、单独 CR、混合换行、含 Unicode 字符的文本、末尾有换行与末尾无换行、连续空行；并对每类输入断言 normalize_newlines(normalize_newlines(x)) == normalize_newlines(x)，即重复规范化保持相同结果。"
  }, {
    "id" : "AC-2",
    "text" : "实际执行 unittest 测试并保留真实输出：输出明确显示运行的测试/用例数量且为 0 failures、0 errors，用例集合与 tests/test_normalize_text.py 源码中的案例相符；不得以『测试已完成』之类的状态描述或文字结论替代实际的测试输入与输出。"
  }, {
    "id" : "AC-3",
    "text" : "README.md 的示例与 src/normalize_text.py 实际行为一致：包含模块导入语句以及一个典型混合换行输入和对应期望输出的展示，且该一致性由实际读取源码与 README（或实际执行示例）加以核验。"
  } ],
  "test_requirements" : [ {
    "id" : "TR-1",
    "text" : "使用 Python 标准库 unittest 编写 tests/test_normalize_text.py，为 AC-1 列出的至少八类输入逐一断言精确期望输出，并包含重复规范化幂等断言。"
  }, {
    "id" : "TR-2",
    "text" : "实际运行测试（如 python -m unittest）并记录完整真实输出，输出需包含运行的测试数量与 0 failures、0 errors 的结果，并与测试源码中的用例集合一致。"
  }, {
    "id" : "TR-3",
    "text" : "通过实际读取 src/normalize_text.py 与 README.md，或实际执行 README 中的示例，核验 README 的导入方式与混合换行输入/输出示例和代码行为一致。"
  } ],
  "constraints" : [ {
    "id" : "C-1",
    "text" : "仅使用 Python 标准库实现 normalize_newlines 及其测试，不引入任何第三方依赖。"
  }, {
    "id" : "C-2",
    "text" : "不使用网络、数据库、浏览器或后台服务，不修改机器配置或运行环境。"
  }, {
    "id" : "C-3",
    "text" : "语义边界严格限定：仅将 CRLF(\\r\\n) 与单独 CR(\\r) 转换为 LF(\\n)；不裁剪也不追加末尾换行，不改动任何其他字符（含 Unicode 字符），空串返回空串，输入已为 LF 时原样返回。"
  }, {
    "id" : "C-4",
    "text" : "src/normalize_text.py、tests/test_normalize_text.py、README.md 均为提案路径，需待设计与计划确认；最终具体文件与 CREATE/MODIFY 操作在实现前的 EXECUTION 阶段单独批准，本提案不构成写授权。"
  }, {
    "id" : "C-5",
    "text" : "测试必须可仅凭标准库自仓库根目录运行（无需第三方测试框架或打包工具），以支撑 AC-2 的实际执行验证。"
  } ],
  "non_goals" : [ {
    "id" : "NG-1",
    "text" : "不引入第三方依赖、网络访问、数据库、浏览器或后台服务。"
  }, {
    "id" : "NG-2",
    "text" : "不修改 .git/、.agent/ 及三个提案文件之外的任何文件。"
  }, {
    "id" : "NG-3",
    "text" : "不实现换行规范化之外的文本处理（如制表符或空白处理、Unicode 规范化、编码转换、末尾换行的增删）。"
  } ],
  "risk_areas" : [ {
    "id" : "R-1",
    "text" : "替换顺序风险：若先替换单独 CR 再处理 CRLF，会把 CRLF 错误展开为两个 LF；实现需先处理 CRLF 再处理剩余的孤立 CR，并由混合换行用例防回归。"
  }, {
    "id" : "R-2",
    "text" : "README 中换行字符不可见，示例若直接内嵌真实 CR/LF 难以人工核验；建议以显式转义或可复现的展示方式书写示例，避免文档与代码不一致。"
  }, {
    "id" : "R-3",
    "text" : "src/ 布局且无打包配置时，测试导入模块需处理导入路径（如自仓库根目录运行 unittest 或在测试内调整 sys.path）；属实现细节，不影响用户可见语义，选取最简可行方案即可。"
  }, {
    "id" : "R-4",
    "text" : "当前仓库 tracked inventory 为空（repo-map.json 显示 inventoryPathCount=0），三个路径均为新建文件，无既有代码冲突；因此 evidence_refs 为空且不新建文件填充证据。"
  } ],
  "evidence_refs" : [ ],
  "open_questions" : [ ],
  "revision_responses" : [ ]
}
```
<!-- dual:typed-payload:end -->

