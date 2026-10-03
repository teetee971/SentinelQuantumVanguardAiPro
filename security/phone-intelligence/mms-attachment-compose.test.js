import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const compose = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);
const loader = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsAttachmentLoader.kt',
  'utf8'
);

test('MMS compose exposes a real user-selected bounded attachment path', () => {
  assert.match(compose, /ActivityResultContracts\.GetMultipleContents\(\)/);
  assert.match(compose, /MmsAttachmentLoader\(applicationContext\)/);
  assert.match(compose, /withContext\(Dispatchers\.IO\)[\s\S]*attachmentLoader\.load\(uris\)/);
  assert.match(compose, /attachments\s*=\s*attachments\.map\s*\{/);
  assert.match(compose, /SentinelMmsSender\.Attachment\(it\.mimeType, it\.payload\)/);
  assert.match(compose, /bodyPresent\s*=\s*body\.isNotBlank\(\)\s*\|\|\s*\(mmsComposeMode\s*&&\s*selectedMmsAttachments\.isNotEmpty\(\)\)/);
});

test('MMS attachment loader rejects broad or unbounded inputs', () => {
  assert.match(loader, /uri\.scheme\s*!=\s*"content"/);
  assert.match(loader, /MAX_ATTACHMENT_BYTES/);
  assert.match(loader, /MAX_TOTAL_ATTACHMENT_BYTES/);
  assert.match(loader, /ATTACHMENT_FORMAT_MISMATCH/);
  assert.match(loader, /readBounded\(/);
});
