# Task Contract

## Objective

提供纯 Python 标准库实现的 normalize_newlines(text: str) -> str：把 CRLF(\r\n) 与单独 CR(\r) 转换为 LF(\n)，其余字符、空串、已有 LF 以及末尾有/无换行的形态一律原样保留，并以 unittest 测试与 README 用法示例验证该行为。

## Scope

**Excluded:**
- 不修改 .git/ 与 .agent/ 下的任何文件
- 不引入第三方依赖，也不添加 requirements 或打包配置文件
- 不进行网络访问、数据库、浏览器、后台服务或机器配置相关改动
- 不添加 CLI 入口或文件批处理能力（仅提供纯字符串函数）

## Acceptance Criteria

- [ ] AC-1: tests/test_normalize_text.py 覆盖至少八类输入并写明精确期望输出：空串、纯 LF、CRLF、单独 CR、混合换行、Unicode 内容、末尾有换行与末尾无换行、连续空行；并对每个用例断言 normalize_newlines(normalize_newlines(x)) == normalize_newlines(x)，即重复规范化保持相同结果。
- [ ] AC-2: 实际执行 unittest 测试并保留真实执行证据：记录实际执行的命令与完整实际输出（stdout/stderr），输出明确显示运行的测试数量（如 Ran N tests）且 failures=0、errors=0（结果为 OK），N 与测试源码中的用例数一致；同时提供代表性用例的实际输入字符串、测试源码中写明的期望输出（断言值）与实际执行结果三者的对应证据，可逐例与测试源码核对。测试完成状态或文字结论不能替代上述实际输入、实际输出与测试结果证据；本任务不要求工具提供独立测量的真实退出码。
- [ ] AC-3: README.md 的示例与 src/normalize_text.py 实际行为一致：包含导入语句（如 from normalize_text import normalize_newlines 或等效导入方式）以及典型混合换行输入及其输出的示例；该一致性结论须以实际读取源码与 README 或实际执行示例为依据。

## Test Requirements

- TR-1: 使用标准库 unittest 编写 tests/test_normalize_text.py，逐例覆盖 AC-1 所列至少八类输入的精确期望字符串，并包含幂等性断言（二次规范化结果等于一次）。
- TR-2: 实际运行测试：从仓库根目录以仅用标准库的命令执行（如 python -m unittest discover -s tests 或等效 python -m unittest 调用，无需安装依赖），记录真实命令与完整 stdout/stderr 实际输出，显示测试数量与零 failure、零 error；验证报告中须给出至少代表性用例的实际输入、测试源码中的期望输出与实际执行结果的对应证据，使其可与测试源码逐例核对；测试完成状态或文字结论不能替代实际输入/输出证据，且不要求独立测量的真实退出码。
- TR-3: 验证 README 示例一致性：实际读取 README.md 与 src/normalize_text.py 核对导入方式与示例输入/输出，或实际执行示例代码确认输出一致。

## Constraints

- normalize_newlines 仅做换行归一：把 \r\n 与单独 \r 转换为 \n；其他一切字符（含 \v、\f、U+2028/U+2029 等）、空串、已有 LF、末尾有无换行均原样保留，不增加、不删除、不改动。
- 实现、测试与文档仅使用 Python 标准库，不引入任何第三方依赖。
- 不涉及网络、数据库、浏览器、后台服务或机器配置修改；当前 scope.included 所列三个文件仅为候选路径而非最终授权的实现文件集合，仓库改动仅限经设计、计划形成并在实现前单独批准的 EXECUTION scope.included 中逐项列明的精确仓库相对路径与 CREATE/MODIFY 操作，只有该获批清单授权仓库修改。
- 测试须可在仓库根目录以标准库命令运行且无需安装或打包配置；src 布局下的模块导入方式（如测试文件内的 sys.path 处理）由设计与计划阶段确定。

## Non-Goals

- 不提供命令行入口、文件读写或批量处理能力，仅提供纯字符串函数。
- 不做编码转换，也不归一化 CRLF/CR 之外的行分隔符（它们属于其他字符，按 C-1 原样保留）。
- 不将用户建议的三个文件（src/normalize_text.py、tests/test_normalize_text.py、README.md）视为 GOAL 阶段固化的实现文件集合：它们仅为候选路径，本条款不预先限定设计、计划阶段形成的最终精确仓库相对路径清单与逐项 CREATE/MODIFY 操作类型（含确需新增的文件如 tests/__init__.py）；实现前须单独提交 EXECUTION 批准，其 scope.included 逐项列明精确仓库相对路径及 CREATE/MODIFY，只有该获批清单是仓库修改的穷尽授权集。

## Risk Areas

- 仓库当前无任何跟踪文件（repo-map 显示 inventoryPathCount=0），src/ 与 tests/ 目录及导入机制需在执行阶段新建并实测；若 unittest 发现机制确需额外 __init__.py，须按流程以 typed scope-addition 单独批准。
- 换行为不可见字符，README 示例应以显式转义形式（\r\n、\r、\n）呈现输入与输出，避免读者误读并保证 AC-3 可核对。
- 连续空行、混合换行等用例的期望值需在测试源码中写成精确字符串，并与 README 示例保持一致，防止断言与文档脱节。

## Nonessential Operation Declarations

- None

## Source

- **Created by:** contract.compile
- **Raw request:** .agent/workstreams/ws-20261011T044656Z-BMDJ/requirement.md

