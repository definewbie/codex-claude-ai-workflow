# Human Review Packet

- Workstream: ws-20261023T005431Z-9YYF
- Gate: gate.human_acceptance
- Gate Instance: node-gate-human_acceptance-039
- Stage: stage-gate
- Iteration: iter-003
- Source Outcome: completed
- Allowed Actions: approve, revise

## Operator Guidance

- Recommended Action: approve
- Reason: No decisive recommendation is available. Read the packet and choose an allowed action.
- Suggested Checks:
  - Confirm the TaskContract objective, scope, non-goals, and acceptance criteria still match the task.
  - Compare Codex acceptance, developer self-test evidence, Git evidence, and changed files before approval.

## Review Focus

- Read the complete referenced design and review sidecars before approving; the packet summary is not a substitute.
- Use `approve` only when the current gate is good enough to continue.
- Use `revise` when the agent should address concrete feedback first.

## Review Summary

- Objective: 在本地 Python 仓库提供 normalize_newlines(text: str) -> str：将 CRLF 与单独 CR 转换为 LF，其余字符、空串、已有 LF 与末尾换行有无全部原样保留，仅使用标准库实现，并以实际执行的 unittest 测试和与代码一致的 README 示例完成验收。
- Blocking: false

## Task Contract

- Objective: 在本地 Python 仓库提供 normalize_newlines(text: str) -> str：将 CRLF 与单独 CR 转换为 LF，其余字符、空串、已有 LF 与末尾换行有无全部原样保留，仅使用标准库实现，并以实际执行的 unittest 测试和与代码一致的 README 示例完成验收。
- Status: READY_FOR_REVIEW
- Included Scope:
  - {path=src/normalize_text.py, operation=CREATE}
  - {path=tests/test_normalize_text.py, operation=CREATE}
  - {path=README.md, operation=CREATE}
- Excluded Scope:
  - 不创建或修改 .git/ 与 .agent/ 下的任何文件
  - 不引入第三方依赖、网络访问、数据库、浏览器、后台服务或机器配置修改
  - 不做 CRLF/单独 CR 转 LF 之外的换行处理（不转换 U+2028/U+2029 等 Unicode 行/段分隔符），不新增或删除末尾换行
- Acceptance Criteria:
  - Clause[id=AC-1, text=测试源码覆盖至少八类输入——空串、纯 LF、CRLF、单 CR、混合换行、Unicode 字符、末尾有换行、末尾无换行、连续空行——每类输入都有明确断言的期望输出，并断言重复规范化（对已规范化结果再次调用）保持相同结果。]
  - Clause[id=AC-2, text=实际执行 unittest 测试并保留真实输出：输出明确显示覆盖的用例数（如 Ran N tests）且 failure=0、error=0，输出所列用例与测试源码中的案例相符；本任务不要求工具提供独立测量的真实退出码，但测试完成状态或文字结论本身不能替代实际输入/输出与测试结果。]
  - Clause[id=AC-3, text=README 用法示例与 src/normalize_text.py 实际实现一致，至少包含函数导入语句和一个典型混合换行输入/输出示例；该一致性由实际读取源码/文档或实际执行示例支持。]
- Test Requirements:
  - Clause[id=TR-1, text=在 tests/test_normalize_text.py 中使用 unittest 编写测试，覆盖 AC-1 列出的全部输入类别，每类输入断言精确的期望输出字符串，并包含对已规范化结果再次调用的幂等性断言。]
  - Clause[id=TR-2, text=实际执行测试（如 python -m unittest 或 python -m unittest discover，具体命令在 EXECUTION 计划中确认）并保留真实命令输出，输出须显示 Ran N tests 且 OK（零 failure、零 error），并与测试源码中的案例一一对应。]
  - Clause[id=TR-3, text=通过实际读取 src/normalize_text.py 与 README.md（或实际运行示例）验证 README 示例与实现一致，验证范围包含导入语句和典型混合换行输入的逐字符输出（例如 "a\r\nb\rc\nd" 型输入的期望结果）。]
- Constraints:
  - normalize_newlines 语义边界精确为：将输入中每个 CRLF（"\r\n"）和单独 CR（"\r"）转换为 LF（"\n"）；除此之外不改变任何字符（包括 Unicode 字符与 U+2028/U+2029 等），空串返回空串，已有 LF 原样保留，末尾有/无换行的状态原样保留；函数为纯函数且重复调用幂等。
  - 仅使用 Python 标准库；不引入任何第三方依赖，不使用网络、数据库、浏览器、后台服务，不做机器配置修改。
  - src/normalize_text.py、tests/test_normalize_text.py、README.md 为 intake 提案路径，不构成写授权；最终具体文件与 CREATE/MODIFY 操作须在 EXECUTION 阶段经设计与计划确认并批准后实施。
- Nonessential Operation Declarations:
  - None

## Requirement Resolution

- Decision: APPROVE
- Approved By: human_gate
- Gate Ref: .agent/workstreams/ws-20261023T005431Z-9YYF/gates/gate-node-gate-human_requirement_review-006.json
- Assumptions:
  - Human approved the requirement review gate before canonical contract generation.

## Git Evidence

- Changed Files:
  - README.md
  - src/normalize_text.py
  - tests/test_normalize_text.py
### Diff Stat

```text
/dev/null => README.md | 62 ++++++++++++++++++++++++++++++++++++++++++++++++++
 1 file changed, 62 insertions(+)
 /dev/null => src/normalize_text.py | 20 ++++++++++++++++++++
 1 file changed, 20 insertions(+)
 /dev/null => tests/test_normalize_text.py | 91 +++++++++++++++++++++++++++++++
 1 file changed, 91 insertions(+)
```

### Status

```text
?? README.md
?? src/normalize_text.py
?? tests/test_normalize_text.py
```


## Delivery Artifacts

- Candidate: .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/candidates/node-delivery-prepare_pr-038/candidate-5b4ba7c4993dcda7ccd7190c47edb54f74fb0b8046deb3cf1e04bc78b9ed59bb.json
- Candidate SHA-256: 5b4ba7c4993dcda7ccd7190c47edb54f74fb0b8046deb3cf1e04bc78b9ed59bb
- Expected Git Tree: 0233afaf8755e44b6c8ab156e311ffbef6e50983
- Accepted Files:
  - README.md
  - src/normalize_text.py
  - tests/test_normalize_text.py
- PR Body: .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/pr-body.md
- Commit Message: .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/commit-message.txt

## Input Artifacts

Raw agent logs are not embedded here by default. Use the paths below when deeper debugging is needed.

### .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/commit-message.txt

- Status: available
- Kind: file
- Raw Artifact: .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/commit-message.txt

### .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/pr-body.md

- Status: available
- Kind: markdown_output
- Raw Artifact: .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/pr-body.md

### .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/candidates/node-delivery-prepare_pr-038/candidate-5b4ba7c4993dcda7ccd7190c47edb54f74fb0b8046deb3cf1e04bc78b9ed59bb.json

- Status: available
- Kind: json
- Raw Artifact: .agent/workstreams/ws-20261023T005431Z-9YYF/delivery/candidates/node-delivery-prepare_pr-038/candidate-5b4ba7c4993dcda7ccd7190c47edb54f74fb0b8046deb3cf1e04bc78b9ed59bb.json

