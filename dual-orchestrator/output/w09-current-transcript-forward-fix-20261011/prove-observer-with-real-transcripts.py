"""Real archived wire bytes + simulated future fixed facts; never a ledger repair."""
from pathlib import Path
import json,re,shutil,sys,copy
sys.path.insert(0,str(Path(__file__).resolve().parent))
import gate_support as g
b=Path(__file__).resolve().parent
root=b.parents[1]
old=root/'output/w09-fresh-fourth-gate-20261011/stopped-ledger-snapshot'
workspace=b/'observer-real-fixture'
ledger=workspace/'.agent/workstreams/ws-20261011T044656Z-BMDJ'
sessions=b/'observer-real-sessions'
sessions.mkdir(exist_ok=True)
for stream in (b/'proof-sessions').glob('*.stream.ndjson'):
    shutil.copy2(stream,sessions/stream.name)
recomputed={}
for line in (b/'actual-current-transcripts-offline-proof.stdout').read_text().splitlines():
    if line.startswith('run-'):
        key,value=line.split(' ',1);recomputed[key]=json.loads(value)
for rp in sorted((old/'runs').glob('*/run.json')):
    run=g.read(rp)
    for o in run['agentResultAcquisition']['occurrences']:
        for name in ['transcriptPath','stderrPath']:
            dest=workspace/o[name];dest.parent.mkdir(parents=True,exist_ok=True)
            original=old/'runs'/o[name].split('/runs/',1)[1]
            shutil.copy2(original,dest)
        record=re.findall(r'\(([a-f0-9-]{36})\)',(workspace/o['stderrPath']).read_text())[-1]
        meta=g.read(b/'proof-sessions'/(record+'.json'))
        meta['cwd']=str(workspace)
        g.write(sessions/(record+'.json'),meta)
    dest=ledger/'runs'/run['runId']/'run.json';dest.parent.mkdir(parents=True,exist_ok=True)
    g.write(dest,run)
    original_completion=next((rp.parent/'outputs').glob('*-completion.json'))
    shutil.copy2(original_completion,dest.parent/'outputs'/original_completion.name)
try:
    g.measure(workspace,ledger,sessions)
except AssertionError as e:
    rejection=str(e)
else:
    raise AssertionError('old UNAVAILABLE was accepted')
assert rejection=='Incomplete run usage',rejection
# Only isolated candidate input changes. No R03 proof is claimed for this fixture.
for rp in (ledger/'runs').glob('*/run.json'):
    run=g.read(rp)
    for o in run['agentResultAcquisition']['occurrences']: o['usage']=recomputed[run['runId']]
    run['usage']=recomputed[run['runId']]
    g.write(rp,run)
    completion_path=next((rp.parent/'outputs').glob('*-completion.json'))
    completion=g.read(completion_path)
    completion['run_observations']=copy.deepcopy(run)
    g.write(completion_path,completion)
measured=g.measure(workspace,ledger,sessions)
assert (measured['actual_tokens'],measured['completed_calls'])==(403420,8)
# Shared logs can append indefinitely; current transcript facts stay stable.
p=b/'proof-sessions/01a1294c-d0dc-7711-b5ff-74b18d96e7d2.stream.ndjson'
(sessions/p.name).write_text(p.read_text()+p.read_text())
assert measured==g.measure(workspace,ledger,sessions)
g.write(b/'observer-real-transcript-proof.json',dict(status='PASS_OFFLINE_CANDIDATE_INPUT_ONLY',
    actual_tokens=403420,completed_calls=8,old_fixed_unavailable_rejected=rejection,
    current_codex_shared_history_append_stable=True,
    current_source_calls=6,strict_shared_fallback_calls=2,old_ledger_modified=False,native_provider_calls=0,
    not_proven='No new R03 commit, native execution, budget permission, recovery or fifth Gate is proven',
    rows=measured['completed_runs']))
print('6 real current results + 2 strict shared results with candidate fixed facts verified; old unavailable rejected; no historical repair')
