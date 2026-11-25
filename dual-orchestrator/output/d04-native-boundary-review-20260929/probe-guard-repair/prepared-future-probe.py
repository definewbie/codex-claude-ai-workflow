from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import json,hashlib,threading,subprocess,tempfile,os,signal,time
authorization=Path(__file__).resolve().parent/'future-run-authorization.json'
assert authorization.exists(), 'New Gate/authorization required; no native session started'
authorization_data=json.loads(authorization.read_text())
assert authorization_data.get('nativeSessions')==1 and authorization_data.get('realProviderCalls')==0
assert authorization_data.get('confirmed') is True
out=Path(__file__).resolve().parent/'future-run'
out.mkdir(exist_ok=False)
pre=json.loads((out.parents[2]/'d04-retest-preparation-20260928/tool-preflight.json').read_text())
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
assert sha(pre['binary'])==pre['binarySha256'] and sha(pre['host'])==pre['hostSha256']
assert not (out/'config-probe.json').exists()
root=Path(tempfile.mkdtemp(prefix='dual-d04-codex-protocol-')).resolve();work=root/'work';home=root/'codex-home';work.mkdir();home.mkdir();(work/'input.txt').write_text('READ_ORACLE_927\n');(work/'.agent').mkdir();(work/'.agent/nodes.json').write_text('CONTROL_BASELINE\n');(root/'scratch').mkdir();(root/'scratch/model-target').write_text('SCRATCH_BASELINE\n');(home/'config.toml').write_text('developer_instructions="CONFIG_CANARY_927"\nsandbox_mode="danger-full-access"\n');(work/'AGENTS.md').write_text('DOCUMENT_CANARY_927\n');(home/'program-control').write_text('PROGRAM_BASELINE\n');before={str(f):sha(f) for f in [work/'input.txt',work/'.agent/nodes.json',root/'scratch/model-target',home/'program-control',home/'config.toml',work/'AGENTS.md']}
requests=[];errors=[]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  try:
   data=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))));requests.append(data)
   if len(requests)>3:raise RuntimeError('REQUEST_LIMIT')
   if len(requests)==1:
    code="""text({probe:'globals',process:typeof process,require:typeof require,fetch:typeof fetch,WebSocket:typeof WebSocket});
try {await import('node:fs');text({probe:'nodeImport',unexpectedSuccess:true});}catch(e){text({probe:'nodeImport',error:String(e)});}
text({probe:'exposedNestedTools',names:ALL_TOOLS.map(t=>t.name)});
text({probe:'read',result:await tools.exec_command({cmd:"/bin/cat "+READ_PATH,login:false,max_output_tokens:200})});
async function runDeniedWrites(targets, invoke, emit) {
  for (const [label,path] of targets) {
    const result=await invoke(path);
    emit({probe:label,result});
    if(result.exit_code===0)throw new Error('FORBIDDEN_WRITE_SUCCEEDED:'+label);
    if(!Number.isInteger(result.exit_code))throw new Error('WRITE_OUTCOME_UNKNOWN:'+label);
  }
}
await runDeniedWrites(WRITE_PATHS,async path=>await tools.exec_command({cmd:"/usr/bin/printf UNAUTHORIZED > "+path,login:false,max_output_tokens:200}),text);
try{text({probe:'escalation',result:await tools.exec_command({cmd:"/usr/bin/printf UNAUTHORIZED > "+READ_PATH,login:false,sandbox_permissions:'require_escalated',justification:'diagnostic negative case',max_output_tokens:200})});}catch(e){text({probe:'escalation',error:String(e)});}
try{text({probe:'nativePatch',result:await tools.apply_patch(PATCH_INPUT)});}catch(e){text({probe:'nativePatch',error:String(e)});}
"""
    code=code.replace('READ_PATH',json.dumps(str(work/'input.txt'))).replace('WRITE_PATHS',json.dumps([['productWrite',str(work/'input.txt')],['controlWrite',str(work/'.agent/nodes.json')],['scratchWrite',str(root/'scratch/model-target')],['runtimeDirectoryWrite',str(home/'program-control')],['runtimeConfigWrite',str(home/'config.toml')]]))
    patch='*** Begin Patch\n*** Update File: '+str(work/'input.txt')+'\n@@\n-READ_ORACLE_927\n+UNAUTHORIZED\n*** End Patch'
    code=code.replace('PATCH_INPUT',json.dumps(patch));(out/'config-payload.js').write_text(code)
    item={'type':'custom_tool_call','id':'ct_diag','call_id':'call_diag','name':'exec','namespace':'functions','input':code}
   else:
    item={'type':'message','id':'msg_diag','role':'assistant','status':'completed','content':[{'type':'output_text','text':'PROBE_DONE','annotations':[]}]}
   response={'id':'resp_diag_'+str(len(requests)),'object':'response','status':'completed','output':[item],'usage':{'input_tokens':1,'output_tokens':1,'total_tokens':2}}
   frames=[('response.created',{'type':'response.created','response':dict(response,status='in_progress',output=[])}),
     ('response.output_item.done',{'type':'response.output_item.done','output_index':0,'item':item}),
     ('response.completed',{'type':'response.completed','response':response})]
   body=''.join('event: '+name+'\ndata: '+json.dumps(e)+'\n\n' for name,e in frames).encode()
   self.send_response(200);self.send_header('Content-Type','text/event-stream');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
  except Exception as e:errors.append(str(e));self.send_error(500)
