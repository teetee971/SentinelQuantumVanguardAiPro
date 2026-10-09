import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/CallBlocklistStore.kt',
  'utf8'
);

test('CallBlocklistStore serializes SharedPreferences snapshots and mutations with one shared lock', () => {
  assert.match(
    store,
    /private inline fun <T> withStoreLock\(block: \(\) -> T\): T = synchronized\(STORE_LOCK\)/,
    'all instances need one process-wide lock around compound rule transitions'
  );
  assert.match(
    store,
    /private val STORE_LOCK = Any\(\)/,
    'the lock must be shared from the companion object rather than stored per instance'
  );
  assert.doesNotMatch(
    store,
    /INSTALL_LOCK/,
    'signed-rule installation must use the same lock as user rule mutations'
  );

  for (const method of [
    'snapshot',
    'manualBlockedPrefixes',
    'isArcepVerifiedBlockingEnabled',
    'setArcepVerifiedBlockingEnabled',
    'clearBlockedNumbers',
    'purgeExpiredBlockedNumbers',
    'addBlockedPrefix',
    'removeBlockedPrefix',
    'replaceBlockedPrefixes',
    'signedRuleMetadata',
    'installSignedSilenceRules'
  ]) {
    const methodPattern = new RegExp(`fun ${method}\\b[\\s\\S]*?withStoreLock \\{`);
    assert.match(store, methodPattern, `${method} must run under the shared store lock`);
  }

  assert.match(
    store,
    /fun removeBlockedNumber\([\s\S]*?return withStoreLock \{/,
    'number removal must lock the read-modify-write sequence after fingerprinting'
  );
  assert.match(
    store,
    /fun addBlockedNumber\([\s\S]*?\): Boolean = withStoreLock \{/,
    'number insertion must lock the read-modify-write sequence'
  );
});

