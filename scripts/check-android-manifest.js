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
const recordAudioPermission = 'RECORD_AUDIO';
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
  const smsDiagnostics = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsActivationDiagnostics.kt'),
    'utf8'
  );
  const smsReceiver = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt'),
    'utf8'
  );
  const smsStatusReceiver = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsStatusReceiver.kt'),
    'utf8'
  );
  const smsDeliveryBus = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsDeliveryStatusBus.kt'),
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
  const mmsSender = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSender.kt'),
    'utf8'
  );
  const mmsSendStatusReceiver = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSendStatusReceiver.kt'),
    'utf8'
  );
  const mmsSendStager = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsSendPduStager.kt'),
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
  const guardedSmsRoleBoundary =
    smsDiagnostics.includes('internal object SmsRoleReadPolicy') &&
    smsDiagnostics.includes('readSmsRoleStateFailClosed') &&
    smsDiagnostics.includes('RoleManager.ROLE_SMS') &&
    smsDiagnostics.includes('SmsRoleState.UNAVAILABLE') &&
    smsDiagnostics.includes('catch (_: SecurityException)') &&
    smsDiagnostics.includes('catch (_: RuntimeException)');

  if (!guardedSmsRoleBoundary ||
      !smsSender.includes('readSmsRoleStateFailClosed') ||
      !smsSender.includes('Manifest.permission.SEND_SMS')) {
    errors.push('SEND_SMS must remain gated by the fail-closed Android SMS role boundary and runtime permission.');
  }

  const privateSmsStatusReceiver =
    /<receiver\b(?=[^>]*android:name="\.security\.SentinelSmsStatusReceiver")(?=[^>]*android:exported="false")[^>]*\/?>/s.test(manifest);
  if (!privateSmsStatusReceiver ||
      !smsSender.includes('Intent(context, SentinelSmsStatusReceiver::class.java)') ||
      !smsSender.includes('PendingIntent.FLAG_IMMUTABLE') ||
      !smsSender.includes('"$sendToken/$persistedMessageId/$partIndex/${parts.size}/$callbackKind"') ||
      !smsStatusReceiver.includes('val callbackUri = intent.data ?: return') ||
      !smsStatusReceiver.includes('callbackUri.scheme != CALLBACK_URI_SCHEME') ||
      !smsStatusReceiver.includes('callbackUri.host != CALLBACK_URI_HOST') ||
      !smsStatusReceiver.includes('sendToken != uriSendToken') ||
      !smsStatusReceiver.includes('providerMessageId != uriProviderMessageId') ||
      !smsStatusReceiver.includes('partIndex != uriPartIndex') ||
      !smsStatusReceiver.includes('partCount != uriPartCount') ||
      !smsStatusReceiver.includes('val pendingResult = goAsync()') ||
      !/Executors\.newSingleThread(?:Scheduled)?Executor\s*[({]/.test(smsStatusReceiver) ||
      !smsDeliveryBus.includes('CALLBACK_REPLAY_CAPACITY = SmsCallbackProgress.MAX_PARTS * 4')) {
    errors.push('SMS status callbacks must be explicit, immutable, identity-bound, serial off-main, and replay-safe for fast multipart callbacks.');
  }
  if (!guardedSmsRoleBoundary ||
      !smsReceiver.includes('readSmsRoleStateFailClosed') ||
      !manifest.includes('android.permission.BROADCAST_SMS') ||
      !manifest.includes('android.provider.Telephony.SMS_DELIVER')) {
    errors.push('RECEIVE_SMS/READ_SMS require the fail-closed role-gated SMS_DELIVER receiver.');
  }
  if ((permissions.includes('RECEIVE_MMS') || permissions.includes('RECEIVE_WAP_PUSH')) &&
      (!guardedSmsRoleBoundary ||
       !mmsReceiver.includes('readSmsRoleStateFailClosed') ||
       !mmsReceiver.includes('val pendingResult = goAsync()') ||
       !mmsReceiver.includes('Executors.newSingleThreadExecutor') ||
       !mmsReceiver.includes('private fun processDelivery(') ||
       !mmsReceiver.includes('pendingResult.finish()') ||
       !mmsReceiver.includes('Intent(intent).putExtra("data", data.copyOf())') ||
       !mmsDownloadCoordinator.includes('readSmsRoleStateFailClosed') ||
       !manifest.includes('android.permission.BROADCAST_WAP_PUSH') ||
       !manifest.includes('android.provider.Telephony.WAP_PUSH_DELIVER') ||
       !manifest.includes('application/vnd.wap.mms-message'))) {
    errors.push('MMS/WAP permissions require the fail-closed role-gated WAP_PUSH_DELIVER path with bounded off-main processing.');
  }
  const privateMmsDownloadReceiver = /<receiver\b(?=[^>]*android:name="\.security\.SentinelMmsDownloadReceiver")(?=[^>]*android:exported="false")[^>]*\/?>/s.test(manifest);
  const capturesMmsResultBeforeAsync =
    /val deliveredResultCode = resultCode[\s\S]{0,240}val pendingResult = goAsync\(\)/.test(mmsDownloadReceiver);
  if ((permissions.includes('RECEIVE_MMS') || permissions.includes('RECEIVE_WAP_PUSH')) &&
      (!mmsDownloadCoordinator.includes('downloadMultimediaMessage') ||
       !mmsDownloadCoordinator.includes('MmsNotificationParser.parse') ||
       !mmsDownloadCoordinator.includes('.setData(Uri.parse("sentinel-mms-download://result/$token"))') ||
       !mmsDownloadCoordinator.includes('PendingIntent.FLAG_IMMUTABLE') ||
       !mmsDownloadReceiver.includes('val callbackUri = intent.data ?: return') ||
       !mmsDownloadReceiver.includes('callbackUri.scheme != "sentinel-mms-download"') ||
       !mmsDownloadReceiver.includes('callbackUri.host != "result"') ||
       !mmsDownloadReceiver.includes('it == "$token.pdu"') ||
       !mmsDownloadReceiver.includes('MmsDownloadCoordinator.EXTRA_SUBSCRIPTION_ID') ||
       !mmsDownloadReceiver.includes('readSmsRoleStateFailClosed') ||
       !mmsDownloadReceiver.includes('MmsDecodePipeline.decodeAndValidate') ||
       !mmsDownloadReceiver.includes('Executors.newSingleThreadExecutor') ||
       !mmsDownloadReceiver.includes('private fun processDownload(') ||
       !mmsDownloadReceiver.includes('pendingResult.finish()') ||
       !capturesMmsResultBeforeAsync ||
       !privateMmsDownloadReceiver ||
       !fileProviderPaths.includes('sentinel_mms_download'))) {
    errors.push('MMS receive path requires bounded carrier download, immutable identity-bound callback, captured result state, serial off-main processing, safe decode, and dedicated cache FileProvider path.');
  }
  const privateMmsSendReceiver =
    /<receiver\b(?=[^>]*android:name="\.security\.SentinelMmsSendStatusReceiver")(?=[^>]*android:exported="false")[^>]*\/?>/s.test(manifest);
  const capturesMmsSendResultBeforeAsync =
    /val androidResultCode = resultCode[\s\S]{0,240}val pendingResult = goAsync\(\)/.test(mmsSendStatusReceiver);
  if (!privateMmsSendReceiver ||
      !mmsSender.includes('readSmsRoleStateFailClosed') ||
      !mmsSender.includes('Manifest.permission.SEND_SMS') ||
      !mmsSender.includes('Manifest.permission.READ_PHONE_STATE') ||
      !mmsSender.includes('MmsSendEligibilityPolicy.evaluate') ||
      !mmsSender.includes('MmsSendPduStager.stage') ||
      !mmsSender.includes('sendMultimediaMessage') ||
      !mmsSender.includes('PendingIntent.FLAG_IMMUTABLE') ||
      !mmsSender.includes('sentinel-mms-send://result/') ||
      !mmsSendStatusReceiver.includes('callbackUri.scheme != "sentinel-mms-send"') ||
      !mmsSendStatusReceiver.includes('callbackUri.host != "result"') ||
      !mmsSendStatusReceiver.includes('it == "$token.pdu"') ||
      !mmsSendStatusReceiver.includes('Executors.newSingleThreadExecutor') ||
      !mmsSendStatusReceiver.includes('MmsSendPduStager.delete(context, fileName)') ||
      !capturesMmsSendResultBeforeAsync ||
      !mmsSendStager.includes('sentinel_mms_send') ||
      !fileProviderPaths.includes('sentinel_mms_send')) {
    errors.push('MMS send path must stay role/permission-gated, bounded, subscription-scoped, immutable-callback-bound, private, and cleaned off-main.');
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

if (permissions.includes(recordAudioPermission)) {
  const voiceStudio = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt'),
    'utf8'
  );
  const voicePolicy = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/voice/VoiceAddonPolicy.kt'),
    'utf8'
  );
  const liveVoiceEngine = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/voice/LiveVoiceTransformEngine.kt'),
    'utf8'
  );
  const voipVoicePipeline = fs.readFileSync(
    path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/voice/SentinelVoipVoicePipeline.kt'),
    'utf8'
  );
  const nonExportedVoiceStudio =
    /<activity\b(?=[^>]*android:name="\.VoiceStudioActivity")(?=[^>]*android:exported="false")[^>]*\/?>/s.test(manifest);
  const forbiddenCarrierAudioSource =
    /AudioSource\.(?:VOICE_CALL|VOICE_UPLINK|VOICE_DOWNLINK)|CAPTURE_AUDIO_OUTPUT/.test(voiceStudio);

  if (!nonExportedVoiceStudio ||
      !voiceStudio.includes('ActivityResultContracts.RequestPermission()') ||
      !voiceStudio.includes('Manifest.permission.RECORD_AUDIO') ||
      !voiceStudio.includes('voice-studio-preview.m4a') ||
      !voiceStudio.includes('carrierCallActive()') ||
      forbiddenCarrierAudioSource ||
      !voicePolicy.includes('paidCheckoutAllowed = false') ||
      !voicePolicy.includes('CARRIER_SIM_BLOCKED_BY_ANDROID') ||
      !voicePolicy.includes('liveTransformEngineIntegrated = true') ||
      !liveVoiceEngine.includes('fun processFloat32Into(') ||
      !voipVoicePipeline.includes('fun processOutgoingMicFrameInto(') ||
      !voipVoicePipeline.includes('FloatArray')) {
    errors.push(
      'RECORD_AUDIO may support explicit Voice Studio preview and Sentinel-owned VoIP processing, but carrier/SIM capture or injection must remain disabled and paid checkout must stay fail-closed until transport validation.'
    );
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

  if (smsRolePermissions.has(declaration.name) || declaration.name === phoneStatePermission || declaration.name === callLogPermission || declaration.name === recordAudioPermission) {
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
  errors.push('Android manifest must register SentinelApplication so call-rule HMAC keys load before onScreenCall().');
}

const applicationSource = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt'),
  'utf8'
);
if (!applicationSource.includes('val screeningSnapshot = store.prepareScreeningSnapshot()') ||
    !applicationSource.includes('screeningSnapshot.blockedNumberHashes.isNotEmpty()') ||
    !applicationSource.includes('store.prepareFingerprintKeys()') ||
    applicationSource.includes('sentinel-call-key-warmup') ||
    applicationSource.includes('kotlin.concurrent.thread')) {
  errors.push('Exact-number call-rule keys must load synchronously before CallScreeningService without background warm-up races.');
}

const inCallService = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt'),
  'utf8'
);
const callNotification = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt'),
  'utf8'
);
const inCallActivity = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt'),
  'utf8'
);
const callActionReceiver = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallActionReceiver.kt'),
  'utf8'
);
const privateCallActionReceiver =
  /<receiver\b(?=[^>]*android:name="\.security\.SentinelCallActionReceiver")(?=[^>]*android:exported="false")[^>]*\/?>/s.test(manifest);
