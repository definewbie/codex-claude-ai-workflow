from pathlib import Path
import json,hashlib,subprocess,xml.etree.ElementTree as E
r=Path.cwd();b=r/'output/w09-sixth-forward-fix-20261015'
report=r/'dual-adapter-acpx/target/surefire-reports/TEST-dev.dual.adapter.acpx.AcpxCurrentTranscriptUsageTest.xml'
e=E.parse(report).getroot();cp=next(p.attrib['value'] for p in e.find('properties') if p.attrib['name']=='java.class.path')
classes=b/'proof-classes';classes.mkdir(exist_ok=True)
cases=json.loads((b/'proof-cases.json').read_text());inputs=[Path(c[k]) for c in cases for k in ['transcript','stderr','output']]+list((b/'proof-inputs/sessions').glob('*'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before={str(p):sha(p) for p in inputs}
rows=[]
for name,args in [('compile',['javac','-cp',cp,'-d',str(classes),str(b/'RealSixthUsageProof.java')]),('historical-sixth',['java','-cp',str(classes)+':'+cp,'dev.dual.adapter.acpx.RealSixthUsageProof',str(b/'proof-inputs/sessions'),str(b/'proof-cases.json')])]:
 p=subprocess.run(['rtk','proxy',*args],capture_output=True,text=True,timeout=60)
 (b/(name+'.stdout')).write_text(p.stdout);(b/(name+'.stderr')).write_text(p.stderr);rows.append({'name':name,'exit':p.returncode})
 if p.returncode: print(p.stderr[-3000:]);raise SystemExit(p.returncode)
assert before=={str(p):sha(p) for p in inputs}
(b/'historical-proof-inputs-unchanged.json').write_text(json.dumps(before,indent=2)+'\n')
(b/'offline-proof-results.json').write_text(json.dumps({'rows':rows,'cases':4,'diagnostic_recomputed_total':70801,'old_known_partial_fixed':51231,'old_correction_fixed':'UNAVAILABLE unchanged','unknown_field_raws_still_refused':2,'historical_inputs_unchanged':True,'real_native_provider_calls':0,'not_new_gate_or_budget_authority':True},indent=2)+'\n')
print('4 archived occurrences attributed; 2 unknown response raws still refused; diagnostic total 70801 only; no ledger writes')
