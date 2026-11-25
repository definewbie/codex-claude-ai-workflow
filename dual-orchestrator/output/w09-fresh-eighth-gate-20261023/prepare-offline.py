from process_check import require_known_pids_absent
"""Bounded local preparation. Never starts acpx, Claude, Codex or a provider.

The only Java action is original `start` WITHOUT --run in a new empty fixture.
Existing fixtures, configurations, release bytes and prior ledgers are read-only.
"""
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

from gate_support import OUT, ROOT, read, write, sha, frozen_check, measure, planning
from check_budget import check

OLD = ROOT / 'output/w09-seventh-gate-calibrated-20261022'
PARENT = Path('/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023')
auth = read(OUT / 'run-authorization.json')
assert auth['offline_preparation_authorized'] and auth['fresh_run_authorized'] is False
assert not PARENT.exists(), 'Do not overwrite or restore an old fixture'
release = frozen_check()
assert len(release['source_inputs']) == 659
old_manifest = read(OUT / 'history-before-sha256.json')['files']
assert all(sha(p) == h for p, h in old_manifest.items())
write(OUT / 'before-source-sha256.json', {p: sha(ROOT / p) for p in release['source_inputs']})

# Preserve the reviewed launch baseline and add the actually observed SDK.
prior = dict(read(OLD / 'launch-identity.json')['config_and_installed_sources'])
sdk = read(OUT / 'observed-sdk-identity.json')
prior[sdk['observed_sdk_executable']['path']] = sdk['observed_sdk_executable']['sha256']
prior[sdk['package']['path']] = sdk['package']['sha256']
current_candidates = {p: sha(p) for p in prior}
assert current_candidates == prior, 'Selected configuration/installed bytes drifted'
explicit = dict(read(OLD / 'local-launch-selection.json')['explicit_child_environment'])
explicit['DUAL_OPERATOR_REGISTRY'] = str(PARENT / 'operator-workspaces.json')
environment = os.environ.copy()
environment.update(explicit)
write(OUT / 'local-launch-selection.json', dict(
    explicit_child_environment=explicit, real_run_authorized=False,
    persistent_environment_changed=False,
    executables={name: shutil.which(name) for name in ['java','python3','node','git','rtk','claude','codex','acpx']},
    process_path_sha256=hashlib.sha256(environment.get('PATH','').encode()).hexdigest()))
operations = []


def command(label, args, cwd=ROOT, expected=0):
    start = time.monotonic()
    result = subprocess.run(['rtk', 'proxy'] + args, cwd=cwd, env=environment,
                            capture_output=True, text=True, timeout=120)
    (OUT / (label + '.stdout.txt')).write_text(result.stdout)
    (OUT / (label + '.stderr.txt')).write_text(result.stderr)
    operations.append(dict(label=label, argv=args, cwd=str(cwd), exit=result.returncode,
                           expected_exit=expected, elapsed_seconds=round(time.monotonic()-start, 3)))
    write(OUT / 'local-operations.json', operations)
    assert result.returncode == expected, (label, result.returncode, result.stderr[-800:])
    return result.stdout


# Exercise the actual first guard while permission is false. It must reject
# before instance lookup, step creation or any Java/provider execution.
command('unauthorized-runner', [sys.executable, '-B', str(OUT/'run-one-step.py'),
                               'unauthorized-offline', 'intake.claude_brainstorm'], OUT, 1)
assert 'Fresh eighth requires new human authorization' in (OUT/'unauthorized-runner.stderr.txt').read_text()
assert not (OUT/'steps').exists() and not PARENT.exists()

old_execution = read(OUT/'prior-executions.json')
session_checks = []
for row in old_execution['sessions']:
    path = Path.home()/'.acpx/sessions'/(row['record_id']+'.json')
    meta = read(path)
    assert meta['closed'] is True and meta['name'] == row['name'], 'Known old session not closed'
    session_checks.append(dict(row, metadata_sha256=sha(path)))
pid_checks = []
process_observation = require_known_pids_absent([row['pid'] for row in old_execution['known_pids']])
pid_checks = [dict(pid=pid, absent=True) for pid in process_observation['checked_pids']]
write(OUT/'prepare-process-observation.json', process_observation)
write(OUT/'old-execution-recheck.json', dict(sessions=session_checks, known_pids=pid_checks,
    scope='Known names/PIDs only; no native session command or arbitrary child-stop claim'))

