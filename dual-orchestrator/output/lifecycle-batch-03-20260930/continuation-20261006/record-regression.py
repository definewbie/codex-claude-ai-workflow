"""Record only suites named by this invocation; never count mixed stale target reports."""
import sys,json,re,pathlib,shutil,hashlib,xml.etree.ElementTree as E
out=pathlib.Path(__file__).resolve().parent
name=sys.argv[1]; log=(out/(name+".log")).read_text(); suites=[];module=None
for line in log.splitlines():
    match=re.search(r"--- surefire:[^ ]+:test .* @ ([^ ]+) ---",line)
    if match:module=match.group(1)
    match=re.search(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).* -- in (.+)$",line)
    if match:
        tests,failures,errors,skipped=map(int,match.groups()[:4]); cls=match.group(5).strip()
        src=pathlib.Path(module)/"target/surefire-reports"/("TEST-"+cls+".xml")
        dst=out/(name+"-reports")/module/src.name
        dst.parent.mkdir(parents=True,exist_ok=True)
        if src.exists():
            tree=E.parse(src).getroot()
            actual=[int(tree.attrib.get(k,0)) for k in ["tests","failures","errors","skipped"]]
            if actual!=[tests,failures,errors,skipped]:raise ValueError((src,actual,[tests,failures,errors,skipped]))
            shutil.copy2(src,dst)
        else:raise ValueError("missing current report: "+str(src))
        suites.append(dict(module=module,classname=cls,tests=tests,failures=failures,errors=errors,skipped=skipped,xml=str(dst.relative_to(out)),sha256=hashlib.sha256(dst.read_bytes()).hexdigest()))
by={}
for s in suites:
    item=by.setdefault(s["module"],dict(tests=0,failures=0,errors=0,skipped=0,suites=0));item["suites"]+=1
    for k in ["tests","failures","errors","skipped"]:item[k]+=s[k]
result=dict(invocation=name,build_success="BUILD SUCCESS" in log,live_provider_runs=0,totals={k:sum(s[k] for s in suites) for k in ["tests","failures","errors","skipped"]},suite_count=len(suites),modules=by,suites=suites)
(out/(name+"-results.json")).write_text(json.dumps(result,indent=2)+"\n")
print(json.dumps({k:v for k,v in result.items() if k!="suites"},indent=2))
