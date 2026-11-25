import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const route=JSON.parse(readFileSync(new URL('./routing.json',import.meta.url),'utf8'));
// Read only the existing connection values. No wholesale settings inheritance.
export function routeOptions() {
  const configured=JSON.parse(readFileSync(route.settingsSource,'utf8')).env ?? {};
  if(configured.ANTHROPIC_BASE_URL !== route.endpoint || configured.ANTHROPIC_DEFAULT_SONNET_MODEL !== route.model)
    throw new Error('ROUTING_CHANGED');
  const token=configured.ANTHROPIC_AUTH_TOKEN;
  if(typeof token!=='string' || !token) throw new Error('MISSING_CONFIGURED_CREDENTIAL');
  const env={};
  for(const key of ['HOME','PATH','TMPDIR','LANG','LC_ALL']) if(process.env[key]) env[key]=process.env[key];
  Object.assign(env,{ANTHROPIC_BASE_URL:route.endpoint,ANTHROPIC_AUTH_TOKEN:token,
    ANTHROPIC_MODEL:route.model,CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC:'1'});
  const redact=text=>String(text).split(token).join('[REDACTED_CREDENTIAL]');
  return {route,redact,options:{env,model:route.model,effort:'low',
    tools:['Read','Grep','Glob'],allowedTools:['Read','Grep','Glob'],
    disallowedTools:['Bash','Write','Edit','NotebookEdit','Agent','Task','Skill','WebFetch','WebSearch','ToolSearch'],
    permissionMode:'dontAsk',settingSources:[],persistSession:false,strictMcpConfig:true,
    mcpServers:{},plugins:[],skills:[],
    extraArgs:{'safe-mode':null,'restricted':null,'disable-slash-commands':null},
    pathToClaudeCodeExecutable:route.nativePath},
    publicIdentity:{model:route.model,endpoint:route.endpoint,nativePath:route.nativePath,
      credentialPresent:true,credentialContentRecorded:false,
      routeSha256:createHash('sha256').update(readFileSync(new URL('./routing.json',import.meta.url))).digest('hex')}};
}
