"""Execute only approved offline preparation; not a run/approval command."""
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import tomllib
from pathlib import Path
from gate_support import OUT, ROOT, read, write, sha, frozen_check, same_config_with_fixture_registration

OLD = ROOT/'output/w09-fresh-sixth-gate-20261015'
FIX = ROOT/'output/w09-sixth-forward-fix-20261015'
parent = Path('/private/tmp/dual-w09-newlines-20261016-fresh-07')
auth = read(OUT/'run-authorization.json')
assert auth['offline_preparation_authorized'] and not auth['fresh_run_authorized']
assert not parent.exists(), 'Fresh directory conflict; never overwrite/reinitialize'
release = frozen_check()
assert len(release['source_inputs']) == 659
write(OUT/'before-source-sha256.json', {p:sha(ROOT/p) for p in release['source_inputs']})
def preserve():
    result = {}
    for directory in [OLD,FIX]:
        manifest = read(directory/'material-sha256.json')
        manifest = manifest.get('files',manifest)
        assert all(sha(directory/p)==h for p,h in manifest.items()), directory
        result[directory.name] = dict(files=len(manifest),unchanged=True,
            manifest_sha256=sha(directory/'material-sha256.json'))
    ids = read(OLD/'stopped-ledger-identity.json')['files']
    ledger = Path(read(OLD/'instance-location.json')['ledger'])
    assert all(sha(ledger/p)==h for p,h in ids.items())
    result['sixth_live_ledger'] = dict(files=len(ids),unchanged=True,lock_excluded=True,recovered=False)
    return result
write(OUT/'historical-before.json',preserve())
baseline = read(OLD/'launch-identity.json')['config_and_installed_sources']
selected, differences = {}, []
for p, h in baseline.items():
    assert Path(p).is_file(), 'Selected input missing: '+p
    current = sha(p)
    if current != h:
        target = Path(read(OLD/'instance-location.json')['target'])
        assert p == str(Path.home()/'.codex/config.toml')
        assert same_config_with_fixture_registration(Path(p).read_text(),h,target), 'Unexpected config difference'
        differences.append(dict(path=p,old_sha256=h,current_sha256=current,
            difference='Only prior sixth fixture same trusted registration; every other byte equal',
            previous_target=str(target),new_settings_or_model_change=False))
    selected[p] = current
write(OUT/'configuration-differences.json',dict(status='CHECKED',differences=differences,
    new_configuration_changes=False,new_native_execution_not_proven=True))
env = os.environ.copy()
explicit = dict(DUAL_ACPX_EXECUTABLE=release['selected_acpx']['wrapper'],
    DUAL_ACPX_COMMAND_TIMEOUT_SECONDS='600',DUAL_FLOW_KERNEL='liteflow',
    DUAL_OPERATOR_REGISTRY=str(parent/'operator-workspaces.json'))
env.update(explicit)
write(OUT/'local-launch-selection.json',dict(explicit_child_environment=explicit,
    real_run_authorized=False,persistent_environment_changed=False,
    executables={n:shutil.which(n) for n in ['java','python3','node','git','rtk','claude','codex','acpx']},
    process_path_sha256=hashlib.sha256(env.get('PATH','').encode()).hexdigest()))
operations = []
def command(label,args,cwd=ROOT,expected=0):
    start = time.monotonic()
    cp = subprocess.run(['rtk','proxy']+args,cwd=cwd,env=env,capture_output=True,text=True,timeout=120)
    (OUT/(label+'.stdout.txt')).write_text(cp.stdout)
    (OUT/(label+'.stderr.txt')).write_text(cp.stderr)
    operations.append(dict(label=label,argv=args,cwd=str(cwd),exit=cp.returncode,
        expected_exit=expected,elapsed_seconds=round(time.monotonic()-start,3)))
    write(OUT/'local-operations.json',operations)
    assert cp.returncode == expected, (label,cp.returncode,cp.stderr[-800:])
    return cp.stdout

