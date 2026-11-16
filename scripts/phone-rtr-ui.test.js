import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { setImmediate } from 'node:timers/promises';

// Minimal DOM harness: exercise the actual form handlers without browser/network dependencies.
class Element {
  constructor(tag = 'div') { this.tagName = tag; this.children = []; this.events = {}; this.attributes = {}; this.value = ''; this.text = ''; }
  set textContent(value) { this.text = String(value); this.children = []; }
  get textContent() { return this.text + this.children.map((node) => node.textContent).join(' '); }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.text = ''; this.children = nodes; }
  setAttribute(key, value) { this.attributes[key] = value; }
  addEventListener(type, handler) { this.events[type] = handler; }
  dispatch(type) { return this.events[type]?.({ preventDefault() {}, currentTarget: this }); }
}

test('actual phone form renders RTR evidence, preserves other-country behavior and discards stale lookups', async () => {
  const keys = ['document', 'localStorage', 'navigator', 'fetch'];
  const previous = Object.fromEntries(keys.map((key) => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  const html = readFileSync(new URL('../public/phone-intelligence.html', import.meta.url), 'utf8');
  const elements = new Map([...html.matchAll(/\bid="([^"]+)"/g)].map(([, id]) => [id, new Element()]));
  const get = (id) => { assert.ok(elements.has(id), `Missing form element ${id}`); return elements.get(id); };
  const document = {
    documentElement: {}, events: {}, title: '',
    getElementById: get, createElement: (tag) => new Element(tag), querySelectorAll: () => [],
    addEventListener(type, handler) { this.events[type] = handler; }
  };
  let resolveRtr;
  const requests = [];
  const payload = {
    schemaVersion: 1, country: 'AT', generatedAt: '2026-09-15T19:28:08Z', sourcePublishedAt: null, recordCount: 1,
    holders: [['<img src=x onerror=alert(1)>', '1234']],
    groups: { '1/7': { kind: 'geographic', category: 'Geografische Rufnummern', area: 'Wien', ranges: [['2000000', '2000099', 0]] } }
  };
  try {
    Object.defineProperty(globalThis, 'document', { value: document, configurable: true });
    Object.defineProperty(globalThis, 'localStorage', { value: { getItem: () => null, setItem() {} }, configurable: true });
    Object.defineProperty(globalThis, 'navigator', { value: { language: 'fr-FR' }, configurable: true });
    Object.defineProperty(globalThis, 'fetch', { value: (url) => {
      requests.push(url);
      if (url === '/public/data/rtr-numbering.json') return new Promise((resolve) => { resolveRtr = resolve; });
      return Promise.resolve(new Response('', { status: 503 }));
    }, configurable: true });
    get('country').value = 'FR';
    await import('../public/phone-intelligence.js?dom-test');
    document.events.DOMContentLoaded();
    assert.equal(requests.filter((url) => url.includes('rtr')).length, 0);

    get('phone-number').value = '+4312000000';
    get('number-search').dispatch('submit');
    assert.equal(get('country').value, 'AT');
    const abandoned = get('number-result').children.at(-1);
    assert.match(abandoned.textContent, /Consultation/);
    // Invalid search invalidates the earlier callback, including its detached card.
    get('phone-number').value = 'invalid';
    get('number-search').dispatch('submit');
    resolveRtr(new Response(JSON.stringify(payload)));
    await setImmediate();
    assert.match(get('number-result').textContent, /invalide/);
    assert.match(abandoned.textContent, /Consultation/);

    get('phone-number').value = '+4312000000';
    get('number-search').dispatch('submit');
    await setImmediate();
    const result = get('number-result');
    assert.match(result.textContent, /Titulaire de l’attribution/);
    assert.match(result.textContent, /Zone de numérotation : Wien/);
    assert.match(result.textContent, /publication de la source : non publié/);
    assert.match(result.textContent, /<img src=x/); // Rendered literally as text, not interpreted HTML.
    const walk = (node) => [node, ...node.children.flatMap(walk)];
    assert.equal(walk(result).some((node) => node.tagName === 'img'), false);
    assert.equal(walk(result).find((node) => node.tagName === 'a').href, 'https://www.rtr.at/TKP/service/rufnummernsuche/Rufnummernsuche.de.html');

    get('language').value = 'en';
    get('language').dispatch('change');
    await setImmediate();
    assert.match(result.textContent, /Allocation holder/);
    get('phone-number').value = '+819012345678';
    get('number-search').dispatch('submit');
    assert.match(result.textContent, /UIT \/ ITU/);
    assert.doesNotMatch(result.textContent, /RTR allocations/);
    assert.equal(requests.filter((url) => url.includes('rtr')).length, 1);
    assert.ok(requests.every((url) => url.startsWith('/public/data/') && !url.includes('+43')));
  } finally {
    for (const key of keys) {
      if (previous[key]) Object.defineProperty(globalThis, key, previous[key]);
      else delete globalThis[key];
    }
  }
});
