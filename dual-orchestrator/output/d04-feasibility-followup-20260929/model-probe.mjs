import {EventGuard,consumeGuarded} from './event-guard.mjs';
import {query} from '/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/sdk.mjs';
import {readFileSync,appendFileSync} from 'node:fs';
const spec=JSON.parse(readFileSync(process.argv[2],'utf8'));
const log=(entry)=>console.log(JSON.stringify(entry));
const q=query({prompt:spec.prompt,options:{
  cwd:spec.fixture,model:spec.model,effort:'low',maxTurns:5,maxBudgetUsd:0.50,
  tools:['Read','Grep','Glob'],allowedTools:['Read','Grep','Glob'],
  disallowedTools:['Bash','Write','Edit','NotebookEdit','Agent','Task','Skill','WebFetch','WebSearch','ToolSearch'],
  permissionMode:'dontAsk',settingSources:[],persistSession:false,
  strictMcpConfig:true,mcpServers:{},plugins:[],skills:[],
  extraArgs:{'safe-mode':null,'disable-slash-commands':null},
  pathToClaudeCodeExecutable:spec.binary,
  stderr:(text)=>appendFileSync(spec.stderr,text),
}});
try {const stopped=await consumeGuarded(q,new EventGuard(spec.model),log,()=>q.close()); if(stopped) process.exitCode=2;}
catch(error){log({type:'probe_error',message:String(error)});process.exitCode=1;}

