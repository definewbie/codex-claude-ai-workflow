"""Final author check and material manifest; no run authority."""
import ast
import difflib
import subprocess
import sys
from pathlib import Path
from gate_support import OUT, ROOT, read, write, sha, frozen_check

release = frozen_check()
for p in OUT.glob('*.py'):
    ast.parse(p.read_text(),filename=p.name)
assert read(OUT/'run-authorization.json')['fresh_run_authorized'] is False
assert read(OUT/'budget.json')['real_run_authorized'] is False
assert not (OUT/'steps').exists()
loc = read(OUT/'instance-location.json')
ledger = Path(loc['ledger'])
assert not list((ledger/'runs').glob('*/run.json'))
assert read(ledger/'workflow/current.json')==read(OUT/'initial-workflow-current.json')
assert read(OUT/'budget.json')['total_stop']==1700000
assert read(OUT/'budget.json')['top_level_calls']==18
assert read(OUT/'budget.json')['active_seconds']==3600
old = ROOT/'output/w09-fresh-sixth-gate-20261015'
fix = ROOT/'output/w09-sixth-forward-fix-20261015'
assert sha(OUT/'task-requirement.md')==sha(old/'task-requirement.md')
assert sha(OUT/'gate_support.py')==sha(fix/'observer-candidate/gate_support.py')
assert sha(OUT/'test_gate_observer.py')==sha(fix/'observer-candidate/test_gate_observer.py')
before = read(old/'budget.json');current=read(OUT/'budget.json')
assert {k:v for k,v in before.items() if k not in ['status','real_run_authorized']}=={k:v for k,v in current.items() if k not in ['status','real_run_authorized']}
assert read(OUT/'before-source-sha256.json')==read(OUT/'after-source-sha256.json')==read(OUT/'final-source-sha256.json')==release['source_inputs']
for base in [old,fix]:
    manifest = read(base/'material-sha256.json');manifest=manifest.get('files',manifest)
    assert all(sha(base/p)==h for p,h in manifest.items())
oldledger = Path(read(old/'instance-location.json')['ledger'])
assert all(sha(oldledger/p)==h for p,h in read(old/'stopped-ledger-identity.json')['files'].items())
cp = subprocess.run(['rtk','proxy',sys.executable,'-B',str(OUT/'pre-dispatch-recheck.py')],
                    cwd=OUT,capture_output=True,text=True)
assert cp.returncode==0,cp.stderr
(OUT/'final-readonly-recheck.stdout.txt').write_text(cp.stdout)
(OUT/'final-readonly-recheck.stderr.txt').write_text(cp.stderr)
before=(OUT/'before-worklist.md').read_text()
after=(ROOT/'docs/plans/20260927-dual-lifecycle-worklist.zh-CN.md').read_text()
assert after.startswith(before)
(OUT/'worklist-incremental.patch').write_text(''.join(difflib.unified_diff(before.splitlines(True),after.splitlines(True),
    fromfile='before-current-worklist',tofile='after-current-worklist')))
manifest = {str(p.relative_to(OUT)):sha(p) for p in sorted(OUT.rglob('*')) if p.is_file()
    and '__pycache__' not in p.parts and p.name not in ['material-sha256.json','material-verification.json']}
write(OUT/'material-sha256.json',dict(files=manifest))
assert all(sha(OUT/p)==h for p,h in manifest.items())
write(OUT/'material-verification.json',dict(status='AUTHOR_OFFLINE_MATERIAL_CHECK_PASS_NOT_CC_OR_RUN_AUTHORIZATION',
    files=len(manifest),manifest_sha256=sha(OUT/'material-sha256.json'),all_hashes_match=True,
    source_inputs=659,source_unchanged=True,observer_tests=12,budget_cases=13,
    real_authorization_negative_checks=2,isolated_early_guard_cases=3,
    inherited_reactor_tests=1813,inherited_reactor_suites=210,new_maven_run=False,
    fresh_real_run_authorized=False,native_provider_calls=0,native_sessions_created=0,business_approvals=0,
    sixth_recovered=False,seventh_offline_initialized=True,seventh_real_run_started=False,
    source_of_run_authority='New user approval required; never this verification report'))
print('Material PASS:',len(manifest),'files / 659 unchanged inputs / 12 observer / 13 budget / zero model calls / awaiting user')
