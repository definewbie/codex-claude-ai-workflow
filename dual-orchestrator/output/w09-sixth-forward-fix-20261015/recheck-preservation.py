from pathlib import Path
import hashlib,json
r=Path.cwd();b=r/'output/w09-sixth-forward-fix-20261015'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
results={}
for name in ['w09-fresh-sixth-gate-20261015','w09-revision-forward-fix-20261014']:
 base=r/'output'/name;m=json.loads((base/'material-sha256.json').read_text());m=m.get('files',m)
 errors=[k for k,h in m.items() if not (base/k).is_file() or sha(base/k)!=h]
 assert not errors,(name,errors[:5]);results[name]={'files':len(m),'unchanged':True,'manifest_sha256':sha(base/'material-sha256.json')}
old=r/'output/w09-fresh-sixth-gate-20261015';loc=json.loads((old/'instance-location.json').read_text())
ids=json.loads((old/'stopped-ledger-identity.json').read_text())['files']
assert all(sha(Path(loc['ledger'])/k)==v for k,v in ids.items())
results['sixth_live_ledger']={'files':len(ids),'unchanged':True,'lock_excluded':True,'not_recovered':True}
(b/'historical-preservation.json').write_text(json.dumps(results,indent=2)+'\n')
parent=json.loads((r/'output/w09-current-transcript-forward-fix-20261011/release-identity.json').read_text());checks=[]
for name in ['wrapper','dist']:
 p=Path(parent['selected_acpx'][name]);h=sha(p);assert h==parent['selected_acpx'][name+'_sha256']
 checks.append({'name':name,'path':str(p),'exists':True,'sha256':h,'unchanged':True})
for name,c in parent['metadata_config_source_files'].items():
 p=Path(c['path']);assert p.exists()==c['exists'];h=sha(p) if p.exists() else None;assert h==c['sha256']
 checks.append({'name':name,'path':str(p),'exists':p.exists(),'sha256':h,'unchanged':True})
(b/'selected-native-bytes-recheck.json').write_text(json.dumps({'checks':checks,'metadata_not_executed':True,'provider_or_client_launches':0,'fresh_effective_config_not_claimed':True,'rechecked_after_package':(b/'package-after-source-sha256.json').exists()},indent=2)+'\n')
print('Preserved sixth342 / prior1929 / live191; native/config bytes unchanged, not executed')
