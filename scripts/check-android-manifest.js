import fs from 'node:fs';
import path from 'node:path';

const manifestPath = path.resolve('native-android-app/app/src/main/AndroidManifest.xml');
const manifest = fs.readFileSync(manifestPath, 'utf8');

const forbiddenPermissions = [
  'READ_CALL_LOG',
  'READ_PHONE_STATE',
  'RECORD_AUDIO',
  'ACCESS_FINE_LOCATION',
  'ACCESS_COARSE_LOCATION',
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

if (permissions.includes('READ_CONTACTS')) {
  const callerSettings = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CallBlockingScreen.kt'),
    'utf8'
  );
  if (!callerSettings.includes('ActivityResultContracts.RequestPermission()') ||
      !callerSettings.includes('Manifest.permission.READ_CONTACTS')) {
    errors.push('READ_CONTACTS is allowed only behind an explicit runtime permission action.');
  }
}

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

if (!manifest.includes('android:name=".SentinelApplication"')) {
  errors.push('Android manifest must register SentinelApplication so call-rule HMAC keys can warm outside onScreenCall().');
}

const screeningService = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt'),
  'utf8'
);
const fingerprinter = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/CallNumberFingerprinter.kt'),
  'utf8'
);

if (!screeningService.includes('store::cachedFingerprintsForNumber')) {
  errors.push('CallScreeningService must use cache-only exact-number fingerprints.');
}
if (screeningService.includes('store::fingerprintsForNumber')) {
  errors.push('CallScreeningService must never use the Keystore-capable fingerprint path.');
}

const cachedCandidatesMatch = /fun cachedCandidates\([\s\S]*?\n    }\n/.exec(fingerprinter)?.[0] ?? '';
if (!cachedCandidatesMatch || /getKey\(|AndroidKeyStore|KeyStore\./.test(cachedCandidatesMatch)) {
  errors.push('cachedCandidates must remain free of AndroidKeyStore access.');
}


// Restricted SMS permissions are allowed only because this app declares the full Android
// default-SMS role surface and requests runtime permissions only after ROLE_SMS is held.
const smsRestrictedPermissions = [
  'READ_SMS', 'RECEIVE_SMS', 'SEND_SMS', 'WRITE_SMS', 'RECEIVE_MMS', 'RECEIVE_WAP_PUSH'
];
const smsPermissionsDeclared = smsRestrictedPermissions.filter((name) => permissions.includes(name));
if (smsPermissionsDeclared.length > 0) {
  const requiredManifestFragments = [
    'android:name=".SmsComposeActivity"',
    'android.intent.action.SENDTO',
    'android:name=".sms.SentinelSmsDeliverReceiver"',
    'android.provider.Telephony.SMS_DELIVER',
    'android.permission.BROADCAST_SMS',
    'android:name=".sms.SentinelMmsDeliverReceiver"',
    'android.provider.Telephony.WAP_PUSH_DELIVER',
    'application/vnd.wap.mms-message',
    'android.permission.BROADCAST_WAP_PUSH',
    'android:name=".sms.SentinelRespondViaMessageService"',
    'android.intent.action.RESPOND_VIA_MESSAGE',
    'android.permission.SEND_RESPOND_VIA_MESSAGE'
  ];
  for (const fragment of requiredManifestFragments) {
    if (!manifest.includes(fragment)) errors.push(`Restricted SMS permissions require default-handler component: ${fragment}`);
  }
  const smsScreen = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/DefaultSmsScreen.kt'),
    'utf8'
  );
  if (!smsScreen.includes('RoleManager.ROLE_SMS') ||
      !smsScreen.includes('ActivityResultContracts.RequestMultiplePermissions()') ||
      !smsScreen.includes('if (roleHeld)')) {
    errors.push('SMS permissions must remain behind explicit ROLE_SMS consent and role-held gating.');
  }
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