PARENT.mkdir(parents=True)
target, workspace = PARENT/'target', PARENT/'worktree'
target.mkdir()
command('git-init', ['git','init','-b','main'], target)
command('empty-base-commit', ['git','-c','core.hooksPath=/dev/null','-c','commit.gpgSign=false',
        'commit','--allow-empty','-m','Initialize offline Dual verification fixture'], target)
command('start', ['java','-Xlog:class+load=debug:file='+str(OUT/'start-classloading.log')+':uptime,level,tags',
    '-jar',release['application_jars']['dual-cli']['path'],'start','--workdir='+str(target),
    '--worktree-dir='+str(workspace),'--branch=codex/w09-newlines-20261023-offline-08',
    '--workflow-variant=terminal-delivery','--prompt-language=zh-CN',
    '--task-file='+str(OUT/'task-requirement.md')])
ledgers = list((workspace/'.agent/workstreams').iterdir())
assert len(ledgers) == 1
ledger = ledgers[0]
location = dict(parent=str(PARENT),target=str(target),workspace=str(workspace),
                workstream=ledger.name,ledger=str(ledger))
write(OUT/'instance-location.json', location)
for role, location_path in [('target', target), ('workspace', workspace)]:
    for label, args in [('head',['rev-parse','HEAD']),('tree',['rev-parse','HEAD^{tree}']),
        ('common-dir',['rev-parse','--path-format=absolute','--git-common-dir']),
        ('status',['status','--porcelain']),('tracked',['ls-files'])]:
        value = command(role+'-'+label, ['git']+args, location_path)
        if label in ['status','tracked']:
            assert not value.strip()
for name in ['head','tree','common-dir']:
    assert (OUT/('target-'+name+'.stdout.txt')).read_text() == (OUT/('workspace-'+name+'.stdout.txt')).read_text()
assert (OUT/'target-tree.stdout.txt').read_text().strip() == '4b825dc642cb6eb9a060e54bf8d69288fbee4904'
assert not (target/'.acpxrc.json').exists() and not (workspace/'.acpxrc.json').exists()
current = read(ledger/'workflow/current.json')
assert current['status'] == 'RUNNING' and not current['openGateIds']
assert not list((ledger/'runs').glob('*/run.json')) and not list(ledger.rglob('pending/*.json'))
identity = read(ledger/'workstream.json')['frozenRuntimeIdentity']
assert identity['executable.cli'] == release['application_jars']['dual-cli']['sha256']
assert identity['executable.server'] == release['application_jars']['dual-server']['sha256']
assert identity['kernel.effective'] == 'liteflow'
assert identity['behavior.env.DUAL_ACPX_COMMAND_TIMEOUT_SECONDS'] == '600'
write(OUT/'initial-workflow-current.json', current)
write(OUT/'initial-ledger-sha256.json', dict(files={str(p.relative_to(ledger)):sha(p)
    for p in ledger.rglob('*') if p.is_file() and p.name != '.mutation.lock'}))
assert not any(read(p).get('cwd') == str(workspace) for p in (Path.home()/'.acpx/sessions').glob('*.json'))
measured = measure(workspace, ledger)
state = planning('intake.claude_brainstorm', measured, 0)
admission = check(state)
assert admission['result'] == 'ALLOW_BUDGET_ONLY'
assert admission['required_token_reserve'] == 1105000
assert admission['required_call_reserve'] == 10
write(OUT/'fresh-budget-check.json', dict(input=state,result=admission,actual_tokens=0,
    completed_model_calls=0,not_spent_reserve=1105000))
write(OUT/'gate-local-preflight.json', dict(
    status='PASS_OFFLINE_PRECONDITIONS_AWAITING_RUN_APPROVAL',
    run_authorized=False, source_inputs_match=659, fresh_initialization='PASS', location=location,
    runtime_identity=identity, actual_router_execution='NOT_RUN', native_tool_execution='NOT_RUN',
    selected_client_config_drift='NO_BYTE_DRIFT; SDK identity added from actual prior observation, future selection/compatibility NOT_RUN',
    native_model_calls=0,native_sessions_created=0,business_approvals=0))
assert all(sha(p) == h for p, h in current_candidates.items()), 'Selected configuration changed during preparation'
write(OUT/'current-config-and-installed-sources.json', current_candidates)
after = {p:sha(ROOT/p) for p in release['source_inputs']}
assert read(OUT/'before-source-sha256.json') == after
write(OUT/'after-source-sha256.json', after)
assert all(sha(p) == h for p,h in old_manifest.items()), 'Original prepared material changed'
print('Offline initialization/checks complete; actual SDK baseline recorded; zero provider dispatch; authorization FALSE')
