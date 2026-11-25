import datetime
import os
import subprocess
import time
from pathlib import Path
from gate_support import OUT, ROOT, read, write, sha, frozen_check

release = frozen_check()
assert read(OUT / 'observer-offline-verification.json')['status'] == 'PASS'
assert read(OUT / 'run-authorization.json')['fresh_run_authorized'] is True
parent = Path('/private/tmp/dual-w09-newlines-20261015-fresh-06')
assert not parent.exists(), 'Fresh directory conflict; never overwrite'
audit = ROOT / 'output/w09-mainline-budget-audit-20261011'
selected = {}
for item in read(audit / 'config-observation.json')['files']:
    if item['exists']:
        if sha(item['path']) != item['sha256']:
            from gate_support import same_config_with_fixture_registration
            config_text = Path(item['path']).read_text()
            import re
            fixture = '/private/tmp/dual-w09-newlines-20261011-fresh-05/target'
            header = '[projects."' + fixture + '"]'
            assert item['path'] == str(Path.home()/'.codex/config.toml') and config_text.count(header) == 1, 'Unexpected settings drift'
            import tomllib
            assert tomllib.loads(config_text)['projects'][fixture] == {'trust_level':'trusted'}
            begin = config_text.index(header)
            match = re.search(r'\n\[', config_text[begin + len(header):])
            end = begin + len(header) + match.start() + 1 if match else len(config_text)
            before_fifth = config_text[:begin] + config_text[end:]
            assert same_config_with_fixture_registration(before_fifth, item['sha256'], Path('/private/tmp/dual-w09-newlines-20261011-fresh-04/target')), 'Settings drift since CC assessment'
        item = dict(item, sha256=sha(item['path']))
        selected[item['path']] = item['sha256']
for item in read(audit / 'source-index.json'):
    assert sha(item['path']) == item['sha256'], 'Selected source drift'
    selected[item['path']] = item['sha256']
old = ROOT / 'output/w09-fresh-third-gate-20261010'
checks = []
for s in read(old / 'native-session-stop-observation.json')['sessions']:
    p = Path.home() / '.acpx/sessions' / (s['after']['acpxRecordId'] + '.json')
    meta = read(p)
    assert meta['closed'] is True and meta['name'] == s['name'], 'Known old session not closed'
    checks.append({'name': s['name'], 'record_id': s['after']['acpxRecordId'], 'closed': True, 'metadata_sha256': sha(p)})
for old_case in ['w09-real-gate-20261010', 'w09-fresh-second-gate-20261010']:
    old_meta = read(old / ('prior-' + old_case + '-session-show.stdout.txt'))
    p = Path.home() / '.acpx/sessions' / (old_meta['acpxRecordId'] + '.json')
    current_meta = read(p)
    assert current_meta['name'] == old_meta['name'] and current_meta['closed'] is True, 'Earlier known session not closed'
    checks.append({'name': old_meta['name'], 'closed': True, 'case': old_case, 'metadata_sha256': sha(p)})
fourth = ROOT/'output/w09-fresh-fourth-gate-20261011'
for entry in read(fourth/'stop-session-proof.json')['operations']:
    p=Path.home()/'.acpx/sessions'/(entry['record_id']+'.json')
    meta=read(p)
    assert meta['closed'] is True and meta['name']==entry['name'], 'Fourth known session not closed'
    checks.append({'name':meta['name'],'record_id':entry['record_id'],'closed':True,'metadata_sha256':sha(p)})
known_pids = read(old / 'known-process-stop-observation.json')['checks']
known_pids += [{'pid':read(p)['pid']} for p in (fourth/'steps').glob('*/process.json')]

fifth = ROOT/'output/w09-fresh-fifth-gate-20261011'
for entry in read(fifth/'stop-session-proof.json')['operations']:
    path=Path.home()/'.acpx/sessions'/(entry['record_id']+'.json')
    meta=read(path)
    assert meta['closed'] is True and meta['name']==entry['name'], 'Fifth known session not closed'
    checks.append({'name':meta['name'],'record_id':entry['record_id'],'closed':True,'metadata_sha256':sha(path)})
known_pids += [{'pid':row['pid']} for row in read(fifth/'known-process-stop-observation.json')['checks']]

pid_checks = []
for c in known_pids:
    result = subprocess.run(['rtk', 'proxy', 'ps', '-p', str(c['pid']), '-o', 'pid=,lstart=,command='], capture_output=True, text=True)
    assert result.returncode == 1 and not result.stdout.strip(), 'Known PID present; inspect before proceeding'
    pid_checks.append({'pid': c['pid'], 'absent': True})
