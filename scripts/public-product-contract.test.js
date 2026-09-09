import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';

const read = (path) => readFileSync(resolve(path), 'utf8');
const index = read('index.html');
const navigation = read('public/shared-navigation.js');
const pricing = read('public/pricing.html');
const downloadGuide = read('public/download-guide.html');
const clientSpace = read('public/espace-client.html');
const serviceWorker = read('public/sw.js');

test('the free phone directory remains a first-class public route', () => {
  assert.match(navigation, /phone-intelligence\.html[^\n]+Annuaire gratuit/);
  assert.match(index, /Rechercher un numéro — gratuit/);
  assert.match(pricing, /Téléphone gratuit/);
  assert.match(pricing, /Protection appels\/SMS Android gratuite/);
});

test('no Android package is publicly downloadable before the release gate', () => {
  const publicPages = [index, pricing, downloadGuide, clientSpace].join('\n');
  assert.doesNotMatch(publicPages, /chatgpt\.com\/api\/library/i);
  assert.doesNotMatch(publicPages, /href=["'][^"']+\.(?:apk|aab)(?:[?#][^"']*)?["']/i);
  assert.match(downloadGuide, /disabled[^>]*aria-disabled="true"|aria-disabled="true"[^>]*disabled/);
  assert.match(clientSpace, /APK indisponible/);
  assert.doesNotMatch(downloadGuide, /APK[^<\n]*réservé[^<\n]*licences actives/i);
});

test('organization activation is visibly unavailable until a server exists', () => {
  assert.match(clientSpace, /Code d’organisation/);
  assert.match(clientSpace, /id="organization-code"[^>]*disabled/);
  assert.match(clientSpace, /Activer — bientôt disponible<\/button>/);
  assert.match(clientSpace, /côté serveur/);
});

test('the Sentinel command-center visual is optimized and identified', () => {
  assert.match(index, /sentinel-command-center\.webp/);
  assert.match(index, /alt="[^"]*[Ss]entinel[^"]*"/);
  assert.match(index, /loading="lazy"/);
  assert.ok(statSync(resolve('assets/images/sentinel-command-center.webp')).size <= 150_000);
});

test('unversioned interface code is not served cache-first', () => {
  assert.match(serviceWorker, /CACHE_VERSION = 'sentinel-v2\.3\.0'/);
  assert.doesNotMatch(serviceWorker, /return \['\.css', '\.js'/);
});
