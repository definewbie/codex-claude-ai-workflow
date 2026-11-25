import hashlib
import json
import re
import subprocess
from pathlib import Path

root=Path.cwd(); b=Path(__file__).parent
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
checks=[]
for name, manifest in [
 ('design',root/'output/w09-revision-wiring-design-20261014/material-sha256.json'),
 ('fifth',root/'output/w09-fresh-fifth-gate-20261011/stopped-material-sha256.json'),
 ('A',root/'output/w09-current-transcript-forward-fix-20261011/material-sha256.json')]:
 files=json.loads(manifest.read_text())['files']
 mismatches=[name for name,value in files.items() if not (manifest.parent/name).is_file() or sha(manifest.parent/name)!=value]
 assert not mismatches,(name,mismatches)
 checks.append({'bundle':name,'manifest':str(manifest.relative_to(root)),'entries':len(files),'mismatches':mismatches,'unchanged':True})
(b/'historical-preservation.json').write_text(json.dumps({'checks':checks,'old_gate_states_unchanged':True,
 'no_old_output_stamp_or_recovery':True,'old_target_jars':'authorized rebuild directory; not asserted unchanged'},indent=2)+'\n')
selected=json.loads((b/'selected-native-bytes-recheck.json').read_text())
for item in selected['checks']:
 p=Path(item['path']); assert p.exists()==item['exists']
 assert (sha(p) if p.exists() else None)==item['sha256']
selected['rechecked_after_package']=True
(b/'selected-native-bytes-recheck.json').write_text(json.dumps(selected,indent=2)+'\n')
source=json.loads((b/'package-after-source-sha256.json').read_text())
assert all(sha(root/k)==v for k,v in source.items())
changed=json.loads((b/'changed-files.json').read_text())
assert all(sha(root/entry['path'])==entry['after_sha256'] for entry in changed['all_changed_files'])
head=subprocess.run(['rtk','proxy','git','rev-parse','HEAD'],capture_output=True,text=True,check=True).stdout.strip()
assert head==(b/'head-before.txt').read_text().strip()
status=subprocess.run(['rtk','proxy','git','status','--short','--untracked-files=normal'],capture_output=True,text=True,check=True)
(b/'workspace-after.txt').write_text(status.stdout)
links=[]
for p in [b/'assessment.md',root/'docs/plans/20260928-dual-lifecycle-detailed-design.zh-CN.md',root/'docs/plans/20260927-dual-lifecycle-worklist.zh-CN.md']:
 text=p.read_text()
 if p!=b/'assessment.md':
  marker='### 10.3 W09' if 'detailed-design' in p.name else '### 23.23 W09'
  text=text.split(marker,1)[1]
  if p!=b/'assessment.md' and 'detailed-design' in p.name:text=text.split('## 11.',1)[0]
 for target in re.findall(r'\]\(([^)]+)\)',text):
  if '://' in target or target.startswith('#'):continue
  target=target.split('#',1)[0]
  assert (p.parent/target).exists(),(str(p),target)
  links.append({'document':str(p.relative_to(root)),'target':target,'exists':True})
result={'historical_materials_equal':True,'selected_native_bytes_unchanged':True,
 'source_inputs':len(source),'source_equal_after_test_package_materials':True,
 'changed_files_identity_verified':len(changed['all_changed_files']),'head_unchanged':head,
 'new_document_links':links,'provider_native_runs':0,'extra_agents':0,'sixth_case_started':False,
 'assessment_is_author_self_review_not_independent_PASS':True}
(b/'material-verification.json').write_text(json.dumps(result,indent=2)+'\n')
files={p.relative_to(b).as_posix():sha(p) for p in sorted(b.rglob('*')) if p.is_file() and p.name!='material-sha256.json'}
(b/'material-sha256.json').write_text(json.dumps({'files':files,'count':len(files),
 'scope':'new implementation submission including snapshots/logs/XML/frozen release copies; manifest excludes itself'},indent=2)+'\n')
print(json.dumps({'verified_materials':len(files),'source_inputs':len(source),'changed_files':len(changed['all_changed_files']),
 'historical_entries':[x['entries'] for x in checks],'document_links':len(links),'sixth_case_started':False}))
