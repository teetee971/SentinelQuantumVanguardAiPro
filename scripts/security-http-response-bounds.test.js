import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const securityRoot = 'native-android-app/app/src/main/java/com/sentinel/quantum/security';
const clients = [
  'CallerReputationClient.kt',
  'CommunityReportClient.kt',
  'PwnedPasswordClient.kt',
  'ArcepDirectoryClient.kt',
  'RtrDirectoryClient.kt'
];

test('security HTTP clients bound response bodies before parsing', () => {
  for (const file of clients) {
    const source = readFileSync(`${securityRoot}/${file}`, 'utf8');
    assert.match(source, /BoundedResponseBody\.read/,
      `${file} must use the bounded response reader`);
    assert.doesNotMatch(source, /(?:response\.body|body)\.(?:string|bytes)\(\)/,
      `${file} must not buffer an unbounded HTTP response`);
  }
});
