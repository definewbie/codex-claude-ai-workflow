from pathlib import Path
import subprocess,json,shutil,xml.etree.ElementTree as ET
root=Path('/private/tmp/dual-b06-counterproof-20261009')
out=Path('/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/lifecycle-batch-06-20261009/implementation/counterproof');out.mkdir(exist_ok=True)
service=root/'dual-runtime/src/main/java/dev/dual/runtime/delivery/DeliveryApplicationService.java'
git=root/'dual-adapter-git/src/main/java/dev/dual/adapter/git/GitPatchMergeService.java'
original={service:service.read_text(),git:git.read_text()}
mutations=[
 ('stop-before-finalize',service,'if ("ACTIVE".equals(stop) || "UNKNOWN".equals(stop))','if (false)','dual-runtime','DeliveryApplicationServiceTest#exactCommitCannotReplaceMissingEvidenceThatTheOldApplyStopped'),
 ('existing-intent-is-not-fresh',service,'if (previous != null) {','if (false) {','dual-runtime','DeliveryApplicationServiceTest#unknownAtBaseNeedsANewExplicitAuthorizationAndDuplicateDoesNotReapply'),
 ('duplicate-before-obsolete-binding',service,'if (oldReceipt != null) {','if (false) {','dual-runtime','DeliveryApplicationServiceTest#humanStopRemainsADeclarationAndCloseStillAllowsAnExactExistingCommit'),
 ('receipt-before-patch-input',git,'lifecycle.started(process);','/* deliberately removed receipt callback */','dual-adapter-git','GitPatchMergeServiceTest#failedReceiptSuppliesNoPatchAndCleanHeadDriftStartsNoChild')]
results=[]
for name,path,before,after,module,test in mutations:
 for p,content in original.items():p.write_text(content)
 assert before in original[path],name
 path.write_text(original[path].replace(before,after,1))
 with (out/(name+'.log')).open('w') as log:
  result=subprocess.run(['rtk','proxy','mvn','-pl',module,'-am','-Dtest='+test,'-Dsurefire.failIfNoSpecifiedTests=false','test'],cwd=root,stdout=log,stderr=subprocess.STDOUT,timeout=180)
 reports=list((root/module/'target/surefire-reports').glob('TEST-*.xml'))
 matching=[]
 for file in reports:
  doc=ET.parse(file).getroot()
  if test.split('#')[0] in doc.attrib.get('name',''):
   matching.append(doc);shutil.copy2(file,out/(name+'.xml'))
 failures=sum(int(d.attrib.get('failures',0)) for d in matching);errors=sum(int(d.attrib.get('errors',0)) for d in matching)
 valid=result.returncode!=0 and failures>0 and errors==0
 results.append({'name':name,'removed_guard':before,'test':test,'exit':result.returncode,'assertion_failures':failures,'errors':errors,'expected_failure_proven':valid})
 print(name,valid,flush=True)
 if not valid:break
for p,content in original.items():p.write_text(content)
(out/'results.json').write_text(json.dumps({'isolated_copy':str(root),'native_provider_runs':0,'mutants':results,'status':'PASS' if len(results)==4 and all(x['expected_failure_proven'] for x in results) else 'FAIL'},indent=2)+'\n')