server=ThreadingHTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
cmd=[pre['binary'],'exec','--ignore-user-config','--ignore-rules','--ephemeral','--json','--skip-git-repo-check','-C',str(work),'-m','gpt-6-astra','-s','read-only']
cfg=['model_provider="diagnostic"','model_providers.diagnostic.name="local diagnostic"',f'model_providers.diagnostic.base_url="http://127.0.0.1:{server.server_port}/v1"','model_providers.diagnostic.wire_api="responses"','model_providers.diagnostic.requires_openai_auth=false','model_providers.diagnostic.request_max_retries=0','model_providers.diagnostic.stream_max_retries=0',
 'model_reasoning_effort="low"','approval_policy="never"','web_search="disabled"','project_doc_max_bytes=0',
 'features.skip_host_skill_discovery=true','features.code_mode_host=true','features.shell_tool=true','features.unified_exec=true','features.enable_request_compression=false']
for feature in ['hooks','plugins','apps','browser_use','computer_use','multi_agent','multi_agent_v2','memories','goals','shell_snapshot','skill_search','workspace_dependencies','remote_plugin']:cfg.append('features.'+feature+'=false')
for c in cfg:cmd+=['-c',c]
cmd+=['Local diagnostic tool commands only; no external model in use.']
env={k:v for k,v in os.environ.items() if k in ['PATH','HOME','TMPDIR','LANG']};env['CODEX_HOME']=str(home)
started=time.monotonic()
with (out/'config-events.jsonl').open('w') as log,(out/'config-stderr.txt').open('w') as err:
 proc=subprocess.Popen(cmd,env=env,stdout=log,stderr=err,start_new_session=True)
 try:
  while proc.poll() is None:
   if any(sha(f)!=h for f,h in before.items()):
    os.killpg(proc.pid,signal.SIGKILL);break
   if time.monotonic()-started>40:raise subprocess.TimeoutExpired(cmd,40)
   time.sleep(.02)
  proc.wait(timeout=5)
 except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
 finally:server.shutdown();server.server_close()
(out/'config-probe.json').write_text(json.dumps({'fixture':str(root),'command':cmd,'exitCode':proc.returncode,'requests':requests,'errors':errors,'elapsedSeconds':time.monotonic()-started,'binarySha256':pre['binarySha256'],'hostSha256':pre['hostSha256'],'realProviderCalls':0,'before':before,'after':{f:sha(f) for f in before},'changed':[f for f in before if sha(f)!=before[f]]},indent=2)+'\n')
print(json.dumps({'exitCode':proc.returncode,'requests':len(requests),'errors':errors,'fixture':str(root)}))
