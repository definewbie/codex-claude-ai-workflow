import {query} from '/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/sdk.mjs';
import {writeFileSync} from 'node:fs';
import {routeOptions} from './route-options.mjs';
const {options,publicIdentity,redact}=routeOptions();
let release; const pending=new Promise(r=>release=r);
async function* input(){await pending;}
const q=query({prompt:input(),options:{...options,cwd:new URL('.',import.meta.url).pathname}});
const timeout=setTimeout(()=>{q.close();release();process.exitCode=2;},15000);
let result;
try {
  const init=await q.initializationResult();
  result={status:'PASS_METADATA_ONLY',userMessages:0,publicIdentity,
    initializationFields:Object.keys(init),serverAuthentication:'UNVERIFIED',
    note:'No model prompt; SDK/native handshake only. Not a model catalog proof for the configured backend.'};
} catch(e) {result={status:'INVALID_METADATA',userMessages:0,error:redact(e)};process.exitCode=1;}
finally {clearTimeout(timeout);q.close();release();}
writeFileSync(new URL('./metadata-preflight.json',import.meta.url),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result));
