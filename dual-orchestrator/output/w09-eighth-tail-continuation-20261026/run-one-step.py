import datetime
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from gate_support import OUT, read, write, frozen_check, measure, planning
from check_budget import check
from continuation import authorize, inspect

# This is a candidate runner, not permission to reuse a prior case or budget.
authorize()
step, expected = sys.argv[1:]
assert not (OUT / 'stop-required.json').exists(), 'Gate already stopped; no automatic continuation'
folder = OUT / 'steps' / step
assert not folder.exists(), 'Never repeat an old step automatically'
loc = read(OUT / 'instance-location.json')
workspace, ledger = Path(loc['workspace']), Path(loc['ledger'])
release, inherited_measurement, active, process_observation, launch = inspect()
assert not (workspace / '.acpxrc.json').exists(), 'Unexpected workspace config'
assert not list(ledger.rglob('pending/*.json')), 'Pending local write requires original recovery assessment'
previous = [read(p) for p in (OUT / 'steps').glob('*/result.json')]
assert all(p.get('exit') in (0, 1) for p in previous), 'Prior runner exception requires assessment'
# Active time already includes old operations and every new receipt via inspect().
current = read(ledger / 'workflow/current.json')
assert current['status'] == 'RUNNING' and not current['openGateIds'], 'Paused/terminal workflow'
assert len(current['activeNodeInstanceIds']) == 1
node = read(ledger / 'nodes' / (current['activeNodeInstanceIds'][0] + '.json'))
assert node['nodeDefinitionId'] == expected and node['status'] == 'READY', 'Wrong/current active task'
identity = read(ledger / 'workstream.json')['frozenRuntimeIdentity']
assert identity['executable.cli'] == release['application_jars']['dual-cli']['sha256']
assert identity['executable.server'] == release['application_jars']['dual-server']['sha256']
assert identity['kernel.effective'] == 'liteflow' and identity['behavior.env.DUAL_ACPX_COMMAND_TIMEOUT_SECONDS'] == '600'
# Same reviewed prerequisite before any step directory or Java side effect.
# Process guard and observation-link validation already ran in inspect().
folder.mkdir(parents=True)
write(folder/'old-process-observation.json', process_observation)
is_model = expected in [s['node'] for s in read(OUT / 'budget.json')['stages']]
if is_model:
    measured = measure(workspace, ledger)
    assert all((r['outcome'] or {}).get('nodeMaySucceed') is True for r in measured['completed_runs']), 'Prior execution rejection requires assessment'
    state = planning(expected, measured, active)
    admission = check(state)
    write(folder / 'budget-admission.json', {'input': state, 'result': admission, 'observation': measured})
    assert admission['result'] == 'ALLOW_BUDGET_ONLY', admission
else:
    assert active < 3600, 'Active time exhausted'
write(folder / 'pre-state.json', {'current': current, 'node': node, 'prior_active_seconds': active})
env = launch['environment']
args = [launch['executables']['rtk'], 'proxy', launch['executables']['java'], '-Xlog:class+load=debug:file=' + str(folder / 'classloading.log') + ':uptime,level,tags',
        '-jar', release['application_jars']['dual-cli']['path'], 'run', '--workdir=' + str(workspace),
        '--max-steps', '1', '--dispatch-mode', 'INLINE', loc['workstream']]
start = time.monotonic()
limit_exceeded = False
with (folder / 'stdout.txt').open('w') as so, (folder / 'stderr.txt').open('w') as se:
    try:
        process = subprocess.Popen(args, cwd=workspace, env=env, stdout=so, stderr=se)
    except OSError as failure:
        # No automatic retry: even a disappearing launcher leaves a durable
        # failed step. A returned Popen handle is not available to prove state.
        write(folder / 'result.json', {'exit': None, 'elapsed_seconds': time.monotonic() - start,
              'launch_failed': True, 'external_start': 'NOT_CONFIRMED', 'error_type': type(failure).__name__})
        write(OUT / 'stop-required.json', {'reason': 'LOCAL_PROCESS_START_FAILED',
              'classification': 'INVALID', 'next_dispatch': 'FORBIDDEN_PENDING_ASSESSMENT'})
        raise
    write(folder / 'process.json', {'pid': process.pid, 'started_at': datetime.datetime.now().astimezone().isoformat(), 'argv': args, 'expected_node': expected})
    print(json.dumps({'step': step, 'pid': process.pid, 'node': expected, 'started': True}), flush=True)
    while True:
        try:
            code = process.wait(timeout=30)
            break
        except subprocess.TimeoutExpired:
            elapsed = time.monotonic() - start
            print(json.dumps({'step': step, 'elapsed_seconds': round(elapsed), 'runner_alive': True}), flush=True)
            if active + elapsed >= 3600:
                limit_exceeded = True
                write(OUT / 'stop-required.json', {'reason': 'ACTIVE_TIME_STOP', 'pid': process.pid, 'must_check_native_stop': True})
                process.terminate()
                try:
                    code = process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill()
                    code = process.wait()
                break
elapsed = round(time.monotonic() - start, 3)
write(folder / 'result.json', {'exit': code, 'elapsed_seconds': elapsed, 'active_time_stop': limit_exceeded,
                             'finished_at': datetime.datetime.now().astimezone().isoformat()})
current = read(ledger / 'workflow/current.json')
write(folder / 'post-current.json', current)
completed_node = read(ledger / 'nodes' / (node['nodeInstanceId'] + '.json'))
write(folder / 'post-node.json', completed_node)
if not (code in (0, 1) and completed_node['status'] == 'COMPLETED'):
    write(OUT / 'stop-required.json', {'reason': 'LOCAL_STEP_NOT_COMPLETED', 'exit': code,
          'next_dispatch': 'FORBIDDEN_PENDING_ASSESSMENT', 'domain_status_not_rewritten': True})
    raise AssertionError('Execution did not complete; assess before continuing')
try:
    measured = measure(workspace, ledger)
    write(OUT / 'current-observation-proof.json', measured)
    assert measured['actual_tokens'] < 3000000 and measured['completed_calls'] <= 18, 'Observed budget stop'
except Exception as e:
    write(OUT / 'stop-required.json', {'reason': str(e), 'next_dispatch': 'FORBIDDEN_PENDING_ASSESSMENT'})
    raise
print(json.dumps({'step': step, 'exit': code, 'elapsed_seconds': elapsed, 'actual_tokens': measured['actual_tokens'],
                  'completed_calls': measured['completed_calls'], 'workflow_status': current['status'],
                  'active_nodes': current['activeNodeInstanceIds'], 'open_gates': current['openGateIds']}, ensure_ascii=False), flush=True)
