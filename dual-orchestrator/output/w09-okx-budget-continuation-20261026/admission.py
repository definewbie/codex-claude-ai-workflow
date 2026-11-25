"""Fresh one-case operator preparation/admission; never business authority."""
import hashlib
import math
import os
import shutil
import sys
from pathlib import Path
from types import MappingProxyType
import gate_support as gate
from process_check import require_unresolved_pids_absent


def authorize():
    auth = gate.read(gate.OUT / 'run-authorization.json')
    assert auth.get('real_run_authorized') is True and auth.get('budget_approved') is True, 'Fresh Gate requires current human budget and run approval'
    assert auth.get('continuation_run_authorized') is True, 'Current continuation approval required'
    spec = gate.read(gate.OUT / 'continuation-spec.json')
    assert auth.get('continuation_spec_sha256') == gate.sha(gate.OUT / 'continuation-spec.json'), 'Wrong continuation subject'
    assert (spec['token_limit'], spec['call_limit'], spec['active_seconds_limit']) == (4000000, 18, 3600)
    assert spec['budget_sha256'] == gate.sha(gate.OUT / 'budget.json'), 'Continuation budget drift'
    assert auth.get('approval_ref') and (gate.OUT / auth['approval_ref']).is_file(), 'Missing current approval evidence'
    location = gate.read(gate.OUT / 'instance-location.json')
    assert auth.get('case') == 'OKX_JAVA_MODIFY_CANDIDATE', 'Wrong case'
    assert auth.get('workstream') == location['workstream'], 'Wrong workstream'
    assert auth.get('initial_ledger_sha256') == gate.sha(gate.OUT / 'initial-ledger-sha256.json'), 'Wrong initial subject'
    assert auth.get('budget_sha256') == gate.sha(gate.OUT / 'budget.json'), 'Wrong budget'
    assert auth.get('approved_launch_identity_sha256') == gate.sha(gate.OUT / 'launch-identity.json'), 'Unapproved launch combination'
    assert auth.get('business_gates_delegated') is False and auth.get('commit_delegated') is False, 'No implicit business or commit delegation'


def selected_environment():
    """One checked launch selection, shared by OS inspection and actual launch.

    The caller freezes the input files first. Parent PATH is diagnostic only;
    the captured child PATH controls every resolution below and in Popen.
    This is operator identity checking, not adversarial filesystem isolation.
    """
    selection = gate.read(gate.OUT / 'local-launch-selection.json')
    identity = gate.read(gate.OUT / 'launch-identity.json')
    fixed = identity['config_and_installed_sources']
    explicit = selection['explicit_child_environment']
    assert isinstance(explicit.get('PATH'), str) and explicit['PATH'], 'Missing selected child PATH'
    environment = os.environ.copy()
    environment.update(explicit)
    for name, expected in identity.get('client_environment_overrides', {}).items():
        assert (name in environment) == expected['present'] and hashlib.sha256(
            environment.get(name, '').encode()).hexdigest() == expected['value_sha256'], 'Client override drift: ' + name
    programs = dict(selection['executables'])
    assert set(programs) == {'python3', 'java', 'node', 'rtk', 'ps', 'git', 'claude', 'codex', 'acpx'}, 'Incomplete selected programs'
    for name, entry in programs.items():
        path = Path(entry)
        assert path.is_absolute() and path.is_file() and os.access(path, os.X_OK), 'Selected program unavailable: ' + name
        real = str(path.resolve())
        assert real in fixed and gate.sha(real) == fixed[real], 'Selected program bytes drift: ' + name
        if name == 'acpx':
            assert entry == environment.get('DUAL_ACPX_EXECUTABLE'), 'Wrong explicit acpx wrapper'
        else:
            resolved = shutil.which(name, path=environment['PATH'])
            assert resolved and Path(resolved).resolve() == path.resolve(), 'Selected resolution drift: ' + name
    assert Path(sys.executable).resolve() == Path(programs['python3']).resolve(), 'Wrong operator interpreter'
    return MappingProxyType({'environment': MappingProxyType(environment),
                             'executables': MappingProxyType(programs)})


