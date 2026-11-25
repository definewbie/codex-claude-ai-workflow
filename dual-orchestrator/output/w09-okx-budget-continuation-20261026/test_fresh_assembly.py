"""Offline assembly fixtures. Fake Java + synthetic usage are NOT native E2E."""
import copy
import hashlib
import json
import os
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import admission as a
import gate_support as g
import check_budget as budget

HERE = Path(__file__).resolve().parent

class FreshAssemblyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.out=self.root/'package';self.out.mkdir()
        self.ws=self.root/'worktree';self.ledger=self.ws/'.agent/workstreams/ws-fixture';self.ledger.mkdir(parents=True)
        self.target=self.root/'target';self.target.mkdir();self.bin=self.root/'bin';self.bin.mkdir()
        self.java=self.bin/'java'
        self.java.write_text('#!'+sys.executable+'\nimport json,os,pathlib\np=pathlib.Path(os.environ["TEST_LEDGER"])/"nodes/n.json"\nx=json.loads(p.read_text());x["status"]="COMPLETED";p.write_text(json.dumps(x))\npathlib.Path(os.environ["TEST_MARKER"]).write_text(os.environ["PATH"])\n')
        self.java.chmod(0o700)
        real=json.loads((HERE/'local-launch-selection.json').read_text())['executables']
        programs={}
        for n,v in real.items():
            f=self.bin/n
            if n!='java':f.symlink_to(Path(sys.executable) if n=='python3' else Path(v))
            programs[n]=str(f)
        self.marker=self.root/'launched'
        env={'PATH':str(self.bin),'DUAL_ACPX_EXECUTABLE':programs['acpx'],'TEST_MARKER':str(self.marker),'TEST_LEDGER':str(self.ledger)}
        self.put('local-launch-selection.json',{'executables':programs,'explicit_child_environment':env})
        self.put('host-environment-identity.json',{'dependencies':[]})
        self.put('instance-location.json',{'workspace':str(self.ws),'target':str(self.target),'ledger':str(self.ledger),'workstream':'ws-fixture'})
        (self.ledger/'workflow').mkdir();(self.ledger/'nodes').mkdir()
        self.current={'workstreamId':'ws-fixture','status':'RUNNING','openGateIds':[],'activeNodeInstanceIds':['n']}
        self.write(self.ledger/'workflow/current.json',self.current)
        self.write(self.ledger/'nodes/n.json',{'nodeInstanceId':'n','nodeDefinitionId':'intake.claude_brainstorm','status':'READY'})
        self.write(self.ledger/'workstream.json',{'frozenRuntimeIdentity':{'executable.cli':'jarhash','executable.server':'serverhash','kernel.effective':'liteflow','behavior.env.DUAL_ACPX_COMMAND_TIMEOUT_SECONDS':'600'}})
        self.put('initial-ledger-sha256.json',{'files':{str(f.relative_to(self.ledger)):g.sha(f) for f in self.ledger.rglob('*') if f.is_file()}})
        self.input=self.root/'input.txt';self.input.write_text('fixed')
        self.jar=self.root/'jar';self.jar.write_text('jar')
        self.dist=self.root/'dist';self.dist.write_text('dist')
        release={'source_inputs':{'input.txt':g.sha(self.input)},'application_jars':{'dual-cli':{'path':str(self.jar),'sha256':'jarhash'},'dual-server':{'path':str(self.jar),'sha256':'serverhash'}},'selected_acpx':{'wrapper':programs['acpx'],'wrapper_sha256':g.sha(programs['acpx']),'dist':str(self.dist),'dist_sha256':g.sha(self.dist)},'metadata_config_source_files':{}}
        # Actual byte hashes for frozen_check; runtime identity uses same values.
        release['application_jars']['dual-cli']['sha256']=g.sha(self.jar);release['application_jars']['dual-server']['sha256']=g.sha(self.jar)
        w=g.read(self.ledger/'workstream.json');w['frozenRuntimeIdentity'].update({'executable.cli':g.sha(self.jar),'executable.server':g.sha(self.jar)});self.write(self.ledger/'workstream.json',w)
        self.put('initial-ledger-sha256.json',{'files':{str(f.relative_to(self.ledger)):g.sha(f) for f in self.ledger.rglob('*') if f.is_file()}})
        self.put('selected-release.json',release)
        self.put('launch-identity.json',{'config_and_installed_sources':{str(Path(v).resolve()):g.sha(v) for v in programs.values()},'observer_inputs':{'selected-release.json':g.sha(self.out/'selected-release.json')},'client_environment_overrides':{}})
        self.put('budget.json',g.read(HERE/'budget.json'))
        self.auth={'case':'OKX_JAVA_MODIFY_CANDIDATE','real_run_authorized':True,'budget_approved':True,'workstream':'ws-fixture','initial_ledger_sha256':g.sha(self.out/'initial-ledger-sha256.json'),'budget_sha256':g.sha(self.out/'budget.json'),'approved_launch_identity_sha256':g.sha(self.out/'launch-identity.json'),'business_gates_delegated':False,'commit_delegated':False,'approval_ref':'approval.txt'}
        (self.out/'approval.txt').write_text('TEST ONLY synthetic approval, not a user grant')
        self.put('run-authorization.json',self.auth)
        self.original=self.root/'original';self.original.mkdir()
        self.write(self.original/'stop-required.json',{'reason':'budget stop'})
        self.put('original-package-sha256.json',{str(self.original/'stop-required.json'):g.sha(self.original/'stop-required.json')})
        self.put('continuation-checkpoint.json',g.read(self.out/'initial-ledger-sha256.json'))
        self.put('inherited-accounting.json',{'actual_tokens':0,'completed_calls':0,'active_seconds':0,'time_items':[]})
        self.put('closed-session-observations.json',[])
        spec={'original_package':str(self.original),'original_stop_sha256':g.sha(self.original/'stop-required.json'),'checkpoint_sha256':g.sha(self.out/'continuation-checkpoint.json'),'token_limit':4000000,'call_limit':18,'active_seconds_limit':3600,'budget_sha256':g.sha(self.out/'budget.json')}
        self.put('continuation-spec.json',spec)
        self.auth.update(continuation_run_authorized=True,continuation_spec_sha256=g.sha(self.out/'continuation-spec.json'))
        self.put('run-authorization.json',self.auth)
        for target,value in [('OUT',self.out),('ROOT',self.root),('RELEASE',self.out/'selected-release.json')]:
            c=patch.object(g,target,value);c.start();self.addCleanup(c.stop)

    def write(self,p,d):p.write_text(json.dumps(d))
    def put(self,n,d):self.write(self.out/n,d)
    def run_step(self):
        with patch.object(sys,'argv',[str(HERE/'run-one-step.py'),'s1','intake.claude_brainstorm']):
            runpy.run_path(str(HERE/'run-one-step.py'),run_name='__main__')
    def test_positive_whole_runner_same_environment(self):
        # Seam only for post-call measurement; all admission/launch/receipt code is real.
        empty=g.measure(self.ws,self.ledger)
        measured={'actual_tokens':32,'completed_calls':1,'completed_runs':[],'measurement_complete':True}
        with patch.object(g,'measure',side_effect=[empty,empty,measured]),patch.dict(os.environ,{'PATH':'/parent-is-not-authority'}):self.run_step()
        self.assertEqual(str(self.bin),self.marker.read_text())
        self.assertEqual(0,g.read(self.out/'steps/s1/result.json')['exit'])
        self.assertEqual(2365000,g.read(self.out/'steps/s1/budget-admission.json')['result']['required_token_reserve'])
    def test_false_auth_blocks_before_instance_or_step(self):
        self.put('run-authorization.json',dict(self.auth,real_run_authorized=False));(self.out/'instance-location.json').unlink()
        with self.assertRaisesRegex(AssertionError,'current human'):self.run_step()
        self.assertFalse((self.out/'steps').exists());self.assertFalse(self.marker.exists())
    def test_wrong_subject_budget_launch_and_delegation(self):
        for key,val in [('workstream','old-ws'),('case','old-case'),('initial_ledger_sha256','old'),('budget_sha256','old'),('approved_launch_identity_sha256','old'),('business_gates_delegated',True),('commit_delegated',True)]:
            self.put('run-authorization.json',dict(self.auth,**{key:val}))
            with self.assertRaises(AssertionError):a.authorize()
    def test_new_stop_rejects(self):
        self.put('stop-required.json',{'reason':'test'})
        with self.assertRaisesRegex(AssertionError,'stopped'):a.inspect()
    def test_fixed_input_drift_rejects(self):
        self.input.write_text('changed')
        with self.assertRaisesRegex(AssertionError,'Frozen source'):a.inspect()
    def test_observer_byte_drift_rejects(self):
        d=g.read(self.out/'selected-release.json');d['extra']='changed';self.put('selected-release.json',d)
        with self.assertRaisesRegex(AssertionError,'Observer/Gate'):a.inspect()
    def test_ledger_checkpoint_and_terminal_reject(self):
        self.write(self.ledger/'workflow/current.json',dict(self.current,status='COMPLETED'))
        with self.assertRaisesRegex(AssertionError,'terminal'):a.inspect()
        self.write(self.ledger/'workflow/current.json',dict(self.current,other='drift'))
        with self.assertRaisesRegex(AssertionError,'ledger drift'):a.inspect()
    def test_pending_write_rejects(self):
        d=self.ledger/'pending';d.mkdir();self.write(d/'x.json',{})
        with self.assertRaisesRegex(AssertionError,'Pending'):a.inspect()
    def test_partial_step_and_process_without_result_reject(self):
        d=self.out/'steps/s1';d.mkdir(parents=True)
        with self.assertRaisesRegex(AssertionError,'Incomplete step'):a.inspect()
        self.write(d/'process.json',{'pid':1})
        with self.assertRaisesRegex(AssertionError,'Unfinished local'):a.inspect()
    def test_time_accumulates_and_failed_or_missing_time_rejects(self):
        d=self.out/'operations/one';d.mkdir(parents=True);self.write(d/'result.json',{'exit':0,'elapsed_seconds':12.5})
        self.assertEqual(12.5,a.active_seconds())
        for r in [{'exit':None,'elapsed_seconds':12.5},{'exit':0,'elapsed_seconds':-1},{'exit':0}]:
            self.write(d/'result.json',r)
            with self.assertRaises(AssertionError):a.active_seconds()
    def test_unknown_usage_blocks_before_launch(self):
        with patch.object(g,'measure',side_effect=AssertionError('unattributable')):
            with self.assertRaisesRegex(AssertionError,'unattributable'):self.run_step()
        self.assertFalse((self.out/'steps').exists());self.assertFalse(self.marker.exists())
    def test_missing_selected_program_rejects(self):
        self.java.unlink()
        with self.assertRaises((AssertionError, OSError)):a.inspect()
    def test_selected_program_disappears_after_inspection_leaves_stop(self):
        inspect=a.inspect
        def disappear():
            value=inspect();self.java.unlink();return value
        with patch.object(a,'inspect',side_effect=disappear):
            with self.assertRaises(AssertionError):self.run_step()
        self.assertTrue((self.out/'stop-required.json').exists());self.assertFalse(self.marker.exists())
    def test_cold_authorization_rejects_without_side_effects(self):
        self.put('run-authorization.json',dict(self.auth,real_run_authorized=False))
        script='import sys;sys.path.insert(0,'+repr(str(HERE))+');import admission,gate_support as g;from pathlib import Path;g.OUT=Path('+repr(str(self.out))+');admission.authorize()'
        c=subprocess.run([sys.executable,'-B','-c',script],capture_output=True,text=True)
        self.assertNotEqual(0,c.returncode);self.assertIn('current human',c.stderr);self.assertFalse(self.marker.exists())

    def test_rtk_disappears_after_inspection_leaves_unknown_start_stop(self):
        inspect=a.inspect
        def disappear():
            value=inspect();(self.bin/'rtk').unlink();return value
        with patch.object(a,'inspect',side_effect=disappear):
            with self.assertRaises(OSError):self.run_step()
        r=g.read(self.out/'steps/s1/result.json')
        self.assertIsNone(r['exit']);self.assertEqual('NOT_CONFIRMED',r['external_start'])
        self.assertTrue((self.out/'stop-required.json').exists());self.assertFalse(self.marker.exists())
    def test_time_reserve_stop_before_java(self):
        d=self.out/'operations/one';d.mkdir(parents=True);self.write(d/'result.json',{'exit':0,'elapsed_seconds':2200})
        with self.assertRaises(AssertionError):self.run_step()
        self.assertEqual('STOP',g.read(self.out/'steps/s1/budget-admission.json')['result']['result'])
        self.assertFalse(self.marker.exists())
    def test_cold_checkpoint_drift_rejects(self):
        self.write(self.ledger/'nodes/n.json',{'changed':True})
        script='import sys;sys.path.insert(0,'+repr(str(HERE))+');import admission,gate_support as g;from pathlib import Path;g.OUT=Path('+repr(str(self.out))+');g.ROOT=Path('+repr(str(self.root))+');g.RELEASE=g.OUT/"selected-release.json";admission.inspect()'
        c=subprocess.run([sys.executable,'-B','-c',script],capture_output=True,text=True)
        self.assertNotEqual(0,c.returncode);self.assertIn('Initial ledger drift',c.stderr);self.assertFalse(self.marker.exists())

    def test_old_stop_cannot_change(self):
        self.write(self.original/'stop-required.json',{'reason':'changed'})
        with self.assertRaises(AssertionError):self.run_step()
        self.assertFalse(self.marker.exists())
    def test_new_stop_refuses_current_authorization(self):
        self.put('stop-required.json',{'reason':'new stop'})
        with self.assertRaises(AssertionError):self.run_step()
        self.assertFalse(self.marker.exists())
    def test_continuation_must_be_explicit(self):
        self.put('run-authorization.json',dict(self.auth,continuation_run_authorized=False))
        with self.assertRaises(AssertionError):self.run_step()
        self.assertFalse(self.marker.exists())
    def test_inherited_time_cannot_reset(self):
        self.write(self.original/'time.json',{'elapsed_seconds':688})
        self.put('inherited-accounting.json',{'actual_tokens':0,'completed_calls':0,'active_seconds':0,'time_items':[{'ref':str(self.original/'time.json'),'sha256':g.sha(self.original/'time.json'),'elapsed_seconds':688}]})
        with self.assertRaisesRegex(AssertionError,'reset'):a.active_seconds()
    def test_inherited_usage_cannot_disappear(self):
        self.put('inherited-accounting.json',{'actual_tokens':986830,'completed_calls':3,'active_seconds':0,'time_items':[]})
        with self.assertRaisesRegex(AssertionError,'usage lost'):a.inspect()
    def test_reserve_old_stop_and_current_allowance(self):
        state={'actual_tokens':986830,'completed_calls':3,'active_seconds':688,'remaining_mainline':[s['node'] for s in g.read(HERE/'budget.json')['stages'][3:]],'new_extra_calls':[],'measurement_complete':True,'external_pending':False}
        result=budget.check(state)
        self.assertEqual('ALLOW_BUDGET_ONLY',result['result']);self.assertEqual(2125000,result['required_token_reserve'])
        self.assertEqual(3013170,result['remaining_tokens'])

if __name__=='__main__':unittest.main()