const protectedInCallService =
  /<service\b(?=[^>]*android:name="\.security\.SentinelInCallService")(?=[^>]*android:exported="true")(?=[^>]*android:permission="android\.permission\.BIND_INCALL_SERVICE")[^>]*>[\s\S]*?<intent-filter>[\s\S]*?<action android:name="android\.telecom\.InCallService"\s*\/>[\s\S]*?<\/intent-filter>[\s\S]*?<\/service>/s.test(manifest);
const protectedCallScreeningService =
  /<service\b(?=[^>]*android:name="\.security\.SentinelCallScreeningService")(?=[^>]*android:exported="true")(?=[^>]*android:permission="android\.permission\.BIND_SCREENING_SERVICE")[^>]*>[\s\S]*?<intent-filter>[\s\S]*?<action android:name="android\.telecom\.CallScreeningService"\s*\/>[\s\S]*?<\/intent-filter>[\s\S]*?<\/service>/s.test(manifest);
const defaultDialerActivityContract =
  /<activity\b(?=[^>]*android:name="\.SentinelDialerActivity")(?=[^>]*android:exported="true")(?=[^>]*tools:node="replace")(?=[^>]*tools:replace="android\.exported")[^>]*>[\s\S]*?<action android:name="android\.intent\.action\.DIAL"\s*\/>[\s\S]*?<data android:scheme="tel"\s*\/>[\s\S]*?<\/activity>/s.test(manifest);
