import {query} from '/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/sdk.mjs';
import {readFileSync,appendFileSync} from 'node:fs';
const s=JSON.parse(readFileSync(process.argv[2],'utf8'));
const env={PATH:process.env.PATH,HOME:s.home,TMPDIR:s.tmp,
  CLAUDE_CONFIG_DIR:s.config,ANTHROPIC_BASE_URL:s.endpoint,
  ANTHROPIC_AUTH_TOKEN:'diagnostic-not-a-real-credential',
  CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC:'1'};
const options={cwd:s.workspace,env,model:'glm-5.3',maxTurns:3,
  tools:s.open ? ['Read','Write'] : ['Read','Grep','Glob'],
  allowedTools:s.open ? ['Read','Write'] : ['Read','Grep','Glob'],
  disallowedTools:s.open ? [] : ['Bash','Write','Edit','NotebookEdit','Agent','Task','Skill','WebFetch','WebSearch','ToolSearch'],
  permissionMode:'dontAsk',settingSources:[],persistSession:false,strictMcpConfig:true,
  mcpServers:{},plugins:[],skills:[],pathToClaudeCodeExecutable:s.binary,
  extraArgs:{'safe-mode':null,'restricted':null,'disable-slash-commands':null},
  stderr:t=>appendFileSync(s.stderr,t)};
const q=query({prompt:'Read the diagnostic fixture. This is a local native protocol test, no external model is in use.',options});
try {for await(const e of q)console.log(JSON.stringify(e));}
catch(e){console.log(JSON.stringify({type:'probe_error',message:String(e)}));process.exitCode=1;}
finally{q.close();}
