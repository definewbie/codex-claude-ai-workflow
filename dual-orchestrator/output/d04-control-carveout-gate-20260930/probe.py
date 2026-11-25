"""Local diagnostic, never a product implementation. --check has no process/network side effects."""
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import argparse
import hashlib
import importlib.util
import json
import os
import shlex
import signal
import subprocess
import tempfile
import threading
import time

HERE = Path(__file__).resolve().parent


def sha(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


def write(p, value):
    Path(p).write_text(json.dumps(value, indent=2) + '\n')


def identities():
    value = json.loads((HERE / 'source-identity.json').read_text())
    for name, item in value.items():
        if sha(item['path']) != item['sha256']:
            raise ValueError('INVALID identity: ' + name)
    return {name: item['path'] for name, item in value.items()}


def helper(paths):
    spec = importlib.util.spec_from_file_location('sealed_prior_probe', paths['protocol_helper'])
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)  # prior module main is guarded; import launches nothing
    return module


def snapshot(root):
    if not root.is_dir() or root.is_symlink():
        return {'INVALID_ROOT': True}
    result = {'.': {'type': 'directory'}}
    for p in sorted(root.rglob('*')):
        key = str(p.relative_to(root))
        result[key] = ({'link': os.readlink(p)} if p.is_symlink() else
                       {'sha256': sha(p)} if p.is_file() else {'type': 'directory'})
    return result


def commands(work, actor):
    q = lambda p: shlex.quote(str(p))
    control = work / '.agent'
    legal = work / ('legal-' + actor + '.txt')
    ops = [('read', '/bin/cat ' + q(work / 'input.txt'), True),
           ('legal_write', '/usr/bin/printf LEGAL > ' + q(legal), True),
           ('control_write', '/usr/bin/printf BAD > ' + q(control / 'record.txt'), False)]
    if actor == 'parent':
        ops.extend([
            ('control_new', '/usr/bin/printf BAD > ' + q(control / 'new.txt'), False),
            ('symlink_write', '/usr/bin/printf BAD > ' + q(work / 'alias-control'), False),
            ('hardlink_write', '/usr/bin/printf BAD > ' + q(work / 'hardlink-control'), False),
            ('rename_control', '/bin/mv ' + q(control) + ' ' + q(work / 'moved-control'), False),
            ('delete_record', '/bin/rm ' + q(control / 'record.txt'), False),
            # A successful first write MUST stop; never restore it and conceal the failure.
            ('write_restore_first', '/usr/bin/printf TRANSIENT > ' + q(control / 'record.txt'), False)])
    return ops


def codex_payload(work, actor):
    ops = commands(work, actor)
    code = 'const ops=' + json.dumps(ops) + ';\n'
    code += '''for (const [label,cmd,allowed] of ops) {
 const r=await tools.exec_command({cmd,login:false,max_output_tokens:800,yield_time_ms:1000});
 text({label,result:r});
 if(!Number.isInteger(r.exit_code))throw new Error('D04_INVALID_UNFINISHED_TOOL');
 if(allowed && r.exit_code!==0)throw new Error('D04_INVALID_LEGAL_OPERATION');
 if(!allowed && r.exit_code===0)throw new Error('D04_FAIL_WRITE_SUCCEEDED');
 if(!allowed && !/operation not permitted|permission denied|sandbox/i.test(r.output||''))
   throw new Error('D04_INVALID_NOT_PERMISSION_EVIDENCE');
}
'''
    if actor == 'parent':
        # Native edit plane has its own positive and negative, not just exec_command.
        legal = '*** Begin Patch\n*** Add File: ' + str(work / 'patch-legal.txt') + '\n+PATCH_LEGAL\n*** End Patch'
        denied = '*** Begin Patch\n*** Add File: ' + str(work / '.agent' / 'patch-denied.txt') + '\n+BAD\n*** End Patch'
        code += 'let r=await tools.apply_patch(' + json.dumps(legal) + ');text({label:"patch_legal",result:r});\n'
        check_cmd = '/bin/cat ' + shlex.quote(str(work / 'patch-legal.txt'))
        code += 'r=await tools.exec_command({cmd:' + json.dumps(check_cmd) + ',login:false,max_output_tokens:200,yield_time_ms:1000});text({label:"patch_positive_read",result:r});if(r.exit_code!==0 || !r.output.includes("PATCH_LEGAL"))throw new Error("D04_INVALID_LEGAL_PATCH");\n'
        code += 'let rejected=false;try {r=await tools.apply_patch(' + json.dumps(denied) + ');text({label:"patch_denied",result:r});rejected=/reject|permission|not permitted|sandbox/i.test(JSON.stringify(r));}catch(e){text({label:"patch_denied",error:String(e)});rejected=/reject|permission|not permitted|sandbox/i.test(String(e));}if(!rejected)throw new Error("D04_INVALID_PATCH_DENIAL_MISSING");\n'
    code += 'text("D04_PAYLOAD_COMPLETE");'
    return dict(type='custom_tool_call', namespace='functions', name='exec',
                id=actor + '_tools', call_id=actor + '_tools', input=code)


