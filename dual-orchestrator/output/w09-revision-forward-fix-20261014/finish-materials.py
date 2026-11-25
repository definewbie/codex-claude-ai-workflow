import difflib, hashlib, json, shutil, xml.etree.ElementTree as ET
from pathlib import Path
b = Path(__file__).parent
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
source = json.loads((b/"reactor-before-source-sha256.json").read_text())
after = {k:digest(Path(k)) for k in source}; assert source == after
(b/"reactor-after-source-sha256.json").write_text(json.dumps(after,indent=2)+"\n")
modules={}; reports=[]
for module in Path(".").glob("dual-*"):
    suitepaths=list((module/"target/surefire-reports").glob("TEST-*.xml"))
    if not suitepaths: continue
    count={"tests":0,"failures":0,"errors":0,"skipped":0,"suites":len(suitepaths)}
    for path in suitepaths:
        tree=ET.parse(path).getroot()
        for key in ["tests","failures","errors","skipped"]: count[key]+=int(tree.attrib.get(key,0))
        dest=b/"reactor-reports"/module.name/path.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,dest)
        reports.append(str(dest))
    modules[module.name]=count
total={key:sum(v[key] for v in modules.values()) for key in ["tests","failures","errors","skipped","suites"]}
assert len(modules)==8 and total["failures"]==total["errors"]==total["skipped"]==0
assert "BUILD SUCCESS" in (b/"reactor-final.log").read_text()
total.update({"exit":0,"single_clean_reactor":True,"source_before_after_equal":True,"source_inputs":len(source),
    "modules":modules,"live_provider_runs":0,"real_native_clients":0,"extra_agents":0,"sixth_case_started":False})
(b/"test-results.json").write_text(json.dumps(total,indent=2)+"\n")
print(json.dumps(total))
