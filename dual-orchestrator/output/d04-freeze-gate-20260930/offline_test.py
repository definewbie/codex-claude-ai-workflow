"""Offline guard/contract checks only. No native or provider calls."""
from pathlib import Path
import ast
import json
import sys
import unittest
from unittest.mock import patch
import probe

HERE = Path(__file__).resolve().parent


class Guards(unittest.TestCase):
    def test_identity_and_syntax(self):
        ast.parse((HERE / 'probe.py').read_text())
        probe.load_plan()

    def test_live_contract(self):
        recorded = json.loads((HERE.parent / 'd04-native-boundary-review-20260929/codex-protocol/config-probe.json').read_text())['requests'][0]
        probe.check_live_contract(recorded)
        self.assertEqual(probe.metadata(recorded)[0], '/root')
        with self.assertRaises(ValueError):
            probe.check_live_contract({'input': []})
        with self.assertRaises(ValueError):
            probe.metadata({'client_metadata': {}})

    def test_spawn_cannot_smuggle_fields(self):
        args = dict(task_name='probe', fork_turns='none', message='fixture')
        self.assertEqual(json.loads(probe.function('spawn_agent', args, 'x')['arguments']), args)
        with self.assertRaises(ValueError):
            probe.function('spawn_agent', dict(args, approve_all=True), 'x')

    def test_role_payloads(self):
        for role in ('read_only', 'implement', 'cancel'):
            body = probe.child_payload(role, Path('/fixture/work'), Path('/fixture/control'))['input']
            self.assertNotIn('spawn_agent', body)
            self.assertEqual('legal_write' in body, role != 'read_only')
            if role != 'cancel':
                self.assertIn('/fixture/work/alias-control', body)
                self.assertIn('D04_VIOLATION_WRITE_SUCCEEDED', body)
                self.assertIn('D04_INVALID_UNKNOWN_TOOL_RESULT', body)
            else:
                self.assertIn('/bin/sleep 20', body)
                self.assertIn('D04_INVALID_NO_ACTIVE_JOB', body)

    def test_no_authorization_no_process(self):
        with patch.object(sys, 'argv', ['probe.py']), patch.object(probe.subprocess, 'Popen') as launch:
            with self.assertRaises(SystemExit):
                probe.main()
            launch.assert_not_called()

    def test_check_mode_no_process(self):
        with patch.object(sys, 'argv', ['probe.py', '--check']), patch.object(probe.subprocess, 'Popen') as launch:
            probe.main()
            launch.assert_not_called()


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(Guards)
    result = unittest.TextTestRunner().run(suite)
    (HERE / 'offline-checks.json').write_text(json.dumps(dict(
        status='OFFLINE_CHECKS_PASS_NOT_NATIVE_PROOF' if result.wasSuccessful() else 'FAIL',
        tests=result.testsRun, failures=len(result.failures), errors=len(result.errors),
        nativeSessions=0, realProviderCalls=0, productTestsRun=False), indent=2) + '\n')
    sys.exit(0 if result.wasSuccessful() else 1)
