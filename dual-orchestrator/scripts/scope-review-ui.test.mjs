import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const base = new URL('../dual-server/src/main/resources/static/', import.meta.url);
function functionSource(source, name) {
  const start = source.indexOf(`function ${name}(`);
  assert.ok(start >= 0, name);
  const next = source.slice(start + 1).search(/\n(?:async )?function /);
  assert.ok(next >= 0, `${name} boundary`);
  return source.slice(start, start + 1 + next);
}

const consoleSource = fs.readFileSync(new URL('console.js', base), 'utf8');
const consoleContext = {
  state: { selectedId: 'ws', review: { packet: { allowedActions: [] } } },
  asList: value => Array.isArray(value) ? value : [],
  approveAndRun() {}, reviseAndRun() {}, requestScopeRevision() {},
};
vm.runInNewContext(functionSource(consoleSource, 'allowedActions'), consoleContext);
for (const actions of [[], ['revise'], ['revise', 'request_scope_revision'], ['approve', 'revise']]) {
  consoleContext.state.review.packet.allowedActions = actions;
  const offered = consoleContext.allowedActions('WAITING_FOR_HUMAN');
  assert.equal(offered.some(item => item.onClick === consoleContext.requestScopeRevision), actions.includes('request_scope_revision'));
  assert.equal(offered.some(item => item.onClick === consoleContext.approveAndRun), actions.includes('approve'));
}

const mgtSource = fs.readFileSync(new URL('mgt/app.js', base), 'utf8');
const element = { classList: { toggle() {} }, parentElement: { classList: { toggle() {} } } };
let visibility;
const mgtContext = {
  state: { selectedId: 'ws', currentReview: { packet: { gateNodeInstanceId: 'gate-current', allowedActions: ['revise', 'request_scope_revision'] } } },
  els: new Proxy({}, { get: () => element }),
  hasRuntimeRecoveryAttention: () => false, canCloseMistakenIteration: () => false,
  setActionVisibility: value => { visibility = value; }, applyDecisionLabels() {}, applyPrimaryActionLabel() {},
};
vm.runInNewContext(functionSource(mgtSource, 'updateActions'), mgtContext);
for (const [status, id, expected] of [
  ['WAITING_FOR_HUMAN', 'gate-current', true],
  ['WAITING_FOR_HUMAN', 'gate-other', false],
  ['COMPLETED', 'gate-current', false],
]) {
  mgtContext.updateActions({ workflow: { status, activeNodes: [{ nodeInstanceId: id, nodeDefinitionId: 'gate.human_implementation_repair' }] } });
  assert.equal(visibility.requestScopeRevision, expected);
  assert.equal(visibility.approve, false);
}
console.log('SCOPE_REVIEW_UI_PASS');
