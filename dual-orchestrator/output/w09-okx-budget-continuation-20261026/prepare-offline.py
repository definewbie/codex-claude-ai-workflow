"""Create this case from fixed Git archive. Only Java start WITHOUT --run.
No native session, provider request, product modification or business decision.
"""
import json
import subprocess
import sys
import tarfile
import time
from pathlib import Path
import gate_support as gate
from admission import selected_environment
from check_budget import check


def main():
    auth = gate.read(gate.OUT / 'run-authorization.json')
    assert auth.get('offline_preparation_authorized') is True
    assert auth.get('real_run_authorized') is False and auth.get('budget_approved') is False
    assert not (gate.OUT / 'stop-required.json').exists(), 'Preparation already stopped'
    parent = gate.OUT / 'fixture/case'
    assert not parent.exists(), 'Never overwrite a prior fresh fixture'
    release = gate.frozen_check()
    launch = selected_environment()
    env, programs = launch['environment'], launch['executables']
    manifest = gate.read(gate.OUT / 'baseline-manifest.json')
    assert gate.sha(gate.OUT / 'source-baseline.tar') == manifest['archive_sha256']
    assert all(gate.sha(gate.OUT / 'fixture/baseline' / f) == h for f, h in manifest['files'].items())
    # The actual false runner guard is exercised before even creating a target.
    start = time.monotonic()
    false = subprocess.run([programs['rtk'], 'proxy', programs['python3'], '-B', str(gate.OUT/'run-one-step.py'),
        'unauthorized-offline', 'intake.claude_brainstorm'], cwd=gate.OUT, env=env, capture_output=True, text=True, timeout=10)
    gate.write(gate.OUT/'false-authorization-proof.json', {'exit':false.returncode,'elapsed_seconds':time.monotonic()-start,
        'stderr':false.stderr,'target_created':parent.exists(),'steps_created':(gate.OUT/'steps').exists(),'native_model_calls':0})
    assert false.returncode != 0 and 'Fresh Gate requires current human budget and run approval' in false.stderr
    assert not parent.exists() and not (gate.OUT/'steps').exists()
    proof = gate.read(gate.OUT/'false-authorization-proof.json')
    receipt = gate.OUT/'operations/authorization-guard'
    receipt.mkdir(parents=True)
    gate.write(receipt/'result.json', {'exit':proof['exit'],'elapsed_seconds':proof['elapsed_seconds'],
        'scope':'expected unapproved refusal; no Java/native execution'})
    parent.mkdir(parents=True)
    target, workspace = parent/'target', parent/'worktree'
    target.mkdir()
    with tarfile.open(gate.OUT/'source-baseline.tar') as archive:
        archive.extractall(target, filter='data')
    assert all(gate.sha(target/f)==h for f,h in manifest['files'].items())

    def command(label, args, cwd, timeout=30):
        folder = gate.OUT/'operations'/label
        folder.mkdir(parents=True, exist_ok=False)
        began = time.monotonic()
        gate.write(folder/'pre-state.json', {'argv':args,'cwd':str(cwd),'native_prompt_permitted':False})
        try:
            with (folder/'stdout.txt').open('w') as so, (folder/'stderr.txt').open('w') as se:
                child = subprocess.Popen([programs['rtk'], 'proxy']+args, cwd=cwd, env=env, stdout=so, stderr=se)
                gate.write(folder/'process.json', {'pid':child.pid,'argv':args})
                try:
                    code=child.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    child.terminate()
                    try:child.wait(timeout=5)
                    except subprocess.TimeoutExpired:child.kill();child.wait()
                    gate.write(folder/'result.json', {'exit':None,'timed_out':True,'elapsed_seconds':time.monotonic()-began})
                    raise
            gate.write(folder/'result.json', {'exit':code,'elapsed_seconds':time.monotonic()-began})
            assert code==0, (label,code,(folder/'stderr.txt').read_text()[-1000:])
            return (folder/'stdout.txt').read_text().strip()
        except Exception:
            gate.write(gate.OUT/'stop-required.json', {'reason':'OFFLINE_PREPARATION_INCOMPLETE','operation':label,
                'next_dispatch':'FORBIDDEN_PENDING_ASSESSMENT','native_model_call_started':False})
            raise

    git=programs['git']
    command('git-init',[git,'init','-b','main'],target)
    command('git-add',[git,'-c','core.autocrlf=false','-c','core.hooksPath=/dev/null','add','--all'],target)
    command('baseline-commit',[git,'-c','user.name=Dual Gate','-c','user.email=dual-gate@localhost',
        '-c','core.hooksPath=/dev/null','-c','commit.gpgSign=false','commit','-m','Fixed committed baseline for isolated Java MODIFY Gate'],target)
    tree=command('target-tree',[git,'rev-parse','HEAD^{tree}'],target)
    original_tree=command('source-committed-tree',[git,'rev-parse',manifest['source_head']+'^{tree}'],Path('/Users/kriswang/work/projects/ai/ai-crypto-arbitrage'))
    assert tree==original_tree, 'Baseline Git tree differs from fixed source commit'
    command('start',[programs['java'],'-jar',release['application_jars']['dual-cli']['path'],'start',
        '--workdir='+str(target),'--worktree-dir='+str(workspace),'--branch=codex/w09-okx-break-even-20261026',
        '--workflow-variant=terminal-delivery','--prompt-language=zh-CN','--task-file='+str(gate.OUT/'task-requirement.md')],target,120)
    ledgers=list((workspace/'.agent/workstreams').iterdir());assert len(ledgers)==1
    ledger=ledgers[0]
    location={'parent':str(parent),'target':str(target),'workspace':str(workspace),'workstream':ledger.name,'ledger':str(ledger)}
    gate.write(gate.OUT/'instance-location.json',location)
    assert all(gate.sha(workspace/f)==h for f,h in manifest['files'].items())
    for role,path in [('target',target),('workspace',workspace)]:
        assert not command(role+'-status',[git,'status','--porcelain','--untracked-files=all'],path)
        assert command(role+'-tree-confirm',[git,'rev-parse','HEAD^{tree}'],path)==tree
    current=gate.read(ledger/'workflow/current.json');workstream=gate.read(ledger/'workstream.json')
    assert current['status']=='RUNNING' and not current['openGateIds']
    assert not list(ledger.rglob('pending/*.json')) and not list((ledger/'runs').glob('*/run.json'))
    ident=workstream['frozenRuntimeIdentity']
    assert ident['executable.cli']==release['application_jars']['dual-cli']['sha256']
    assert ident['executable.server']==release['application_jars']['dual-server']['sha256']
    assert ident['kernel.effective']=='liteflow' and ident['behavior.env.DUAL_ACPX_COMMAND_TIMEOUT_SECONDS']=='600'
    gate.write(gate.OUT/'initial-ledger-sha256.json',{'files':{str(f.relative_to(ledger)):gate.sha(f) for f in ledger.rglob('*') if f.is_file() and f.name!='.mutation.lock'}})
    gate.write(gate.OUT/'initial-workflow-current.json',current)
    auth.update(workstream=ledger.name,initial_ledger_sha256=gate.sha(gate.OUT/'initial-ledger-sha256.json'))
    gate.write(gate.OUT/'run-authorization.json',auth)
    from admission import inspect
    _,measured,active,process,_=inspect()
    state=gate.planning('intake.claude_brainstorm',measured,active)
    admission=check(state);assert admission['result']=='ALLOW_BUDGET_ONLY'
    assert measured['actual_tokens']==0 and measured['completed_calls']==0
    gate.write(gate.OUT/'fresh-preparation-result.json',{'status':'OFFLINE_INITIALIZED_AWAITING_REVIEW_AND_APPROVAL','location':location,
        'baseline_git_tree':tree,'current':current,'runtime_identity':ident,'budget_input':state,'budget_result':admission,
        'process_observation':process,'model_calls':0,'native_sessions_created':0,'business_approvals':0,'real_run_authorized':False})
    print(json.dumps({'offline_initialization':'PASS','workstream':ledger.name,'baseline_files':len(manifest['files']),
        'budget_reserve':admission['required_token_reserve'],'native_calls':0,'real_run_authorized':False}))


if __name__=='__main__':main()
