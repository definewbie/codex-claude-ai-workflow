# Task Contract

## Objective

在本地 Python 仓库提供 normalize_newlines(text: str) -> str：将 CRLF 与单独 CR 转换为 LF，其余字符、空串、已有 LF 与末尾换行有无全部原样保留，仅使用标准库实现，并以实际执行的 unittest 测试和与代码一致的 README 示例完成验收。

## Scope

**Excluded:**
- 不创建或修改 .git/ 与 .agent/ 下的任何文件
- 不引入第三方依赖、网络访问、数据库、浏览器、后台服务或机器配置修改
- 不做 CRLF/单独 CR 转 LF 之外的换行处理（不转换 U+2028/U+2029 等 Unicode 行/段分隔符），不新增或删除末尾换行

## Acceptance Criteria

- [ ] AC-1: 测试源码覆盖至少八类输入——空串、纯 LF、CRLF、单 CR、混合换行、Unicode 字符、末尾有换行、末尾无换行、连续空行——每类输入都有明确断言的期望输出，并断言重复规范化（对已规范化结果再次调用）保持相同结果。
- [ ] AC-2: 实际执行 unittest 测试并保留真实输出：输出明确显示覆盖的用例数（如 Ran N tests）且 failure=0、error=0，输出所列用例与测试源码中的案例相符；本任务不要求工具提供独立测量的真实退出码，但测试完成状态或文字结论本身不能替代实际输入/输出与测试结果。
- [ ] AC-3: README 用法示例与 src/normalize_text.py 实际实现一致，至少包含函数导入语句和一个典型混合换行输入/输出示例；该一致性由实际读取源码/文档或实际执行示例支持。

## Test Requirements

- TR-1: 在 tests/test_normalize_text.py 中使用 unittest 编写测试，覆盖 AC-1 列出的全部输入类别，每类输入断言精确的期望输出字符串，并包含对已规范化结果再次调用的幂等性断言。
- TR-2: 实际执行测试（如 python -m unittest 或 python -m unittest discover，具体命令在 EXECUTION 计划中确认）并保留真实命令输出，输出须显示 Ran N tests 且 OK（零 failure、零 error），并与测试源码中的案例一一对应。
- TR-3: 通过实际读取 src/normalize_text.py 与 README.md（或实际运行示例）验证 README 示例与实现一致，验证范围包含导入语句和典型混合换行输入的逐字符输出（例如 "a\r\nb\rc\nd" 型输入的期望结果）。

## Constraints

- normalize_newlines 语义边界精确为：将输入中每个 CRLF（"\r\n"）和单独 CR（"\r"）转换为 LF（"\n"）；除此之外不改变任何字符（包括 Unicode 字符与 U+2028/U+2029 等），空串返回空串，已有 LF 原样保留，末尾有/无换行的状态原样保留；函数为纯函数且重复调用幂等。
- 仅使用 Python 标准库；不引入任何第三方依赖，不使用网络、数据库、浏览器、后台服务，不做机器配置修改。
- src/normalize_text.py、tests/test_normalize_text.py、README.md 为 intake 提案路径，不构成写授权；最终具体文件与 CREATE/MODIFY 操作须在 EXECUTION 阶段经设计与计划确认并批准后实施。

## Non-Goals

- 不引入第三方依赖，也不生成 requirements.txt、pyproject.toml 等依赖清单。
- 不进行网络访问、数据库、浏览器、后台服务或机器配置修改。
- 不扩展换行处理范围：不转换 LF 以外的 Unicode 行/段分隔符，不添加、删除或补齐末尾换行，不提供 CLI 或打包发布。

## Risk Areas

- 仓库当前 git 跟踪清单为空（repo-map fileCount=0），全部产物需新建；测试对 src.normalize_text 的导入方式（如包结构、__init__.py、sys.path 或 unittest discover 的启动目录）需在 EXECUTION 计划中确定，避免因导入路径导致测试无法实际执行。
- AC-2 的覆盖数以 unittest 实际输出的 Ran N tests 为准，执行记录必须保留真实命令输出而非转述，防止以“测试已完成”之类结论替代实测结果。

## Nonessential Operation Declarations

- None

## Source

- **Created by:** contract.compile
- **Raw request:** .agent/workstreams/ws-20261023T005431Z-9YYF/requirement.md