command('observer-tests',[sys.executable,'-B','-m','unittest','-v','test_gate_observer'],OUT)
command('budget-check',[sys.executable,'-B','check_budget.py','--self-check'],OUT)
# This actual invocation must die at the first authorization check, before it
# can read instance state or create a step. It cannot call Java/native clients.
command('unauthorized-runner',[sys.executable,'-B','run-one-step.py','denied-offline','intake.claude_brainstorm'],OUT,1)
assert 'Fresh seventh requires new human authorization' in (OUT/'unauthorized-runner.stderr.txt').read_text()
assert not (OUT/'steps').exists() and not parent.exists()
write(OUT/'authorization-guard-proof.json',dict(status='PASS_EXPECTED_REJECTION',
    authorization=False,exit=1,steps_created=False,target_created=False,provider_calls=0))
write(OUT/'observer-offline-verification.json',dict(status='PASS',observer_tests=12,budget_cases=13,
    native_provider_calls=0,runner_is_not_production_guard=True,authorization_negative='PASS',
    observer_changes='Byte-identical accepted mixed-transcript candidate; no parser changes'))

checks = []
prior_checks = read(OLD/'old-execution-recheck.json')['sessions']
for entry in prior_checks + read(OLD/'stop-session-proof.json')['operations']:
    record = entry.get('record_id')
    if record:
        p = Path.home()/'.acpx/sessions'/(record+'.json')
    else:
        # First two case summaries did not retain record_id; select exact name.
        matches = [p for p in (Path.home()/'.acpx/sessions').glob('*.json')
                   if read(p).get('name')==entry['name']]
        assert len(matches)==1, 'Ambiguous prior named session'
        p = matches[0]
    meta = read(p)
    assert meta['closed'] is True and meta['name']==entry['name'], 'Known old session is not closed'
    checks.append(dict(name=meta['name'],record_id=p.stem,closed=True,
        metadata_sha256=sha(p),observation='Existing local metadata read only; no session command'))
pids = {r['pid'] for r in read(OLD/'known-process-stop-observation.json')['checks']}
pids.update(r['pid'] for r in read(OLD/'old-execution-recheck.json')['known_pids'])
pid_checks = []
for pid in sorted(pids):
    cp = subprocess.run(['rtk','proxy','ps','-p',str(pid),'-o','pid=,lstart=,command='],capture_output=True,text=True)
    assert cp.returncode==1 and not cp.stdout.strip(), ('Known PID present; inspect',pid)
    pid_checks.append(dict(pid=pid,absent=True))
write(OUT/'old-execution-recheck.json',dict(sessions=checks,known_pids=pid_checks,
    scope='Known prior named sessions and recorded PIDs only',arbitrary_children_stop='NOT_PROVEN',
    no_earlier_case_continued=True))

parent.mkdir()
target, workspace = parent/'target',parent/'worktree'
target.mkdir()
command('git-init',['git','init','-b','main'],target)
command('empty-base-commit',['git','-c','core.hooksPath=/dev/null','-c','commit.gpgSign=false',
    'commit','--allow-empty','-m','Initialize fresh Dual verification target'],target)
assert not (target/'.acpxrc.json').exists()
command('start',['java','-Xlog:class+load=debug:file='+str(OUT/'start-classloading.log')+':uptime,level,tags',
    '-jar',release['application_jars']['dual-cli']['path'],'start','--workdir='+str(target),
    '--worktree-dir='+str(workspace),'--branch=codex/w09-newlines-20261016-fresh-07',
    '--workflow-variant=terminal-delivery','--prompt-language=zh-CN','--task-file='+str(OUT/'task-requirement.md')])
ledgers = list((workspace/'.agent/workstreams').iterdir())
assert len(ledgers)==1
ledger = ledgers[0]
loc = dict(parent=str(parent),target=str(target),workspace=str(workspace),workstream=ledger.name,ledger=str(ledger))
write(OUT/'instance-location.json',loc)
command('build-info',[explicit['DUAL_ACPX_EXECUTABLE'],'build-info','--json'],workspace)
assert read(OUT/'build-info.stdout.txt')==release['selected_acpx']['actual_build_info']
command('config-show',[explicit['DUAL_ACPX_EXECUTABLE'],'config','show'],workspace)
config = read(OUT/'config-show.stdout.txt')
old_config = read(OLD/'config-show.stdout.txt')
assert config['agents']==old_config['agents']
for k in ['defaultAgent','defaultPermissions','nonInteractivePermissions','authPolicy','ttl','timeout','queueMaxDepth','format','disableExec']:
    assert config[k]==old_config[k], 'Unexpected selected resolver config: '+k