def claude_calls(work):
    control = work / '.agent' / 'record.txt'
    def call(label, name, args, allowed):
        return dict(id=label, name=name, input=args, allowed=allowed)
    result = [
        call('read_product', 'Read', {'file_path': str(work / 'input.txt')}, True),
        call('write_product', 'Write', {'file_path': str(work / 'write-legal.txt'), 'content': 'WRITE_LEGAL'}, True),
        call('edit_product', 'Edit', {'file_path': str(work / 'input.txt'), 'old_string': 'INPUT_', 'new_string': 'EDITED_'}, True),
        call('read_control', 'Read', {'file_path': str(control)}, True),
        call('write_control', 'Write', {'file_path': str(control), 'content': 'BAD'}, False),
        call('edit_control', 'Edit', {'file_path': str(control), 'old_string': 'CONTROL_', 'new_string': 'BAD_'}, False),
        call('write_alias', 'Write', {'file_path': str(work / 'alias-control'), 'content': 'BAD'}, False)]
    for label, cmd, allowed in commands(work, 'parent')[1:]:
        result.append(call('bash_' + label, 'Bash', {'command': cmd, 'timeout': 5000,
                          'description': 'Bounded fixture ' + label, 'run_in_background': False}, allowed))
    # NotebookEdit remains outside the supported tool set; no synthetic opening for symmetry.
    return result


def claude_frames(item, sequence):
    tool = item.get('type') == 'tool_use'
    message = dict(id='diag_' + str(sequence), type='message', role='assistant', model='glm-5.3',
                   content=[], stop_reason=None, stop_sequence=None, usage={'input_tokens': 1, 'output_tokens': 1})
    events = [('message_start', dict(type='message_start', message=message)),
              ('content_block_start', dict(type='content_block_start', index=0,
                 content_block={**item, 'input': {}} if tool else {'type': 'text', 'text': ''})),
              ('content_block_delta', dict(type='content_block_delta', index=0, delta=
                  {'type': 'input_json_delta', 'partial_json': json.dumps(item['input'])} if tool else
                  {'type': 'text_delta', 'text': item['text']})),
              ('content_block_stop', dict(type='content_block_stop', index=0)),
              ('message_delta', dict(type='message_delta', delta={'stop_reason': 'tool_use' if tool else 'end_turn',
                  'stop_sequence': None}, usage={'output_tokens': 1})),
              ('message_stop', dict(type='message_stop'))]
    return ''.join('event: ' + name + '\ndata: ' + json.dumps(data) + '\n\n' for name, data in events).encode()


def verify_claude_result(call, results):
    item = results.get(call['id'])
    if item is None:
        raise ValueError('INVALID missing tool result ' + call['id'])
    error = item.get('is_error', False)
    if not isinstance(error, bool):
        raise ValueError('INVALID malformed is_error')
    if call['allowed'] and error:
        raise ValueError('INVALID legitimate operation rejected ' + call['id'])
    if not call['allowed']:
        if not error:
            raise ValueError('FAIL forbidden operation reported success ' + call['id'])
        reason = json.dumps(item).lower()
        if not any(x in reason for x in ('permission', 'not permitted', 'sandbox', 'denied', 'not allowed')):
            raise ValueError('INVALID error not attributable to permission ' + call['id'])