const inCallUiMetadataContract =
  /<service\b(?=[^>]*android:name="\.security\.SentinelInCallService")[^>]*>[\s\S]*?<meta-data\b(?=[^>]*android:name="android\.telecom\.IN_CALL_SERVICE_UI")(?=[^>]*android:value="true")[^>]*\/>[\s\S]*?<\/service>/s.test(manifest);
if (!manifest.includes('android.permission.USE_FULL_SCREEN_INTENT') ||
    !protectedInCallService ||
    !protectedCallScreeningService ||
    !defaultDialerActivityContract ||
    !inCallUiMetadataContract ||
    !privateCallActionReceiver ||
    !inCallService.includes('onBringToForeground') ||
    !inCallService.includes('serviceInstanceToken = java.util.UUID.randomUUID()') ||
    !inCallService.includes('fun answer(id: String)') ||
    !inCallService.includes('fun reject(id: String)') ||
    !inCallService.includes('fun startDtmf(id: String, digit: Char)') ||
    !inCallService.includes('fun stopDtmf(id: String)') ||
    !inCallService.includes('fun setMicrophoneMuted(id: String, muted: Boolean)') ||
    !inCallService.includes('fun selectAudioRoute(id: String, routeId: String)') ||
    !callNotification.includes('NotificationCompat.CallStyle.forIncomingCall') ||
    !callNotification.includes('setFullScreenIntent') ||
    !callNotification.includes('Intent(context, SentinelCallActionReceiver::class.java)') ||
    !callNotification.includes('PendingIntent.FLAG_IMMUTABLE') ||
    !callNotification.includes('Uri.encode(snapshot.id)') ||
    !callNotification.includes('EXTRA_CALL_ID') ||
    !callActionReceiver.includes('val callbackUri = intent.data ?: return') ||
    !callActionReceiver.includes('callbackUri.scheme != ACTION_URI_SCHEME') ||
    !callActionReceiver.includes('callbackUri.host != ACTION_URI_HOST') ||
    !callActionReceiver.includes('CALL_ID.matches(it)') ||
    !callActionReceiver.includes('EXTRA_CALL_ID) != callId') ||
    !callActionReceiver.includes('SentinelInCallService.answer(callId)') ||
    !callActionReceiver.includes('SentinelInCallService.reject(callId)') ||
    !inCallActivity.includes('IncomingActions(snapshot)') ||
    !inCallActivity.includes('SentinelInCallService.answer(snapshot.id)') ||
    !inCallActivity.includes('SentinelInCallService.reject(snapshot.id)') ||
    !inCallActivity.includes('SentinelInCallService.disconnect(snapshot.id)') ||
    !inCallActivity.includes('SentinelInCallService.hold(snapshot.id)') ||
    !inCallActivity.includes('SentinelInCallService.unhold(snapshot.id)') ||
    !inCallActivity.includes('SentinelInCallService.setMicrophoneMuted(snapshot.id, !it)') ||
    !inCallActivity.includes('SentinelInCallService.selectAudioRoute(snapshot.id, route.id)') ||
    !inCallActivity.includes('SentinelInCallService.startDtmf(callId, digit)') ||
    !inCallActivity.includes('SentinelInCallService.stopDtmf(callId)') ||
    /SentinelInCallService\.(?:answer|reject|disconnect|hold|unhold)\(\)/.test(inCallActivity)) {
  errors.push('Incoming call actions must be immutable, explicit, private, and bound to a lifetime-unique Telecom call id.');
}

const screeningService = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt'),
  'utf8'
);
const callBlocklistStore = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/CallBlocklistStore.kt'),
  'utf8'
);
const fingerprinter = fs.readFileSync(
  path.resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/CallNumberFingerprinter.kt'),
  'utf8'
);

if (!screeningService.includes('CallBlocklistStore.cachedSnapshotForScreening()') ||
    !callBlocklistStore.includes('internal fun cachedSnapshotForScreening')) {
  errors.push('CallScreeningService must read its rule snapshot from the process-memory screening cache only.');
}
if (!screeningService.includes('SCREENING_FINGERPRINTER::cachedCandidates')) {
  errors.push('CallScreeningService must use cache-only exact-number fingerprints.');
}
if (screeningService.includes('CallBlocklistStore(this)') ||
    screeningService.includes('::fingerprintsForNumber') ||
    screeningService.includes('.fingerprintsForNumber(')) {
  errors.push('CallScreeningService must never construct persistent rule storage or use the Keystore-capable fingerprint path before respondToCall.');
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
