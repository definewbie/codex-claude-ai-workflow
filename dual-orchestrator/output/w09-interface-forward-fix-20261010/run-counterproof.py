from pathlib import Path
import subprocess,json,xml.etree.ElementTree as E,hashlib
root=Path.cwd();base=root/'output/w09-interface-forward-fix-20261010';out=base/'counterproof';out.mkdir(exist_ok=True)
report=root/'dual-adapter-acpx/target/surefire-reports/TEST-dev.dual.adapter.acpx.AcpxCompatibilitySequenceTest.xml'
cp=next(p.get('value') for p in E.parse(report).getroot().findall('properties/property') if p.get('name')=='surefire.test.class.path')
runner=out/'RegressionCounterproof.java'
runner.write_text(r"""
import java.nio.file.*;
public class RegressionCounterproof {
 public static void main(String[] args) throws Exception {
  boolean compatibility=args[0].equals("compatibility");
  String name=compatibility?"dev.dual.adapter.acpx.AcpxCompatibilitySequenceTest":"dev.dual.adapter.acpx.WorkflowFirstLifecycleSequenceTest";
  var type=Class.forName(name);var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var test=ctor.newInstance();
  var field=type.getDeclaredField("root");field.setAccessible(true);field.set(test,Files.createTempDirectory("w09-counterproof-"));
  var method=type.getDeclaredMethod(compatibility?"optionalEngineIdentityDoesNotBlockPrimaryOrCorrectionOrInventAnIdentity":"interfaceFailureKeepsRunNodeRecoveryAndSummaryConsistentAcrossColdRead",compatibility?String.class:boolean.class);method.setAccessible(true);
  var owner=Class.forName(compatibility?"dev.dual.adapter.acpx.AcpxSessionCompatibility":"dev.dual.ledger.file.FailureSummaryWriter");
  System.out.println("OWNER="+owner.getProtectionDomain().getCodeSource().getLocation());
  try {method.invoke(test,compatibility?args[2]:Boolean.valueOf(args[2]));if(args[1].equals("mutant"))throw new AssertionError("mutant escaped user-flow assertion");System.out.println("CONTROL_PASS");}
  catch(java.lang.reflect.InvocationTargetException error){if(!args[1].equals("mutant") || !(error.getCause() instanceof AssertionError))throw error;System.out.println("EXPECTED_ASSERTION_FAILURE="+error.getCause());}
 }
}
""")
subprocess.run(['javac','--release','21','-cp',cp,'-d',str(out),str(runner)],check=True)
files={'compatibility':'dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxSessionCompatibility.java','summary':'dual-ledger-file/src/main/java/dev/dual/ledger/file/FailureSummaryWriter.java'}
rows=[]
for kind,f in files.items():
 overlay=out/(kind+'-mutant');overlay.mkdir(exist_ok=True)
 original=base/'before'/f
 subprocess.run(['javac','--release','21','-cp',cp,'-d',str(overlay),str(original)],check=True)
 for variant in (['claude','codex'] if kind=='compatibility' else ['false','true']):
  for mode in ['control','mutant']:
   actualcp=(str(overlay)+':' if mode=='mutant' else '')+str(out)+':'+cp
   result=subprocess.run(['java','-cp',actualcp,'RegressionCounterproof',kind,mode,variant],capture_output=True,text=True,cwd=root/'dual-adapter-acpx',timeout=60)
   log=out/(kind+'-'+variant+'-'+mode+'.log');log.write_text(result.stdout+result.stderr)
   assert result.returncode==0,(str(log),result.stdout,result.stderr)
   assert ('EXPECTED_ASSERTION_FAILURE=' if mode=='mutant' else 'CONTROL_PASS') in result.stdout
   if mode=='mutant':assert str(overlay) in result.stdout
   rows.append({'kind':kind,'variant':variant,'mode':mode,'exit':result.returncode,'expected_assertion_failure':mode=='mutant','owner_source_sha256':hashlib.sha256((original if mode=='mutant' else root/f).read_bytes()).hexdigest(),'log':str(log.relative_to(base))})
(base/'counterproof-results.json').write_text(json.dumps({'control_passes':4,'expected_failures':4,'production_workspace_sources_untouched':True,'scope':'old owner bytes isolated on classpath; actual regression methods, no real clients'},indent=2)+'\n')
(out/'runs.json').write_text(json.dumps(rows,indent=2)+'\n')
print('counterproof: 4 controls PASS; 4 old-owner expected assertion failures')
