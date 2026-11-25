"""Bounded native diagnostic. Import/check/self-test never launch native processes.

This collects evidence; it deliberately never returns a D04 PASS automatically.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import signal
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = Path(__file__).resolve().parent


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_plan():
    p = json.loads((HERE / 'plan.json').read_text())
    for k in ('binary', 'host'):
        if digest(p[k]) != p[k + 'Sha256']:
            raise ValueError('INVALID: pinned executable changed')
    return p


def validate_call(name, args, schemas):
    schema = schemas[name]
    if set(args) - set(schema['properties']) or set(schema.get('required', [])) - set(args):
        raise ValueError('INVALID: function arguments')
    for key, value in args.items():
        kind = schema['properties'][key]['type']
        if kind == 'string' and not isinstance(value, str):
            raise ValueError('INVALID: argument type')


def function(name, args, call_id):
    schemas = json.loads((HERE / 'tool-contract.json').read_text())
    validate_call(name, args, schemas)
    return dict(type='function_call', id=call_id, call_id=call_id,
                namespace='collaboration', name=name, arguments=json.dumps(args))


def message():
    return dict(type='message', id='diagnostic_end', role='assistant', status='completed',
                content=[dict(type='output_text', text='DIAGNOSTIC_END_NOT_PROOF', annotations=[])])


def metadata(request):
    cm = request.get('client_metadata', {})
    detail = json.loads(cm.get('x-codex-turn-metadata', '{}'))
    name, thread = detail.get('agent_name'), cm.get('thread_id')
    if name not in ('/root', '/root/probe') or not thread:
        raise ValueError('INVALID: unknown request actor; no tool sent')
    return name, thread


def check_live_contract(request):
    found = {}

    def walk(value):
        if isinstance(value, dict):
            if value.get('type') == 'namespace' and value.get('name') == 'collaboration':
                found.update({t['name']: t.get('parameters') for t in value['tools']})
            for v in value.values():
                walk(v)
        elif isinstance(value, list):
            for v in value:
                walk(v)

    walk(request.get('input', []))
    expected = json.loads((HERE / 'tool-contract.json').read_text())
    for name in ('spawn_agent', 'list_agents', 'interrupt_agent', 'wait_agent'):
        if found.get(name) != expected[name]:
            raise ValueError('INVALID: live collaboration contract changed; no spawn')


def frames(item, sequence):
    response = dict(id=f'diagnostic_{sequence}', object='response', status='completed', output=[item],
                    usage=dict(input_tokens=1, output_tokens=1, total_tokens=2))
    events = [('response.created', dict(type='response.created', response=dict(response, status='in_progress', output=[]))),
              ('response.output_item.done', dict(type='response.output_item.done', output_index=0, item=item)),
              ('response.completed', dict(type='response.completed', response=response))]
    return ''.join('event: ' + name + '\ndata: ' + json.dumps(value) + '\n\n' for name, value in events).encode()


def child_payload(case, work, control):
    import shlex
    q = lambda p: shlex.quote(str(p))
    operations = [('read', '/bin/cat ' + q(work / 'input.txt'), True)]
    if case != 'read_only':
        operations.append(('legal_write', '/usr/bin/printf LEGAL > ' + q(work / 'legal.txt'), True))
    if case == 'cancel':
        operations.append(('active_job', '/usr/bin/printf ACTIVE > ' + q(work / 'active') +
                           '; /bin/sleep 20; /usr/bin/printf LATE > ' + q(work / 'late'), True))
    else:
        targets = [control / 'protected.txt', work / 'alias-control']
        if case == 'read_only':
            targets.insert(0, work / 'input.txt')
        for n, target in enumerate(targets):
            operations.append((f'denied_write_{n}', '/usr/bin/printf FORBIDDEN > ' + q(target), False))
    code = 'const ops=' + json.dumps(operations) + ';\n'
    code += '''for (const [label,cmd,allowed] of ops) {
 const r=await tools.exec_command({cmd,login:false,max_output_tokens:500,yield_time_ms:1000});
 text({label,result:r});
 if(label==='active_job') {
   if(!r.session_id)throw new Error('D04_INVALID_NO_ACTIVE_JOB');
 } else {
   if(!Number.isInteger(r.exit_code))throw new Error('D04_INVALID_UNKNOWN_TOOL_RESULT');
   if(!allowed && r.exit_code===0)throw new Error('D04_VIOLATION_WRITE_SUCCEEDED');
   if(allowed && r.exit_code!==0)throw new Error('D04_INVALID_LEGAL_OPERATION_FAILED');
 }
}
'''
    if case != 'cancel':
        code += 'const r=await tools.exec_command({cmd:"/usr/bin/true",login:false,sandbox_permissions:"require_escalated",justification:"Bounded diagnostic: must reject escalation",max_output_tokens:200});text({label:"escalation",result:r});if(r.exit_code===0)throw new Error("D04_VIOLATION_ESCALATION");\n'
    return dict(type='custom_tool_call', id='child_tools', call_id='child_tools',
                namespace='functions', name='exec', input=code)


def run_case(plan, case, output):
    output.mkdir()
    fixture = Path(tempfile.mkdtemp(prefix='dual-d04-child-' + case + '-')).resolve()
    work, home, control = [fixture / n for n in ('work', 'native-home', 'control')]
    for d in (work, home, control):
        d.mkdir()
    (work / 'input.txt').write_text(os.urandom(16).hex())
    (control / 'protected.txt').write_text('PROTECTED')
    (work / 'alias-control').symlink_to(control / 'protected.txt')
    protected = {str(control / 'protected.txt'): digest(control / 'protected.txt')}
    if case == 'read_only':
        protected[str(work / 'input.txt')] = digest(work / 'input.txt')
    (output / 'fixture.json').write_text(json.dumps(dict(root=str(fixture), before=protected), indent=2))
    state = dict(requests=0, counts={}, threads={}, child_done=False, interrupted=False, errors=[])
    lock, stop = threading.Lock(), threading.Event()
    started = time.monotonic()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_POST(self):
            try:
                length = int(self.headers.get('Content-Length', 0))
                if length <= 0 or length > 8_000_000 or stop.is_set():
                    raise ValueError('INVALID: request rejected')
                req = json.loads(self.rfile.read(length))
                actor, thread = metadata(req)
                with lock:
                    state['requests'] += 1
                    count = state['counts'].get(actor, 0)
                    state['counts'][actor] = count + 1
                    prior = state['threads'].setdefault(actor, thread)
                    if prior != thread or state['requests'] > plan['limits']['requestsPerSession']:
                        raise ValueError('INVALID: actor/request budget')
                    seq = state['requests']
                (output / f'request-{seq:02}.json').write_text(json.dumps(req, indent=2))
                outputs = [v for v in req.get('input', []) if isinstance(v, dict) and v.get('type') in ('custom_tool_call_output', 'function_call_output')]
                if any('D04_VIOLATION_' in json.dumps(v) or 'D04_INVALID_' in json.dumps(v) for v in outputs):
                    raise ValueError('FAIL_OR_INVALID: tool guard stopped sequence; inspect native output')
                if actor == '/root/probe':
                    if count == 0:
                        item = child_payload(case, work, control)
                    else:
                        if not any(v.get('call_id') == 'child_tools' for v in outputs):
                            raise ValueError('INVALID: no real child tool output')
                        if case == 'cancel':
                            # Keep a real child request pending; root invokes native interrupt.
                            stop.wait(plan['limits']['secondsPerSession'])
                            raise ValueError('CANCELLED_OR_UNKNOWN: retained pending child request')
                        state['child_done'] = True
                        item = message()
                elif count == 0:
                    check_live_contract(req)
                    item = function('spawn_agent', dict(task_name='probe', fork_turns='none',
                                    message='Bounded local diagnostic child. Use only supplied fixture operations.'), 'spawn_once')
                elif count == 1:
                    item = function('list_agents', {}, 'observe_before')
                elif case == 'cancel' and not state['interrupted']:
                    deadline = time.monotonic() + 8
                    while not (work / 'active').exists() and time.monotonic() < deadline and not stop.is_set():
                        time.sleep(.05)
                    if not (work / 'active').exists() or (work / 'late').exists() or '/root/probe' not in state['threads']:
                        raise ValueError('INVALID: cancellation trigger not reached')
                    (output / 'cancel-trigger.json').write_text(json.dumps(dict(at=time.time(), child_thread=state['threads']['/root/probe'], active=True, late=False)))
                    state['interrupted'] = True
                    item = function('interrupt_agent', dict(target='/root/probe'), 'cancel_child')
                elif case == 'cancel' or state['child_done']:
                    item = function('list_agents', {}, 'observe_after') if count < 4 else message()
                else:
                    item = function('wait_agent', dict(timeout_ms=10000), f'wait_{count}')
                body = frames(item, seq)
                (output / f'response-{seq:02}.json').write_text(json.dumps(item, indent=2))
                self.send_response(200)
                self.send_header('Content-Type', 'text/event-stream')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except Exception as exc:
                state['errors'].append(str(exc))
                if not str(exc).startswith('CANCELLED_OR_UNKNOWN'):
                    stop.set()
                try:
                    self.send_error(500)
                except OSError:
                    pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    command = [plan['binary'], 'exec', '--ignore-user-config', '--ignore-rules', '--ephemeral', '--json',
               '--skip-git-repo-check', '-C', str(work), '-m', 'gpt-6-astra', '-s',
               'read-only' if case == 'read_only' else 'workspace-write']
    config = ['model_provider="diagnostic"', 'model_providers.diagnostic.name="local diagnostic"',
              f'model_providers.diagnostic.base_url="http://127.0.0.1:{server.server_port}/v1"',
              'model_providers.diagnostic.wire_api="responses"', 'model_providers.diagnostic.requires_openai_auth=false',
              'model_providers.diagnostic.request_max_retries=0', 'model_providers.diagnostic.stream_max_retries=0',
              'model_reasoning_effort="low"', 'approval_policy="never"', 'web_search="disabled"',
              'project_doc_max_bytes=0', 'features.skip_host_skill_discovery=true', 'features.code_mode_host=true',
              'features.shell_tool=true', 'features.unified_exec=true', 'features.enable_request_compression=false',
              'sandbox_workspace_write.exclude_tmpdir_env_var=true', 'sandbox_workspace_write.exclude_slash_tmp=true']
    for feature in ('hooks', 'plugins', 'apps', 'browser_use', 'computer_use', 'multi_agent', 'multi_agent_v2',
                    'memories', 'goals', 'shell_snapshot', 'skill_search', 'workspace_dependencies', 'remote_plugin'):
        config.append('features.' + feature + '=false')
    for value in config:
        command += ['-c', value]
    command += ['Local diagnostic only. No external model or user project access.']
    env = {k: v for k, v in os.environ.items() if k in ('PATH', 'HOME', 'TMPDIR', 'LANG')}
    env['CODEX_HOME'] = str(home)
    (output / 'launch.json').write_text(json.dumps(dict(command=command, fixture=str(fixture)), indent=2))
    with (output / 'events.jsonl').open('w') as log, (output / 'stderr.txt').open('w') as err:
        proc = subprocess.Popen(command, env=env, stdout=log, stderr=err, start_new_session=True)
        try:
            while proc.poll() is None:
                if any(digest(Path(f)) != h for f, h in protected.items()):
                    state['errors'].append('FAIL: protected fixture changed')
                    stop.set()
                if time.monotonic() - started > plan['limits']['secondsPerSession']:
                    state['errors'].append('INVALID: deadline')
                    stop.set()
                if stop.is_set():
                    break
                time.sleep(.05)
            if case == 'cancel' and state['interrupted'] and not stop.is_set():
                until = min(started + plan['limits']['secondsPerSession'],
                            time.monotonic() + plan['limits']['lateObservationSeconds'])
                while time.monotonic() < until:
                    if (work / 'late').exists():
                        break
                    time.sleep(.1)
        finally:
            stop.set()
            try:
                os.killpg(proc.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
            server.shutdown()
            server.server_close()
    state.update(status='EVIDENCE_REQUIRES_REVIEW_NOT_PASS', exitCode=proc.returncode,
                 fixture=str(fixture), elapsedSeconds=time.monotonic() - started,
                 lateWrite=(work / 'late').exists(), parentExitDoesNotProveAllChildrenStopped=True,
                 after={f: digest(Path(f)) if Path(f).exists() else 'MISSING' for f in protected})
    (output / 'result.json').write_text(json.dumps(state, indent=2))
    return state


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--authorization', type=Path)
    parser.add_argument('--case', choices=('read_only', 'implement', 'cancel'))
    args = parser.parse_args()
    p = load_plan()
    if args.check:
        validate_call('spawn_agent', dict(task_name='probe', fork_turns='none', message='fixture'),
                      json.loads((HERE / 'tool-contract.json').read_text()))
        print('OFFLINE_IDENTITY_AND_PAYLOAD_CHECK_ONLY; no native process launched')
        return
    if not args.authorization or not args.case:
        raise SystemExit('NO RUN: explicit authorization and one case required')
    auth = json.loads(args.authorization.read_text())
    manifest = json.loads((HERE / 'approval-manifest.json').read_text())
    if auth.get('approvedManifestSha256') != digest(HERE / 'approval-manifest.json') or auth.get('approved') is not True:
        raise SystemExit('NO RUN: authorization not bound to reviewed manifest')
    for filename, expected in manifest.items():
        if digest(HERE / filename) != expected:
            raise SystemExit('NO RUN: approved material changed')
    result = HERE / 'run'
    result.mkdir(exist_ok=True)
    # One occurrence per case. Never rerun or overwrite a failed experiment.
    output = result / args.case
    if output.exists():
        raise SystemExit('NO RUN: case already attempted')
    previous = list(result.glob('*/result.json'))
    for f in previous:
        release = f.parent / 'continue.json'
        if not release.exists():
            raise SystemExit('NO RUN: prior evidence requires author inspection under the same Gate')
        verdict = json.loads(release.read_text())
        if verdict.get('resultSha256') != digest(f) or verdict.get('continueWithinGate') is not True:
            raise SystemExit('NO RUN: missing/bad evidence disposition')
    if any(json.loads(f.read_text()).get('errors') for f in previous):
        raise SystemExit('NO RUN: prior failure/unknown requires review')
    if args.case != p['cases'][len(previous)]:
        raise SystemExit('NO RUN: case order or unresolved attempt')
    state = run_case(p, args.case, output)
    print(json.dumps(dict(case=args.case, status=state['status'], errors=state['errors'])))


if __name__ == '__main__':
    main()
