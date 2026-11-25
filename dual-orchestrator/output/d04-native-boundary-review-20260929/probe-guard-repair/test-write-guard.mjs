import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
import {runDeniedWrites} from './write-guard.mjs';
const checks=[];
for (const [name,codes,expectedCalls,error] of [
 ['all refused',[1,1,1],3,null],
 ['first success',[0,1,1],1,'FORBIDDEN_WRITE_SUCCEEDED'],
 ['later success',[1,0,1],2,'FORBIDDEN_WRITE_SUCCEEDED'],
 ['pending outcome',[null,1],1,'WRITE_OUTCOME_UNKNOWN'],
 ['transport exception',['throw',1],1,'TRANSPORT'],
]) {
 let called=0,caught=null;
 try {await runDeniedWrites(codes.map((_,i)=>[String(i),String(i)]),async()=>{const code=codes[called++];if(code==='throw')throw new Error('TRANSPORT');return {exit_code:code};},()=>{});}
 catch(e){caught=String(e);}
 assert.equal(called,expectedCalls); if(error)assert.ok(caught?.includes(error));else assert.equal(caught,null);
 checks.push({name,calls:called,status:'PASS'});
}
writeFileSync(new URL('./offline-results.json',import.meta.url),JSON.stringify({status:'PASS_OFFLINE_FAIL_FAST',checks,nativeSessions:0,doesNotRetroactivelyValidatePastGate:true},null,2)+'\n');
console.log(JSON.stringify({status:'PASS',checks:checks.length}));
