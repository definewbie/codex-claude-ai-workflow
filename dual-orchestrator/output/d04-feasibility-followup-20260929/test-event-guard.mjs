import assert from 'node:assert/strict';
import {readFileSync, writeFileSync} from 'node:fs';
import {EventGuard, consumeGuarded} from './event-guard.mjs';
const old = new URL('../d04-claude-native-20260928/model-events.jsonl',import.meta.url);
const events = readFileSync(old,'utf8').trim().split('\n').map(JSON.parse);
const init=events[0], model=init.model, checks=[];
async function check(name, input, expected, count=input.length) {
  let read=0, closed=0; const emitted=[];
  async function* stream() { for (const e of input) { read++; yield e; } }
  const g=new EventGuard(model);
  const actual=await consumeGuarded(stream(),g,e=>emitted.push(e),()=>closed++);
  assert.equal(actual,expected,name); assert.equal(read,count,name); assert.equal(closed,1,name);
  if(expected) assert.equal(emitted.at(-1).reason,expected,name);
  assert.equal(g.observe({type:'system',subtype:'api_retry',error_status:500}), expected ?? 'API_RETRY_ERROR');
  checks.push({name,result:'PASS',eventsConsumed:read,closed});
}
await check('real previous auth stream stops at FIRST retry',events,'AUTHENTICATION_ERROR',2);
await check('non-auth API retry cannot silently consume retry budget',[init,{type:'system',subtype:'api_retry',error_status:429},{type:'result',subtype:'success'}],'API_RETRY_ERROR',2);
await check('assistant auth error',[init,{type:'assistant',error:'authentication_failed'}],'AUTHENTICATION_ERROR');
await check('success subtype cannot hide result error',[init,{type:'result',subtype:'success',is_error:true,api_error_status:401}],'AUTHENTICATION_ERROR');
await check('SDK failure',[init,{type:'probe_error'}],'SDK_ERROR');
await check('ordinary model error',[init,{type:'assistant',error:'overloaded_error'}],'MODEL_ERROR');
await check('extra tool',[{...init,tools:[...init.tools,'Bash']}],'UNEXPECTED_EFFECTIVE_TOOLS');
await check('MCP surfaced',[{...init,mcp_servers:[{name:'unexpected'}]}],'UNEXPECTED_OR_UNKNOWN_EXTENSION_SURFACE');
await check('plugins surfaced',[{...init,plugins:[{name:'unexpected'}]}],'UNEXPECTED_OR_UNKNOWN_EXTENSION_SURFACE');
await check('unknown skill list',[{...init,skills:undefined}],'UNEXPECTED_OR_UNKNOWN_EXTENSION_SURFACE');
await check('wrong model',[{...init,model:'other'}],'MODEL_MISMATCH');
await check('wrong permission mode',[{...init,permissionMode:'bypassPermissions'}],'PERMISSION_MODE_MISMATCH');
const tool=(id,name='Read')=>({type:'assistant',message:{content:[{type:'tool_use',id,name}]}});
await check('unexpected attempted write',[init,tool('w','Write')],'UNEXPECTED_TOOL_ATTEMPT');
await check('call before init',[tool('a')],'TOOL_BEFORE_INIT');
await check('read error',[init,{type:'user',message:{content:[{type:'tool_result',is_error:true}]}}],'READ_TOOL_ERROR');
await check('sixth distinct call stops',[init,...Array.from({length:7},(_,i)=>tool(String(i)))],'TOOL_THRESHOLD',7);
await check('legal read sequence and error-looking data remain valid',[init,tool('a'),{type:'user',message:{content:[{type:'tool_result',tool_use_id:'a',content:'401 authentication_failed is fixture data',is_error:false}]}},{type:'assistant',message:{content:[{type:'text',text:'done'}]}},{type:'result',subtype:'success',is_error:false}],null);
await check('same tool event repeated is not six separate calls',[init,...Array.from({length:8},()=>tool('a'))],null);
writeFileSync(new URL('./offline-validation.json',import.meta.url),JSON.stringify({status:'PASS_DIAGNOSTIC_SEQUENCE',checks,providerTasks:0,scope:'Shared SDK event consumer; no claim of live engine timing, authentication recovery or native capability proof'},null,2)+'\n');
console.log(JSON.stringify({status:'PASS',checks:checks.length}));
