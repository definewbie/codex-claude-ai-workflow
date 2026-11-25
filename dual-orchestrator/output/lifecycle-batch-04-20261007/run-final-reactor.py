from pathlib import Path
import json,hashlib,subprocess,xml.etree.ElementTree as ET,datetime
root=Path.cwd(); out=root/'output/lifecycle-batch-04-20261007'
def snapshot():
 files=set()
 for module in root.glob('dual-*'):
  if not module.is_dir():continue
  files.update(p for p in (module/'src').rglob('*') if p.is_file())
  if (module/'pom.xml').exists():files.add(module/'pom.xml')
 for sub in ['src','test','scripts','skills','agents','docs']:
  files.update(p for p in (root/'.tools/acpx-e1/source'/sub).rglob('*') if p.is_file())
 files.update(p for p in (root/'tools/acpx-e1').rglob('*') if p.is_file())
 files.update(p for p in (root/'scripts').rglob('*') if p.is_file() and '__pycache__' not in str(p))
 files.update([root/'pom.xml',root/'.tools/acpx-e1/source/package.json',root/'.tools/acpx-e1/source/pnpm-lock.yaml',root/'.tools/acpx-e1/source/CHANGELOG.md'])
 return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)}
before=snapshot(); (out/'final-before-source-sha256.json').write_text(json.dumps(before,indent=2)+'\n')
started=datetime.datetime.now().astimezone().isoformat()
with (out/'reactor-final.log').open('wb') as log:
 result=subprocess.run(['rtk','proxy','mvn','clean','test'],cwd=root,stdout=log,stderr=subprocess.STDOUT)
after=snapshot();(out/'final-after-source-sha256.json').write_text(json.dumps(after,indent=2)+'\n')
modules={};suiteTotal=0
for module in root.glob('dual-*'):
 suites=list((module/'target/surefire-reports').glob('TEST-*.xml'))
 if not suites:continue
 counts={key:0 for key in ['tests','failures','errors','skipped']}
 for file in suites:
  xml=ET.parse(file).getroot()
  for key in counts:counts[key]+=int(xml.attrib.get(key,0))
 counts['suites']=len(suites);suiteTotal+=len(suites);modules[module.name]=counts
summary={key:sum(m[key] for m in modules.values()) for key in ['tests','failures','errors','skipped']}
value={'status':'PASS' if result.returncode==0 and before==after and not any(summary[k] for k in ['failures','errors','skipped']) else 'FAIL' if before==after else 'INVALID','command':['mvn','clean','test'],'exit_code':result.returncode,'started_at':started,'finished_at':datetime.datetime.now().astimezone().isoformat(),'single_clean_reactor':True,'source_fingerprints_unchanged':before==after,'source_file_count':len(before),'suites':suiteTotal,'summary':summary,'modules':modules,'live_provider_runs':0,'native_client_runs':0,'agents_spawned':0,'note':'Local child processes and simulated ACP peers only. Actual native/provider and browser runtime remain NOT RUN.'}
(out/'test-results.json').write_text(json.dumps(value,indent=2)+'\n');print(json.dumps(value))
