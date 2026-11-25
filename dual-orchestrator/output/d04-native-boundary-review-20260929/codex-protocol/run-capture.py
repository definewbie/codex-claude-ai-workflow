from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import json,hashlib,threading,subprocess,tempfile,os,signal,time
out=Path(__file__).resolve().parent
pre=json.loads((out.parents[1]/'d04-retest-preparation-20260928/tool-preflight.json').read_text())
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
assert sha(pre['binary'])==pre['binarySha256'] and sha(pre['host'])==pre['hostSha256']
assert not (out/'capture.json').exists()
root=Path(tempfile.mkdtemp(prefix='dual-d04-codex-protocol-')).resolve();work=root/'work';home=root/'codex-home';work.mkdir();home.mkdir();(work/'input.txt').write_text('CAPTURE_FIXTURE\n')
requests=[];errors=[]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  try:
   data=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))));requests.append(data)
   if len(requests)>3:raise RuntimeError('REQUEST_LIMIT')
   item={'type':'message','id':'msg_diag','role':'assistant','status':'completed','content':[{'type':'output_text','text':'CAPTURE_DONE','annotations':[]}]}
   response={'id':'resp_diag','object':'response','status':'completed','output':[item],'usage':{'input_tokens':1,'output_tokens':1,'total_tokens':2}}
   frames=[('response.created',{'type':'response.created','response':dict(response,status='in_progress',output=[])}),
    ('response.output_item.added',{'type':'response.output_item.added','output_index':0,'item':dict(item,content=[],status='in_progress')}),
    ('response.content_part.added',{'type':'response.content_part.added','item_id':'msg_diag','output_index':0,'content_index':0,'part':{'type':'output_text','text':'','annotations':[]}}),
    ('response.output_text.delta',{'type':'response.output_text.delta','item_id':'msg_diag','output_index':0,'content_index':0,'delta':'CAPTURE_DONE'}),
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
cmd+=['Capture the native tool interface; do not access any files.']
env={k:v for k,v in os.environ.items() if k in ['PATH','HOME','TMPDIR','LANG']};env['CODEX_HOME']=str(home)
started=time.monotonic()
with (out/'capture-events.jsonl').open('w') as log,(out/'capture-stderr.txt').open('w') as err:
 proc=subprocess.Popen(cmd,env=env,stdout=log,stderr=err,start_new_session=True)
 try:proc.wait(timeout=40)
 except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
 finally:server.shutdown();server.server_close()
(out/'capture.json').write_text(json.dumps({'fixture':str(root),'command':cmd,'exitCode':proc.returncode,'requests':requests,'errors':errors,'elapsedSeconds':time.monotonic()-started,'binarySha256':pre['binarySha256'],'hostSha256':pre['hostSha256'],'realProviderCalls':0},indent=2)+'\n')
print(json.dumps({'exitCode':proc.returncode,'requests':len(requests),'errors':errors,'fixture':str(root)}))
