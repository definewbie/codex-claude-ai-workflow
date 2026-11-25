import {query} from '/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/sdk.mjs';
import {writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const out=fileURLToPath(new URL('.',import.meta.url));
let release;const pending=new Promise(r=>release=r);
async function* input(){await pending;}
const q=query({prompt:input(),options:{cwd:out,tools:[],settingSources:[],persistSession:false,strictMcpConfig:true,mcpServers:{},permissionMode:'dontAsk',extraArgs:{'safe-mode':null,'disable-slash-commands':null},pathToClaudeCodeExecutable:'/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk-darwin-arm64/claude'}});
const timeout=setTimeout(()=>{q.close();release();process.exitCode=2;},15000);
try{const models=await q.supportedModels();writeFileSync(out+'metadata-preflight.json',JSON.stringify({status:'PASS_METADATA_ONLY',userMessages:0,models},null,2)+'\n');console.log(JSON.stringify(models));}catch(e){writeFileSync(out+'metadata-preflight.json',JSON.stringify({status:'INVALID',userMessages:0,error:String(e)},null,2));console.error(String(e));process.exitCode=1;}finally{clearTimeout(timeout);q.close();release();}
