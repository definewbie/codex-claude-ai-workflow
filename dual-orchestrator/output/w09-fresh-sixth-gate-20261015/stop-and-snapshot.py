import datetime,json,subprocess,shutil,re,time
from pathlib import Path
from gate_support import OUT,ROOT,read,write,sha,frozen_check,measure
loc=read(OUT/'instance-location.json');workspace=Path(loc['workspace']);ledger=Path(loc['ledger']);release=frozen_check()
assert (OUT/'stop-required.json').exists()
try:
 m=measure(workspace,ledger)
except Exception as error:
 m={'measurement_complete':False,'actual_tokens':None,'completed_calls':sum(len(read(r).get('agentResultAcquisition',{}).get('occurrences',[])) for r in (ledger/'runs').glob('*/run.json')),'error':str(error)}
write(OUT/'stopped-observation-proof.json',m)
assert all(read(p).get('finishedAt') for p in (ledger/'runs').glob('*/run.json'))
rows=[];seen=set();pids=set()
for rp in (ledger/'runs').glob('*/run.json'):
 r=read(rp)
 for o in r['agentResultAcquisition']['occurrences']:
  stderr=(workspace/o['stderrPath']).read_text();rid=re.search(r'\(([0-9a-f-]{36})\)',stderr).group(1)
  pids.add(o['processResult']['pid'])
  if rid in seen:continue
  seen.add(rid);path=Path.home()/'.acpx/sessions'/(rid+'.json');meta=read(path)
  assert meta['name']==o['sessionName'] and Path(meta['cwd']).resolve()==workspace.resolve()
  dest=OUT/'stop-session-operations'/rid;dest.mkdir(parents=True)
  shutil.copy2(path,dest/'before.json');stream=path.with_suffix('.stream.ndjson')
  if stream.exists():shutil.copy2(stream,dest/'before.stream.ndjson')
  if meta.get('pid'):pids.add(meta['pid'])
  for operation in ['close','show']:
   args=['rtk','proxy',release['selected_acpx']['wrapper'],'--format','json',r['agent'],'sessions',operation,o['sessionName']]
   cp=subprocess.run(args,cwd=workspace,capture_output=True,text=True,timeout=60)
   (dest/(operation+'.stdout.txt')).write_text(cp.stdout);(dest/(operation+'.stderr.txt')).write_text(cp.stderr)
   write(dest/(operation+'.command.json'),{'argv':args,'exit':cp.returncode})
   assert cp.returncode==0,'Session operation failed'
  after=read(path);shown=read(dest/'show.stdout.txt');assert after['closed'] is True and shown['closed'] is True
  shutil.copy2(path,dest/'after.json');rows.append({'name':o['sessionName'],'record_id':rid,'agent':r['agent'],'closed_observed':True,'proof':str(dest.relative_to(OUT))})
write(OUT/'stop-session-proof.json',{'observed_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'operations':rows,'all_prompt_results_completed':True,'scope':'These named sessions only; arbitrary descendant termination NOT_PROVEN','new_attempt_authority':False})
for p in (OUT/'steps').glob('*/process.json'):pids.add(read(p)['pid'])
checks=[]
for pid in sorted(pids):
 cp=subprocess.run(['rtk','proxy','ps','-p',str(pid),'-o','pid=,lstart=,command='],capture_output=True,text=True)
 checks.append({'pid':pid,'exit':cp.returncode,'observation':cp.stdout.strip(),'absent':cp.returncode==1 and not cp.stdout.strip()})
write(OUT/'known-process-stop-observation.json',{'checks':checks,'scope':'known parent/native PIDs only; absence not general subtree proof'})
assert all(c['absent'] for c in checks),'Known process present, inspect identity before any next attempt'
snapshot=OUT/'stopped-ledger-snapshot';snapshot.mkdir()
files={}
for path in ledger.rglob('*'):
 if path.is_file() and path.name!='.mutation.lock':
  rel=path.relative_to(ledger);d=snapshot/rel;d.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,d);files[str(rel)]=sha(path)
write(OUT/'stopped-ledger-identity.json',{'files':files,'count':len(files),'excluded':['.mutation.lock'],'live_ledger':str(ledger)})
product={}
for role in ['target','workspace']:
 cwd=Path(loc[role]);entries={}
 for label,args in [('head',['rev-parse','HEAD']),('tree',['rev-parse','HEAD^{tree}']),('tracked',['ls-files']),('status',['status','--porcelain'])]:
  cp=subprocess.run(['rtk','proxy','git']+args,cwd=cwd,capture_output=True,text=True);assert cp.returncode==0
  entries[label]=cp.stdout.strip()
 product[role]=entries
write(OUT/'stopped-product-tree.json',product)
assert all(not row['tracked'] and not row['status'] for row in product.values()),'Unexpected product changes'
frozen_check();write(OUT/'stop-complete.json',{'status':'NAMED_SESSIONS_CLOSED_KNOWN_PIDS_ABSENT_LEDGER_SNAPSHOTTED','actual_tokens':m['actual_tokens'],'calls':m['completed_calls'],'snapshot_files':len(files),'no_extra_prompt':True})
print(json.dumps({'stopped':True,'sessions':len(rows),'known_pids':len(checks),'authority_files':len(files),'tokens':m['actual_tokens'],'calls':m['completed_calls']}))
