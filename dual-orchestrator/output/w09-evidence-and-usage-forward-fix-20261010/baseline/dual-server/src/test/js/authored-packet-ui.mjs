import assert from 'node:assert/strict';
import { readFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE
  ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const server = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const staticRoot = path.join(server, 'src/main/resources/static');
const packets = await Promise.all([0, 1].map(async index => JSON.parse(await readFile(
  path.join(server, '../dual-runtime/target/authored-packets', `${index}.json`), 'utf8'))));
const output = path.join(server, '../output/playwright/e3-authored');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  for (const surface of ['console', 'mgt']) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    let servedPacket;
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      assert.equal(url.origin, 'http://dual.test', 'test must never access an external service');
      if (url.pathname.includes('/review/')) return route.fulfill({ json: {
        packet: servedPacket, gateNodeDefinitionId: servedPacket.gateNodeDefinitionId,
        packetMarkdown: 'Read-only packet fixture',
      } });
      if (url.pathname.startsWith('/api/')) return route.fulfill({ json: [] });
      const relative = url.pathname === '/' ? 'index.html' : url.pathname.slice(1);
      const file = path.resolve(staticRoot, relative);
      assert.ok(file.startsWith(staticRoot + path.sep));
      await route.fulfill({ body: await readFile(file), contentType: file.endsWith('.js')
        ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html' });
    });
    await page.goto(surface === 'console' ? 'http://dual.test/' : 'http://dual.test/mgt/index.html');
    await page.waitForLoadState('networkidle');
    for (const [index, packet] of packets.entries()) {
      servedPacket = packet;
      await page.evaluate(async ({ surface, packet }) => {
        if (surface === 'console') {
          state.review = { packet };
          state.timeline = null;
          state.selectedRound = '';
          renderEvidence('WAITING_FOR_HUMAN');
        } else {
          state.selectedId = packet.workstreamId;
          state.status = { workflow: { status: 'WAITING_FOR_HUMAN' } };
          await loadReviewPacket();
        }
      }, { surface, packet });
      const region = page.locator(surface === 'console' ? '#evidence' : '#gateEvidence');
      const text = await region.textContent();
      const authored = packet.authoredStage;
      for (const value of [authored.candidate.sidecar.ref, authored.candidate.sidecar.sha256,
        authored.candidate.runId, ...authored.plan.edit_files,
        ...authored.activeObligations.flatMap(item => [item.id, item.text, item.source.ref]),
        ...authored.revisionResponses.flatMap(item => [item.obligation_id, item.disposition, item.evidence]),
        ...authored.newMustFix]) assert.ok(text.includes(value), `${surface} omitted ${value}`);
      assert.equal(await region.locator('img, script').count(), 0, 'evidence must be rendered as text');
      assert.ok(await region.evaluate(element => element.scrollWidth <= element.clientWidth + 1),
        `${surface} must wrap complete authority identifiers within the evidence panel`);
      if (index === 1) assert.ok(!text.includes(packets[0].authoredStage.candidate.runId), 'must not retain the earlier candidate card');
      await region.screenshot({ path: path.join(output, `${surface}-${index}.png`) });
    }
    if (surface === 'mgt') {
      servedPacket = packets[0];
      await page.evaluate(async packet => {
        state.status = { workflow: { status: 'WAITING_FOR_HUMAN', activeNodes: [
          { nodeInstanceId: 'a-later-current-gate' },
        ] } };
        await showGateReviewPacket({ nodeInstanceId: packet.gateNodeInstanceId,
          nodeDefinitionId: packet.gateNodeDefinitionId });
      }, servedPacket);
      assert.equal(await page.locator('#reviewPanelBadge').textContent(), 'Historical packet');
      assert.equal(await page.locator('#decisionPanel').isVisible(), false);
      assert.ok((await page.locator('#gateEvidence').textContent()).includes(
        packets[0].authoredStage.candidate.runId));
    }
    await page.close();
  }
  console.log('E3_AUTHORED_PACKET_UI_PASS: two real packets, both production renderers, escaped evidence');
} finally { await browser.close(); }
