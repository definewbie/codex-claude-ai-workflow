import hashlib, json, subprocess
from pathlib import Path
out = Path(__file__).parent
main = Path("dual-runtime/src/main/java/dev/dual/runtime/handler")
cases = [
    ("full-reference", main/"TaskContractProposalReference.java", "!resolved.reference().equals(candidate.get())", "!resolved.reference().proposalId().equals(candidate.get().proposalId())", "ProposalReferenceIdentityTest", "SameIdDifferentFullReference"),
    ("program-sha", main/"TaskContractProposalRevisionMerger.java", "errors.add(\"REVISION_BASE_PROPOSAL_CONTENT_MISMATCH\");", "// mutant: ignore program base content mismatch", "TaskContractProposalRevisionMergerTest#exactIdCannotHideChangedProgramBaseBytes", "exactIdCannotHide"),
    ("goal-shape", main/"TaskContractProposalValidator.java", "errors.add(\"goal re-proposal requires actual objective or exclusion change\");", "errors.addAll(java.util.List.of());", "HumanRequirementContinuationSequenceTest#humanGoalRevisionUsesFreshSubjectAndMissingOrForgedFormsNeverPublish", "humanGoalRevision")]
results=[]
for name,path,old,new,test,marker in cases:
    original=path.read_bytes(); text=original.decode(); assert old in text
    log=out/("counterproof-"+name+".log")
    try:
        mutated=text.replace(old,new)
        path.write_text(mutated)
        with log.open("wb") as f:
            result=subprocess.run(["rtk","proxy","mvn","-pl","dual-adapter-acpx","-am", "-Dtest="+test,
                "-Dsurefire.failIfNoSpecifiedTests=false","test"],stdout=f,stderr=subprocess.STDOUT)
        content=log.read_text()
        detected=result.returncode!=0 and marker in content and "<<< FAILURE" in content and "COMPILATION ERROR" not in content
        results.append({"name":name,"source":str(path),"test":test,"mutation":old+" => "+new,
            "exit":result.returncode,"expected_failure_detected":detected,"log":str(log),"original_sha256":hashlib.sha256(original).hexdigest()})
    finally:
        path.write_bytes(original)
    results[-1]["source_restored"]=path.read_bytes()==original
    print(name,results[-1]["expected_failure_detected"],flush=True)
(out/"counterproof-results.json").write_text(json.dumps({"type":"ISOLATED_FORWARD_GUARD_MUTANTS_NOT_REGRESSION_TOTAL", "cases":results},indent=2)+"\n")
assert all(r["expected_failure_detected"] and r["source_restored"] for r in results)
