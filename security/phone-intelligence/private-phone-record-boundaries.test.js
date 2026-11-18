import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

function androidSource(path) {
  return readFileSync(
    new URL(`../../native-android-app/app/src/main/java/com/sentinel/quantum/${path}`, import.meta.url),
    'utf8',
  );
}

test('a queued directory request uses the generation reserved before the preference is read', () => {
  const source = androidSource('security/CallDirectoryCache.kt');
  assert.match(source, /fun onDirectoryObservationChanged\(context: Context\) \{\s*val ticket = publication\.openRefresh\(\)\s*if \(!observesDirectory\(context\)\)/);
  assert.match(source, /executor\.execute \{ refresh\(context\.applicationContext, ticket\) \}/);
  assert.match(source, /private fun refresh\(app: Context, ticket: Int\): Boolean \{\s*if \(!publication\.isLatest\(ticket\)\) return true/);
  assert.match(source, /override fun onChange\(selfChange: Boolean\) \{\s*onDirectoryObservationChanged\(app\)/);
});

test('a missing contact number column makes the directory unavailable', () => {
  const source = androidSource('security/CallDirectoryCache.kt');
  assert.match(source, /getColumnIndex\(ContactsContract\.CommonDataKinds\.Phone\.NUMBER\)\s*if \(index < 0\) return Load\.Unavailable/);
});

test('call log access is checked before the query and at the row delivery boundary', () => {
  const source = androidSource('security/SystemCallLogReader.kt');
  assert.match(source, /fun recent\(limit: Int = 100\): List<Entry> \{\s*if \(!canRead\(\)\) return emptyList\(\)/);
  assert.match(source, /\)\?\.use \{ cursor ->\s*if \(!canRead\(\)\) return emptyList\(\)/);
  assert.match(source, /if \(canRead\(\)\) result else emptyList\(\)/);
});

test('recents discard a late read on pause or loss of live dialer access', () => {
  const source = androidSource('SentinelDialerActivity.kt');
  assert.match(source, /fun clearRecents\(\) \{\s*recentGeneration\+\+\s*recentItems = emptyList\(\)/);
  assert.match(source, /Lifecycle\.Event\.ON_PAUSE -> \{\s*resumed = false\s*clearRecents\(\)/);
  assert.match(source, /if \(generation != recentGeneration\) return@launch\s*if \(!callLog\.canRead\(\)\) \{/);
  assert.match(source, /if \(phoneTab == 1 && recentAccessGranted\) \{/);
  assert.match(source, /recentItems\.take\(recentVisibleLimit\)\.forEach[\s\S]*?if \(!callLog\.canRead\(\)\) \{\s*clearRecents\(\)/);
});

test('contacts discard a late read and identity on loss of access', () => {
  const source = androidSource('SentinelDialerActivity.kt');
  assert.match(source, /fun clearContacts\(\) \{\s*contactGeneration\+\+\s*contactItems = emptyList\(\)[\s\S]*?contactStatus = null/);
  assert.match(source, /if \(generation != contactGeneration\) return@launch\s*if \(!canReadContactsNow\(\)\) \{/);
  assert.match(source, /contactStatus = localIdentity\s*\?\.takeIf \{ identityGeneration == contactGeneration && canReadContactsNow\(\) \}/);
});

test('the contacts provider boundary discards a read that outlives its permission', () => {
  const source = androidSource('security/LocalContactLookup.kt');
  assert.match(source, /if \(!canRead\(\)\) return ContactListResult\(ContactAccessState\.PERMISSION_REQUIRED\)\s*val selectedIds/);
  assert.match(source, /if \(!canRead\(\)\) return ContactListResult\(ContactAccessState\.PERMISSION_REQUIRED\)\s*val callableContacts/);
  assert.match(source, /if \(!canRead\(\)\) return null\s*val organisation/);
  assert.match(source, /return if \(canRead\(\)\) Identity\(contact\.second, organisation\) else null/);
});

test('the commercial dialer gates optional reputation and contact sharing while preserving official attribution', () => {
  const source = androidSource('SentinelDialerActivity.kt');
  assert.match(source, /val reputationEnabled = !BuildConfig\.COMMERCIAL_PHONE_CORE_V1 &&/);
  assert.match(source, /val remoteReputationAllowed = !BuildConfig\.COMMERCIAL_PHONE_CORE_V1 &&/);
  assert.match(source, /if \(!BuildConfig\.COMMERCIAL_PHONE_CORE_V1\) \{\s*WhatsAppClickToChatPolicy\.urlFor/);
  assert.match(source, /rtr\.lookup\(lookupNumber\)/);
  assert.match(source, /arcep\.lookup\(lookupNumber\)/);
});

test('contact editing uses the platform lookup key and an explicit system editor without URI grants', () => {
  const source = androidSource('SentinelDialerActivity.kt');
  const lookup = androidSource('security/LocalContactLookup.kt');
  assert.match(lookup, /ContactsContract\.Contacts\.LOOKUP_KEY/);
  assert.match(lookup, /lookupKey = lookupKeys\[entry\.contactId\]/);
  const editor = source.slice(source.indexOf('private fun editSystemContact('), source.indexOf('override fun onCreate('));
  assert.match(editor, /ContactsContract\.Contacts\.getLookupUri\(contact\.contactId, lookupKey\)/);
  assert.match(editor, /Intent\(Intent\.ACTION_EDIT\)/);
  assert.match(editor, /PackageManager\.MATCH_DEFAULT_ONLY or PackageManager\.MATCH_SYSTEM_ONLY/);
  assert.match(editor, /startActivity\(intent\.setComponent\(ComponentName\(editor\.packageName, editor\.name\)\)\)/);
  assert.doesNotMatch(editor, /FLAG_GRANT_\w+_URI_PERMISSION|WRITE_CONTACTS/);
  assert.match(source, /contact\.lookupKey\?\.let \{ lookupKey ->[\s\S]*?if \(!canReadContactsNow\(\)\)[\s\S]*?editSystemContact\(contact, lookupKey\)/);
});
