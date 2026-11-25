import json,re,hashlib
from pathlib import Path
out=Path(__file__).resolve().parent
loc=json.loads((out/'instance-location.json').read_text());workspace=Path(loc['workspace']);ledger=Path(loc['ledger'])
observations=[]
for rp in sorted((ledger/'runs').glob('*/run.json')):
 r=json.loads(rp.read_text())
 if not r.get('finishedAt'):continue
 u=r.get('usage') or {};assert u.get('measurementType')=='PROVIDER_RUN_USAGE' and not u.get('cumulative'), 'No complete measured run usage'
 acq=r.get('agentResultAcquisition') or {};occ=acq.get('occurrences',[]);assert occ,'No acquisition occurrences'
 row={'run_id':r['runId'],'agent':r['agent'],'outcome':r['executionOutcome'],'decision':r['agentResultContract']['decision'],'run_usage':u,'occurrences':[]}
 for o in occ:
  stderr=workspace/o['stderrPath'];m=re.search(r'\(([0-9a-f-]{36})\)',stderr.read_text());assert m,'No native record id'
  record=m.group(1);stream=Path.home()/'.acpx/sessions'/(record+'.stream.ndjson');meta=json.loads(stream.with_name(record+'.json').read_text());items=[json.loads(x) for x in stream.read_text().splitlines()]
  req=str(o['requestId']);sid=meta['acp_session_id'];prompts=[x for x in items if x.get('method')=='session/prompt' and str(x.get('id'))==req and x.get('params',{}).get('sessionId')==sid]
  results=[x for x in items if str(x.get('id'))==req and 'stopReason' in x.get('result',{})];assert len(prompts)==len(results)==1,'Ambiguous native request'
  native=results[0]['result'].get('usage');fixed=o['usage'];assert native and fixed['attribution']=='EXACT_REQUEST_MATCH' and native['totalTokens']==fixed['totalTokens'],'Native/fixed usage mismatch'
  row['occurrences'].append({'session_id':sid,'record_id':record,'request_id':req,'native_usage':native,'fixed_usage':fixed,'stream_ref':str(stream),'stream_prefix_sha256':hashlib.sha256(stream.read_bytes()).hexdigest()})
 assert sum(o['fixed_usage']['totalTokens'] for o in row['occurrences'])==u['totalTokens'],'Partial aggregate'
 observations.append(row)
current=json.loads((ledger/'workflow/current.json').read_text())
result={'completed_runs':observations,'top_level_prompt_calls':sum(len(r['occurrences']) for r in observations),'visible_actual_tokens':sum(r['run_usage']['totalTokens'] for r in observations),'workflow_status':current['status'],'active_nodes':current['activeNodeInstanceIds'],'open_gates':current['openGateIds'],'amounts':'per-run SDK amounts retained; not subscription paid bill; mixed providers not assigned one price','unseen_internal_calls':'NOT_PROVEN'}
(out/'current-observation-proof.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ['completed_runs']},ensure_ascii=False))
