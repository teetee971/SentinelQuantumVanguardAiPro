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

// Permission intentionally allowed for the local, read-only network surveillance module,
// but only when declared with the attribute that bounds its scope. The gate is narrowed,
// not removed: an unbounded declaration still fails.
const scopedExceptions = new Map([
  [
    'ACCESS_FINE_LOCATION',
    {
      requiredAttributes: { 'android:maxSdkVersion': '32' },
      reason: 'legacy WiFi/BLE scan results on Android 12L and below only'
    }
  ],
  [
    'ACCESS_COARSE_LOCATION',
    {
      requiredAttributes: { 'android:maxSdkVersion': '32' },
      reason: 'required by Android 12 alongside the bounded fine-location scan permission'
    }
  ]
]);

// Permissions that must never be usable to derive the physical location of the user.
const neverForLocationPermissions = ['NEARBY_WIFI_DEVICES', 'BLUETOOTH_SCAN'];

const declarations = [...manifest.matchAll(/<uses-permission\b([^>]*?)\/>/gs)].map((match) => {
  const attributesSource = match[1];
  const name = /android:name="android\.permission\.([A-Z0-9_]+)"/.exec(attributesSource)?.[1];
  const attributes = Object.fromEntries(
    [...attributesSource.matchAll(/(android:[a-zA-Z]+)="([^"]*)"/g)].map((attribute) => [attribute[1], attribute[2]])
  );
  return { name, attributes };
});

const permissions = declarations.map((declaration) => declaration.name).filter(Boolean);
const errors = [];

for (const declaration of declarations) {
  if (!declaration.name || !forbiddenPermissions.includes(declaration.name)) {
    continue;
  }

  const exception = scopedExceptions.get(declaration.name);
  if (!exception) {
    errors.push(`Forbidden Android permission declared: ${declaration.name}`);
    continue;
  }

  for (const [attribute, expectedValue] of Object.entries(exception.requiredAttributes)) {
    if (declaration.attributes[attribute] !== expectedValue) {
      errors.push(
        `${declaration.name} is only allowed with ${attribute}="${expectedValue}" (${exception.reason}).`
      );
    }
  }
}

for (const permissionName of neverForLocationPermissions) {
  const declaration = declarations.find((candidate) => candidate.name === permissionName);
  if (declaration && declaration.attributes['android:usesPermissionFlags'] !== 'neverForLocation') {
    errors.push(`${permissionName} must be declared with android:usesPermissionFlags="neverForLocation".`);
  }
}

if (!manifest.includes('android:usesCleartextTraffic="false"')) {
  errors.push('Android manifest must explicitly disable cleartext traffic.');
}

if (!manifest.includes('android:allowBackup="false"')) {
  errors.push('Android manifest must explicitly disable application backup.');
}

if (errors.length > 0) {
  for (const error of errors) {
    console.error(error);
  }
  process.exit(1);
}

console.log(
  `Android manifest OK: ${permissions.length} declared permissions; no unbounded sensitive permissions.`
);
