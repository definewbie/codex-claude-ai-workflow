import hashlib, json, shutil, subprocess, zipfile
from pathlib import Path
root = Path.cwd(); out = Path(__file__).parent; release = out/"release"; release.mkdir(exist_ok=True)
def sha(data): return hashlib.sha256(data).hexdigest()
apps={}; resources=[]; commands=[]
for app in ["dual-cli","dual-server"]:
    jar = root/app/"target"/(app+"-0.1.0-SNAPSHOT.jar")
    frozen = release/jar.name; shutil.copy2(jar,frozen)
    with zipfile.ZipFile(frozen) as z:
        modules=[]
        for entry in z.namelist():
            if entry.startswith("BOOT-INF/lib/dual-") and entry.endswith(".jar"):
                name=entry.split("/")[-1].removesuffix("-0.1.0-SNAPSHOT.jar")
                local=root/name/"target"/(name+"-0.1.0-SNAPSHOT.jar")
                assert z.read(entry)==local.read_bytes(),(app,name)
                modules.append({"module":name,"sha256":sha(local.read_bytes()),"embedded_bytes_equal":True})
        own=[]
        for p in (root/app/"target/classes").rglob("*"):
            if not p.is_file(): continue
            rel=p.relative_to(root/app/"target/classes").as_posix(); entry="BOOT-INF/classes/"+rel
            assert z.read(entry)==p.read_bytes(),(app,rel)
            own.append({"path":entry,"sha256":sha(p.read_bytes())})
    apps[app]={"path":str(jar),"frozen_path":str(frozen.resolve()),"sha256":sha(frozen.read_bytes()),
        "frozen_copy_equal":frozen.read_bytes()==jar.read_bytes(),"embedded_project_modules":modules,
        "own_class_resource_files":len(own),"own_class_resource_bytes_equal":True,"own_files":own}
for module in ["dual-runtime","dual-adapter-acpx"]:
    with zipfile.ZipFile(root/module/"target"/(module+"-0.1.0-SNAPSHOT.jar")) as z:
        for p in (root/module/"src/main/resources").rglob("*"):
            if not p.is_file(): continue
            rel=p.relative_to(root/module/"src/main/resources").as_posix(); assert z.read(rel)==p.read_bytes()
            resources.append({"module":module,"path":rel,"sha256":sha(p.read_bytes()),"jar_bytes_equal":True})
checks=[(["--help"],"cli-help"),(["start","--help"],"cli-start-help"),
        (["review","record-readiness","--help"],"cli-readiness-help")]
for variant in ["contract","full","terminal-delivery"]:
    checks.append((["workflow","validate",str(root/"dual-runtime/src/main/resources/dev/dual/runtime/lifecycle"/("agent-lifecycle."+variant+".json"))],variant+"-validate"))
for args,name in checks:
    proc=subprocess.run(["rtk","proxy","java","-jar",apps["dual-cli"]["frozen_path"],*args],capture_output=True,timeout=90)
    (out/(name+".stdout.txt")).write_bytes(proc.stdout); (out/(name+".stderr.txt")).write_bytes(proc.stderr)
    commands.append({"args":args,"exit":proc.returncode,"stdout_sha256":sha(proc.stdout),"stderr_sha256":sha(proc.stderr)})
    assert proc.returncode==0,(name,proc.returncode)
artifact={"package_exit":0,"package_tests_skipped":True,"not_a_second_test_run":True,"applications":apps,
    "module_resources":resources,"commands":commands,"client_or_provider_sessions":0}
(out/"artifact-verification.json").write_text(json.dumps(artifact,indent=2)+"\n")
source=json.loads((out/"reactor-after-source-sha256.json").read_text())
current={name:sha(Path(name).read_bytes()) for name in source}; assert current==source
(out/"package-after-source-sha256.json").write_text(json.dumps(current,indent=2)+"\n")
identity={"status":"FROZEN_IMPLEMENTATION_CANDIDATE / AWAITING_AFFECTED_CC / SEVENTH_NOT_RUN",
    "head":(out/"head-before.txt").read_text().strip(),"head_is_not_worktree_identity":True,
    "source_inputs":source,"source_input_count":len(source),"source_before_after_equal":True,
    "single_reactor_tests":json.loads((out/"test-results.json").read_text()),"application_jars":apps,
    "artifact_verification":str((out/"artifact-verification.json").resolve()),
    "selected_native_bytes_recheck":str((out/"selected-native-bytes-recheck.json").resolve()),
    "native_metadata_reused_only_as_unchanged_bytes":True,"fresh_native_effective_configuration":"NOT_RUN; next authorized Gate must recheck",
    "topology_candidate":"single INLINE router JVM/ClassLoader; actual PID/loader/client behavior NOT_RUN",
    "parent_release_identity":str((root/"output/w09-revision-forward-fix-20261014/release-identity.json").resolve()),
    "historical_incident_materials":"UNCHANGED; no old raw-output stamping or recovery",
    "real_native_provider_runs":0,"sixth_case":"FAIL preserved; not recovered", "seventh_case":"NOT_STARTED; no real-run authorization"}
(out/"release-identity.json").write_text(json.dumps(identity,indent=2)+"\n")
print(json.dumps({"application_jars":{k:v["sha256"] for k,v in apps.items()},"resources":len(resources),"cli_readonly_checks":len(commands)}))
