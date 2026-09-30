import test from 'node:test';
import assert from 'node:assert/strict';
import { auditProductTruth, loadProductTruthSources } from './check-android-product-truth.js';

const source = () => loadProductTruthSources();

test('current Android user-facing claims match declared role-gated capabilities', () => {
  assert.deepEqual(auditProductTruth(source()), []);
});

test('detects obsolete unconditional call-log denial', () => {
  const s = source();
  s.strings += "\n<string name=\"obsolete\">Ne lit pas le journal d'appels</string>";
  assert.ok(auditProductTruth(s).some((e) => e.includes('call-log reader')));
});

test('detects obsolete unconditional SMS denial', () => {
  const s = source();
  s.strings += "\n<string name=\"obsolete\">Ne lit ni journal d’appels, ni boîte SMS.</string>";
  assert.ok(auditProductTruth(s).some((e) => e.includes('SMS access')));
});

test('detects outdated architecture claiming unavailable READ_CALL_LOG', () => {
  const s = source();
  s.architecture += "\nNo \x60READ_CALL_LOG\x60 permission is requested.";
  assert.ok(auditProductTruth(s).some((e) => e.includes('call-log reader')));
});

test('requires Play listing to disclose role-gated system call-log access', () => {
  const s = source();
  s.listing = s.listing.replaceAll('READ_CALL_LOG', 'CALL_HISTORY_PERMISSION_NOT_DISCLOSED');
  assert.ok(auditProductTruth(s).some((e) => e.includes('missing disclosure')));
});

test('detects false denial of multi-SIM phone-state permission', () => {
  const s = source();
  s.architecture += "\nNo \x60READ_PHONE_STATE\x60 permission is requested.";
  assert.ok(auditProductTruth(s).some((e) => e.includes('phone-state access')));
});

test('requires explicit opt-in remote Caller Reputation wording', () => {
  const s = source();
  s.strings = s.strings.replace(
    'N’envoie aucun numéro au moteur Caller Reputation par défaut ; l’enrichissement distant exige une activation explicite.',
    'Aucun numéro transmis.'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('strings: about')));
});


test('rejects a premature paid carrier-call voice claim', () => {
  const s = source();
  s.voicePolicy = s.voicePolicy.replace(
    'paidCheckoutAllowed = false',
    'paidCheckoutAllowed = true'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('paid carrier-call claim')));
});

test('requires disclosure for the local Voice Studio microphone path', () => {
  const s = source();
  s.privacy = s.privacy.replaceAll('cache privé', 'stockage temporaire');
  assert.ok(auditProductTruth(s).some((e) => e.includes('local Voice Studio recording disclosure')));
});
