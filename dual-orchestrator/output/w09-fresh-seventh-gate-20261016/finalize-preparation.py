"""Freeze preparation material, then read-only recheck. Never dispatch."""
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from gate_support import OUT, ROOT, read, write, sha

assert not read(OUT/'run-authorization.json')['fresh_run_authorized']
release = read(OUT/'selected-release.json')
loc = read(OUT/'instance-location.json')
ledger = Path(loc['ledger'])
selected = read(OUT/'current-config-and-installed-sources.json')
# Measure actual host executable bytes without running any coding client.
binary = []
for name,p in read(OUT/'local-launch-selection.json')['executables'].items():
    if p and Path(p).is_file():
        real = str(Path(p).resolve())
        selected[real] = sha(real)
        binary.append(dict(name=name,path=p,realpath=real,sha256=selected[real],
            meaning='Measured current binary, not a historical byte comparison or product version lock'))
write(OUT/'host-executable-identity.json',dict(binaries=binary,native_clients_executed=False))
for label,args in [('readiness-help',['review','record-readiness','--help']),
                   ('delivery-finalize-help',['delivery','finalize','--help'])]:
    cp = subprocess.run(['rtk','proxy','java','-jar',release['application_jars']['dual-cli']['path']]+args,
        cwd=ROOT,capture_output=True,text=True,timeout=60)
    (OUT/(label+'.stdout.txt')).write_text(cp.stdout)
    (OUT/(label+'.stderr.txt')).write_text(cp.stderr)
    assert cp.returncode==0,(label,cp.stderr)
write(OUT/'verification-exit-entry-checks.json',dict(status='PASS_LOCAL_HELP_AND_EXISTING_PROOF',
    commands=['review record-readiness --help','delivery finalize --help'],
    actual_criteria_execution=False,readiness_registered=False,compiled_goal=False,
    typed_evidence_not_replaced_by_host_observation=True))
# Exact initial ledger must survive a second real invocation of the run guard.
before = {str(p.relative_to(ledger)):sha(p) for p in ledger.rglob('*') if p.is_file() and p.name!='.mutation.lock'}
cp = subprocess.run(['rtk','proxy',sys.executable,'-B',str(OUT/'run-one-step.py'),
    'denied-after-init','intake.claude_brainstorm'],cwd=OUT,capture_output=True,text=True)
(OUT/'unauthorized-after-init.stdout.txt').write_text(cp.stdout)
(OUT/'unauthorized-after-init.stderr.txt').write_text(cp.stderr)
assert cp.returncode==1 and 'Fresh seventh requires new human authorization' in cp.stderr
after = {str(p.relative_to(ledger)):sha(p) for p in ledger.rglob('*') if p.is_file() and p.name!='.mutation.lock'}
assert before==after and not (OUT/'steps').exists()
write(OUT/'authorization-after-init-proof.json',dict(status='PASS_EXPECTED_REJECTION',
    exit=1,unchanged_ledger_files=len(after),steps_created=False,native_provider_calls=0))
names = ['gate_support.py','test_gate_observer.py','check_budget.py','budget.json','run-one-step.py',
    'pre-dispatch-recheck.py','task-requirement.md','fresh-seventh-gate.md','selected-release.json',
    'verification-exits.md','execution-boundaries.md','local-launch-selection.json',
    'host-environment-identity.json','instance-location.json','initial-ledger-sha256.json','initial-workflow-current.json']
write(OUT/'launch-identity.json',dict(checked_at=datetime.datetime.now().astimezone().isoformat(),
    config_and_installed_sources=selected,observer_inputs={p:sha(OUT/p) for p in names},
    run_authorization_is_separate_human_decision=True,
    release_ref=str(OUT/'selected-release.json'),no_production_or_config_changes=True,
    topology='Single INLINE router selected; initialization only, actual run NOT_STARTED'))
cp = subprocess.run(['rtk','proxy',sys.executable,'-B',str(OUT/'pre-dispatch-recheck.py')],
    cwd=OUT,capture_output=True,text=True)
(OUT/'pre-dispatch-recheck.stdout.txt').write_text(cp.stdout)
(OUT/'pre-dispatch-recheck.stderr.txt').write_text(cp.stderr)
assert cp.returncode==0,cp.stderr
assert read(OUT/'pre-dispatch-recheck.json')['fresh_run_authorized'] is False
write(OUT/'preflight-summary.json',dict(checked_at=datetime.datetime.now().astimezone().isoformat(),
    status='OFFLINE_READY_AWAITING_USER_RUN_APPROVAL',real_run_authorized=False,
    budget_plan=dict(tokens=1700000,requests=18,active_seconds=3600,warning_tokens=1200000,
        command_timeout_seconds=600,unchanged_reserve_semantics=True),
    source_inputs=659,observer_tests=12,budget_cases=13,authorization_negative_checks=2,
    native_provider_calls=0,native_sessions_created=0,business_approvals=0,
    seventh_offline_initialized=True,seventh_real_run_started=False,sixth_recovered=False,
    prior_reactor=dict(tests=1813,suites=210,current_task_reran=False),location=loc))
# Recheck old evidence and fresh source after local initialization/checks.
results = {}
for name in ['w09-fresh-sixth-gate-20261015','w09-sixth-forward-fix-20261015']:
    base = ROOT/'output'/name
    manifest = read(base/'material-sha256.json');manifest=manifest.get('files',manifest)
    assert all(sha(base/p)==h for p,h in manifest.items())
    results[name] = dict(files=len(manifest),unchanged=True,manifest_sha256=sha(base/'material-sha256.json'))
old = ROOT/'output/w09-fresh-sixth-gate-20261015'
oldledger = Path(read(old/'instance-location.json')['ledger'])
ids = read(old/'stopped-ledger-identity.json')['files']
assert all(sha(oldledger/p)==h for p,h in ids.items())
results['sixth_live_ledger'] = dict(files=len(ids),unchanged=True,lock_excluded=True,recovered=False)
write(OUT/'historical-preservation.json',results)
assert all(sha(ROOT/p)==h for p,h in release['source_inputs'].items())
write(OUT/'final-source-sha256.json',{p:sha(ROOT/p) for p in release['source_inputs']})
print('Final offline preflight PASS; frozen observer/launch identity; authorization false; no real run')