write(OUT / 'old-execution-recheck.json', {'sessions': checks, 'known_pids': pid_checks, 'arbitrary_children_stop': 'NOT_PROVEN'})
observer_names = ['gate_support.py','run-one-step.py','check_budget.py','budget.json','test_gate_observer.py','task-requirement.md','run-authorization.json','fresh-sixth-gate.md','selected-release.json','prepare-local.py']
write(OUT / 'launch-identity.json', {'checked_at': datetime.datetime.now().astimezone().isoformat(),
    'config_and_installed_sources': selected, 'observer_inputs': {p: sha(OUT / p) for p in observer_names},
    'release_ref': str(OUT / 'selected-release.json'),
    'no_production_or_config_changes': True})
parent.mkdir()
target, workspace = parent / 'target', parent / 'worktree'
target.mkdir()
env = os.environ.copy()
explicit = {'DUAL_ACPX_EXECUTABLE': release['selected_acpx']['wrapper'],
    'DUAL_ACPX_COMMAND_TIMEOUT_SECONDS': '600', 'DUAL_FLOW_KERNEL': 'liteflow',
    'DUAL_OPERATOR_REGISTRY': str(parent / 'operator-workspaces.json')}
env.update(explicit)
write(OUT / 'local-launch-selection.json', {'explicit_child_environment': explicit, 'real_run_authorized': True, 'persistent_environment_changed': False})
operations = []
def command(label, args, cwd):
    start = time.monotonic()
    cp = subprocess.run(['rtk', 'proxy'] + args, cwd=cwd, env=env, capture_output=True, text=True)
    (OUT / (label + '.stdout.txt')).write_text(cp.stdout)
    (OUT / (label + '.stderr.txt')).write_text(cp.stderr)
    operations.append({'label': label, 'argv': args, 'cwd': str(cwd), 'exit': cp.returncode, 'elapsed_seconds': round(time.monotonic()-start, 3)})
    write(OUT / 'local-operations.json', operations)
    assert cp.returncode == 0, label
    return cp.stdout
command('git-init', ['git', 'init', '-b', 'main'], target)
command('empty-base-commit', ['git', '-c', 'core.hooksPath=/dev/null', '-c', 'commit.gpgSign=false', 'commit', '--allow-empty', '-m', 'Initialize fresh Dual verification target'], target)
command('start', ['java', '-Xlog:class+load=debug:file='+str(OUT/'start-classloading.log')+':uptime,level,tags', '-jar', release['application_jars']['dual-cli']['path'], 'start', '--workdir='+str(target), '--worktree-dir='+str(workspace), '--branch=codex/w09-newlines-20261015-fresh-06', '--workflow-variant=terminal-delivery', '--prompt-language=zh-CN', '--task-file='+str(OUT/'task-requirement.md')], ROOT)
ledgers = list((workspace / '.agent/workstreams').iterdir())
assert len(ledgers) == 1
ledger = ledgers[0]
write(OUT / 'instance-location.json', {'parent': str(parent), 'target': str(target), 'workspace': str(workspace), 'workstream': ledger.name, 'ledger': str(ledger)})
build = command('build-info', [explicit['DUAL_ACPX_EXECUTABLE'], 'build-info', '--json'], workspace)
assert read(OUT / 'build-info.stdout.txt') == release['selected_acpx']['actual_build_info'], 'Wrong selected build'
command('config-show', [explicit['DUAL_ACPX_EXECUTABLE'], 'config', 'show'], workspace)
command('host-python', ['python3', '-I', '-c', 'import sys,unittest,importlib,json; print(json.dumps(dict(executable=sys.executable,version=sys.version,unittest=unittest.__file__,importlib=importlib.__file__,isolated=sys.flags.isolated)))'], workspace)
for role, location in [('target', target), ('workspace', workspace)]:
    for label, args in [('head', ['rev-parse', 'HEAD']), ('tree', ['rev-parse', 'HEAD^{tree}']), ('common-dir', ['rev-parse', '--path-format=absolute', '--git-common-dir']), ('status', ['status', '--porcelain']), ('tracked', ['ls-files'])]:
        result = command(role+'-'+label, ['git']+args, location)
        if label in ['status', 'tracked']: assert not result.strip(), 'Unexpected initial files'
identity = read(ledger / 'workstream.json')['frozenRuntimeIdentity']
assert identity['executable.cli'] == release['application_jars']['dual-cli']['sha256'] and identity['kernel.effective'] == 'liteflow'
assert read(ledger / 'workflow/current.json')['status'] == 'RUNNING' and not list((ledger / 'runs').glob('*/run.json'))
write(OUT / 'gate-local-preflight.json', {'status': 'PASS_LOCAL_PRECONDITIONS', 'authorization': 'USER_GRANTED_FRESH_SIXTH_1700000', 'source_inputs_match': len(release['source_inputs']), 'observer_offline': 'PASS', 'fresh_initialization': 'PASS', 'runtime_identity': identity, 'native_model_calls': 0, 'business_approvals': 0, 'location': read(OUT/'instance-location.json')})
frozen_check()
print('Local preflight PASS; fresh sixth initialized, zero model calls')
