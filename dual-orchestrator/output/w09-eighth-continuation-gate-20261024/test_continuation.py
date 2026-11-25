"""Full operator entrypoints with temporary files and an explicit Java process double.

No Java/native/provider is started. The measured-usage seam is synthetic here;
test_gate_observer and the real historical read-only preflight test its real reader.
"""
import contextlib
import copy
import hashlib
import io
import json
import os
import runpy
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import continuation as c
import gate_support as g
from check_budget import check
from process_check import ProcessInspectionError

PACKAGE = Path(__file__).resolve().parent
REAL_RUN = subprocess.run


class AssemblyTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.out = self.root / 'output/package'; self.out.mkdir(parents=True)
        self.ledger = self.root / 'workspace/.agent/ws'; self.ledger.mkdir(parents=True)
        self.workspace = self.ledger.parents[1]
        self.original = self.root / 'original'; self.original.mkdir()
        self.stack = contextlib.ExitStack(); self.addCleanup(self.stack.close)
        for name, value in [('OUT', self.out), ('ROOT', self.root), ('RELEASE', self.out / 'selected-release.json')]:
            self.stack.enter_context(patch.object(g, name, value))
        self.measured = {'actual_tokens':848242, 'completed_calls':10, 'measurement_complete':True,
                         'completed_runs':[{'node_id':s['node'], 'outcome':{'nodeMaySucceed':True}}
                                           for s in g.read(PACKAGE/'budget.json')['stages'][:6]]}
        self.stack.enter_context(patch.object(g, 'measure', side_effect=lambda *a, **k:copy.deepcopy(self.measured)))
        self.stack.enter_context(patch('process_check.subprocess.run', side_effect=AssertionError('No actual OS/native calls in assembly fixtures')))
        self.write(self.out/'budget.json', g.read(PACKAGE/'budget.json'))
        jar = self.root/'java.fixture'; jar.write_text('not an executable')
        self.write(g.RELEASE, {'source_inputs':{},'application_jars':{
            'dual-cli':{'path':str(jar),'sha256':g.sha(jar)},'dual-server':{'path':str(jar),'sha256':g.sha(jar)}},
            'metadata_config_source_files':{},'selected_acpx':{'wrapper':str(jar),'dist':str(jar),
            'wrapper_sha256':g.sha(jar),'dist_sha256':g.sha(jar)}})
        self.write(self.original/'stop-required.json', {'experiment':'INVALID'})
        self.write(self.out/'original-package-sha256.json', {str(self.original/'stop-required.json'):g.sha(self.original/'stop-required.json')})
        self.write(self.out/'instance-location.json', {'workspace':str(self.workspace),'ledger':str(self.ledger),'workstream':'ws'})
        self.write(self.out/'local-launch-selection.json', {'process_path_sha256':hashlib.sha256(os.environ.get('PATH','').encode()).hexdigest(),'executables':{},'explicit_child_environment':{}})
        self.write(self.out/'host-environment-identity.json', {'dependencies':[]})
        self.write(self.ledger/'workflow/current.json', {'status':'RUNNING','workstreamId':'ws','activeNodeInstanceIds':['n'],'openGateIds':[]})
        self.write(self.ledger/'nodes/n.json', {'nodeInstanceId':'n','nodeDefinitionId':'plan.decision','status':'READY'})
        self.write(self.ledger/'workstream.json', {'frozenRuntimeIdentity':{'executable.cli':g.sha(jar),'executable.server':g.sha(jar),'kernel.effective':'liteflow','behavior.env.DUAL_ACPX_COMMAND_TIMEOUT_SECONDS':'600'}})
        self.write(self.ledger/'runs/historical/run.json', {'fixed':'old immutable execution'})
        self.write(self.out/'checkpoint-ledger-sha256.json', {str(f.relative_to(self.ledger)):g.sha(f) for f in self.ledger.rglob('*') if f.is_file()})
        spec={'case':'EIGHTH_CONTINUATION','original_package':str(self.original),'workstream':'ws',
              'checkpoint_sha256':g.sha(self.out/'checkpoint-ledger-sha256.json'),
              'original_stop_sha256':g.sha(self.original/'stop-required.json'),'preserve_original_stop':True,
              'token_limit':1700000,'call_limit':18,'active_seconds_limit':3600}
        self.write(self.out/'continuation-spec.json', spec)
        self.auth={k:spec[k] for k in ['case','workstream','checkpoint_sha256','token_limit','call_limit','active_seconds_limit']}
        self.auth.update(continuation_run_authorized=False,approval_ref=None,business_gates_delegated=False)
        self.write(self.out/'run-authorization.json', self.auth)
        self.write(self.original/'time.json', {'elapsed_seconds':1263.444})
        self.write(self.out/'inherited-accounting.json', {'actual_tokens':848242,'completed_calls':10,'active_seconds':1263.444,
                   'time_items':[{'ref':str(self.original/'time.json'),'sha256':g.sha(self.original/'time.json'),'elapsed_seconds':1263.444}]})
        self.write(self.out/'absence.json', {'status':'ABSENT','ps_exit':0,'checked_pids':[99]})
        self.write(self.out/'old-execution-recheck.json', {'known_pids':[{'pid':99,'absent':True,'observation_ref':'absence.json','observation_sha256':g.sha(self.out/'absence.json')}],'sessions':[]})
        self.write(self.out/'launch-identity.json', {'config_and_installed_sources':{},'observer_inputs':{
            'old-execution-recheck.json':g.sha(self.out/'old-execution-recheck.json')}})

    def write(self, path, value):
        path.parent.mkdir(parents=True,exist_ok=True);g.write(path,value)

    def allow(self):
        self.auth.update(continuation_run_authorized=True,approval_ref='approval.txt')
        (self.out/'approval.txt').write_text('SYNTHETIC TEST AUTHORIZATION ONLY')
        self.write(self.out/'run-authorization.json', self.auth)

    def run_entry(self, filename, args=()):
        with patch.object(sys,'argv',[filename,*args]), contextlib.redirect_stdout(io.StringIO()):
            return runpy.run_path(str(PACKAGE/filename),run_name='__main__')

    def process_double(self, code=0):
        owner=self
        class Double:
            pid=12345
            def __init__(self,*args,**kwargs):
                owner.started=getattr(owner,'started',0)+1
                owner.argv=args[0]
            def wait(self,timeout=None):
                node=g.read(owner.ledger/'nodes/n.json');node['status']='COMPLETED'
                owner.write(owner.ledger/'nodes/n.json',node)
                return code
        return Double

    def test_whole_runner_denies_without_approval_before_any_step_or_process(self):
        with patch('subprocess.Popen') as process:
            with self.assertRaisesRegex(AssertionError,'new human run approval'):
                self.run_entry('run-one-step.py',['first','plan.decision'])
            process.assert_not_called()
        self.assertFalse((self.out/'steps').exists())

    def test_wrong_subject_limit_or_missing_evidence_denies(self):
        self.allow()
        for key,value in [('workstream','other'),('checkpoint_sha256','bad'),('token_limit',2000000),('approval_ref','missing')]:
            original=copy.deepcopy(self.auth);self.auth[key]=value
            self.write(self.out/'run-authorization.json',self.auth)
            with self.subTest(key=key),self.assertRaises(AssertionError):c.authorize()
            self.auth=original

    def test_complete_readonly_entry_does_not_dispatch_or_reset_usage(self):
        with patch('subprocess.Popen') as process:
            self.run_entry('pre-dispatch-recheck.py')
            process.assert_not_called()
        receipt=g.read(next((self.out/'preflight-receipts').glob('*/result.json')))
        self.assertEqual(848242,receipt['budget_input']['actual_tokens'])
        self.assertEqual(10,receipt['budget_input']['completed_calls'])
        self.assertEqual(600000,receipt['budget_result']['required_token_reserve'])
        self.assertGreaterEqual(c.active_seconds(),1263.444)

    def test_whole_runner_reaches_original_java_boundary_with_cumulative_time(self):
        self.allow()
        with patch('subprocess.Popen',self.process_double()):
            self.run_entry('run-one-step.py',['first','plan.decision'])
        self.assertEqual(1,self.started)
        self.assertIn('java',self.argv);self.assertIn('--max-steps',self.argv)
        pre=g.read(self.out/'steps/first/pre-state.json')
        self.assertEqual(1263.444,pre['prior_active_seconds'])
        self.assertEqual('COMPLETED',g.read(self.out/'steps/first/post-node.json')['status'])
        self.assertTrue((self.original/'stop-required.json').exists())
        # Cold re-import is not needed to reset counters: durable receipts own time.
        self.assertGreaterEqual(c.active_seconds(),1263.444)
        with self.assertRaises(AssertionError):self.run_entry('run-one-step.py',['first','plan.decision'])

    def test_original_stop_checkpoint_and_pending_refuse_before_process(self):
        self.allow()
        scenarios=[self.original/'stop-required.json',self.ledger/'workflow/current.json',self.ledger/'pending/x.json']
        for f in scenarios:
            before=f.read_bytes() if f.exists() else None
            f.parent.mkdir(parents=True,exist_ok=True);f.write_text('{}')
            with self.subTest(path=f),patch('subprocess.Popen') as process:
                with self.assertRaises(AssertionError):self.run_entry('run-one-step.py',['first','plan.decision'])
                process.assert_not_called()
            if before is None:f.unlink()
            else:f.write_bytes(before)

    def test_new_stop_marker_is_not_bypassed_by_original_invalid_exception(self):
        self.allow();self.write(self.out/'stop-required.json',{'reason':'new boundary failure'})
        with patch('subprocess.Popen') as process:
            with self.assertRaises(AssertionError):self.run_entry('run-one-step.py',['first','plan.decision'])
            process.assert_not_called()

    def test_tampered_or_unlinked_process_confirmation_refuses(self):
        for filename in ['old-execution-recheck.json','absence.json']:
            f=self.out/filename;before=f.read_bytes();f.write_bytes(before+b' ')
            with self.subTest(filename=filename),self.assertRaises(AssertionError):c.inspect()
            f.write_bytes(before)

    def test_unresolved_present_and_unknown_stop_whole_runner(self):
        self.allow()
        # Strict-reader status propagation across the full runner. The strict
        # reader itself is exercised by the separate reviewed 17-case suite.
        for status in ['PRESENT','UNKNOWN']:
            with self.subTest(status=status),patch.object(c,'require_unresolved_pids_absent',side_effect=ProcessInspectionError(status,'synthetic strict-reader result')):
                with patch('subprocess.Popen') as process:
                    with self.assertRaises(ProcessInspectionError):self.run_entry('run-one-step.py',['first','plan.decision'])
                    process.assert_not_called()

    def test_missing_or_decreased_historical_usage_refuses(self):
        for k,value in [('actual_tokens',0),('completed_calls',0)]:
            before=self.measured[k];self.measured[k]=value
            with self.subTest(field=k),self.assertRaises(AssertionError):c.inspect()
            self.measured[k]=before

    def test_budget_and_time_tail_remain_protected(self):
        for tokens,calls,seconds,expected in [(848242,10,1263.444,'ALLOW_BUDGET_ONLY'),(1250000,10,1263.444,'STOP'),(848242,17,1263.444,'STOP'),(848242,10,2101,'STOP')]:
            state=g.planning('implement.claude_implement',self.measured,seconds)
            state.update(actual_tokens=tokens,completed_calls=calls)
            self.assertEqual(expected,check(state)['result'])

    def test_incomplete_execution_or_receipt_drift_stops_cold_replay(self):
        self.write(self.out/'steps/interrupted/process.json',{'pid':12345})
        with self.assertRaisesRegex(AssertionError,'Unfinished local operation'):c.active_seconds()
        (self.out/'steps/interrupted/process.json').unlink()
        self.write(self.out/'steps/interrupted/result.json',{'elapsed_seconds':3,'exit':0})
        self.assertEqual(1266.444,c.active_seconds())
        (self.original/'time.json').write_text('{}')
        with self.assertRaisesRegex(AssertionError,'time receipt drift'):c.active_seconds()

    def test_write_failure_before_and_after_external_start_cannot_auto_retry(self):
        self.allow();normal=g.write
        for suffix,expected_starts in [('pre-state.json',0),('process.json',1)]:
            folder=self.out/'steps'/suffix.replace('.json','')
            def failing(path,value):
                if Path(path).name==suffix:raise OSError('Injected durable write failure')
                normal(path,value)
            self.started=0
            with self.subTest(point=suffix),patch.object(g,'write',side_effect=failing),patch('subprocess.Popen',self.process_double()):
                with self.assertRaises(OSError):self.run_entry('run-one-step.py',[folder.name,'plan.decision'])
            self.assertEqual(expected_starts,self.started)
            with self.assertRaises(AssertionError):c.active_seconds()
            import shutil
            shutil.rmtree(folder)

    def test_cold_whole_runner_and_later_historical_drift(self):
        self.allow()
        measured=self.out/'synthetic-measured.json';self.write(measured,self.measured)
        child=f'''import json,sys,runpy,subprocess
from pathlib import Path
sys.path.insert(0,{str(PACKAGE)!r})
import gate_support as g
g.OUT=Path({str(self.out)!r});g.ROOT=Path({str(self.root)!r});g.RELEASE=g.OUT/'selected-release.json'
g.measure=lambda *a,**k:g.read(g.OUT/'synthetic-measured.json')
def forbidden(*a,**k):raise AssertionError('No actual native or OS command in cold fixture')
subprocess.run=forbidden
class ProcessDouble:
    pid=12345
    def __init__(self,*a,**k):pass
    def wait(self,timeout=None):
        ledger=Path(g.read(g.OUT/'instance-location.json')['ledger'])
        node=g.read(ledger/'nodes/n.json');node['status']='COMPLETED';g.write(ledger/'nodes/n.json',node)
        return 0
subprocess.Popen=ProcessDouble
sys.argv=['run-one-step.py',sys.argv[1],'plan.decision']
runpy.run_path({str(PACKAGE/'run-one-step.py')!r},run_name='__main__')
'''
        first=REAL_RUN([sys.executable,'-B','-c',child,'cold'],capture_output=True,text=True)
        self.assertEqual(0,first.returncode,first.stderr)
        self.assertTrue((self.out/'steps/cold/result.json').exists())
        self.assertGreaterEqual(c.active_seconds(),1263.444)
        (self.ledger/'runs/historical/run.json').write_text('changed historical bytes')
        second=REAL_RUN([sys.executable,'-B','-c',child,'after-drift'],capture_output=True,text=True)
        self.assertNotEqual(0,second.returncode)
        self.assertIn('Historical execution changed',second.stderr)
        self.assertFalse((self.out/'steps/after-drift').exists())


if __name__=='__main__':unittest.main()
