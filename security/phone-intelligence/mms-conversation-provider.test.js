import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsConversationStore.kt',
  'utf8'
);
const journal = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsProviderJournal.kt',
  'utf8'
);

test('conversation store merges canonical SMS and MMS provider state by thread', () => {
  assert.match(store, /Telephony\.Sms\.CONTENT_URI/);
  assert.match(store, /Telephony\.Mms\.CONTENT_URI/);
  assert.match(store, /recentSmsMessages\(bounded, nowMs\)\s*\+\s*recentMmsMessages\(bounded, nowMs\)/);
  assert.match(store, /Telephony\.Mms\.THREAD_ID/);
  assert.match(store, /readMmsAddress\(providerId, messageBox\)/);
  assert.match(store, /readMmsPreview\(providerId\)/);
});

test('MMS conversation previews stay metadata-only and bounded', () => {
  assert.match(store, /Telephony\.Mms\.Part\.CONTENT_TYPE/);
  assert.match(store, /Telephony\.Mms\.Part\.TEXT/);
  assert.match(store, /MAX_MMS_PARTS\s*=\s*32/);
  assert.match(store, /MAX_MMS_PREVIEW_CHARS\s*=\s*1000/);
  assert.doesNotMatch(store, /openInputStream\(/);
});

test('unified deletion uses Android MmsSms conversation boundary', () => {
  assert.match(store, /Telephony\.MmsSms\.CONTENT_CONVERSATIONS_URI/);
  assert.match(store, /UnifiedProviderMessage\.decodeMmsId\(id\)/);
});

test('MMS sent state is never projected as delivered and export remains explicitly SMS-only', () => {
  assert.match(store, /MESSAGE_BOX_SENT[\s\S]*MESSAGE_TYPE_SENT[\s\S]*STATUS_NONE/);
  assert.match(store, /fun exportRecentMessages[\s\S]*recentSmsMessages\(bounded\)/);
  assert.match(store, /\.put\("transport", "sms"\)/);
});

test('incoming MMS provider journal serializes transitions across store instances', () => {
  assert.match(journal, /private inline fun <T> withJournalLock/);
  assert.match(journal, /private val LOCK = Any\(\)/);
  assert.match(journal, /fun begin\([\s\S]*\): Boolean = withJournalLock/);
  assert.match(journal, /fun markReady\([\s\S]*\): Boolean = withJournalLock/);
  assert.match(journal, /fun all\(\): List<Record> = withJournalLock/);
});
