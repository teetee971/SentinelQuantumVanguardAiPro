import fs from 'node:fs';
import path from 'node:path';

const manifestPath = path.resolve('native-android-app/app/src/main/AndroidManifest.xml');
const manifest = fs.readFileSync(manifestPath, 'utf8');

const forbiddenPermissions = [
  'READ_CALL_LOG',
  'READ_PHONE_STATE',
  'READ_SMS',
  'RECEIVE_SMS',
  'RECORD_AUDIO',
  'ACCESS_FINE_LOCATION',
  'ACCESS_COARSE_LOCATION',
  'READ_CONTACTS',
  'WRITE_CONTACTS',
  'READ_EXTERNAL_STORAGE',
  'WRITE_EXTERNAL_STORAGE'
];

const permissions = [...manifest.matchAll(/android:name="android\.permission\.([A-Z0-9_]+)"/g)].map((match) => match[1]);
const forbidden = permissions.filter((permission) => forbiddenPermissions.includes(permission));

if (forbidden.length > 0) {
  console.error(`Forbidden Android permissions declared: ${forbidden.join(', ')}`);
  process.exit(1);
}

if (!manifest.includes('android:usesCleartextTraffic="false"')) {
  console.error('Android manifest must explicitly disable cleartext traffic.');
  process.exit(1);
}

if (!manifest.includes('android:allowBackup="false"')) {
  console.error('Android manifest must explicitly disable application backup.');
  process.exit(1);
}

console.log(`Android manifest OK: ${permissions.length} declared permissions; no forbidden sensitive permissions.`);
