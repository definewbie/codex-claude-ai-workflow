import os,sys,json,hashlib,subprocess,time,datetime
from pathlib import Path
root=Path(__file__).resolve().parents[2]
out=Path(__file__).resolve().parent
workspace=Path('/private/tmp/dual-w09-newlines-20261010/worktree')
ws='ws-20261010T055644Z-6QCS'
ledger=workspace/'.agent/workstreams'/ws
step,expected=sys.argv[1:]
folder=out/'steps'/step
assert not folder.exists(), 'Do not repeat a prior step blindly'
release=json.loads((root/'output/w08-preparation-20261010/release-identity.json').read_text())
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert all(sha(root/p)==h for p,h in release['source_inputs'].items()), 'Frozen source drift'
assert all(sha(j['path'])==j['sha256'] for j in release['application_jars'].values()), 'JAR drift'
current=json.loads((ledger/'workflow/current.json').read_text())
assert current['status']=='RUNNING' and not current['openGateIds'], 'Current workflow is paused or terminal'
assert len(current['activeNodeInstanceIds'])==1
node=json.loads((ledger/'nodes'/(current['activeNodeInstanceIds'][0]+'.json')).read_text())
assert node['nodeDefinitionId']==expected and node['status']=='READY', 'Wrong node or active execution'
folder.mkdir()
(folder/'pre-state.json').write_text(json.dumps({'current':current,'node':node},ensure_ascii=False,indent=2)+'\n')
selection=json.loads((root/'output/w09-local-preflight-20261010/local-launch-selection.json').read_text())['explicit_child_environment']
env=os.environ.copy();env.update(selection)
args=['java','-Xlog:class+load=debug:file='+str(folder/'classloading.log')+':uptime,level,tags','-jar',release['application_jars']['dual-cli']['path'],'run','--workdir='+str(workspace),'--max-steps','1','--dispatch-mode','INLINE',ws]
t=time.monotonic()
with (folder/'stdout.txt').open('w') as so,(folder/'stderr.txt').open('w') as se:
 p=subprocess.Popen(args,cwd=workspace,env=env,stdout=so,stderr=se)
 (folder/'process.json').write_text(json.dumps({'pid':p.pid,'started_at':datetime.datetime.now().astimezone().isoformat(),'argv':args,'expected_node':expected},indent=2)+'\n')
 print(json.dumps({'step':step,'pid':p.pid,'node':expected,'started':True}),flush=True)
 while True:
  try:code=p.wait(timeout=30);break
  except subprocess.TimeoutExpired:
   print(json.dumps({'step':step,'elapsed_seconds':round(time.monotonic()-t),'runner_alive':True}),flush=True)
 elapsed=round(time.monotonic()-t,3)
(folder/'result.json').write_text(json.dumps({'exit':code,'elapsed_seconds':elapsed,'finished_at':datetime.datetime.now().astimezone().isoformat()},indent=2)+'\n')
current=json.loads((ledger/'workflow/current.json').read_text())
(folder/'post-current.json').write_text(json.dumps(current,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'step':step,'exit':code,'elapsed_seconds':elapsed,'workflow_status':current['status'],'active_nodes':current['activeNodeInstanceIds'],'open_gates':current['openGateIds']},ensure_ascii=False),flush=True)
