// Offline control-flow replay of installed npm functions. No npm/agent execution,
// network, package installation, or session creation. Manifest I/O is replaced.
const fs = require('node:fs');
const vm = require('node:vm');
const crypto = require('node:crypto');
const sourcePath = '/usr/local/lib/node_modules/npm/node_modules/libnpmexec/lib/index.js';
const source = fs.readFileSync(sourcePath, 'utf8');
const begin = source.indexOf('const getManifest = async');
const end = source.indexOf('// see if the package.json', begin);
if (begin < 0 || end < 0) throw new Error('Installed source shape changed');
const functions = source.slice(begin, end);
const spec = {registry: true, type: 'version', name: '@agentclientprotocol/claude-agent-acp',
              raw: '@agentclientprotocol/claude-agent-acp@0.70.0', rawSpec: '0.70.0'};
async function replay(localNodes) {
  const calls = [];
  const context = {
    manifests: new Map(),
    pacote: {manifest: async (input, options) => {
      calls.push({spec: input.raw, preferOnline: options.preferOnline});
      return {name: input.name, version: '0.70.0', bin: {'claude-agent-acp': 'dist/index.js'}};
    }},
    // This replay uses exact versions and must not enter a range comparison.
    semver: {satisfies: () => { throw new Error('Unexpected range branch'); }},
  };
  vm.createContext(context);
  vm.runInContext(functions + '\nthis.check = missingFromTree;', context);
  const result = await context.check({spec, tree: {inventory: {query: () => localNodes}},
                                     flatOptions: {offline: false}});
  return {manifestCalls: calls, foundLocal: !!result.node};
}
(async () => {
  const absentLocal = await replay([]);
  const installedLocal = await replay([{pkgid: spec.raw, package: {version: '0.70.0'}}]);
  if (absentLocal.manifestCalls.length !== 1 || !absentLocal.manifestCalls[0].preferOnline)
    throw new Error('Expected missing local package to consult manifest');
  if (installedLocal.manifestCalls.length || !installedLocal.foundLocal)
    throw new Error('Exact local version should not consult manifest');
  console.log(JSON.stringify({
    sourcePath, sourceSha256: crypto.createHash('sha256').update(source).digest('hex'),
    packagePresentInNpxCache: fs.existsSync('/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@agentclientprotocol/claude-agent-acp/package.json'),
    emptyWorkspaceLocalTree: absentLocal, exactWorkspaceLocalTree: installedLocal,
    networkCalls: 0, nativeAgentCalls: 0, modelCalls: 0,
    proves: 'Installed npm missing-local-tree branch consults preferOnline manifest before later npx-cache check',
    doesNotProve: 'Which subprocess stage blocked in the historical seventh execution; historical npm bytes were not snapshotted'
  }, null, 2));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
