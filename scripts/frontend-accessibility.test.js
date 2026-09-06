import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const navigationSource = readFileSync(resolve('public/shared-navigation.js'), 'utf8');

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