def active_seconds():
    inherited = gate.read(gate.OUT / 'inherited-accounting.json')
    items = inherited['time_items']
    assert len({r['ref'] for r in items}) == len(items), 'Duplicate inherited receipt'
    for row in items:
        assert gate.sha(row['ref']) == row['sha256'], 'Inherited receipt drift'
        assert gate.read(row['ref'])['elapsed_seconds'] == row['elapsed_seconds'], 'Inherited time differs'
    total = sum(row['elapsed_seconds'] for row in items)
    assert abs(total - inherited['active_seconds']) < 0.000001, 'Inherited active time reset' 
    for name in ['steps', 'human-decisions', 'operations', 'preflight-receipts']:
        folder = gate.OUT / name
        for process in folder.rglob('*process.json'):
            assert process.with_name(process.name.replace('process.json', 'result.json')).is_file(), 'Unfinished local operation; inspect external state'
        if name == 'steps':
            assert all((d / 'result.json').is_file() for d in folder.glob('*') if d.is_dir()), 'Incomplete step; no automatic retry'
        for result in folder.rglob('*result.json'):
            r = gate.read(result)
            value = r.get('elapsed_seconds')
            assert type(value) in (int, float) and math.isfinite(value) and value >= 0, 'Unknown active time'
            assert type(r.get('exit')) is int and r['exit'] in (0, 1), 'Failed or unknown local operation requires assessment'
            total += value
    return total


def inspect():
    release = gate.frozen_check()
    assert not (gate.OUT / 'stop-required.json').exists(), 'Fresh Gate stopped; no automatic replay'
    spec = gate.read(gate.OUT / 'continuation-spec.json')
    original = Path(spec['original_package'])
    assert gate.sha(original / 'stop-required.json') == spec['original_stop_sha256'], 'Original stop drift'
    assert all(gate.sha(f) == h for f, h in gate.read(gate.OUT / 'original-package-sha256.json').items()), 'Original evidence drift'
    assert gate.sha(gate.OUT / 'continuation-checkpoint.json') == spec['checkpoint_sha256'], 'Wrong continuation checkpoint'
    for row in gate.read(gate.OUT / 'closed-session-observations.json'):
        assert gate.sha(row['observation_ref']) == row['observation_sha256'], 'Session observation drift'
        meta = gate.read(row['metadata_path'])
        assert meta['closed'] is True and meta['name'] == row['name'], 'Historical named session not closed'
    launch = selected_environment()
    budget = gate.read(gate.OUT / 'budget.json')
    assert (budget['total_stop'], budget['top_level_calls'], budget['active_seconds']) == (4000000, 18, 3600), 'Unexpected proposed limits'
    location = gate.read(gate.OUT / 'instance-location.json')
    workspace, ledger = Path(location['workspace']), Path(location['ledger'])
    assert ledger == workspace / '.agent/workstreams' / location['workstream'], 'Wrong ledger location'
    assert workspace.resolve() != Path(location['target']).resolve(), 'Target/workspace must differ'
    assert not (workspace / '.acpxrc.json').exists(), 'Unexpected workspace config'
    for dep in gate.read(gate.OUT / 'host-environment-identity.json')['dependencies']:
        assert gate.sha(dep['path']) == dep['sha256'], 'Host dependency drift'
    assert not list(ledger.rglob('pending/*.json')), 'Pending mutation requires original recovery'
    current = gate.read(ledger / 'workflow/current.json')
    assert current['workstreamId'] == location['workstream'] and current['status'] == 'RUNNING', 'Wrong or terminal workstream'
    initial = gate.read(gate.OUT / 'continuation-checkpoint.json')['files']
    if not list((gate.OUT / 'steps').glob('*')) and not list((gate.OUT / 'human-decisions').glob('*')):
        actual = {str(f.relative_to(ledger)): gate.sha(f) for f in ledger.rglob('*') if f.is_file() and f.name != '.mutation.lock'}
        assert actual == initial, 'Initial ledger drift'
    else:
        assert all(gate.sha(ledger / f) == h for f, h in initial.items() if f.startswith('runs/')), 'Historical execution drift'
    time_used = active_seconds()
    # This fresh workspace has no old execution PID set. A completed local
    # wait receipt proves only that command returned; no child-tree claim.
    process = require_unresolved_pids_absent([], environment=launch['environment'], executables=launch['executables'])
    measured = gate.measure(workspace, ledger)
    inherited = gate.read(gate.OUT / 'inherited-accounting.json')
    assert measured['actual_tokens'] >= inherited['actual_tokens'] and measured['completed_calls'] >= inherited['completed_calls'], 'Historical usage lost'
    return release, measured, time_used, process, launch
