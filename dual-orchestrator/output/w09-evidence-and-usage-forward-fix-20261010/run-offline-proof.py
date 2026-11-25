from pathlib import Path
import subprocess,json,hashlib
r=Path.cwd();b=r/'output/w09-evidence-and-usage-forward-fix-20261010'
# Classpath copied from the actual clean reactor JVM, not a separately selected dependency version.
import xml.etree.ElementTree as E
report=r/'dual-adapter-acpx/target/surefire-reports/TEST-dev.dual.adapter.acpx.AcpxNativeCommandSequenceTest.xml'
e=E.parse(report).getroot();cp=next(p.attrib['value'] for p in e.find('properties') if p.attrib['name']=='java.class.path')
classes=b/'proof-classes';classes.mkdir(exist_ok=True)
def call(args,name,expected=0):
 result=subprocess.run(['rtk','proxy']+args,capture_output=True,text=True,timeout=60)
 (b/(name+'.stdout')).write_text(result.stdout);(b/(name+'.stderr')).write_text(result.stderr)
 assert result.returncode==expected or expected=='nonzero' and result.returncode!=0,(name,result.returncode,result.stderr)
 return {'name':name,'exit':result.returncode,'expected':expected,'stdout_sha256':hashlib.sha256(result.stdout.encode()).hexdigest(),'stderr_sha256':hashlib.sha256(result.stderr.encode()).hexdigest()}
rows=[call(['javac','-cp',cp,'-d',str(classes),str(b/'RealUsageProof.java')],'proof-compile')]
actual=['usage',str(Path.home()/'.acpx/sessions'),'60521b08-07f4-4cc7-b656-71a2af3d7c26']
logs=r/'output/w09-fresh-second-gate-20261010/stopped-ledger-snapshot/runs/run-1791622684632/outputs'
actual += [str(logs/'intake-claude-brainstorm.stderr.log'),str(logs/'intake-claude-brainstorm.contract-correction.stderr.log')]
base=['java','-cp',str(classes)+':'+cp,'dev.dual.adapter.acpx.RealUsageProof']
native_paths=[Path(actual[1])/'e67d5ff9-924f-403b-8fc4-98ccd7d42f62.stream.ndjson',Path(actual[1])/'e67d5ff9-924f-403b-8fc4-98ccd7d42f62.json']
native_before={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in native_paths}
rows.append(call(base+actual,'actual-stream-offline-proof'))
rows.append(call(base+['render'],'render-control-proof'))
for name,rel,edit,args in [
 ('request-id-removed','dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxUsageReader.java',lambda s:s.replace('&& completion.requestId().equals(wire.requestId())','&& true'),actual),
 ('shared-evidence-rule-removed','dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultPromptContract.java',lambda s:s.replace('evidenceReferenceInstruction(nodeId) + contractRevisionInstruction','contractRevisionInstruction'),['render'])]:
 d=b/'mutants'/name;d.mkdir(parents=True,exist_ok=True);source=r/rel;s=source.read_text();edited=edit(s);assert s!=edited
 f=d/source.name;f.write_text(edited)
 rows.append(call(['javac','-cp',cp,'-d',str(d),str(f)],name+'-compile'))
 rows.append(call(['java','-cp',str(d)+':'+str(classes)+':'+cp,'dev.dual.adapter.acpx.RealUsageProof']+args,name,'nonzero'))
assert native_before=={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in native_paths}
(b/'native-proof-inputs-unchanged.json').write_text(json.dumps(native_before,indent=2)+'\n')
(b/'offline-proof-results.json').write_text(json.dumps({'rows':rows,'native_provider_calls':0,'maven_tests_not_added_by_this_proof':True,'historical_stream_read_only':True,'no_ledger_repair':True,'frozen_production_classes_not_modified':True},indent=2)+'\n')
print('offline actual stream PASS; current render PASS; isolated two mutants expected failures')
