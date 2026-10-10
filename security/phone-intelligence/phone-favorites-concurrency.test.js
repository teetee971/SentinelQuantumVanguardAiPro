import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneFavoriteStore.kt',
  'utf8'
);

test('PhoneFavoriteStore serializes cross-instance favorite updates', () => {
  assert.match(
    store,
    /private inline fun <T> withStoreLock\(block: \(\) -> T\): T = synchronized\(STORE_LOCK\)/,
    'favorite read-modify-write transitions need one shared process lock'
  );
  assert.match(store, /private val STORE_LOCK = Any\(\)/);
  assert.match(store, /fun all\(\): Set<String> = withStoreLock \{/);
  assert.match(store, /fun setFavorite\([\s\S]*\): Boolean = withStoreLock \{/);
  assert.doesNotMatch(store, /private val LOCK = Any\(\)/);
});

