from pathlib import Path
import hashlib,json,zipfile,subprocess
root=Path.cwd();b=root/'output/w09-interface-forward-fix-20261010'
def sha(data):return hashlib.sha256(data).hexdigest()
old=json.loads((root/'output/w08-preparation-20261010/release-identity.json').read_text())
source={f:sha((root/f).read_bytes()) for f in old['source_inputs']}
before=json.loads((b/'reactor-before-source-sha256.json').read_text());assert source==before
apps={};resources=[];commands=[]
modules=['dual-core','dual-ledger-file','dual-flow-liteflow','dual-runtime','dual-adapter-acpx','dual-adapter-git']
for app in ['dual-cli','dual-server']:
 jar=root/app/'target'/(app+'-0.1.0-SNAPSHOT.jar');rows=[]
 with zipfile.ZipFile(jar) as z:
  for m in modules:
   dep=root/m/'target'/(m+'-0.1.0-SNAPSHOT.jar');name='BOOT-INF/lib/'+dep.name;data=z.read(name);assert data==dep.read_bytes()
   rows.append({'module':m,'sha256':sha(data),'embedded_bytes_equal':True})
  own=[]
  for f in (root/app/'target/classes').rglob('*'):
   if f.is_file():
    name='BOOT-INF/classes/'+str(f.relative_to(root/app/'target/classes'));data=z.read(name);assert data==f.read_bytes();own.append({'path':name,'sha256':sha(data)})
 apps[app]={'path':str(jar),'sha256':sha(jar.read_bytes()),'embedded_project_modules':rows,'own_class_resource_files':len(own),'own_class_resource_bytes_equal':True,'own_files':own}
for row in json.loads((root/'output/w08-preparation-20261010/artifact-verification.json').read_text())['module_resources']:
 m=row['module'];name=row['path'];file=root/m/'src/main/resources'/name
 with zipfile.ZipFile(root/m/'target'/(m+'-0.1.0-SNAPSHOT.jar')) as z: data=z.read(name)
 assert data==file.read_bytes();resources.append({'module':m,'path':name,'sha256':sha(data),'jar_bytes_equal':True})
for row in json.loads((root/'output/w08-preparation-20261010/artifact-verification.json').read_text())['commands']:
 result=subprocess.run(['java','-jar',apps['dual-cli']['path']]+row['args'],capture_output=True,timeout=60)
 assert result.returncode==0,(row['args'],result.stderr)
 log=b/row['artifact'];log.write_bytes(result.stdout);(b/(row['artifact']+'.stderr')).write_bytes(result.stderr)
 commands.append({'args':row['args'],'exit':result.returncode,'stdout_sha256':sha(result.stdout),'stderr_sha256':sha(result.stderr),'artifact':row['artifact']})
assert source=={f:sha((root/f).read_bytes()) for f in source}
(b/'artifact-verification.json').write_text(json.dumps({'package_exit':0,'package_tests_skipped':True,'not_a_second_test_run':True,'applications':apps,'module_resources':resources,'commands':commands,'client_or_provider_sessions':0},indent=2)+'\n')
old.update({'status':'FROZEN_REPAIR_CANDIDATE / AWAITING_AFFECTED_CC / FRESH_RUN_NOT_AUTHORIZED','source_inputs':source,'source_input_count':len(source),'single_reactor_tests':json.loads((b/'test-results.json').read_text())['totals'],'source_before_after_equal':True,'application_jars':{k:{'path':v['path'],'sha256':v['sha256']} for k,v in apps.items()},'current_actual_target_git_or_client_behavior':'NOT_RUN_IN_REPAIR; FIRST_REAL_GATE_FAIL_PRESERVED','repair_artifact_verification':str(b/'artifact-verification.json'),'selected_native_bytes_recheck':str(b/'selected-native-bytes-recheck.json'),'fresh_gate':str(b/'fresh-second-gate.md'),'parent_release_identity':str(root/'output/w08-preparation-20261010/release-identity.json')})
old['actual_effective_workspace_config']='FRESH_SECOND_WORKSPACE_NOT_CREATED; RECHECK_AFTER_AUTHORIZATION'
old['topology_candidate']['actual_start_pid_loader_code_source']='FRESH_SECOND_NOT_STARTED; RECHECK_AFTER_AUTHORIZATION'
(b/'release-identity.json').write_text(json.dumps(old,indent=2)+'\n')
print('release verified:',len(source),'inputs;',len(resources),'resources;',len(commands),'metadata commands; CLI/server + six embedded modules byte equal')
