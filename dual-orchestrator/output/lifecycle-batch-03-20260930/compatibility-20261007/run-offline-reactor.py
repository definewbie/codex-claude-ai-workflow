from pathlib import Path
import json, hashlib, subprocess, time, shutil, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
out = Path(__file__).resolve().parent

def fingerprint():
    paths = set(json.loads((root / 'output/lifecycle-batch-03-20260930/mainline-20261007/before-source.json').read_text()))
    paths.update(str(p.relative_to(root)) for p in root.glob('dual-*/src/**/*') if p.is_file())
    paths.update(str(p.relative_to(root)) for p in root.glob('dual-*/pom.xml'))
    paths.add('pom.xml')
    return {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in sorted(paths) if (root / p).is_file()}

before = fingerprint()
(out / 'reactor-before-sha256.json').write_text(json.dumps(before, indent=2))
started = time.time()
with (out / 'reactor-clean.log').open('w') as log:
    process = subprocess.Popen(['rtk', 'proxy', 'mvn', 'clean', 'test'], cwd=root, stdout=log, stderr=subprocess.STDOUT)
    print('Owned reactor PID:', process.pid, '; fingerprint inputs:', len(before), flush=True)
    code = process.wait()
after = fingerprint()
(out / 'reactor-after-sha256.json').write_text(json.dumps(after, indent=2))
modules = {}
for module in sorted(root.glob('dual-*')):
    for file in sorted((module / 'target/surefire-reports').glob('TEST-*.xml')):
        doc = ET.parse(file).getroot()
        stat = modules.setdefault(module.name, dict(tests=0, failures=0, errors=0, skipped=0, suites=0))
        for key in ('tests', 'failures', 'errors', 'skipped'):
            stat[key] += int(doc.attrib.get(key, 0))
        stat['suites'] += 1
        dest = out / 'reactor-reports' / module.name
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, dest / file.name)
drift = [p for p in sorted(set(before) | set(after)) if before.get(p) != after.get(p)]
total = {k: sum(m[k] for m in modules.values()) for k in ('tests', 'failures', 'errors', 'skipped', 'suites')}
verification = dict(status='INVALID' if drift else 'PASS' if code == 0 else 'FAIL', command='rtk proxy mvn clean test',
    exit_code=code, single_clean_reactor=code == 0, seconds=round(time.time()-started, 1), modules=modules, total=total,
    identity_inputs=len(before), identity_drift=drift, native_client_runs=0, live_provider_runs=0, extra_agents=0,
    B03='IN_PROGRESS_PENDING_INDEPENDENT_REVIEW', real_gate='NO_GO')
if code != 0:
    verification['coverage_caveat'] = 'Incomplete reactor; untouched module reports may be stale. Do not use aggregate as current coverage proof.'
(out / 'verification.json').write_text(json.dumps(verification, indent=2))
print(json.dumps(verification), flush=True)
