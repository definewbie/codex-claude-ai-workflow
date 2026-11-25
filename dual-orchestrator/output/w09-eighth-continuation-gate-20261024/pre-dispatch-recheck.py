"""Read-only continuation preflight: no Java/native/provider command."""
import time
from pathlib import Path
import gate_support as gate
from check_budget import check
from continuation import inspect

start = time.monotonic()
release, measured, active, process = inspect()
location = gate.read(gate.OUT / 'instance-location.json')
workspace, ledger = Path(location['workspace']), Path(location['ledger'])
current = gate.read(ledger / 'workflow/current.json')
if not list((gate.OUT / 'steps').glob('*')):
    assert len(current['activeNodeInstanceIds']) == 1 and not current['openGateIds']
    node = gate.read(ledger / 'nodes' / (current['activeNodeInstanceIds'][0] + '.json'))
    assert node['nodeDefinitionId'] == 'plan.decision' and node['status'] == 'READY'
state = gate.planning('implement.claude_implement', measured, active + time.monotonic() - start)
admission = check(state)
assert admission['result'] == 'ALLOW_BUDGET_ONLY', admission
receipt = gate.OUT / 'preflight-receipts' / str(time.time_ns())
receipt.mkdir(parents=True)
gate.write(receipt / 'result.json', {'elapsed_seconds': time.monotonic() - start, 'exit': 0,
    'budget_input': state, 'budget_result': admission, 'process_observation': process,
    'current': current, 'native_model_calls': 0, 'dispatch_performed': False})
print('Offline continuation preflight PASS; no run approval or dispatch implied')
