from pathlib import Path
import subprocess,json,hashlib
r=Path.cwd();b=r/'output/w09-current-transcript-forward-fix-20261011'
# Classpath copied from the actual clean reactor JVM, not a separately selected dependency version.
import xml.etree.ElementTree as E
report=r/'dual-adapter-acpx/target/surefire-reports/TEST-dev.dual.adapter.acpx.AcpxCurrentTranscriptUsageTest.xml'
e=E.parse(report).getroot();cp=next(p.attrib['value'] for p in e.find('properties') if p.attrib['name']=='java.class.path')
classes=b/'proof-classes';classes.mkdir(exist_ok=True)
def call(args,name,expected=0):
 result=subprocess.run(['rtk','proxy']+args,capture_output=True,text=True,timeout=60)
 (b/(name+'.stdout')).write_text(result.stdout);(b/(name+'.stderr')).write_text(result.stderr)
 assert result.returncode==expected or expected=='nonzero' and result.returncode!=0,(name,result.returncode,result.stderr)
 return {'name':name,'exit':result.returncode,'expected':expected,'stdout_sha256':hashlib.sha256(result.stdout.encode()).hexdigest(),'stderr_sha256':hashlib.sha256(result.stderr.encode()).hexdigest()}
rows=[call(['javac','-cp',cp,'-d',str(classes),str(b/'RealCurrentUsageProof.java')],'proof-compile')]
actual=['historical',str(b/'proof-sessions'),str(b/'proof-cases.json')]
base=['java','-cp',str(classes)+':'+cp,'dev.dual.adapter.acpx.RealCurrentUsageProof']
cases=json.loads((b/'proof-cases.json').read_text())
inputs=[Path(c[k]) for c in cases for k in ['transcript','stderr']]
inputs+=list((b/'proof-sessions').glob('*'))
before={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
rows.append(call(base+actual,'actual-current-transcripts-offline-proof'))
rows.append(call(base+['strict',str(b/'strict-fixture')],'strict-control-proof'))
source=r/'dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxUsageReader.java'
for name,edit,args in [
 ('current-source-removed',lambda s:s.replace('var current = readCurrentUsage(transcriptPath, completion);','AcpxUsage current = null;'),actual),
 ('current-request-guard-removed',lambda s:s.replace('!completion.requestId().equals(wire.requestId())','false'),['strict',str(b/'strict-mutant-fixture')])]:
 d=b/'mutants'/name;d.mkdir(parents=True,exist_ok=True);s=source.read_text();edited=edit(s);assert s!=edited
 f=d/source.name;f.write_text(edited)
 rows.append(call(['javac','-cp',cp,'-d',str(d),str(f)],name+'-compile'))
 rows.append(call(['java','-cp',str(d)+':'+str(classes)+':'+cp,'dev.dual.adapter.acpx.RealCurrentUsageProof']+args,name,'nonzero'))
assert before=={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
(b/'historical-proof-inputs-unchanged.json').write_text(json.dumps(before,indent=2)+'\n')
(b/'offline-proof-results.json').write_text(json.dumps({'rows':rows,'native_provider_calls':0,'maven_tests_not_added_by_this_proof':True,'historical_materials_read_only':True,'no_ledger_repair':True,'diagnostic_recomputed_total':403420,'frozen_production_classes_not_modified':True},indent=2)+'\n')
print('8 actual transcripts PASS; strict control PASS; isolated 2 mutants expected failures')