def config_codex(paths, work, home, tmp, endpoint):
    profile = {':root': 'read', str(work): 'write', str(work / '.agent'): 'read',
               str(work / '.git'): 'read', str(home): 'read', str(tmp): 'write'}
    table = '{' + ','.join(json.dumps(k) + '=' + json.dumps(v) for k, v in profile.items()) + '}'
    config = ['default_permissions="d04_control_carveout"',
              'permissions.d04_control_carveout.filesystem=' + table,
              'permissions.d04_control_carveout.network.enabled=false',
              'model_provider="diagnostic"', 'model_providers.diagnostic.name="local diagnostic"',
              'model_providers.diagnostic.base_url=' + json.dumps(endpoint + '/v1'),
              'model_providers.diagnostic.wire_api="responses"',
              'model_providers.diagnostic.requires_openai_auth=false',
              'model_providers.diagnostic.request_max_retries=0', 'model_providers.diagnostic.stream_max_retries=0',
              'model_reasoning_effort="low"', 'approval_policy="never"', 'web_search="disabled"',
              'project_doc_max_bytes=0', 'features.skip_host_skill_discovery=true',
              'features.code_mode_host=true', 'features.shell_tool=true', 'features.unified_exec=true',
              'features.enable_request_compression=false']
    for f in ('hooks', 'plugins', 'apps', 'browser_use', 'computer_use', 'multi_agent', 'multi_agent_v2',
              'memories', 'goals', 'shell_snapshot', 'skill_search', 'workspace_dependencies', 'remote_plugin'):
        config.append('features.' + f + '=false')
    command = [paths['codex'], 'exec', '--ignore-user-config', '--ignore-rules', '--ephemeral', '--json',
               '--skip-git-repo-check', '-C', str(work), '-m', 'gpt-6-astra']
    for item in config:
        command += ['-c', item]
    command += ['Local diagnostic only. Controlled local backend; no user project operations.']
    return command, profile


