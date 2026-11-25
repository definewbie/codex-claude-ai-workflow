"""One authorized session initialization; no prompt API or business runner."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

OUT = Path(__file__).resolve().parent
PACKAGE = OUT.parent
ROOT = PACKAGE.parents[1]
sys.path.insert(0, str(PACKAGE))
from process_check import require_known_pids_absent


def write(name, value):
    path = OUT / name
    with path.open('x') as handle:
        os.chmod(path, 0o600)
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write('\n')


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def table(timeout=2):
    result = subprocess.run(['rtk', 'proxy', 'ps', '-axo', 'pid=,ppid=,pgid=,lstart=,command='],
                            capture_output=True, text=True, timeout=timeout)
    if result.returncode or result.stderr.strip() or not result.stdout.strip():
        raise RuntimeError('UNKNOWN_PROCESS_TABLE')
    return result.stdout.splitlines()


def run_command(argv, cwd, env, label, timeout, cleanup_deadline=None):
    start = time.monotonic()
    with (OUT / (label + '.stdout.txt')).open('x') as stdout, (OUT / (label + '.stderr.txt')).open('x') as stderr:
        os.chmod(stdout.name, 0o600)
        os.chmod(stderr.name, 0o600)
        process = subprocess.Popen(['rtk', 'proxy', *argv], cwd=cwd, env=env,
                                   stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
                                   start_new_session=True)
        write(label + '-process.json', {'pid': process.pid, 'pgid': process.pid, 'argv': argv,
              'started_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'stdin': 'DEVNULL'})
        timed_out = False
        try:
            process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
        finished_wait = time.monotonic()
        deadline = cleanup_deadline or (finished_wait + 15)
        # This group was created solely for this command. Never signal unrelated PIDs.
        observed_group = []
        group_error = None
        try:
            observed_group = [line for line in table(max(0.001, min(2, deadline-time.monotonic())))
                              if int(line.split(None, 3)[2]) == process.pid]
        except Exception as error:
            group_error = type(error).__name__ + ':' + str(error)
        if timed_out or observed_group:
            for sig in (signal.SIGTERM, signal.SIGKILL):
                try:
                    os.killpg(process.pid, sig)
                except ProcessLookupError:
                    break
                if sig == signal.SIGTERM:
                    try:
                        process.wait(timeout=max(0.001, min(1, deadline-time.monotonic())))
                    except subprocess.TimeoutExpired:
                        pass
        try:
            process.wait(timeout=max(0.001, min(1, deadline-time.monotonic())))
        except subprocess.TimeoutExpired:
            group_error = 'PROCESS_EXIT_UNCONFIRMED'
        result = {'exit': process.returncode, 'timed_out': timed_out,
                  'wait_seconds': round(finished_wait-start, 3), 'cleanup_deadline': deadline,
                  'observed_owned_group_before_cleanup': observed_group,
                  'process_observation_error': group_error,
                  'elapsed_seconds': round(time.monotonic()-start, 3)}
        write(label + '-result.json', result)
        return result


def main():
    os.umask(0o077)
    candidate = json.loads((PACKAGE / 'observation-command-candidate.json').read_text())
    pre = json.loads((OUT / 'preflight-static.json').read_text())
    auth = json.loads((OUT / 'authorization.json').read_text())
    assert auth['native_initializations_limit'] == 1 and auth['prompt_limit'] == 0
    assert not pre['identity_drift'] and not pre['original_ledger_differences']
    for check in pre['identity_checks']:
        assert sha(check['path']) == check['expected'], 'Identity drift before launch'
    assert sha(PACKAGE / 'observation-command-candidate.json') == pre['command_candidate_sha256']
    write('dispatch-process-check.json', require_known_pids_absent(pre['known_pids']))
    argv, cwd = candidate['argv'], candidate['cwd']
    assert argv[-5:-2] == ['claude', 'sessions', 'new'] and '--verbose' in argv
    assert Path(cwd).is_dir() and '--json-strict' not in argv
    original = json.loads((ROOT / candidate['source_intent']).read_text())['details']['invocation']
    env = os.environ.copy()
    env.update(original['launch_environment'])
    write('attempt-consumed.json', {'native_initialization_attempt': 1, 'prompt_limit': 0,
          'automatic_retry': False, 'at': datetime.datetime.now(datetime.timezone.utc).isoformat()})
    result = run_command(argv, cwd, env, 'initialization', 45)
    deadline = result['cleanup_deadline']
    records = []
    for path in (Path.home() / '.acpx/sessions').glob('*.json'):
        try:
            value = json.loads(path.read_text())
        except (OSError, ValueError):
            continue
        if value.get('name') == argv[-1] and value.get('cwd') == cwd:
            records.append((path, value))
    write('session-records-before-close.json', [{'path': str(p), 'record': v} for p, v in records])
    if len(records) == 1 and time.monotonic() < deadline-3:
        prefix = argv[:-5]
        close = run_command(prefix + ['claude', 'sessions', 'close', argv[-1]], cwd, env,
                            'close', min(3, deadline-time.monotonic()-2), deadline)
        if close['exit'] == 0 and time.monotonic() < deadline-2:
            run_command(prefix + ['claude', 'sessions', 'show', argv[-1]], cwd, env,
                        'show', min(2, deadline-time.monotonic()-1), deadline)
    final_records = [{'path': str(p), 'record': json.loads(p.read_text())} for p, _ in records]
    write('session-records-after-close.json', final_records)
    try:
        if time.monotonic() >= deadline:
            raise RuntimeError('CLEANUP_TIME_EXHAUSTED')
        observed = table(max(0.001, min(2, deadline-time.monotonic())))
        pid = json.loads((OUT / 'initialization-process.json').read_text())['pid']
        remaining = [line for line in observed if int(line.split(None, 3)[2]) == pid
                     or argv[-1] in line or (cwd in line and 'python3 -' not in line)]
        stop = {'status': 'ABSENT' if not remaining else 'PRESENT', 'matching_processes': remaining,
                'scope': 'owned group and named local workspace/session only; not arbitrary or remote descendants'}
    except Exception as error:
        stop = {'status': 'UNKNOWN', 'error': type(error).__name__ + ':' + str(error)}
    write('post-stop-process.json', stop)
    write('observation-complete.json', {'native_initialization_attempts': 1, 'prompt_commands': 0,
          'cleanup_within_15_seconds': time.monotonic() <= deadline,
          'session_records': len(records), 'stop_status': stop['status'],
          'finished_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'seventh_not_resumed': True, 'eighth_not_started': True})
    print(json.dumps({'initialization_exit': result['exit'], 'timed_out': result['timed_out'],
                      'wait_seconds': result['wait_seconds'], 'session_records': len(records),
                      'stop_status': stop['status'], 'prompt_commands': 0}))


if __name__ == '__main__':
    main()
