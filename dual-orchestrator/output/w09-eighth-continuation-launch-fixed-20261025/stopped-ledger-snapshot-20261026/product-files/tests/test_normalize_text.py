"""normalize_newlines 的 unittest 测试。

在仓库根目录执行：python -m unittest discover -s tests -v
（unittest 默认将逐用例行与 Ran/OK 摘要写入 stderr，保存完整输出可用 2>&1 合并。）
"""

import sys
import unittest
from pathlib import Path

# 定位仓库 src/ 目录并注入 sys.path（不存在时才插入），支持从仓库根直接运行。
SRC_DIR = Path(__file__).resolve().parents[1] / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

# normalize_text 位于 src/ 下，导入须在 sys.path 注入之后（故 noqa: E402）。
from normalize_text import normalize_newlines  # noqa: E402

# 共享 CASES 表：九类输入 → 期望输出。
CASES = [
    ("", ""),                          # 空串
    ("a\nb\n", "a\nb\n"),              # 纯 LF，原样保留
    ("a\r\nb", "a\nb"),                # CRLF → 单个 LF
    ("a\rb", "a\nb"),                  # 孤立 CR → LF
    ("a\r\nb\rc\nd", "a\nb\nc\nd"),    # 混合换行
    ("中文\u2028😀\r\nx", "中文\u2028😀\nx"),  # Unicode，U+2028 保留
    ("end\r\n", "end\n"),              # 末尾有换行，保留
    ("no newline", "no newline"),      # 末尾无换行，不新增
    ("a\r\n\r\n\rb", "a\n\n\nb"),      # 连续空行
]


class TestNormalizeNewlines(unittest.TestCase):
    def test_empty_string_returns_empty(self):
        self.assertEqual(CASES[0], ("", ""))
        self.assertEqual(normalize_newlines(CASES[0][0]), CASES[0][1])

    def test_pure_lf_unchanged(self):
        self.assertEqual(CASES[1], ("a\nb\n", "a\nb\n"))
        self.assertEqual(normalize_newlines(CASES[1][0]), CASES[1][1])

    def test_crlf_becomes_single_lf(self):
        self.assertEqual(CASES[2], ("a\r\nb", "a\nb"))
        # 锁定替换顺序：先合并 CRLF 再处理孤立 CR，CRLF 不得拆成两个 LF。
        self.assertEqual(normalize_newlines("a\r\nb"), "a\nb")
        self.assertNotEqual(normalize_newlines("a\r\nb"), "a\n\nb")

    def test_lone_cr_becomes_lf(self):
        self.assertEqual(CASES[3], ("a\rb", "a\nb"))
        self.assertEqual(normalize_newlines(CASES[3][0]), CASES[3][1])

    def test_mixed_newlines_normalized(self):
        self.assertEqual(CASES[4], ("a\r\nb\rc\nd", "a\nb\nc\nd"))
        self.assertEqual(normalize_newlines(CASES[4][0]), CASES[4][1])

    def test_unicode_and_line_separators_preserved(self):
        self.assertEqual(CASES[5], ("中文\u2028😀\r\nx", "中文\u2028😀\nx"))
        result = normalize_newlines(CASES[5][0])
        self.assertEqual(result, CASES[5][1])
        self.assertIn("\u2028", result)  # U+2028 保留，不做转换

    def test_trailing_newline_present_kept(self):
        self.assertEqual(CASES[6], ("end\r\n", "end\n"))
        result = normalize_newlines(CASES[6][0])
        self.assertEqual(result, CASES[6][1])
        self.assertTrue(result.endswith("\n"))

    def test_trailing_newline_absent_kept(self):
        self.assertEqual(CASES[7], ("no newline", "no newline"))
        result = normalize_newlines(CASES[7][0])
        self.assertEqual(result, CASES[7][1])
        self.assertFalse(result.endswith("\n"))

    def test_consecutive_blank_lines_normalized(self):
        self.assertEqual(CASES[8], ("a\r\n\r\n\rb", "a\n\n\nb"))
        self.assertEqual(normalize_newlines(CASES[8][0]), CASES[8][1])

    def test_no_cr_left_in_output(self):
        for source, _expected in CASES:
            with self.subTest(source=source):
                self.assertNotIn("\r", normalize_newlines(source))

    def test_idempotent_across_all_cases(self):
        for source, _expected in CASES:
            with self.subTest(source=source):
                once = normalize_newlines(source)
                self.assertEqual(normalize_newlines(once), once)


if __name__ == "__main__":
    unittest.main()
