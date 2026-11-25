import os,sys,json,hashlib,subprocess,time,datetime
from pathlib import Path
root=Path(__file__).resolve().parents[2]
out=Path(__file__).resolve().parent
loc=json.loads((out/'instance-location.json').read_text())
workspace=Path(loc['workspace']);ws=loc['workstream'];ledger=Path(loc['ledger'])
step,expected=sys.argv[1:];folder=out/'steps'/step
assert not folder.exists(), 'Do not repeat a prior step blindly'
release=json.loads((root/'output/w09-evidence-and-usage-forward-fix-20261010/release-identity.json').read_text())
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
assert all(sha(root/p)==h for p,h in release['source_inputs'].items()), 'Frozen source drift'
assert all(sha(j['path'])==j['sha256'] for j in release['application_jars'].values()), 'JAR drift'
a=release['selected_acpx'];assert all(sha(a[n])==a[n+'_sha256'] for n in ['wrapper','dist'])
for c in release['metadata_config_source_files'].values():assert Path(c['path']).exists()==c['exists'] and (not c['exists'] or sha(c['path'])==c['sha256'])
assert not (workspace/'.acpxrc.json').exists()
previous=[json.loads(p.read_text()) for p in (out/'steps').glob('*/result.json')] if (out/'steps').exists() else []
active=sum(p['elapsed_seconds'] for p in previous);assert active<3000, 'Cannot reserve next 600 seconds within 60 minute active limit'
runs=[json.loads(p.read_text()) for p in (ledger/'runs').glob('*/run.json')]
# Conservative reserve: every previous run could have used one correction.
assert len(runs)*2+2<=18, 'Need actual call accounting before further dispatch'
for r in runs:
 if r.get('finishedAt'):
  u=r.get('usage',{});assert u.get('measurementType') not in [None,'UNAVAILABLE'], 'Missing actual usage; stop'
assert sum(r.get('usage',{}).get('totalTokens',0) for r in runs)<160000
current=json.loads((ledger/'workflow/current.json').read_text())
assert current['status']=='RUNNING' and not current['openGateIds'], 'Current workflow paused or terminal'
assert len(current['activeNodeInstanceIds'])==1
node=json.loads((ledger/'nodes'/(current['activeNodeInstanceIds'][0]+'.json')).read_text())
assert node['nodeDefinitionId']==expected and node['status']=='READY', 'Wrong node or active execution'
identity=json.loads((ledger/'workstream.json').read_text())['frozenRuntimeIdentity']
assert identity['executable.cli']==release['application_jars']['dual-cli']['sha256']
assert identity['executable.server']==release['application_jars']['dual-server']['sha256']
assert identity['kernel.effective']=='liteflow' and identity['behavior.env.DUAL_ACPX_COMMAND_TIMEOUT_SECONDS']=='600'
folder.mkdir(parents=True)
(folder/'pre-state.json').write_text(json.dumps({'current':current,'node':node,'prior_active_seconds':active,'prior_run_count':len(runs)},ensure_ascii=False,indent=2)+'\n')
env=os.environ.copy();env.update(json.loads((out/'local-launch-selection.json').read_text())['explicit_child_environment'])
args=['rtk','proxy','java','-Xlog:class+load=debug:file='+str(folder/'classloading.log')+':uptime,level,tags','-jar',release['application_jars']['dual-cli']['path'],'run','--workdir='+str(workspace),'--max-steps','1','--dispatch-mode','INLINE',ws]
t=time.monotonic()
with (folder/'stdout.txt').open('w') as so,(folder/'stderr.txt').open('w') as se:
 p=subprocess.Popen(args,cwd=workspace,env=env,stdout=so,stderr=se)
 (folder/'process.json').write_text(json.dumps({'pid':p.pid,'started_at':datetime.datetime.now().astimezone().isoformat(),'argv':args,'expected_node':expected},indent=2)+'\n')
 print(json.dumps({'step':step,'pid':p.pid,'node':expected,'started':True}),flush=True)
 while True:
  try:code=p.wait(timeout=30);break
  except subprocess.TimeoutExpired:print(json.dumps({'step':step,'elapsed_seconds':round(time.monotonic()-t),'runner_alive':True}),flush=True)
 elapsed=round(time.monotonic()-t,3)
(folder/'result.json').write_text(json.dumps({'exit':code,'elapsed_seconds':elapsed,'finished_at':datetime.datetime.now().astimezone().isoformat()},indent=2)+'\n')
current=json.loads((ledger/'workflow/current.json').read_text());(folder/'post-current.json').write_text(json.dumps(current,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'step':step,'exit':code,'elapsed_seconds':elapsed,'workflow_status':current['status'],'active_nodes':current['activeNodeInstanceIds'],'open_gates':current['openGateIds']},ensure_ascii=False),flush=True)