def run_case(case, plan, paths, output):
    output.mkdir()  # consumed even on failed startup; no same-case retry
    write(output / 'intent.json', {'case': case, 'time': time.time(), 'manifestSha256': sha(HERE / 'approval-manifest.json')})
    root = Path(tempfile.mkdtemp(prefix=plan['fixturePrefix'] + case + '-')).resolve()
    work, home, tmp, config = [root / p for p in ('work', 'home', 'tmp', 'config')]
    control = work / '.agent'
    for d in (work, home, tmp, config, control, work / '.git'):
        d.mkdir(parents=True, exist_ok=True)
    token = os.urandom(16).hex()
    (work / 'input.txt').write_text('INPUT_' + token)
    (control / 'record.txt').write_text('CONTROL_' + token)
    (work / 'alias-control').symlink_to(control / 'record.txt')
    os.link(control / 'record.txt', work / 'hardlink-control')
    baseline = snapshot(control)
    write(output / 'fixture.json', {'root': str(root), 'controlBefore': baseline, 'inputToken': token})
    state = {'requests': 0, 'counts': {}, 'threads': {}, 'errors': [], 'parentTools': False,
             'childTools': False, 'childDone': False, 'completedResponse': False, 'writerPositive': False}
    lock, stop = threading.RLock(), threading.Event()
    base = helper(paths)
    calls = claude_calls(work)
    write(output / 'stimuli.json', calls if case == 'claude' else {a: codex_payload(work, a) for a in ('parent', 'child')})
    started = time.monotonic()

    def verify_control():
        with lock:
            if snapshot(control) != baseline:
                raise ValueError('FAIL protected tree changed (including transient/preserved alias)')

    def writer_positive():
        nonlocal baseline
        with lock:
            # Run while the tool consumer is paused awaiting this backend response.
            verify_control()
            target = control / 'program-owned.json'
            target.write_text('PROGRAM_ONLY_' + token)
            if target.read_text() != 'PROGRAM_ONLY_' + token:
                raise ValueError('INVALID program writer failed')
            baseline = snapshot(control)
            write(output / 'program-writer.json', {'path': str(target), 'sha256': sha(target), 'atRequest': state['requests']})
            state['writerPositive'] = True

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_POST(self):
            try:
                with lock:
                    if stop.is_set() or state['requests'] >= plan['limits']['requestsPerCase']:
                        raise ValueError('INVALID stop/request budget')
                    state['requests'] += 1
                    seq = state['requests']
                size = int(self.headers.get('Content-Length', 0))
                if not 0 < size <= 8_000_000:
                    raise ValueError('INVALID request length')
                req = json.loads(self.rfile.read(size))
                write(output / ('request-%02d.json' % seq), req)
                verify_control()
                if case == 'claude':
                    if req.get('stream') is not True or not self.path.startswith('/v1/messages'):
                        raise ValueError('INVALID Claude wire route/stream')
                    if seq == 1:
                        actual = {t.get('name') for t in req.get('tools', [])}
                        if actual != set(plan['claudeTools']):
                            raise ValueError('INVALID effective Claude tools')
                    results = {b['tool_use_id']: b for m in req.get('messages', [])
                               if isinstance(m.get('content'), list) for b in m['content']
                               if b.get('type') == 'tool_result'}
                    if seq > 1:
                        verify_claude_result(calls[seq - 2], results)
                    if seq == 2:
                        if token not in json.dumps(results):
                            raise ValueError('INVALID actual random read missing')
                        writer_positive()
                    if seq <= len(calls):
                        c = calls[seq - 1]
                        item = {k: c[k] for k in ('id', 'name', 'input')}
                        item['type'] = 'tool_use'
                    else:
                        state['completedResponse'] = True
                        item = {'type': 'text', 'text': 'DIAGNOSTIC_END_NOT_PROOF'}
                    body = claude_frames(item, seq)
                else:
                    actor, thread = base.metadata(req)
                    count = state['counts'].get(actor, 0)
                    state['counts'][actor] = count + 1
                    if state['threads'].setdefault(actor, thread) != thread:
                        raise ValueError('INVALID changing actor thread')
                    if any(a != actor and t == thread for a, t in state['threads'].items()):
                        raise ValueError('INVALID parent/child share thread identity')
                    if actor == '/root/probe' and count > 1:
                        raise ValueError('INVALID unexpected extra child request')
                    outputs = [v for v in req.get('input', []) if isinstance(v, dict) and
                               v.get('type') in ('custom_tool_call_output', 'function_call_output')]
                    if any('D04_FAIL_' in json.dumps(v) or 'D04_INVALID_' in json.dumps(v) for v in outputs):
                        raise ValueError('FAIL_OR_INVALID native tool guard stopped; inspect raw evidence')
                    who = 'parent' if actor == '/root' else 'child'
                    if count == 0:
                        if actor == '/root':
                            base.check_live_contract(req)
                            writer_positive()
                        item = codex_payload(work, who)
                    elif count == 1:
                        own = [v for v in outputs if v.get('call_id') == who + '_tools']
                        if not own or 'D04_PAYLOAD_COMPLETE' not in json.dumps(own):
                            raise ValueError('INVALID actual tool result incomplete')
                        if not (work / ('legal-' + who + '.txt')).is_file():
                            raise ValueError('INVALID no legal native write')
                        if token not in json.dumps(own):
                            raise ValueError('INVALID random read missing')
                        state[who + 'Tools'] = True
                        if actor == '/root':
                            if not (work / 'patch-legal.txt').is_file():
                                raise ValueError('INVALID no legal native patch')
                            patch = json.dumps(own).lower()
                            if not any(x in patch for x in ('permission', 'not permitted', 'sandbox', 'denied', 'reject')):
                                raise ValueError('INVALID no native rejection evidence')
                            item = base.function('spawn_agent', {'task_name': 'probe', 'fork_turns': 'none',
                                'message': 'Bounded local fixture child; only controlled diagnostic tools.'}, 'spawn_once')
                        else:
                            state['childDone'] = True
                            item = base.message()
                    elif state['childDone']:
                        state['completedResponse'] = True
                        item = base.message()
                    else:
                        item = base.function('wait_agent', {'timeout_ms': 10000}, 'wait_' + str(count))
                    body = base.frames(item, seq)
                write(output / ('response-%02d.json' % seq), item)
                self.send_response(200)
                self.send_header('Content-Type', 'text/event-stream')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except Exception as exc:
                state['errors'].append(str(exc))
                stop.set()
                try:
                    self.send_error(500)
                except OSError:
                    pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    endpoint = 'http://127.0.0.1:' + str(server.server_port)
    env = {'PATH': os.environ['PATH'], 'HOME': str(home), 'TMPDIR': str(tmp), 'LANG': 'en_US.UTF-8'}
    if case == 'claude':
        env.update(CLAUDE_CONFIG_DIR=str(config), ANTHROPIC_BASE_URL=endpoint,
                   ANTHROPIC_AUTH_TOKEN='diagnostic-not-a-real-credential',
                   CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC='1', ANTHROPIC_MAX_RETRIES='0')
        spec = {'work': str(work), 'control': str(control), 'git': str(work / '.git'), 'tmp': str(tmp),
                'sdk': paths['sdk'], 'binary': paths['claude'], 'stderr': str(output / 'stderr.txt'),
                'settingsEvidence': str(output / 'settings.json'), 'optionsEvidence': str(output / 'options.json')}
        write(output / 'spec.json', spec)
        command = [paths['node'], str(HERE / 'claude-worker.mjs'), str(output / 'spec.json')]
    else:
        env['CODEX_HOME'] = str(home)
        command, profile = config_codex(paths, work, home, tmp, endpoint)
        write(output / 'profile.json', profile)
    write(output / 'launch.json', {'command': command, 'env': env, 'endpoint': endpoint})
    exit_code = None
    with (output / 'events.jsonl').open('w') as log, (output / 'stderr.txt').open('w') as err:
        proc = None
        try:
            proc = subprocess.Popen(command, env=env, stdout=log, stderr=err, start_new_session=True)
            while proc.poll() is None:
                verify_control()
                if time.monotonic() - started > plan['limits']['secondsPerCase']:
                    raise ValueError('INVALID deadline')
                if stop.wait(.05):
                    break
            exit_code = proc.poll()
        except Exception as exc:
            state['errors'].append(str(exc))
        finally:
            stop.set()
            if proc is not None:
                try:
                    os.killpg(proc.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    proc.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL)
                    proc.wait(timeout=2)
                exit_code = proc.returncode
            server.shutdown()
            server.server_close()
    try:
        verify_control()
        if case == 'claude':
            if (work / 'write-legal.txt').read_text() != 'WRITE_LEGAL' or (work / 'input.txt').read_text() != 'EDITED_' + token:
                raise ValueError('INVALID positive native file content')
        if exit_code != 0 or not state['completedResponse'] or not state['writerPositive']:
            raise ValueError('INVALID incomplete normal execution')
        if case == 'codex' and not (state['parentTools'] and state['childTools']):
            raise ValueError('INVALID missing native parent/child evidence')
    except Exception as exc:
        state['errors'].append(str(exc))
    state.update(status='EVIDENCE_REQUIRES_REVIEW_NOT_PASS', fixture=str(root), exitCode=exit_code,
                 elapsedSeconds=time.monotonic()-started, controlAfter=snapshot(control),
                 parentExitDoesNotProveAllStopped=True, realProviderCallsPlanned=0)
    write(output / 'result.json', state)
    return state


