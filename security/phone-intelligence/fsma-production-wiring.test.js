import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const html = fs.readFileSync('public/phone-intelligence.html', 'utf8');
const runtime = fs.readFileSync('public/fsma-regulatory.js', 'utf8');
const updater = fs.readFileSync('scripts/update-fsma-warnings.js', 'utf8');
const workflow = fs.readFileSync('.github/workflows/fsma-warning-refresh.yml', 'utf8');
const serviceWorker = fs.readFileSync('public/sw.js', 'utf8');
const inventory = JSON.parse(fs.readFileSync('config/module-continuity-inventory.json', 'utf8'));

test('FSMA advisory is wired into the public phone-intelligence surface without false availability', () => {
  assert.match(html, /id="fsma-source-badge"[^>]*>Non chargé</);
  assert.match(html, /src="fsma-regulatory\.js"/);
  assert.match(html, /aucun blocage automatique/i);
  assert.match(runtime, /FSMA_SNAPSHOT_URL\s*=\s*'\/public\/data\/fsma-warnings\.json'/);
  assert.match(runtime, /advisoryOnly\s*!==\s*true/);
  assert.match(runtime, /warning\.autoBlock\s*!==\s*false/);
});

test('FSMA refresh is bounded and opens a reviewable data PR instead of mutating runtime state', () => {
  assert.match(updater, /MAX_INPUT_BYTES\s*=\s*4 \* 1024 \* 1024/);
  assert.match(updater, /redirect:\s*'error'/);
  assert.match(updater, /public\/data\/fsma-warnings\.json/);
  assert.match(workflow, /automation\/fsma-warning-refresh/);
  assert.match(workflow, /gh pr create/);
  assert.doesNotMatch(workflow, /gh pr merge/);
});

test('PWA caches the FSMA runtime module but does not require an absent regulatory snapshot at install time', () => {
  assert.match(serviceWorker, /'\/public\/fsma-regulatory\.js'/);
  assert.doesNotMatch(serviceWorker, /STATIC_ASSETS[\s\S]*?'\/public\/data\/fsma-warnings\.json'/);
});

test('FSMA adapter is no longer classified as a dormant orphan after production import', () => {
  assert.equal(
    Object.hasOwn(inventory.entries, 'security/phone-intelligence/fsma-warning-adapter.js'),
    false,
    'imported FSMA adapter must not retain a stale orphan classification'
  );
});
