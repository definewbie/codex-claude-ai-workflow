from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib, json, os, runpy, signal, subprocess, sys, tempfile, threading, time

out = Path(__file__).resolve().parent
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
budget = json.loads((out / 'capture-budget.json').read_text())
assert sha(out / 'verification-gate.md') == budget['gateSha256']
assert sha(out / 'capture-guard.py') == budget['guardSha256']
assert sha(budget['binary']) == budget['binarySha256']
assert sha(budget['host']) == budget['hostSha256']
assert all(x['pass'] for x in json.loads((out / 'guard-offline-tests.json').read_text()))
variant = sys.argv[1]
assert variant in ('structured', 'exclusions')
assert len(list(out.glob('capture-*/started.json'))) < budget['maxNativeSessions']
assert not list(out.glob('capture-*/STOP'))
dest = out / ('capture-' + variant)
dest.mkdir()
checked_final = runpy.run_path(str(out / 'capture-guard.py'))['checked_final']
fixture = Path(tempfile.mkdtemp(prefix='d04-interface-capture-')).resolve()
for name in ('home', 'codex-home', 'work', 'tmp'):
    (fixture / name).mkdir()
oracle = fixture / 'work/input.txt'
oracle.write_text('INTERFACE_FIXTURE_UNCHANGED\n')
before = sha(oracle)
requests, errors = [], []
stop = threading.Event()

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_POST(self):
        try:
            request = json.loads(self.rfile.read(int(self.headers.get('Content-Length', '0'))))
            requests.append(request)
            item = checked_final({'type':'message', 'id':'msg_diag', 'role':'assistant',
                'status':'completed', 'content':[{'type':'output_text','text':'CAPTURE_DONE','annotations':[]}]}, len(requests))
            response = {'id':'resp_diag', 'object':'response','status':'completed','output':[item],
                'usage':{'input_tokens':1,'output_tokens':1,'total_tokens':2}}
            frames = [('response.created', {'type':'response.created','response':dict(response,status='in_progress',output=[])}),
                ('response.output_item.done', {'type':'response.output_item.done','output_index':0,'item':item}),
                ('response.completed', {'type':'response.completed','response':response})]
            body = ''.join('event: '+name+'\ndata: '+json.dumps(data)+'\n\n' for name,data in frames).encode()
            self.send_response(200)
            self.send_header('Content-Type','text/event-stream')
            self.send_header('Content-Length',str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except Exception as error:
            errors.append(str(error))
            stop.set()
            self.send_error(500)

server = ThreadingHTTPServer(('127.0.0.1',0),Handler)
threading.Thread(target=server.serve_forever,daemon=True).start()
cmd = [budget['binary'],'exec','--strict-config','--ignore-user-config','--ignore-rules',
    '--ephemeral','--json','--skip-git-repo-check','-C',str(fixture/'work'),'-m','gpt-6-astra','-s','read-only']
config = ['model_provider="diagnostic"','model_providers.diagnostic.name="local diagnostic"',
    f'model_providers.diagnostic.base_url="http://127.0.0.1:{server.server_port}/v1"',
    'model_providers.diagnostic.wire_api="responses"','model_providers.diagnostic.requires_openai_auth=false',
    'model_providers.diagnostic.request_max_retries=0','model_providers.diagnostic.stream_max_retries=0',
    'model_reasoning_effort="low"','approval_policy="never"','web_search="disabled"',
    'project_doc_max_bytes=0','features.skip_host_skill_discovery=true',
    'features.code_mode_host=true','features.shell_tool=true','features.unified_exec=true',
    'features.enable_request_compression=false','analytics.enabled=false','check_for_update_on_startup=false',
    'features.multi_agent=false','features.multi_agent_v2.enabled=false']
for feature in ['hooks','plugins','apps','browser_use','computer_use','memories','goals','shell_snapshot','skill_search','workspace_dependencies','remote_plugin']:
    config.append('features.'+feature+'=false')
if variant == 'exclusions':
    config.append('features.code_mode.excluded_tool_namespaces=["collaboration"]')
for entry in config: cmd += ['-c',entry]
cmd += ['Local interface capture only.']
env = {'PATH':'/usr/bin:/bin:/opt/homebrew/bin','HOME':str(fixture/'home'),
    'CODEX_HOME':str(fixture/'codex-home'),'TMPDIR':str(fixture/'tmp'),'LANG':'en_US.UTF-8'}
(dest/'started.json').write_text(json.dumps({'command':cmd,'env':env,'driverSha256':sha(__file__),
    'gateSha256':budget['gateSha256'],'guardSha256':budget['guardSha256'],'budgetSha256':sha(out/'capture-budget.json')},indent=2)+'\n')
started = time.monotonic()
with (dest/'events.jsonl').open('w') as log, (dest/'stderr.txt').open('w') as err:
    proc = subprocess.Popen(cmd,env=env,cwd=fixture/'work',stdout=log,stderr=err,start_new_session=True)
    try:
        while proc.poll() is None:
            elapsed = time.monotonic()-started
            events = (dest/'events.jsonl').read_text()
            if stop.is_set() or elapsed>budget['maxSecondsPerSession'] or sha(oracle)!=before or any(x in events for x in ['"type":"command_execution"','"type":"mcp_tool_call"','"type":"collab_tool_call"']):
                errors.append('STOP_GUARD')
                stop.set()
                os.killpg(proc.pid,signal.SIGKILL)
                break
            time.sleep(.02)
        proc.wait(timeout=5)
    finally:
        if proc.poll() is None: os.killpg(proc.pid,signal.SIGKILL); proc.wait()
        server.shutdown(); server.server_close()
if stop.is_set(): (dest/'STOP').write_text('\n'.join(errors))
tools = [t for req in requests for item in req.get('input',[]) if item.get('type')=='additional_tools' for t in item.get('tools',[])]
exposed = [{'type':t.get('type'),'name':t.get('name'),'tools':[c.get('name') for c in t.get('tools',[])]} for t in tools]
result = {'variant':variant,'exitCode':proc.returncode,'requests':requests,'errors':errors,'elapsedSeconds':time.monotonic()-started,
    'exposed':exposed,'oracleUnchanged':sha(oracle)==before,'realProviderCalls':0,'toolCommandsEmitted':0,
    'scope':'advertisement capture only; no dispatch proof', 'status':'OBSERVED' if proc.returncode==0 and len(requests)==1 and not errors else 'INVALID'}
(dest/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['variant','exitCode','status','errors','exposed','elapsedSeconds']}))
