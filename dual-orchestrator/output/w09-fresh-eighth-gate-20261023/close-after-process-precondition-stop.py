import datetime,json,os,re,subprocess,time
from pathlib import Path
from gate_support import OUT,read,write,sha,frozen_check,measure
loc=read(OUT/'instance-location.json');workspace=Path(loc['workspace']);ledger=Path(loc['ledger'])
release=frozen_check();before=read(ledger/'workflow/current.json');measured=measure(workspace,ledger)
assert measured['actual_tokens']==848242 and measured['completed_calls']==10
assert before['status']=='RUNNING' and before['activeNodeInstanceIds']==['node-plan-decision-026']
assert not (OUT/'steps/22-readiness-plan-decision').exists()
write(OUT/'stop-required.json',{'reason':'STOP_REQUIRED_PROCESS_PRESENT','pid':83976,'trigger':'Shared reviewed process guard returned PRESENT before step directory or Java start','failed_step':'22-readiness-plan-decision','failed_step_created':False,'next_dispatch':'FORBIDDEN_PENDING_HUMAN_REVIEW','domain_status_unchanged':True,'actual_tokens':848242,'completed_calls':10,'later_snapshot_note':'Successful read-only table no longer showed pid; trigger-time start/command not captured, do not invent identity or silently exempt reuse','experiment_classification':'INVALID_CONTINUATION_PRECONDITION','at':datetime.datetime.now().astimezone().isoformat()})
folder=OUT/'process-stop-cleanup';folder.mkdir(exist_ok=False)
write(folder/'pre-current.json',before)
records={}
for row in measured['completed_runs']:
    for o in row['occurrences']:
        meta_path=Path.home()/'.acpx/sessions'/(o['record_id']+'.json');meta=read(meta_path)
        assert meta['cwd']==str(workspace)
        records[o['record_id']]={'agent':row['agent'],'name':meta['name'],'record_id':o['record_id'],'metadata_path':str(meta_path)}
env=os.environ.copy();env.update(read(OUT/'local-launch-selection.json')['explicit_child_environment'])
wrapper=release['selected_acpx']['wrapper'];results=[]
for index,record in enumerate(records.values(),1):
    record_folder=folder/f'{index:02d}';record_folder.mkdir();meta=read(record['metadata_path']);write(record_folder/'metadata-before.json',meta)
    for action in ['close','show']:
        args=['rtk','proxy',wrapper,record['agent'],'sessions',action,record['name']]
        with (record_folder/f'{action}.stdout.txt').open('w') as so,(record_folder/f'{action}.stderr.txt').open('w') as se:
            start=time.monotonic();process=subprocess.Popen(args,cwd=workspace,env=env,stdout=so,stderr=se)
            write(record_folder/f'{action}.process.json',{'pid':process.pid,'argv':args})
            try:code=process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                write(record_folder/f'{action}.result.json',{'result':'UNKNOWN_TIMEOUT','pid':process.pid,'no_prompt_sent':True});raise
        write(record_folder/f'{action}.result.json',{'exit':code,'elapsed_seconds':round(time.monotonic()-start,3),'no_prompt_sent':True})
        assert code==0,'Cleanup command failed; do not retry or invent stopped status'
    meta=read(record['metadata_path']);write(record_folder/'metadata-after.json',meta)
    assert meta['closed'] is True and meta['name']==record['name'] and meta['cwd']==str(workspace)
    results.append({**record,'closed':True,'close_and_show_exit':0})
after=read(ledger/'workflow/current.json');assert after==before,'Cleanup changed domain status'
measured_after=measure(workspace,ledger);assert measured_after==measured,'Cleanup changed fixed usage'
known={83976}
for f in (OUT/'steps').glob('*/process.json'):known.add(read(f)['pid'])
r=subprocess.run(['rtk','proxy','ps','-axo','pid=,lstart=,command='],capture_output=True,text=True,timeout=5)
rows=[line for line in r.stdout.splitlines() if line.strip() and line.strip().split(maxsplit=1)[0].isdigit() and int(line.strip().split(maxsplit=1)[0]) in known]
write(folder/'process-observation.json',{'exit':r.returncode,'stderr':r.stderr,'successful_complete_table':r.returncode==0 and not r.stderr and bool(r.stdout.strip()),'known_step_parent_pids':sorted(known),'matching_rows':rows,'scope':'Only these listed PIDs in this snapshot, not arbitrary child trees or remote state'})
assert r.returncode==0 and not r.stderr and r.stdout.strip(),'Process observation UNKNOWN; no absent claim'
write(folder/'summary.json',{'sessions':results,'same_domain_current':True,'same_fixed_usage':True,'actual_tokens':848242,'completed_calls':10,'model_calls_added':0,'known_parent_pids_present':rows,'not_claimed':'All child processes or remote operations stopped'})
print(json.dumps({'session_count':len(results),'close_show_and_closed_metadata':True,'domain_unchanged':True,'usage_unchanged':True,'known_step_parent_rows':rows,'model_calls_added':0},ensure_ascii=False))
