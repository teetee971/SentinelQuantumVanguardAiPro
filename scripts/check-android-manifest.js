import fs from 'node:fs';
import path from 'node:path';

const manifestPath = path.resolve('native-android-app/app/src/main/AndroidManifest.xml');
const manifest = fs.readFileSync(manifestPath, 'utf8');

const forbiddenPermissions = [
  'READ_CALL_LOG',
  'READ_PHONE_STATE',
  'READ_SMS',
  'RECEIVE_SMS',
  'SEND_SMS',
  'RECEIVE_MMS',
  'RECEIVE_WAP_PUSH',
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
      requiredAttributes: {},
      reason: 'WifiManager scan results require explicit fine-location runtime consent'
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
const smsRolePermissions = new Set(['READ_SMS', 'RECEIVE_SMS', 'SEND_SMS', 'RECEIVE_MMS', 'RECEIVE_WAP_PUSH']);
const phoneStatePermission = 'READ_PHONE_STATE';
const callLogPermission = 'READ_CALL_LOG';
const declaredSmsRolePermissions = permissions.filter((permission) => smsRolePermissions.has(permission));

if (declaredSmsRolePermissions.length > 0) {
  const smsPolicy = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsRoleMigrationPolicy.kt'),
    'utf8'
  );
  const smsSender = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt'),
    'utf8'
  );
  const smsReceiver = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt'),
    'utf8'
  );
  const mmsReceiver = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsDeliverReceiver.kt'),
    'utf8'
  );
  const mmsDownloadCoordinator = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsDownloadCoordinator.kt'),
    'utf8'
  );
  const mmsDownloadReceiver = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsDownloadReceiver.kt'),
    'utf8'
  );
  const fileProviderPaths = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/res/xml/file_paths.xml'),
    'utf8'
  );

  if (!smsPolicy.includes('smsPermissionsAllowed') ||
      !smsPolicy.includes('ACTIVE_DEFAULT_HANDLER')) {
    errors.push('SMS permissions require the fail-closed SmsRoleMigrationPolicy gate.');
  }
  if (!smsSender.includes('RoleManager.ROLE_SMS') ||
      !smsSender.includes('Manifest.permission.SEND_SMS')) {
    errors.push('SEND_SMS must remain gated by the Android SMS role and runtime permission.');
  }
  if (!smsReceiver.includes('RoleManager.ROLE_SMS') ||
      !manifest.includes('android.permission.BROADCAST_SMS') ||
      !manifest.includes('android.provider.Telephony.SMS_DELIVER')) {
    errors.push('RECEIVE_SMS/READ_SMS require the role-gated SMS_DELIVER receiver.');
  }
  if ((permissions.includes('RECEIVE_MMS') || permissions.includes('RECEIVE_WAP_PUSH')) &&
      (!mmsReceiver.includes('RoleManager.ROLE_SMS') ||
       !manifest.includes('android.permission.BROADCAST_WAP_PUSH') ||
       !manifest.includes('android.provider.Telephony.WAP_PUSH_DELIVER') ||
       !manifest.includes('application/vnd.wap.mms-message'))) {
    errors.push('MMS/WAP permissions require the role-gated WAP_PUSH_DELIVER receiver.');
  }
  const privateMmsDownloadReceiver = /<receiver\b(?=[^>]*android:name="\.security\.SentinelMmsDownloadReceiver")(?=[^>]*android:exported="false")[^>]*\/?>/s.test(manifest);
  if ((permissions.includes('RECEIVE_MMS') || permissions.includes('RECEIVE_WAP_PUSH')) &&
      (!mmsDownloadCoordinator.includes('downloadMultimediaMessage') ||
       !mmsDownloadCoordinator.includes('MmsNotificationParser.parse') ||
       !mmsDownloadReceiver.includes('MmsDecodePipeline.decodeAndValidate') ||
       !privateMmsDownloadReceiver ||
       !fileProviderPaths.includes('sentinel_mms_download'))) {
    errors.push('MMS receive path requires bounded carrier download, non-exported callback receiver, safe decode, and dedicated cache FileProvider path.');
  }
  for (const scheme of ['sms', 'smsto', 'mms', 'mmsto']) {
    if (!manifest.includes(`android:scheme="${scheme}"`)) {
      errors.push(`Default SMS SENDTO handler must declare the ${scheme}: scheme.`);
    }
  }
}

if (permissions.includes(callLogPermission)) {
  const callLogReader = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SystemCallLogReader.kt'),
    'utf8'
  );
  if (!callLogReader.includes('RoleManager.ROLE_DIALER') ||
      !callLogReader.includes('Manifest.permission.READ_CALL_LOG') ||
      !callLogReader.includes('CallLog.Calls.CONTENT_URI') ||
      !callLogReader.includes('if (!canRead()) return emptyList()')) {
    errors.push('READ_CALL_LOG is allowed only behind the fail-closed ROLE_DIALER call-log reader.');
  }
}

if (permissions.includes(phoneStatePermission)) {
  const smsSender = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt'),
    'utf8'
  );
  const dialer = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt'),
    'utf8'
  );
  if (!smsSender.includes('Manifest.permission.READ_PHONE_STATE') ||
      !smsSender.includes('READ_PHONE_STATE_PERMISSION_NOT_GRANTED') ||
      !smsSender.includes('activeSubscriptionInfoList')) {
    errors.push('READ_PHONE_STATE SMS usage must remain a fail-closed active-subscription validation.');
  }
  if (!dialer.includes('Manifest.permission.READ_PHONE_STATE') ||
      !dialer.includes('callCapablePhoneAccounts') ||
      !dialer.includes('CallLineSelectionPolicy.reconcile') ||
      !dialer.includes('TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE')) {
    errors.push('READ_PHONE_STATE call usage must remain fail-closed and require explicit multi-line PhoneAccount selection.');
  }
}

const fineLocationDeclaration = declarations.find((declaration) => declaration.name === 'ACCESS_FINE_LOCATION');
if (fineLocationDeclaration && !fineLocationDeclaration.attributes['android:maxSdkVersion']) {
  const wifiScanner = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/WifiScanner.kt'),
    'utf8'
  );
  if (!wifiScanner.includes('Manifest.permission.ACCESS_FINE_LOCATION') ||
      !wifiScanner.includes('isLocationEnabled()') ||
      !wifiScanner.includes('LocationManager')) {
    errors.push('Unbounded ACCESS_FINE_LOCATION is allowed only for the explicit, runtime-gated WifiManager scan path.');
  }
}

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

  if (smsRolePermissions.has(declaration.name) || declaration.name === phoneStatePermission || declaration.name === callLogPermission) {
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

const inCallService = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt'),
  'utf8'
);
const callNotification = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt'),
  'utf8'
);
if (!manifest.includes('android.permission.USE_FULL_SCREEN_INTENT') ||
    !manifest.includes('android:name=".security.SentinelCallActionReceiver"') ||
    !inCallService.includes('onBringToForeground') ||
    !callNotification.includes('NotificationCompat.CallStyle.forIncomingCall') ||
    !callNotification.includes('setFullScreenIntent')) {
  errors.push('ROLE_DIALER requires the bounded incoming-call notification/full-screen UI path.');
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

if (errors.length > 0) {
  for (const error of errors) {
    console.error(error);
  }
  process.exit(1);
}

console.log(
  `Android manifest OK: ${permissions.length} declared permissions; sensitive permissions are bounded or role-gated.`
);
