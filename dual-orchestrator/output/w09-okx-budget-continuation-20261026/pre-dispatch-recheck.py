"""Read-only fresh Gate preflight: no Java/native/provider command."""
import time
from pathlib import Path
import gate_support as gate
from check_budget import check
from admission import inspect

start = time.monotonic()
release, measured, active, process, launch = inspect()
location = gate.read(gate.OUT / 'instance-location.json')
workspace, ledger = Path(location['workspace']), Path(location['ledger'])
current = gate.read(ledger / 'workflow/current.json')
if not list((gate.OUT / 'steps').glob('*')):
    assert len(current['activeNodeInstanceIds']) == 1 and not current['openGateIds']
    node = gate.read(ledger / 'nodes' / (current['activeNodeInstanceIds'][0] + '.json'))
    assert node['status'] == 'READY'
node = gate.read(ledger / 'nodes' / (current['activeNodeInstanceIds'][0] + '.json'))
stages = [s['node'] for s in gate.read(gate.OUT / 'budget.json')['stages']]
seen = {r['node_id'] for r in measured['completed_runs']}
remaining = [s for s in stages if s not in seen]
assert remaining, 'No unfinished model work for this readonly admission'
expected = node['nodeDefinitionId'] if node['nodeDefinitionId'] in stages else remaining[0]
state = gate.planning(expected, measured, active + time.monotonic() - start)
admission = check(state)
assert admission['result'] == 'ALLOW_BUDGET_ONLY', admission
receipt = gate.OUT / 'preflight-receipts' / str(time.time_ns())
receipt.mkdir(parents=True)
gate.write(receipt / 'result.json', {'elapsed_seconds': time.monotonic() - start, 'exit': 0,
    'budget_input': state, 'budget_result': admission, 'process_observation': process,
    'current': current, 'native_model_calls': 0, 'dispatch_performed': False})
print('Offline fresh Gate preflight PASS; no run approval or dispatch implied')
