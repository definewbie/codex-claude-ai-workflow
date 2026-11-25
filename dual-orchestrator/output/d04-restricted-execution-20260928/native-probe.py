"""Bounded diagnostic payload. All write targets are fresh synthetic fixtures."""
import errno
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

fixture = Path(sys.argv[1]).resolve()
assert fixture.name.startswith('dual-d04-native-')
workspace = fixture / 'workspace'
manifest = json.loads((fixture / 'fixture.json').read_text())
results = []

def emit(status, reason=None):
    print(json.dumps({'status': status, 'reason': reason, 'cases': results}, ensure_ascii=False), flush=True)

def reject_write(name, action):
    try:
        action()
    except OSError as error:
        if error.errno not in (errno.EACCES, errno.EPERM, errno.EROFS):
            emit('INVALID', name + ': unexpected errno ' + str(error.errno))
            sys.exit(3)
        results.append({'name': name, 'result': 'DENIED', 'errno': error.errno})
        return
    emit('FAIL', name + ': forbidden write succeeded')
    sys.exit(2)

try:
    # Discover the marker by reading the fixture, not by receiving its value as input.
    read = subprocess.run(['/bin/sh', '-c', 'find src -type f -name "*.txt" -exec grep -H D04_READ_TOKEN {} +'],
                          cwd=workspace, capture_output=True, text=True, timeout=5)
    assert read.returncode == 0, read.stderr
    assert manifest['token'] in read.stdout
    results.append({'name': 'shell_find_and_read', 'result': 'PASS', 'stdout': read.stdout})
    raw = (workspace / 'src/sample.txt').read_bytes()
    assert hashlib.sha256(raw).hexdigest() == manifest['sampleSha256']
    results.append({'name': 'native_process_read', 'result': 'PASS'})
    env = dict(os.environ, GIT_OPTIONAL_LOCKS='0')
    status = subprocess.run(['/usr/bin/git', 'status', '--porcelain'], cwd=workspace,
                            env=env, capture_output=True, text=True, timeout=5)
    assert status.returncode == 0, status.stderr
    results.append({'name': 'git_read_status', 'result': 'PASS', 'stdout': status.stdout})
    
    reject_write('product_modify', lambda: (workspace/'src/sample.txt').write_text('changed'))
    reject_write('new_untracked_file', lambda: (workspace/'new.txt').write_text('changed'))
    reject_write('ignored_file', lambda: (workspace/'ignored/cache').write_text('changed'))
    reject_write('control_ledger', lambda: (workspace/'.agent/nodes.json').write_text('changed'))
    reject_write('policy_document', lambda: (workspace/'.agent/policy.json').write_text('changed'))
    reject_write('git_metadata', lambda: (workspace/'.git/config').write_text('changed'))
    reject_write('delete_product', lambda: (workspace/'src/delete-me.txt').unlink())
    reject_write('rename_product', lambda: (workspace/'src/rename-me.txt').rename(workspace/'renamed.txt'))
    reject_write('symlink_to_control', lambda: (workspace/'alias-to-control').write_text('changed'))
    reject_write('symlink_external_git', lambda: (workspace/'alias-to-external-git').write_text('changed'))
    
    def write_then_restore():
        p = workspace/'src/sample.txt'
        original = p.read_bytes()
        p.write_bytes(b'changed')
        p.write_bytes(original)
    reject_write('write_then_restore_first_write', write_then_restore)
    
    child = subprocess.run(['/bin/sh', '-c', 'printf changed > .agent/nodes.json'], cwd=workspace,
                           capture_output=True, text=True, timeout=5)
    assert child.returncode != 0, 'child write unexpectedly succeeded'
    assert (workspace/'.agent/nodes.json').read_text() == 'baseline-control\n'
    results.append({'name': 'child_shell_write', 'result': 'DENIED', 'stderr': child.stderr})
    git = subprocess.run(['/usr/bin/git', 'add', 'src/sample.txt'], cwd=workspace,
                         capture_output=True, text=True, timeout=5)
    assert git.returncode != 0, 'git add unexpectedly succeeded'
    assert not (workspace/'.git/index').exists()
    results.append({'name': 'git_add', 'result': 'DENIED', 'stderr': git.stderr})
    emit('PASS')
except AssertionError as error:
    emit("FAIL", str(error))
    sys.exit(2)
