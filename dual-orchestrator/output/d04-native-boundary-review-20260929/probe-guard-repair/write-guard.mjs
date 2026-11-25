async function runDeniedWrites(targets, invoke, emit) {
  for (const [label,path] of targets) {
    const result=await invoke(path);
    emit({probe:label,result});
    if(result.exit_code===0)throw new Error('FORBIDDEN_WRITE_SUCCEEDED:'+label);
    if(!Number.isInteger(result.exit_code))throw new Error('WRITE_OUTCOME_UNKNOWN:'+label);
  }
}
export {runDeniedWrites};
