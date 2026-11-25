// Diagnostic only. Not imported by offline checks. No adapter or engine modification.
import {readFileSync, appendFileSync, writeFileSync} from 'node:fs';
const spec = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const {query, resolveSettings} = await import(spec.sdk);
const roots = [spec.control, spec.git];
const deny = roots.flatMap(p => ['Edit', 'Write'].flatMap(t => [t + '(/' + p + ')', t + '(/' + p + '/**)']));
const sandbox = {enabled:true, failIfUnavailable:true, allowUnsandboxedCommands:false,
  autoAllowBashIfSandboxed:true, excludedCommands:[],
  filesystem:{allowWrite:[spec.work, spec.tmp], denyWrite:roots, disabled:false},
  network:{allowedDomains:[], strictAllowlist:true, allowLocalBinding:false, allowUnixSockets:[]}};
const managedSettings = {sandbox, permissions:{deny}};
const resolved = await resolveSettings({cwd:spec.work, settingSources:[], managedSettings});
// Save only restriction-related values, never raw settings/credentials.
const effective = resolved.effective;
const snapshot = {sandbox:effective.sandbox, permissions:{deny:effective.permissions?.deny},
  sources:resolved.sources.map(s => ({source:s.source, path:s.path, policyOrigin:s.policyOrigin})),
  missingDenies:deny.filter(x => !effective.permissions?.deny?.includes(x))};
writeFileSync(spec.settingsEvidence, JSON.stringify(snapshot, null, 2));
if (resolved.sources.some(s => s.settings.policyHelper) || snapshot.missingDenies.length ||
    effective.sandbox?.enabled !== true || effective.sandbox?.failIfUnavailable !== true ||
    effective.sandbox?.allowUnsandboxedCommands !== false || effective.sandbox?.filesystem?.disabled === true ||
    !roots.every(p => effective.sandbox?.filesystem?.denyWrite?.includes(p)) ||
    effective.sandbox?.excludedCommands?.length) {
  throw new Error('D04_INVALID_EFFECTIVE_SETTINGS: no query started');
}
const options = {cwd:spec.work, env:{...process.env}, model:'glm-5.3', maxTurns:24,
  tools:['Read','Write','Edit','Bash'], allowedTools:['Read','Write','Edit','Bash'],
  disallowedTools:['NotebookEdit','Agent','Task','Skill','WebFetch','WebSearch','ToolSearch'],
  permissionMode:'dontAsk', allowDangerouslySkipPermissions:false,
  settingSources:[], managedSettings, sandbox,
  persistSession:false, strictMcpConfig:true, mcpServers:{}, plugins:[], skills:[],
  pathToClaudeCodeExecutable:spec.binary,
  extraArgs:{'safe-mode':null, 'restricted':null, 'disable-slash-commands':null},
  stderr:t => appendFileSync(spec.stderr,t)};
writeFileSync(spec.optionsEvidence, JSON.stringify({cwd:options.cwd, tools:options.tools,
  permissionMode:options.permissionMode, settingSources:[], managedSettings, sandbox,
  executable:options.pathToClaudeCodeExecutable, realProviderCallsPlanned:0},null,2));
const q = query({prompt:'Local diagnostic fixture only; controlled local backend supplies tool calls.',options});
try { for await (const event of q) console.log(JSON.stringify(event)); }
catch (e) { console.log(JSON.stringify({type:'probe_error', message:String(e)})); process.exitCode=1; }
finally { q.close(); }
