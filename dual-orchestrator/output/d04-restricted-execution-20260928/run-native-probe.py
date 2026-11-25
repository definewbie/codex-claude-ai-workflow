"""Run pinned native sandbox against disposable fixtures; zero provider calls."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import secrets
import signal
import subprocess
import tempfile

out = Path(__file__).resolve().parent
binary = Path('/Users/kriswang/.npm/_npx/208e725ef8df0f5d/node_modules/@openai/codex-darwin-arm64/vendor/aarch64-apple-darwin/bin/codex')
payload = out / 'native-probe.py'
fixture = Path(tempfile.mkdtemp(prefix='dual-d04-native-')).resolve()
workspace = fixture / 'workspace'
for relative in ['src', '.agent', 'ignored']:
    (workspace / relative).mkdir(parents=True, exist_ok=True)
(fixture/'external-git').mkdir()
token = secrets.token_hex(12)
(workspace/'src/sample.txt').write_text('D04_READ_TOKEN='+token+'\n')
for relative, content in {
    'src/delete-me.txt':'delete-baseline\n', 'src/rename-me.txt':'rename-baseline\n',
    '.agent/nodes.json':'baseline-control\n', '.agent/policy.json':'baseline-policy\n',
    '.gitignore':'ignored/\n', 'ignored/cache':'baseline-cache\n',
}.items():
    (workspace/relative).write_text(content)
(fixture/'external-git/config').write_text('external-git-baseline\n')
subprocess.run(['/usr/bin/git', 'init', '--quiet', str(workspace)], check=True, timeout=5)
(workspace/'alias-to-control').symlink_to(workspace/'.agent/nodes.json')
(workspace/'alias-to-external-git').symlink_to(fixture/'external-git/config')

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def snapshot():
    return {str(p.relative_to(fixture)):
            {'link': os.readlink(p)} if p.is_symlink() else {'sha256': sha(p)}
            for p in fixture.rglob('*') if p.is_file() or p.is_symlink()}

(fixture/'fixture.json').write_text(json.dumps({'token':token,'sampleSha256':sha(workspace/'src/sample.txt')}))
# Ordinary Unix permissions must permit writes; otherwise a refusal could be unrelated to native sandboxing.
host_control = workspace/'host-writable-control'
host_control.write_text('control')
host_control.unlink()
before = snapshot()
cmd = [str(binary), 'sandbox', '-P', ':read-only', '-C', str(workspace), '--',
       '/usr/bin/python3', '-B', str(payload), str(fixture)]
identity = {'binary':str(binary),'binarySha256':sha(binary),'payloadSha256':sha(payload),
            'driverSha256':sha(Path(__file__))}
record = {'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(), 'identity':identity,
          'command':cmd,'fixture':str(fixture),'hostWriteControl':True,'before':before,
          'providerCalls':0,'tokenCost':0,'scope':'native command sandbox only; not model/ACP/Dual lifecycle'}
(out/'native-start.json').write_text(json.dumps(record,indent=2)+'\n')
try:
    run = subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,start_new_session=True)
    stdout,stderr=run.communicate(timeout=30)
    record.update(exitCode=run.returncode,stdout=stdout,stderr=stderr)
except subprocess.TimeoutExpired as error:
    os.killpg(run.pid,signal.SIGKILL)
    stdout,stderr=run.communicate()
    record.update(exitCode=None,stdout=stdout,stderr=stderr,status='INVALID_TIMEOUT')
after = snapshot()
record['after'] = after
record['changed'] = [p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
if 'status' not in record:
    try: observed = json.loads(record['stdout'])
    except (ValueError,TypeError): observed = None
    record['observed'] = observed
    record['status'] = 'FAIL' if record['changed'] or observed and observed.get('status')=='FAIL' else (
        'PASS_NATIVE_SLICE' if record['exitCode']==0 and observed and observed.get('status')=='PASS'
        else 'INVALID')
record['identityUnchanged'] = identity['binarySha256']==sha(binary) and identity['payloadSha256']==sha(payload)
if not record['identityUnchanged']: record['status']='INVALID_IDENTITY'
(out/'native-result.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({k:record[k] for k in ['status','exitCode','changed','providerCalls','fixture']}))
