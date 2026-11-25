import datetime,json,os,subprocess,sys,time
from pathlib import Path
from gate_support import OUT,read,write,sha,frozen_check,measure
from process_check import require_known_pids_absent
loc=read(OUT/'instance-location.json'); workspace=Path(loc['workspace']); ledger=Path(loc['ledger'])
assert read(OUT/'run-authorization.json')['fresh_run_authorized'] is True
assert not (OUT/'stop-required.json').exists()
release=frozen_check()
subject='node-gate-human_plan_approval-021'; gate_id='gate-'+subject
current=read(ledger/'workflow/current.json')
assert current['status']=='WAITING_FOR_HUMAN' and current['activeNodeInstanceIds']==[subject] and current['openGateIds']==[gate_id]
snapshot=read(OUT/'readiness-pause-ledger-sha256.json')['files']
assert all(sha(ledger/p)==h for p,h in snapshot.items()),'Paused ledger changed'
assert {str(p.relative_to(ledger)) for p in ledger.rglob('*') if p.is_file() and p.name!='.mutation.lock'}==set(snapshot),'Paused ledger file set changed'
m=measure(workspace,ledger);assert m['actual_tokens']==563916 and m['completed_calls']==8
assert not list(ledger.rglob('pending/*.json'))
processes=require_known_pids_absent([x['pid'] for x in read(OUT/'old-execution-recheck.json')['known_pids']])
folder=OUT/'human-decisions/02-readiness';folder.mkdir(parents=True,exist_ok=False)
write(folder/'authorization.json',{'received_user_message':'同意','scope':'Confirm current Python/stdlib availability declarations, original record-readiness, revise plan for exact refs and independent review; no final EXECUTION, delivery, commit or native permission decision delegated','approved_document':str(OUT/'readiness-approval.md'),'approved_document_sha256':sha(OUT/'readiness-approval.md'),'subject':subject,'gate':gate_id,'at':datetime.datetime.now().astimezone().isoformat(),'process_observation':processes})
env=os.environ.copy();env.update(read(OUT/'local-launch-selection.json')['explicit_child_environment'])
cli=['rtk','proxy','java','-jar',release['application_jars']['dual-cli']['path'],'review']
refs=[]
for criterion in ['AC-1','AC-2','AC-3']:
    draft=read(OUT/'readiness-drafts'/f'{criterion}.json')
    assert draft['subject']==subject and draft['epoch']==1 and draft['criterionIds']==[criterion]
    assert all(sha(d['path'])==d['sha256'] for d in draft['environment']['dependencies'])
    assert draft['statement'].startswith('待用户确认：')
    draft['statement']='用户已确认：'+draft['statement'].removeprefix('待用户确认：')
    request=folder/f'{criterion}.json';write(request,draft)
    args=cli+['record-readiness','--workdir',str(workspace),'--subject',subject,'--request-id',draft['requestId'],'--file',str(request),loc['workstream']]
    start=time.monotonic()
    with (folder/f'{criterion}.stdout.txt').open('w') as so,(folder/f'{criterion}.stderr.txt').open('w') as se:
        p=subprocess.Popen(args,cwd=workspace,env=env,stdout=so,stderr=se)
        write(folder/f'{criterion}.process.json',{'pid':p.pid,'argv':args})
        code=p.wait(timeout=60)
    write(folder/f'{criterion}.result.json',{'exit':code,'elapsed_seconds':round(time.monotonic()-start,3)})
    assert code==0,'Original record-readiness failed; stop without retry'
    ref=f'.agent/workstreams/{loc["workstream"]}/artifacts/verification-readiness/{draft["requestId"]}.json'
    fixed=read(workspace/ref);assert fixed['source']=='OPERATOR_ATTESTED' and fixed['request']==draft
    refs.append({'criterion_id':criterion,'ref':ref,'sha256':sha(workspace/ref),'method':draft['method']})
write(folder/'fixed-readiness-references.json',refs)
feedback=folder/'feedback.md'
feedback.write_text('用户已确认当前环境声明。请仅补齐当前已审计划各 verification_binding 的 environment_refs 和 feasibility_refs，逐条使用下列原 owner 已登记的 ref/SHA；method、criterion_id、executor、evidence_kind、detail_owner、退出码要求、三个 CREATE 文件以及其他已审计划语义保持不变。声明只证明 operator 认可前提可用，不证明原生工具执行或测试成功；实际证据仍在后续自测/验收采集。不要创造引用、不要把人工声明写为机器观察。补齐后经原独立计划复评和当前 EXECUTION 精确批准，不提前实施。\n\n'+json.dumps(refs,ensure_ascii=False,indent=2)+'\n')
args=cli+['revise','--workdir',str(workspace),'--subject',subject,'--feedback-file',str(feedback),loc['workstream']]
start=time.monotonic()
with (folder/'revise.stdout.txt').open('w') as so,(folder/'revise.stderr.txt').open('w') as se:
    p=subprocess.Popen(args,cwd=workspace,env=env,stdout=so,stderr=se)
    write(folder/'revise.process.json',{'pid':p.pid,'argv':args})
    code=p.wait(timeout=60)
write(folder/'revise.result.json',{'exit':code,'elapsed_seconds':round(time.monotonic()-start,3)})
assert code==0,'Original revise failed; stop without retry'
after=read(ledger/'workflow/current.json');write(folder/'post-current.json',after)
assert after['status']=='RUNNING' and not after['openGateIds']
node=read(ledger/'nodes'/(after['activeNodeInstanceIds'][0]+'.json'));write(folder/'post-node.json',node)
print(json.dumps({'readiness_records':3,'original_revise':code,'next_node':node['nodeDefinitionId'],'status':node['status'],'model_calls_added':0},ensure_ascii=False))
