import ast
import json
import subprocess
import sys
import tempfile
import unittest
import hashlib
import importlib.util
from pathlib import Path
from unittest.mock import patch

from process_check import ProcessInspectionError, require_known_pids_absent, require_unresolved_pids_absent


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

    def test_three_assembled_consumers_stop_before_following_action(self):
        for name, variable, later_marker in [
            ("prepare-offline.py", "old_execution", "PARENT.mkdir(parents=True)"),
            ("pre-dispatch-recheck.py", "old", "observed = measure("),
            ("run-one-step.py", "runner", "folder.mkdir(parents=True)"),
        ]:
            source = Path(name).read_text()
            self.assertLess(source.index("process_observation = require_unresolved_pids_absent("),
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
                           "require_unresolved_pids_absent": require_unresolved_pids_absent,
                           "read": lambda path: {"known_pids": [{"pid": 99}]}, "OUT": Path(".")}
                with patch("process_check.subprocess.run", return_value=response):
                    if allowed:
                        exec(compile(body, name, "exec"), context)
                    else:
                        with self.assertRaises(ProcessInspectionError):
                            exec(compile(body, name, "exec"), context)
                self.assertEqual(["reached"] if allowed else [], context["following"])


class HistoricalAbsenceTest(unittest.TestCase):
    def test_confirmed_history_is_not_a_current_absence_claim(self):
        with patch("process_check.subprocess.run", return_value=subprocess.CompletedProcess(
                [], 0, "12 unrelated process\n", "")) as command:
            result = require_unresolved_pids_absent([{"pid": 83976, "absent": True}])
            command.assert_not_called()
        self.assertEqual("NO_UNRESOLVED_PIDS", result["status"])
        self.assertEqual([83976], result["previously_confirmed_absent"])
        self.assertEqual([], result["checked_pids"])
        self.assertNotIn("ps_exit", result)

    def test_mixed_records_only_check_unresolved_targets(self):
        response = subprocess.CompletedProcess([], 0, "99 new unrelated process\n", "")
        with patch("process_check.subprocess.run", return_value=response) as command:
            value = require_unresolved_pids_absent([{"pid": 99, "absent": True}, {"pid": 88}])
        self.assertEqual(1, command.call_count)
        self.assertEqual([88], value["checked_pids"])
        self.assertEqual([99], value["previously_confirmed_absent"])

    def test_unresolved_present_or_failed_observation_still_stops(self):
        cases = [(0, "88 old or unidentified process\n", "", "PRESENT"),
                 (1, "", "Operation not permitted", "UNKNOWN"),
                 (0, "", "", "UNKNOWN")]
        for code, stdout, stderr, expected in cases:
            with self.subTest(expected=expected), patch("process_check.subprocess.run",
                    return_value=subprocess.CompletedProcess([], code, stdout, stderr)):
                with self.assertRaises(ProcessInspectionError) as caught:
                    require_unresolved_pids_absent([{"pid": 99, "absent": True}, {"pid": 88, "absent": False}])
                self.assertEqual(expected, caught.exception.status)

    def test_invalid_history_cannot_silently_remove_a_target(self):
        cases = [None, {}, [None], [{"pid": True}], [{"pid": -1}],
                 [{"pid": 99, "absent": "true"}], [{"pid": 99, "absent": 1}],
                 [{"pid": 99, "absent": None}], [{"pid": 99}, {"pid": 99, "absent": True}]]
        for records in cases:
            with self.subTest(records=records), patch("process_check.subprocess.run") as command:
                with self.assertRaises(ProcessInspectionError) as caught:
                    require_unresolved_pids_absent(records)
                self.assertEqual("UNKNOWN", caught.exception.status)
                command.assert_not_called()

    def test_empty_watchlist_is_not_a_synthetic_successful_read(self):
        with patch("process_check.subprocess.run") as command:
            result = require_unresolved_pids_absent([])
        command.assert_not_called()
        self.assertEqual("NO_UNRESOLVED_PIDS", result["status"])
        self.assertNotIn("observed_processes", result)

    def test_first_preparation_never_trusts_input_absence_assertion(self):
        source = Path("prepare-offline.py").read_text()
        tree = ast.parse(source)
        guard = next(n for n in tree.body if isinstance(n, ast.Assign)
                     and any(isinstance(t, ast.Name) and t.id == "process_observation" for t in n.targets))
        context = {"old_execution": {"known_pids": [{"pid": 99, "absent": True}]},
                   "require_unresolved_pids_absent": require_unresolved_pids_absent}
        with patch("process_check.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "99 process\n", "")):
            with self.assertRaises(ProcessInspectionError) as caught:
                exec(compile(ast.Module(body=[guard], type_ignores=[]), "prepare-offline.py", "exec"), context)
        self.assertEqual("PRESENT", caught.exception.status)

    def test_preparation_preserves_successfully_observed_records(self):
        source = ast.parse(Path("prepare-offline.py").read_text())
        assignments = [n for n in source.body if isinstance(n, ast.Assign)
                       and any(isinstance(t, ast.Name) and t.id in {"process_observation", "pid_checks"}
                               for t in n.targets)]
        context = {"old_execution": {"known_pids": [{"pid": 99}]},
                   "require_unresolved_pids_absent": require_unresolved_pids_absent}
        with patch("process_check.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "12 unrelated\n", "")):
            exec(compile(ast.Module(body=assignments, type_ignores=[]), "prepare-offline.py", "exec"), context)
        self.assertEqual([{"pid": 99, "absent": True}], context["pid_checks"])
        self.assertEqual([99], context["process_observation"]["checked_pids"])

    def test_three_entrypoints_share_guard_and_frozen_check_precedes_it(self):
        for name in ["prepare-offline.py", "pre-dispatch-recheck.py", "run-one-step.py"]:
            source = Path(name).read_text()
            self.assertIn("from process_check import require_unresolved_pids_absent", source)
            self.assertLess(source.index("release = frozen_check()"),
                            source.index("process_observation = require_unresolved_pids_absent("))
        for name in ["pre-dispatch-recheck.py", "run-one-step.py"]:
            source = Path(name).read_text()
            self.assertIn("old-execution-recheck.json", source)
            self.assertNotIn("process_observation = require_known_pids_absent(", source)

    def test_preparation_receipt_write_failures_prevent_fixture_creation(self):
        source = ast.parse(Path("prepare-offline.py").read_text())
        begin = next(i for i, n in enumerate(source.body) if isinstance(n, ast.Assign)
                     and any(isinstance(t, ast.Name) and t.id == "process_observation" for t in n.targets))
        end = next(i for i, n in enumerate(source.body) if isinstance(n, ast.Expr)
                   and isinstance(n.value, ast.Call) and ast.unparse(n.value.func) == "PARENT.mkdir")
        body = ast.Module(body=source.body[begin:end + 1], type_ignores=[])
        for fail_at in [1, 2, None]:
            with self.subTest(write_failure=fail_at), tempfile.TemporaryDirectory() as folder:
                root = Path(folder); written = []
                def write(path, value):
                    if len(written) + 1 == fail_at:
                        raise OSError("Injected receipt write failure")
                    path.write_text(json.dumps(value)); written.append(path)
                context = {"OUT": root, "PARENT": root / "fixture", "write": write,
                           "session_checks": [],
                           "old_execution": {"known_pids": [{"pid": 99}]},
                           "require_unresolved_pids_absent": require_unresolved_pids_absent}
                with patch("process_check.subprocess.run", return_value=subprocess.CompletedProcess([], 0, "12 unrelated\n", "")):
                    if fail_at:
                        with self.assertRaises(OSError):
                            exec(compile(body, "prepare-offline.py", "exec"), context)
                    else:
                        exec(compile(body, "prepare-offline.py", "exec"), context)
                self.assertEqual(fail_at is None, (root / "fixture").exists())
                self.assertEqual(0 if fail_at == 1 else 1 if fail_at == 2 else 2, len(written))
                self.assertFalse((root / "launch-identity.json").exists())

    def test_cold_consumers_reuse_fixed_fact_but_refuse_tampered_bytes(self):
        # Run the actual old frozen_check and actual candidate consumer guard
        # statements, with synthetic frozen files. Not a Java/provider Gate.
        candidate = Path.cwd().resolve()
        old_support = candidate.parents[1] / "w09-fresh-eighth-gate-20261023" / "gate_support.py"
        self.assertTrue(old_support.is_file())
        for name in ["pre-dispatch-recheck.py", "run-one-step.py"]:
            tree = ast.parse(Path(name).read_text())
            selected = [n for n in tree.body if isinstance(n, ast.Assign)
                        and any(isinstance(t, ast.Name) and t.id in {"release", "process_observation"}
                                for t in n.targets)]
            self.assertEqual(2, len(selected))
            statements = ast.unparse(ast.Module(body=selected, type_ignores=[]))
            with tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                record = root / "old-execution-recheck.json"
                record.write_text(json.dumps({"known_pids": [{"pid": 83976, "absent": True}]}))
                h = hashlib.sha256(record.read_bytes()).hexdigest()
                source = root / "source.txt"; source.write_text("fixed source")
                source_sha = hashlib.sha256(source.read_bytes()).hexdigest()
                (root / "selected-release.json").write_text(json.dumps({
                    "source_inputs": {"source.txt": source_sha}, "application_jars": {},
                    "metadata_config_source_files": {}, "selected_acpx": {
                        "wrapper": str(source), "wrapper_sha256": source_sha,
                        "dist": str(source), "dist_sha256": source_sha}}))
                (root / "launch-identity.json").write_text(json.dumps({
                    "config_and_installed_sources": {}, "observer_inputs": {record.name: h}}))
                snippet = f'''import sys,json,importlib.util
from pathlib import Path
sys.path.insert(0,{str(candidate)!r})
from process_check import require_unresolved_pids_absent
import process_check
def forbidden(*args,**kwargs): raise AssertionError("No process table needed for a confirmed old absence")
process_check.subprocess.run=forbidden
spec=importlib.util.spec_from_file_location("actual_gate_support",{str(old_support)!r})
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
OUT=Path({str(root)!r});g.OUT=OUT;g.ROOT=OUT;g.RELEASE=OUT/"selected-release.json"
read=g.read;frozen_check=g.frozen_check
old=read(OUT/"old-execution-recheck.json")
{statements}
(OUT/"following-action").write_text("reached")
print(json.dumps(process_observation))
'''
                child = root / "probe.py"; child.write_text(snippet)
                result = subprocess.run([sys.executable, "-B", str(child)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual("NO_UNRESOLVED_PIDS", json.loads(result.stdout)["status"])
                (root / "following-action").unlink()
                record.write_text(record.read_text() + " ")
                result = subprocess.run([sys.executable, "-B", str(child)], capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Observer/Gate drift", result.stderr)
                self.assertFalse((root / "following-action").exists())


if __name__ == "__main__":
    unittest.main()
