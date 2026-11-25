"""Pure guard/payload checks; node runs only mocked JavaScript, never SDK/native/server."""
from pathlib import Path
import importlib.util
import json
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('candidate_probe', HERE / 'probe.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class GuardTests(unittest.TestCase):
    def test_approval_required(self):
        for auth in ({}, {'approved': True}, {'approved': True, 'approvedManifestSha256': 'old'}):
            with self.assertRaises(ValueError):
                p.check_authorization(auth, 'new')
        p.check_authorization({'approved': True, 'approvedManifestSha256': 'new'}, 'new')

    def test_legitimate_result_with_optional_error_field(self):
        p.verify_claude_result({'id': 'read', 'allowed': True}, {'read': {'content': 'random'}})

    def test_missing_result_is_not_success(self):
        with self.assertRaisesRegex(ValueError, 'missing'):
            p.verify_claude_result({'id': 'read', 'allowed': True}, {})

    def test_legitimate_refusal_stops(self):
        with self.assertRaisesRegex(ValueError, 'legitimate'):
            p.verify_claude_result({'id': 'read', 'allowed': True}, {'read': {'is_error': True}})

    def test_forbidden_success_stops(self):
        with self.assertRaisesRegex(ValueError, 'FAIL'):
            p.verify_claude_result({'id': 'write', 'allowed': False}, {'write': {'is_error': False}})

    def test_generic_error_does_not_prove_denial(self):
        with self.assertRaisesRegex(ValueError, 'not attributable'):
            p.verify_claude_result({'id': 'write', 'allowed': False}, {'write': {'is_error': True, 'content': 'missing file'}})
        p.verify_claude_result({'id': 'write', 'allowed': False}, {'write': {'is_error': True, 'content': 'permission denied'}})

    def test_named_profile_has_no_legacy_sandbox_override(self):
        cmd, profile = p.config_codex({'codex': '/pinned/native'}, Path('/fixture/work'),
                                     Path('/fixture/home'), Path('/fixture/tmp'), 'http://127.0.0.1:123')
        self.assertNotIn('-s', cmd)
        self.assertEqual(profile['/fixture/work'], 'write')
        self.assertEqual(profile['/fixture/work/.agent'], 'read')
        self.assertTrue(any('default_permissions=' in x for x in cmd))
        self.assertFalse(any('sandbox_workspace_write' in x for x in cmd))

    def test_child_has_positive_and_denial_without_recursion(self):
        ops = p.commands(Path('/fixture/work'), 'child')
        self.assertEqual([o[2] for o in ops], [True, True, False])
        self.assertNotIn('spawn_agent', p.codex_payload(Path('/fixture/work'), 'child')['input'])

    def test_claude_family_and_sequential_budget(self):
        calls = p.claude_calls(Path('/fixture/work'))
        self.assertEqual({c['name'] for c in calls}, {'Read', 'Write', 'Edit', 'Bash'})
        self.assertLessEqual(len(calls) + 1, 24)
        self.assertEqual(len({c['id'] for c in calls}), len(calls))
        self.assertEqual([c['id'] for c in calls if c['name'] == 'Edit'], ['edit_product', 'edit_control'])

    def test_guarded_tree_detects_alias_write_and_new_file(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / '.agent'; root.mkdir()
            f = root / 'record'; f.write_text('before')
            base = p.snapshot(root)
            alias = Path(d) / 'alias'; alias.symlink_to(f)
            alias.write_text('after')
            self.assertNotEqual(p.snapshot(root), base)
            f.write_text('before'); (root / 'new').write_text('BAD')
            self.assertNotEqual(p.snapshot(root), base)

    def test_sse_is_input_only(self):
        frame = p.claude_frames({'type': 'tool_use', 'id': 'x', 'name': 'Write',
                                 'input': {'file_path': '/fixture/file', 'content': 'test'}}, 1)
        self.assertIn(b'input_json_delta', frame)
        self.assertNotIn(b'tool_result', frame)

    def test_native_payload_fail_fast_with_fake_tools(self):
        node = json.loads((HERE / 'source-identity.json').read_text())['node']['path']
        code = p.codex_payload(Path('/fixture/work'), 'child')['input']
        harness = '''
const code=JSON.parse(process.argv[1]);
const F=Object.getPrototypeOf(async function(){}).constructor;
for(const mode of ['good','first_positive_fail','forbidden_success','unknown','generic_error']) {
 let count=0, failed=false;
 const tools={exec_command:async()=>{count++;
  if(mode==='first_positive_fail')return {exit_code:1,output:'permission denied'};
  if(mode==='unknown')return {session_id:1,output:''};
  if(count<3)return {exit_code:0,output:'LEGAL'};
  return mode==='forbidden_success'?{exit_code:0,output:''}:
   {exit_code:1,output:mode==='generic_error'?'missing file':'operation not permitted'};
 }};
 try {await new F('tools','text',code)(tools,()=>{});}catch(e){failed=true;}
 if(mode==='good'?(failed||count!==3):(!failed||count>(['unknown','first_positive_fail'].includes(mode)?1:3)))
  throw new Error('counterproof '+mode);
}
console.log('5 mock-only JS guard scenarios passed');
'''
        result = subprocess.run([node, '--input-type=module', '-e', harness, json.dumps(code)],
                                capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
