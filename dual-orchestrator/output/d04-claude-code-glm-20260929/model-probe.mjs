import {EventGuard,consumeGuarded} from './event-guard.mjs';
import {routeOptions} from './route-options.mjs';
import {query} from '/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/sdk.mjs';
import {readFileSync,appendFileSync} from 'node:fs';
const spec=JSON.parse(readFileSync(process.argv[2],'utf8'));
const {options,redact}=routeOptions();
if(spec.model!==options.model || spec.binary!==options.pathToClaudeCodeExecutable) throw new Error('SPEC_ROUTING_MISMATCH');
const log=entry=>console.log(redact(JSON.stringify(entry)));
const q=query({prompt:spec.prompt,options:{...options,cwd:spec.fixture,maxTurns:5,
  stderr:text=>appendFileSync(spec.stderr,redact(text))}});
try {const stopped=await consumeGuarded(q,new EventGuard(spec.model),log,()=>q.close());if(stopped)process.exitCode=2;}
catch(error){log({type:'probe_error',message:redact(error)});process.exitCode=1;}
