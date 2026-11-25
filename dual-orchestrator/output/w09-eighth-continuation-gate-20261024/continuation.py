"""One-case operator admission; never mutates the domain or grants business approval."""
import math
import hashlib
import os
import shutil
from pathlib import Path

import gate_support as gate
from process_check import require_unresolved_pids_absent


def authorize():
    spec = gate.read(gate.OUT / 'continuation-spec.json')
    auth = gate.read(gate.OUT / 'run-authorization.json')
    assert auth.get('continuation_run_authorized') is True, 'Continuation requires new human run approval'
    assert auth.get('approval_ref'), 'Missing current approval evidence reference'
    assert (gate.OUT / auth['approval_ref']).is_file(), 'Missing current approval evidence'
    for key in ['case', 'workstream', 'checkpoint_sha256', 'token_limit', 'call_limit', 'active_seconds_limit']:
        assert auth.get(key) == spec[key], 'Wrong continuation approval subject or limit'
    assert auth.get('business_gates_delegated') is False, 'Business approvals are not delegated'


def active_seconds():
    inherited = gate.read(gate.OUT / 'inherited-accounting.json')
    items = inherited['time_items']
    assert len({(r['ref'], r.get('index')) for r in items}) == len(items), 'Duplicate inherited time receipt'
    for r in items:
        assert gate.sha(r['ref']) == r['sha256'], 'Inherited time receipt drift'
        original = gate.read(r['ref'])
        value = original[r['index']] if 'index' in r else original
        assert value['elapsed_seconds'] == r['elapsed_seconds'], 'Time receipt differs'
    total = sum(r['elapsed_seconds'] for r in items)
    assert abs(total - inherited['active_seconds']) < 0.000001, 'Inherited active time reset'
    for name in ['steps', 'human-decisions', 'operations', 'preflight-receipts']:
        folder = gate.OUT / name
        for process in folder.rglob('*process.json'):
            result = process.with_name(process.name.replace('process.json', 'result.json'))
            assert result.exists(), 'Unfinished local operation; inspect external state, never retry blindly'
        if name == 'steps':
            assert all((d / 'result.json').is_file() for d in folder.glob('*') if d.is_dir()), 'Incomplete step'
        for result in folder.rglob('*result.json'):
            r = gate.read(result)
            value = r.get('elapsed_seconds')
            assert type(value) in (int, float) and math.isfinite(value) and value >= 0, 'Unknown active time'
            total += value
    return total


def inspect():
    """Read-only prerequisite; caller must separately require run approval."""
    release = gate.frozen_check()
    spec = gate.read(gate.OUT / 'continuation-spec.json')
    assert spec['case'] == 'EIGHTH_CONTINUATION' and spec['preserve_original_stop'] is True
    budget = gate.read(gate.OUT / 'budget.json')
    assert (spec['token_limit'], spec['call_limit'], spec['active_seconds_limit']) == (
        budget['total_stop'], budget['top_level_calls'], budget['active_seconds']) == (1700000, 18, 3600)
    original = Path(spec['original_package'])
    assert gate.sha(original / 'stop-required.json') == spec['original_stop_sha256'], 'Original stop changed'
    assert not (gate.OUT / 'stop-required.json').exists(), 'Continuation Gate stopped; no automatic replay'
    assert all(gate.sha(f) == h for f, h in gate.read(gate.OUT / 'original-package-sha256.json').items()), 'Original evidence drift'
    location = gate.read(gate.OUT / 'instance-location.json')
    assert location['workstream'] == spec['workstream'], 'Wrong continuation workstream'
    workspace, ledger = Path(location['workspace']), Path(location['ledger'])
    launch = gate.read(gate.OUT / 'local-launch-selection.json')
    assert hashlib.sha256(os.environ.get('PATH', '').encode()).hexdigest() == launch['process_path_sha256'], 'PATH drift'
    assert all(shutil.which(name) == path for name, path in launch['executables'].items()), 'Executable resolution drift'
    for dep in gate.read(gate.OUT / 'host-environment-identity.json')['dependencies']:
        assert gate.sha(dep['path']) == dep['sha256'], 'Host environment drift'
    assert not (workspace / '.acpxrc.json').exists(), 'Unexpected workspace config'
    baseline = gate.read(gate.OUT / 'checkpoint-ledger-sha256.json')
    assert gate.sha(gate.OUT / 'checkpoint-ledger-sha256.json') == spec['checkpoint_sha256']
    first = not list((gate.OUT / 'steps').glob('*'))
    if first:
        actual = {str(f.relative_to(ledger)): gate.sha(f) for f in ledger.rglob('*')
                  if f.is_file() and f.name != '.mutation.lock' and '__pycache__' not in f.parts}
        assert actual == baseline, 'Continuation checkpoint drift before first operation'
    else:
        assert all(gate.sha(ledger / f) == h for f, h in baseline.items() if f.startswith('runs/')), 'Historical execution changed'
    assert not list(ledger.rglob('pending/*.json')), 'Pending local mutation requires original recovery'
    current = gate.read(ledger / 'workflow/current.json')
    assert current['workstreamId'] == spec['workstream'] and current['status'] == 'RUNNING'
    old = gate.read(gate.OUT / 'old-execution-recheck.json')
    for r in old['known_pids']:
        assert gate.sha(gate.OUT / r['observation_ref']) == r['observation_sha256'], 'Process observation drift'
        observation = gate.read(gate.OUT / r['observation_ref'])
        if observation.get('status') == 'ABSENT':
            assert observation['ps_exit'] == 0 and r['pid'] in observation['checked_pids']
        else:
            assert observation['successful_complete_table'] is True and observation['exit'] == 0
            assert not observation['stderr'] and not observation['matching_rows']
            assert r['pid'] in observation['known_step_parent_pids']
    process = require_unresolved_pids_absent(old['known_pids'])
    for row in old['sessions']:
        meta = gate.read(row['metadata_path'])
        assert meta['closed'] is True and meta['name'] == row['name'], 'Old named session not closed'
    measured = gate.measure(workspace, ledger)
    inherited = gate.read(gate.OUT / 'inherited-accounting.json')
    assert measured['actual_tokens'] >= inherited['actual_tokens'], 'Historical token usage lost'
    assert measured['completed_calls'] >= inherited['completed_calls'], 'Historical requests lost'
    if first:
        assert (measured['actual_tokens'], measured['completed_calls']) == (
            inherited['actual_tokens'], inherited['completed_calls']), 'Checkpoint usage changed'
    return release, measured, active_seconds(), process
