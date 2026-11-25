import ast
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from process_check import ProcessInspectionError, require_known_pids_absent


class ProcessCheckTest(unittest.TestCase):
    def result(self, code=0, stdout="12 Thu Oct 22 23:00:00 2026 /usr/bin/other\n", stderr=""):
        return subprocess.CompletedProcess([], code, stdout, stderr)

    def test_successful_complete_snapshot_proves_absence_only(self):
        with patch("process_check.subprocess.run", return_value=self.result()) as call:
            self.assertEqual("ABSENT", require_known_pids_absent([99])["status"])
        self.assertEqual(1, call.call_count)
        self.assertEqual(5, call.call_args.kwargs["timeout"])

    def test_present_pid_including_reused_pid_is_rejected(self):
        with patch("process_check.subprocess.run", return_value=self.result()):
            with self.assertRaises(ProcessInspectionError) as caught:
                require_known_pids_absent([12])
        self.assertEqual("PRESENT", caught.exception.status)

    def test_failures_never_become_absent(self):
        cases = [
            self.result(1, "", "ps: Operation not permitted (os error 1)"),
            self.result(1, "", ""),
            self.result(0, "", ""),
            self.result(0, stderr="permission warning"),
            self.result(-9),
            self.result(0, "truncated\n"),
            self.result(0, "0 command\n"),
            self.result(0, "-1 command\n"),
            self.result(0, "12 command\n12 duplicate\n"),
            self.result(0, "１２ command\n"),
        ]
        for response in cases:
            with self.subTest(response=response), patch("process_check.subprocess.run", return_value=response):
                with self.assertRaises(ProcessInspectionError) as caught:
                    require_known_pids_absent([99])
                self.assertEqual("UNKNOWN", caught.exception.status)

    def test_timeout_and_spawn_failure_are_unknown(self):
        for error in [subprocess.TimeoutExpired("ps", 5), PermissionError("denied")]:
            with self.subTest(error=error), patch("process_check.subprocess.run", side_effect=error):
                with self.assertRaises(ProcessInspectionError) as caught:
                    require_known_pids_absent([99])
                self.assertEqual("UNKNOWN", caught.exception.status)

    def test_invalid_pid_rejected_before_command(self):
        for pid in [True, 0, -1, "12"]:
            with self.subTest(pid=pid), patch("process_check.subprocess.run") as command:
                with self.assertRaises(ProcessInspectionError):
                    require_known_pids_absent([pid])
                command.assert_not_called()

    def test_cold_cli_permission_failure_and_success(self):
        # Cold process exercises the actual CLI consumer and exit status, no provider.
        with tempfile.TemporaryDirectory() as folder:
            prior = Path(folder) / "prior.json"
            prior.write_text(json.dumps({"known_pids": [{"pid": 99}]}))
            for code, stdout, stderr, expected in [
                (1, "", "Operation not permitted", 2),
                (0, "12 running command\n", "", 0),
                (0, "99 reused command\n", "", 2),
            ]:
                snippet = (
                    "import runpy,subprocess,sys; "
                    f"subprocess.run=lambda *a,**k: subprocess.CompletedProcess([], {code}, {stdout!r}, {stderr!r}); "
                    f"sys.argv=['process_check.py',{str(prior)!r}]; "
                    "runpy.run_path('process_check.py',run_name='__main__')"
                )
                result = subprocess.run([sys.executable, "-B", "-c", snippet], capture_output=True, text=True)
                self.assertEqual(expected, result.returncode, result.stderr)
                self.assertEqual("ABSENT" if expected == 0 else ("PRESENT" if stdout else "UNKNOWN"),
                                 json.loads(result.stdout)["status"])

    def test_both_candidate_consumers_stop_before_following_action(self):
        for name, variable, later_marker in [
            ("prepare-offline.py.candidate", "old_execution", "PARENT.mkdir()"),
            ("pre-dispatch-recheck.py.candidate", "old", "observed = measure("),
        ]:
            source = Path(name).read_text()
            self.assertLess(source.index("process_observation = require_known_pids_absent("),
                            source.index(later_marker))
            tree = ast.parse(source)
            assignment = next(n for n in tree.body if isinstance(n, ast.Assign)
                              and any(isinstance(t, ast.Name) and t.id == "process_observation"
                                      for t in n.targets))
            body = ast.Module(body=[assignment, ast.parse("following.append('reached')").body[0]],
                              type_ignores=[])
            for response, allowed in [(self.result(), True),
                                      (self.result(1, "", "Operation not permitted"), False),
                                      (self.result(0, "99 running command\n"), False)]:
                context = {variable: {"known_pids": [{"pid": 99}]}, "following": [],
                           "require_known_pids_absent": require_known_pids_absent}
                with patch("process_check.subprocess.run", return_value=response):
                    if allowed:
                        exec(compile(body, name, "exec"), context)
                    else:
                        with self.assertRaises(ProcessInspectionError):
                            exec(compile(body, name, "exec"), context)
                self.assertEqual(["reached"] if allowed else [], context["following"])


if __name__ == "__main__":
    unittest.main()
