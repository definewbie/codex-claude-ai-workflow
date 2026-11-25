# normalize_newlines —— 换行规范化函数

本仓库提供纯函数 `normalize_newlines(text: str) -> str`：把 CRLF（`\r\n`）与孤立 CR（`\r`）统一转换为 LF（`\n`），其余内容原样保留。仅使用 Python 标准库，不引入任何第三方依赖。

## 语义规则

- CRLF 与孤立 CR 都转换为单个 LF；一个 CRLF 不会被拆成两个 LF。
- 已有的 LF 与其余所有字符原样保留，包括全部 Unicode 内容；U+2028（行分隔符）与 U+2029（段分隔符）不在转换范围内，保持不变。
- 末尾换行状态原样保留：输入末尾有换行，输出末尾仍有换行；输入末尾没有换行，不会新增。
- 纯函数：无 I/O、无共享可变状态、无外部副作用；重复调用幂等，`normalize_newlines(normalize_newlines(x)) == normalize_newlines(x)`；空串返回空串。

## 导入方式

实现位于 `src/normalize_text.py`（src 布局）。导入前需把 `src` 加入模块搜索路径，两种等价做法：

1. 运行时设置环境变量：`PYTHONPATH=src python your_script.py`；
2. 在代码中把 `src/` 目录插入 `sys.path`（`tests/test_normalize_text.py` 顶部采用的就是这种做法：用 `pathlib` 定位仓库 `src/`，且仅在 `sys.path` 不含该路径时插入）。

## 用法示例

以下示例为 doctest 风格，输入与输出和 `src/normalize_text.py` 的实现一致：

```python
>>> from normalize_text import normalize_newlines

>>> normalize_newlines("a\r\nb\rc\nd")  # 混合换行：CRLF 与孤立 CR 都变为 LF
'a\nb\nc\nd'

>>> normalize_newlines("")  # 空串返回空串
''

>>> normalize_newlines("end\r\n")  # 末尾有换行：规范化为 LF 后仍保留
'end\n'

>>> normalize_newlines("no newline")  # 末尾无换行：不会新增
'no newline'

```

## 运行测试

在仓库根目录执行：

```console
$ python -m unittest discover -s tests -v
```

`tests/test_normalize_text.py` 共 11 个用例，通过共享 `CASES` 表覆盖九类输入：空串、纯 LF、CRLF、单 CR、混合换行、Unicode（含 U+2028 保留）、末尾有换行、末尾无换行、连续空行；其中 `test_crlf_becomes_single_lf` 锁定替换顺序（CRLF 不拆成两个 LF），`test_no_cr_left_in_output` 断言全表输出无残留 `\r`，`test_idempotent_across_all_cases` 以 `subTest` 断言重复规范化幂等。

注意：unittest 默认把逐用例行与结尾的 `Ran 11 tests`、`OK`/`FAILED` 摘要写入 stderr；如需保存完整输出，用 `2>&1` 合并两个流：

```console
$ python -m unittest discover -s tests -v 2>&1 | tee unittest-output.txt
```

若环境中只有 `python3` 命令，把上述命令中的 `python` 等价替换为 `python3` 即可。

也可以把本文档的示例作为 doctest 执行，核对文档与实现的一致性（通过时无输出）：

```console
$ PYTHONPATH=src python -m doctest README.md
```
