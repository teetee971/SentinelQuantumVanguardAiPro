import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const navigationSource = readFileSync(resolve('public/shared-navigation.js'), 'utf8');
const threatIntelSource = readFileSync(resolve('public/threat-intelligence.html'), 'utf8');
const comparisonSource = readFileSync(resolve('public/comparatif.html'), 'utf8');

test('mobile navigation exposes its controlled region and expanded state', () => {
  assert.match(navigationSource, /toggle\.setAttribute\('aria-controls',\s*'primary-navigation'\)/);
  assert.match(navigationSource, /list\.id\s*=\s*'primary-navigation'/);
  assert.match(navigationSource, /toggle\.setAttribute\('aria-expanded',\s*'false'\)/);
});

test('mobile navigation closes with Escape and restores focus', () => {
  assert.match(navigationSource, /event\.key\s*===\s*'Escape'/);
  assert.match(navigationSource, /navLinks\.classList\.remove\('active'\)/);
  assert.match(navigationSource, /mobileToggle\.focus\(\)/);
});

test('the current page link exposes aria-current', () => {
  assert.match(navigationSource, /link\.setAttribute\('aria-current',\s*'page'\)/);
  assert.match(navigationSource, /link\.removeAttribute\('aria-current'\)/);
});

test('shared navigation adds a keyboard-visible skip link when main content exists', () => {
  assert.match(navigationSource, /document\.querySelector\('main'\)/);
  assert.match(navigationSource, /createElement\('a',\s*'skip-link'/);
  assert.match(navigationSource, /skipLink\.href\s*=\s*`#\$\{main\.id\}`/);
  assert.match(navigationSource, /addEventListener\('focus'/);
});

test('comparison table has an accessible caption and scoped column headers', () => {
  assert.match(comparisonSource, /<caption>[^<]+<\/caption>/);
  const columnHeaders = comparisonSource.match(/<th scope="col"/g) || [];
  assert.equal(columnHeaders.length, 5);
});

test('threat refresh state is announced as a status', () => {
  assert.match(threatIntelSource, /id="status"[^>]*role="status"/);
});
