# Context Package

- Workstream: ws-20261010T124812Z-XCZL
- Builder Node: context.intake_codex_product_review
- Target Node: intake.codex_product_review
- Generated At: 2026-10-10T12:51:17.784359Z
- Profile: product_review
- Total: 1.7k / 4.0k tokens

## Context Governance

| Section | Level | Degraded | Original | Current |
|---|---|---|---|---|
| input_artifacts | full | no | 398 | 398 |
| review_evidence | full | no | 64 | 64 |
| recent_agent_outputs | full | no | 178 | 178 |
| ledger_pointers | pointer_only | yes | 139 | 153 |
| required_review_subject | full | no | 886 | 886 |

Degraded sections have full data at their original paths listed below.

## State & Evidence Boundary

This package separates deterministic harness facts from agent proposals.

### Trusted Harness Facts

- TaskContract qualification: not available
- Git Evidence: not generated yet

### Human Decisions

- Requirement Resolution: not available

### Agent Proposed States

- intake.claude_brainstorm: decision=READY_FOR_PRODUCT_REVIEW, stageOutcome=READY, evidenceState={blocking=false, evidence_count=1, missing_evidence_count=0, must_fix_count=0, acceptance_note_count=0, open_risk_count=0} (/private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.contract.json)

### Pending Contract/Evidence Gaps

- No contract failure diagnostics recorded

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: intake
- Governance refs: capabilityCatalog=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/agent-capability-catalog.json; readReuseContract=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/read-reuse-contract.json; repoMap=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/repo-map.json; repositoryFactBinding=.agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-context-intake_codex_product_review-003/binding.json

- Tracked inventory: count=0; head=; sha256=

- Available context capabilities: direct_file_read:Read

- Recommended read files: (none planned)
- Expected edit files: (none planned)
## Input Artifacts

### .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.stderr.log

(execution diagnostic omitted from context)

### .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.transcript.log

(execution transcript omitted from context; inspect raw file at /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.transcript.log)

### .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.json

- Contract: intake.claude_brainstorm
- Decision: READY_FOR_PRODUCT_REVIEW
- Summary: 在空产品树中新建纯标准库的换行规范化函数、unittest 测试与 README,并以真实测试输出和示例核对作为验收证据。
- Verification: task contract proposal validated
- Contract Sidecar: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.contract.json
- Raw Output: .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.json

### .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/context-governance/occurrences/node-intake-claude_brainstorm-002-run-1791636537680/binding.json

(routed artifact has no typed context projection; content omitted)

### .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/task-contract-proposals/node-intake-claude_brainstorm-002-44bd6b6d7b341527.ref.json

(routed artifact has no typed context projection; content omitted)

## Review Evidence Brief

TaskContract projection: unavailable

(no cumulative review verdict recorded; no open issues, changed files, or reusable memory recorded yet) Evidence: .agent/workstreams/ws-20261010T124812Z-XCZL/memory/workstream-memory.json.

## Recent Agent Outputs

### /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.contract.json

- Contract: intake.claude_brainstorm
- Decision: READY_FOR_PRODUCT_REVIEW
- Summary: 在空产品树中新建纯标准库的换行规范化函数、unittest 测试与 README,并以真实测试输出和示例核对作为验收证据。
- Verification: task contract proposal validated
- Contract Sidecar: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.contract.json
- Raw Output: .agent/workstreams/ws-20261010T124812Z-XCZL/runs/run-1791636537680/outputs/intake-claude-brainstorm.json

## Ledger Pointers

- Events: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/events.jsonl
- Workflow current: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL/workflow/current.json
- Node state directory: /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-2026101

[adaptively compressed; section degraded to stay within ~125 tokens; full source remains in ledger under /private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.agent/workstreams/ws-20261010T124812Z-XCZL]

## Required Review Subject

- Proposal ID: node-intake-claude_brainstorm-002:44bd6b6d7b341527
- Proposal Reference: .agent/workstreams/ws-20261010T124812Z-XCZL/artifacts/task-contract-proposals/node-intake-claude_brainstorm-002-44bd6b6d7b341527.ref.json
- Proposal SHA-256: 44bd6b6d7b34152796f68e8b35358a55f20aa90e97e1bea46bc935e031f38722

<!-- dual:typed-payload:start -->
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
    "text" : "产品变更仅限于仓库内的 src/normalize_text.py、tests/test_normalize_text.py、README.md 三个文件;不改动 .git/ 或 .agent/ 下的任何内容。"
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
  "revision_responses" : [ ]
}
```
<!-- dual:typed-payload:end -->

