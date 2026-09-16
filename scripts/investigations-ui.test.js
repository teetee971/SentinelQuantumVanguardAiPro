import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { mountInvestigations } from '../public/investigations.js';
import { exampleCase, exportCase } from '../public/investigation-core.js';

class Element {
  constructor(tag = 'div') { this.tagName = tag; this.children = []; this.events = {}; this.attributes = {}; this.value = ''; this.text = ''; }
  set textContent(value) { this.text = String(value); this.children = []; }
  get textContent() { return this.text + this.children.map(node => node.textContent).join(' '); }
  append(...nodes) { this.children.push(...nodes); }
  prepend(...nodes) { this.children.unshift(...nodes); }
  replaceChildren(...nodes) { this.text = ''; this.children = nodes; }
  setAttribute(key, value) { this.attributes[key] = value; }
  addEventListener(type, handler) { this.events[type] = handler; }
  dispatch(type) { return this.events[type]?.({ preventDefault() {}, target: this }); }
  click() { this.dispatch('click'); }
  remove() {}
}
function setup() {
  const html = readFileSync(new URL('../public/investigations.html', import.meta.url), 'utf8');
  const elements = new Map([...html.matchAll(/\bid="([^"]+)"/g)].map(([, id]) => [id, new Element()]));
  const get = id => { assert.ok(elements.has(id), `missing ${id}`); return elements.get(id); };
  const doc = { body: new Element('body'), getElementById: get, createElement: tag => new Element(tag), createElementNS: (_, tag) => new Element(tag) };
  const blobs = []; let n = 0;
  const host = { confirm: () => true, crypto: { randomUUID: () => `id-${++n}` }, URL: { createObjectURL: blob => { blobs.push(blob); return 'blob:local'; }, revokeObjectURL() {} }, setTimeout: fn => fn(), addEventListener() {} };
  const app = mountInvestigations(doc, host); return { get, app, host, blobs };
}
test('real form submits render graph, relations, and timeline; deletion cascades', () => {
  const { get, app } = setup();
  assert.equal(get('add-link').disabled, true);
  for (const label of ['example.invalid', 'Organisation fictive']) {
    get('entity-type').value = 'domaine'; get('entity-label').value = label; get('entity-source').value = 'https://example.invalid/source'; get('entity-kind').value = 'observation'; get('entity-note').value = 'Exemple.';
    get('entity-form').dispatch('submit');
  }
  assert.equal(app.snapshot().entities.length, 2); assert.equal(get('add-link').disabled, false);
  get('link-from').value = 'id-1'; get('link-to').value = 'id-2'; get('link-label').value = 'Mentionne'; get('link-source').value = 'https://example.invalid/link'; get('link-kind').value = 'hypothese'; get('link-note').value = 'À vérifier.';
  get('link-form').dispatch('submit');
  assert.equal(app.snapshot().links.length, 1); assert.equal(get('timeline').children.length, 3);
  assert.ok(get('graph').children.some(node => node.tagName === 'line'));
  get('entities').children[0].children.at(-1).click();
  assert.equal(app.snapshot().entities.length, 1); assert.equal(app.snapshot().links.length, 0);
});
test('import renders untrusted strings as text, strips claims, never creates remote links', async () => {
  const { get, app } = setup(); const input = exampleCase(); input.entities[0].label = '<img src=x onerror=alert(1)>'; input.entities[0].verification = 'verified';
  const raw = JSON.stringify(input); get('import').files = [{ size: raw.length, text: async () => raw }]; await get('import').dispatch('change');
  assert.equal(app.snapshot().entities[0].verification, 'non_verifiee');
  assert.match(get('entities').textContent, /<img src=x/);
  const walk = node => [node, ...node.children.flatMap(walk)];
  assert.equal(walk(get('entities')).some(node => ['img', 'script', 'a'].includes(node.tagName)), false);
});
test('invalid and oversized imports preserve dossier; delayed imports cannot overwrite edits', async () => {
  const { get, app } = setup(); get('demo').click(); const before = app.snapshot();
  for (const file of [{ size: 300000, text: () => { throw new Error('must not read'); } }, { size: 2, text: async () => '{}' }]) {
    get('import').files = [file]; await get('import').dispatch('change'); assert.deepEqual(app.snapshot(), before);
  }
  let finish; get('import').files = [{ size: 100, text: () => new Promise(resolve => { finish = resolve; }) }];
  const pending = get('import').dispatch('change'); get('clear').click(); finish(exportCase(before)); await pending;
  assert.equal(app.snapshot().entities.length, 0); assert.match(get('status').textContent, /annulé/);
});
test('explicit cancellation preserves dossier and prevents export; accepted export round-trips', async () => {
  const { get, app, host, blobs } = setup(); get('demo').click(); host.confirm = () => false;
  get('clear').click(); get('export').click(); assert.equal(app.snapshot().entities.length, 3); assert.equal(blobs.length, 0);
  host.confirm = () => true; get('export').click(); assert.equal(blobs.length, 1);
  assert.deepEqual(JSON.parse(await blobs[0].text()), app.snapshot());
});
test('experimental surface has labels and no automatic network or persistence calls', () => {
  const html = readFileSync(new URL('../public/investigations.html', import.meta.url), 'utf8');
  for (const [, id] of html.matchAll(/<(?:input|select|textarea)\b[^>]*id="([^"]+)"/g)) assert.ok(html.includes(`for="${id}"`));
  for (const path of ['../public/investigations.js', '../public/investigation-core.js']) {
    const js = readFileSync(new URL(path, import.meta.url), 'utf8');
    assert.doesNotMatch(js, /\b(?:fetch|XMLHttpRequest|WebSocket|localStorage|sessionStorage|indexedDB)\b|\.innerHTML\b/);
  }
});