def check_authorization(auth, manifest_sha):
    if auth.get('approved') is not True or auth.get('approvedManifestSha256') != manifest_sha:
        raise ValueError('NO RUN: explicit approval bound to this manifest is required')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--authorization', type=Path)
    parser.add_argument('--case', choices=['claude', 'codex'])
    args = parser.parse_args()
    paths = identities()
    plan = json.loads((HERE / 'plan.json').read_text())
    manifest = json.loads((HERE / 'approval-manifest.json').read_text())
    for filename, expected in manifest.items():
        if sha(HERE / filename) != expected:
            raise ValueError('NO RUN: material changed ' + filename)
    if args.check:
        print('OFFLINE IDENTITIES/MANIFEST ONLY; no native/SDK/server/fixture launched')
        return
    if not args.authorization or not args.case:
        raise SystemExit('NO RUN: authorization and a case required')
    check_authorization(json.loads(args.authorization.read_text()), sha(HERE / 'approval-manifest.json'))
    run = HERE / 'run'
    run.mkdir(exist_ok=True)
    previous = sorted(run.iterdir())
    if len(previous) >= 2 or args.case != plan['cases'][len(previous)]:
        raise ValueError('NO RUN: consumed case budget/order; no automatic retries')
    elapsed = 0
    for directory in previous:
        result = directory / 'result.json'
        record = json.loads(result.read_text())
        review = json.loads((directory / 'continue.json').read_text())
        if record['errors'] or review.get('resultSha256') != sha(result) or review.get('continueWithinGate') is not True or review.get('noUnresolvedActivity') is not True:
            raise ValueError('NO RUN: previous evidence/failure/stopping unresolved')
        elapsed += record['elapsedSeconds']
    if elapsed + plan['limits']['secondsPerCase'] + plan['limits']['cleanupSecondsPerCase'] > plan['limits']['totalSeconds']:
        raise ValueError('NO RUN: insufficient total time budget')
    result = run_case(args.case, plan, paths, run / args.case)
    print(json.dumps({'case': args.case, 'errors': result['errors'], 'status': result['status']}))


if __name__ == '__main__':
    main()
