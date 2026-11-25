"""One-case offline preparation. No provider, session, installer or dispatch."""
import hashlib
import json
import shutil
from pathlib import Path

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
OLD = ROOT / 'output/w09-fresh-sixth-gate-20261015'
FIX = ROOT / 'output/w09-sixth-forward-fix-20261015'
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()
read = lambda p: json.loads(Path(p).read_text())
def write(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

assert not (OUT / 'selected-release.json').exists(), 'Never reset a prepared case'
release = read(FIX / 'release-identity.json')
prior = read(OLD / 'selected-release.json')
assert all(sha(ROOT / p) == h for p, h in release['source_inputs'].items())
apps = {}
for name, info in release['application_jars'].items():
    p = Path(info['frozen_path'])
    assert sha(p) == info['sha256']
    apps[name] = {'path': str(p), 'sha256': info['sha256']}
selected = dict(source_inputs=release['source_inputs'], source_input_count=len(release['source_inputs']),
    application_jars=apps, selected_acpx=prior['selected_acpx'],
    metadata_config_source_files=prior['metadata_config_source_files'],
    parent_release_identity=str(FIX / 'release-identity.json'),
    prior_single_reactor=read(FIX/'test-results.json'),
    status='OFFLINE_SELECTED_CANDIDATE_NOT_RUN_AUTHORITY')
write('selected-release.json', selected)
for name in ['gate_support.py', 'test_gate_observer.py']:
    shutil.copyfile(FIX / 'observer-candidate' / name, OUT / name)
for name in ['check_budget.py', 'task-requirement.md']:
    shutil.copyfile(OLD / name, OUT / name)
text = (OUT/'check_budget.py').read_text().replace('fifth authorization', 'seventh authorization')
(OUT/'check_budget.py').write_text(text)
budget = read(OLD / 'budget.json')
budget.update(status='USER_APPROVED_OFFLINE_PLAN_SEVENTH_RUN_PENDING', real_run_authorized=False)
write('budget.json', budget)
runner = (OLD/'run-one-step.py').read_text().replace('FRESH_SIXTH', 'FRESH_SEVENTH').replace('Fresh sixth', 'Fresh seventh')
runner = runner.replace('PASS_LOCAL_PRECONDITIONS', 'PASS_OFFLINE_PRECONDITIONS_AWAITING_RUN_APPROVAL')
# Keep the original authorization check first. No run is authorized by preparing.
(OUT/'run-one-step.py').write_text(runner)
write('run-authorization.json', dict(case='FRESH_SEVENTH', offline_preparation_authorized=True,
    fresh_run_authorized=False, user_request='先离线准备第七例Gate并完成启动前核验，材料齐备后交我批准，暂不启动真实运行',
    proposed_budget=dict(total_tokens=1700000, top_level_requests=18, active_seconds=3600),
    budget_plan_confirmed=True, business_gate_approvals=False, previous_balance_inherited=False))
write('preparation-provenance.json', dict(preparation_date='2026-10-16 Asia/Shanghai',
    release_source=str(FIX), observer_source=str(FIX/'observer-candidate'),
    budget_and_runner_source=str(OLD), product_requirement_sha256=sha(OUT/'task-requirement.md'),
    copies=[dict(path=p, sha256=sha(OUT/p)) for p in ['gate_support.py','test_gate_observer.py','check_budget.py','run-one-step.py']],
    production_changes=False, native_patch_changes=False, extra_agents=0,
    real_provider_calls=0, sixth_recovered=False, seventh_real_run_authorized=False))
print('Prepared selected frozen release, observer, unchanged reserve arithmetic; real-run authorization FALSE')
