"""换行规范化工具模块。

提供纯函数 normalize_newlines：将 CRLF 与孤立 CR 统一转换为 LF。
仅使用 Python 标准库，无 I/O、无共享可变状态、无外部副作用。
"""


def normalize_newlines(text: str) -> str:
    r"""将 CRLF（"\r\n"）与孤立 CR（"\r"）统一转换为 LF（"\n"）。

    - CRLF 与孤立 CR 都转换为单个 LF，一个 CRLF 不会被拆成两个 LF。
    - 已有的 LF 与其余字符原样保留，包括 U+2028/U+2029 等 Unicode
      行/段分隔符（它们不在转换范围内）。
    - 末尾换行的有无状态原样保留：不新增、也不删除末尾换行。
    - 纯函数且幂等：normalize_newlines(normalize_newlines(x)) 与
      normalize_newlines(x) 结果相同；空串返回空串。
    """
    text = text.replace("\r\n", "\n")
    text = text.replace("\r", "\n")
    return text
