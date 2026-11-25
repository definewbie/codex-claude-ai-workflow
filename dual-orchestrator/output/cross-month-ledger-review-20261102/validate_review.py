"""Verify conservation, evidence stability, test archives and report references."""
from pathlib import Path
from collections import Counter
import hashlib
import json
import re

HERE=Path(__file__).resolve().parent
errors=[]


def check(condition,message):
    if not condition: errors.append(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


d=json.loads((HERE/'inventory.json').read_text())
real=[c for c in d['cases'] if c['population']=='OPERATIONAL_RETAINED']
check(len(real)==158,'execution population')
check(sum(c['runCount'] for c in real)==len(d['runs'])==990,'run conservation')
check(Counter(r['status'] for r in d['runs'])=={'completed':821,'failed':165,'prepared':4},'run statuses')
check(sum(c['eventCounts'].get('node_failed',0) for c in real)==170,'node failed count')
check(sum(c['eventCounts'].get('node_retry_requested',0) for c in real)==95,'retry count')
check(sum(c['eventCounts'].get('workflow_completed',0) for c in real)==12,'completion count')
check(sum(c['eventCounts'].get('revision_limit_reached',0) for c in real)==16,'revision count')
check(not d['parseErrors'],'raw parse errors')
check(len({(r['workstream'],r['run']) for r in d['runs']})==990,'run uniqueness')
io=json.loads((HERE/'io-forensics.json').read_text())
failed={(r['workstream'],r['run']) for r in d['runs'] if r['status']=='failed'}
check({(r['workstream'],r['run']) for r in io['inspections']}==failed,'failed IO coverage')
check(sum(f.get('missing',False) for r in io['inspections'] for f in r['files'])==85,'missing role references')

manifest=json.loads((HERE/'source-sha256.json').read_text())
for path,digest in manifest.items():
    p=Path(path)
    check(p.is_file() and sha(p)==digest,'raw evidence changed/missing: '+path)

v=json.loads((HERE/'current-verification.json').read_text())
for path,digest in v['sources'].items():
    p=Path(path)
    check(p.is_file() and sha(p)==digest,'tested source changed/missing: '+path)
for report in v['reports']:
    check(sha(Path(report['path']))==report['sha256'],'test archive changed: '+report['path'])
check(sum(x['tests'] for x in v['reports'])==197,'test total')
check(not any(x['errors'] or x['failures'] or x['skipped'] for x in v['reports']),'tests not all passed')

links=0
for report in ('review.zh-CN.md','evidence-index.md'):
    for target in re.findall(r'\]\(([^)]+)\)',(HERE/report).read_text()):
        links+=1
        check(target.startswith('/'),'nonabsolute reference: '+target)
        parts=target.rsplit(':',1)
        if len(parts)==2 and parts[1].isdigit():
            p=Path(parts[0]); line=int(parts[1])
            check(p.is_file() and 1<=line<=len(p.read_text().splitlines()),'invalid source line: '+target)
        else:
            check(Path(target).exists(),'missing linked artifact: '+target)

generated={str(p):sha(p) for p in HERE.glob('*') if p.is_file() and p.name!='validation.json'}
result={'status':'PASS' if not errors else 'FAIL','errors':errors,'rawFilesHashChecked':len(manifest),
        'testedSourceFilesHashChecked':len(v['sources']),'reportLinksChecked':links,
        'tests':197,'generatedArtifactHashes':generated,
        'scope':'evidence integrity and accounting conservation; not external product grading'}
(HERE/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:x for k,x in result.items() if k!='generatedArtifactHashes'},ensure_ascii=False))
raise SystemExit(bool(errors))
