import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {createHash} from 'node:crypto';
import {resolveBuiltInAgentLaunch, findBuiltInAgentPackage, AGENT_REGISTRY} from '../../../.tools/acpx-e1/source/src/agent-registry.ts';
const results=[];
const test=(name,run)=>{run();results.push({name,status:'PASS'});};
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'dual-w03-resolver-'));
try {
 const root=path.join(temp,'adapter');fs.mkdirSync(root);
 const bin=path.join(root,'entry.js');
 fs.writeFileSync(path.join(root,'package.json'),JSON.stringify({name:'@agentclientprotocol/codex-acp',version:'fixture-1',bin:{'codex-acp':'entry.js'}}));
 fs.writeFileSync(bin,'// fixture A\n');
 const installedOptions={resolvePackageRoot:()=>root};
 test('existing resolver selects installed bin, not package-exec',()=>{const r=resolveBuiltInAgentLaunch(AGENT_REGISTRY.codex,installedOptions);assert.equal(r.source,'installed');assert.equal(r.binPath,bin);});
 const missingOptions={resolvePackageRoot:()=>{throw Error('fixture missing package');},execPath:'/fixture/node',resolveNpmCliPath:()=>'/fixture/npm-cli.js',existsSync:()=>true};
 test('missing installation produces a package-exec plan without installed bin identity',()=>{const r=resolveBuiltInAgentLaunch(AGENT_REGISTRY.codex,missingOptions);assert.equal(r.source,'package-exec');assert.equal(r.binPath,undefined);assert.equal(r.packageVersion,undefined);});
 test('configure default Codex pinned npx string is a custom launch, not a builtin installed binding',()=>{assert.equal(findBuiltInAgentPackage('npx -y @agentclientprotocol/codex-acp@1.10.0'),undefined);assert.equal(resolveBuiltInAgentLaunch('npx -y @agentclientprotocol/codex-acp@1.10.0',installedOptions),undefined);});
 test('configure default Claude wrapper is a custom launch, not a builtin installed binding',()=>{const command=path.resolve('scripts/acpx-claude-agent-env.sh')+' npx -y @agentclientprotocol/claude-agent-acp@0.70.0';assert.equal(findBuiltInAgentPackage(command),undefined);assert.equal(resolveBuiltInAgentLaunch(command,installedOptions),undefined);});
 test('custom direct command is not silently reclassified as installed builtin',()=>{assert.equal(resolveBuiltInAgentLaunch('/fixture/custom-adapter --flag',installedOptions),undefined);});
 const before=resolveBuiltInAgentLaunch(AGENT_REGISTRY.codex,installedOptions);
 const beforeDigest=createHash('sha256').update(fs.readFileSync(bin)).digest('hex');
 fs.writeFileSync(bin,'// fixture B, same package version\n');
 const after=resolveBuiltInAgentLaunch(AGENT_REGISTRY.codex,installedOptions);
 test('same version and resolver result do not prove same executable bytes',()=>{assert.deepEqual(before,after);assert.notEqual(beforeDigest,createHash('sha256').update(fs.readFileSync(bin)).digest('hex'));});
 test('unavailable installation and npm bridge do not produce fake identity',()=>{assert.equal(resolveBuiltInAgentLaunch(AGENT_REGISTRY.codex,{...missingOptions,resolveNpmCliPath:()=>{throw Error('fixture no npm');}}),undefined);});
 console.log(JSON.stringify({schema:'dual.w03.resolver-desk-check.v1',scope:'Imports unchanged source utility with synthetic filesystem fixtures; no acpx CLI, adapter/native, npm execution, network or provider',results,spawn_count:0},null,2));
} finally {fs.rmSync(temp,{recursive:true,force:true});}