assert config['loaded']==old_config['loaded'] and config['loaded']['project'] is False
assert not (workspace/'.acpxrc.json').exists()
command('host-python',['python3','-I','-c',
    'import sys,unittest,importlib,json; print(json.dumps(dict(executable=sys.executable,version=sys.version,unittest=unittest.__file__,importlib=importlib.__file__,isolated=sys.flags.isolated)))'],workspace)
host = read(OUT/'host-python.stdout.txt')
dependencies = [dict(path=str(Path(host[k]).resolve()),sha256=sha(Path(host[k]).resolve()),absent=False)
                for k in ['executable','unittest','importlib']]
write(OUT/'host-environment-identity.json',dict(dependencies=dependencies,
    identity=hashlib.sha256(json.dumps(dependencies,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
    environment='HOST_ONLY_NOT_NATIVE_TOOL_PROOF',native_tool_execution=False))
for role,location in [('target',target),('workspace',workspace)]:
    for label,args in [('head',['rev-parse','HEAD']),('tree',['rev-parse','HEAD^{tree}']),
        ('common-dir',['rev-parse','--path-format=absolute','--git-common-dir']),('status',['status','--porcelain']),('tracked',['ls-files'])]:
        result = command(role+'-'+label,['git']+args,location)
        if label in ['status','tracked']: assert not result.strip()
assert (OUT/'target-head.stdout.txt').read_text()==(OUT/'workspace-head.stdout.txt').read_text()
assert (OUT/'target-common-dir.stdout.txt').read_text()==(OUT/'workspace-common-dir.stdout.txt').read_text()
assert (OUT/'target-tree.stdout.txt').read_text().strip()=='4b825dc642cb6eb9a060e54bf8d69288fbee4904'
identity = read(ledger/'workstream.json')['frozenRuntimeIdentity']
assert identity['executable.cli']==release['application_jars']['dual-cli']['sha256']
assert identity['executable.server']==release['application_jars']['dual-server']['sha256']
assert identity['kernel.effective']=='liteflow' and identity['behavior.env.DUAL_ACPX_COMMAND_TIMEOUT_SECONDS']=='600'
current = read(ledger/'workflow/current.json')
assert current['status']=='RUNNING' and not current['openGateIds']
assert not list((ledger/'runs').glob('*/run.json'))
assert not list(ledger.rglob('pending/*.json'))
write(OUT/'initial-workflow-current.json',current)
write(OUT/'initial-ledger-sha256.json',dict(files={str(p.relative_to(ledger)):sha(p) for p in ledger.rglob('*')
    if p.is_file() and p.name!='.mutation.lock'}))
write(OUT/'gate-local-preflight.json',dict(status='PASS_OFFLINE_PRECONDITIONS_AWAITING_RUN_APPROVAL',
    run_authorized=False,source_inputs_match=659,fresh_initialization='PASS',runtime_identity=identity,
    observer_offline='PASS',native_model_calls=0,native_sessions_created=0,business_approvals=0,location=loc,
    actual_router_execution='NOT_RUN; single INLINE selection frozen for future invocation',
    native_tool_execution='NOT_RUN; require actual typed observations when criteria are consumed'))
write(OUT/'after-source-sha256.json',{p:sha(ROOT/p) for p in release['source_inputs']})
assert read(OUT/'before-source-sha256.json')==read(OUT/'after-source-sha256.json')
assert all(sha(p)==h for p,h in selected.items()), 'Selected configuration changed during preparation'
write(OUT/'current-config-and-installed-sources.json',selected)
write(OUT/'historical-preservation.json',preserve())
frozen_check()
print('OFFLINE preflight PASS: fresh seventh initialized, zero model/native session/approval; run authorization FALSE')
