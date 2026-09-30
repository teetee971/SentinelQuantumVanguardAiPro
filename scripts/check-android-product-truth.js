import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const SOURCE_PATHS = Object.freeze({
  manifest: 'native-android-app/app/src/main/AndroidManifest.xml',
  strings: 'native-android-app/app/src/main/res/values/strings.xml',
  listing: 'native-android-app/PLAY_STORE_LISTING.md',
  architecture: 'docs/PHONE-PROTECTION-ARCHITECTURE.md',
  privacy: 'PRIVACY_POLICY.md',
  callLogReader: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SystemCallLogReader.kt',
  smsStore: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsConversationStore.kt',
  remoteCaller: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/CallerReputationClient.kt',
  voicePolicy: 'native-android-app/app/src/main/java/com/sentinel/quantum/voice/VoiceAddonPolicy.kt',
  voiceStudio: 'native-android-app/app/src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt',
  timelineStore: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/PhonePrivateTimelineStore.kt',
  callScreening: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt',
  localLogger: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/LocalLogger.kt'
});

export function loadProductTruthSources(root = ROOT) {
  return Object.fromEntries(
    Object.entries(SOURCE_PATHS).map(([key, relativePath]) => [
      key,
      fs.readFileSync(path.join(root, relativePath), 'utf8')
    ])
  );
}

/**
 * Cross-check explicit Android claims against executable capabilities.
 * This narrow contradiction guard does not prove runtime security or compliance.
 * Role, permission and physical-device acceptance remain separate gates.
 */
export function auditProductTruth(sources) {
  const errors = [];
  const {
    manifest, strings, listing, architecture, privacy,
    callLogReader, smsStore, remoteCaller, voicePolicy, voiceStudio, timelineStore,
    callScreening, localLogger
  } = sources;

  const presented = { strings, listing, architecture };

  const durablePhysicalEvidence =
    /val provenance\s*=\s*PhoneCoreCertificationScopeProvider\.current\(appContext\)\s*\?: return@synchronized false/.test(timelineStore) &&
    timelineStore.includes('synchronized(LOCK)') &&
    timelineStore.includes('private val LOCK = Any()') &&
    !timelineStore.includes('@Synchronized') &&
    timelineStore.includes('write(next)') &&
    timelineStore.includes('putString(KEY, array.toString()).commit()') &&
    !timelineStore.includes('putString(KEY, array.toString()).apply()') &&
    timelineStore.includes('prefs.edit().remove(KEY).commit()');
  if (!durablePhysicalEvidence) {
    errors.push('timeline store: physical evidence must be durably committed before append reports success');
  }
  const serializedLocalLog =
    localLogger.includes('val FILE_LOCK = Any()') &&
    localLogger.includes('synchronized(FILE_LOCK)') &&
    localLogger.includes('fun logAsync(') &&
    localLogger.includes('ASYNC_WRITER = Executors.newSingleThreadExecutor') &&
    localLogger.includes('private val appContext = context.applicationContext');
  if (!serializedLocalLog) {
    errors.push('local logger: shared file access must remain serialized and system callbacks need an async logging path');
  }

  const screeningPostResponseOffMain =
    callScreening.includes('respondToCall(callDetails, response.build())') &&
    callScreening.includes('POST_RESPONSE_WORKER.execute') &&
    callScreening.includes('CallFilterLogStore.get(appContext).recordAsync(decision)') &&
    callScreening.includes('PhonePrivateTimelineStore(appContext).append(CallTimelineMapper.toEvent(decision))') &&
    callScreening.includes('LocalLogger(applicationContext).logAsync(') &&
    !callScreening.includes('CallFilterLogStore.get(this).recordAsync(decision)') &&
    !callScreening.includes('PhonePrivateTimelineStore(this).append(') &&
    !callScreening.includes('LocalLogger(this).log(');
  if (!screeningPostResponseOffMain) {
    errors.push('call screening: post-response Room, timeline and file logging must remain off the system callback thread');
  }

  const obsoleteCallLogDenials = [
    /\bne\s+lit\s+pas\s+le\s+journal\s+d['’]appels/iu,
    /\bne\s+lit\s+ni\s+(?:le\s+)?journal\s+d['’]appels/iu,
    /\baucun\s+accès\s+au\s+journal\s+d['’]appels/iu,
    /\bno\s+\x60READ_CALL_LOG\x60/iu
  ];
  const obsoleteSmsDenials = [
    /\bne\s+lit\s+ni\s+[^<\n]{0,80}\bbo[iî]te\s+SMS/iu,
    /\bno\s+\x60READ_SMS\x60/iu
  ];

  const callLogAvailable =
    manifest.includes('android.permission.READ_CALL_LOG') &&
    callLogReader.includes('CallLog.Calls.CONTENT_URI');
  if (callLogAvailable) {
    for (const [file, text] of Object.entries(presented)) {
      if (obsoleteCallLogDenials.some((pattern) => pattern.test(text))) {
        errors.push(file + ': absolute denial of a role-gated system call-log reader');
      }
    }
    if (!listing.includes('READ_CALL_LOG')) {
      errors.push('listing: missing disclosure of role-gated READ_CALL_LOG');
    }
    if (!architecture.includes('ROLE_DIALER') ||
        !architecture.includes('READ_CALL_LOG')) {
      errors.push('architecture: missing role-gated call-log boundary');
    }
  }

  const smsAvailable =
    manifest.includes('android.permission.READ_SMS') &&
    smsStore.includes('Telephony.Sms.');
  if (smsAvailable) {
    for (const [file, text] of Object.entries(presented)) {
      if (obsoleteSmsDenials.some((pattern) => pattern.test(text))) {
        errors.push(file + ': absolute denial of role-gated SMS access');
      }
    }
    if (!listing.includes('ROLE_SMS') || !architecture.includes('ROLE_SMS')) {
      errors.push('listing/architecture: missing role-gated SMS disclosure');
    }
  }

  const phoneStateDeclared = manifest.includes('android.permission.READ_PHONE_STATE');
  if (phoneStateDeclared) {
    const obsoletePhoneStateDenial = /\bno\s+\x60READ_PHONE_STATE\x60/iu;
    for (const [file, text] of Object.entries(presented)) {
      if (obsoletePhoneStateDenial.test(text)) {
        errors.push(file + ': absolute denial of declared role-scoped phone-state access');
      }
    }
    if (!listing.includes('READ_PHONE_STATE') || !architecture.includes('READ_PHONE_STATE')) {
      errors.push('listing/architecture: missing multi-SIM READ_PHONE_STATE disclosure');
    }
  }

  const voicePreviewDeclared = manifest.includes('android.permission.RECORD_AUDIO');
  if (voicePreviewDeclared) {
    if (!voiceStudio.includes('Manifest.permission.RECORD_AUDIO') ||
        !voiceStudio.includes('voice-studio-preview.m4a')) {
      errors.push('voice studio: RECORD_AUDIO must remain bound to the explicit local-preview path');
    }
    if (!voicePolicy.includes('paidCheckoutAllowed = false') ||
        !voicePolicy.includes('CARRIER_SIM_UNSUPPORTED')) {
      errors.push('voice add-on: paid carrier-call claim must remain fail-closed');
    }
    if (!listing.includes('RECORD_AUDIO') || !listing.includes('appel SIM')) {
      errors.push('listing: missing Voice Studio microphone / carrier-call boundary');
    }
    if (!privacy.includes('Studio voix') || !privacy.includes('cache privé')) {
      errors.push('privacy: missing local Voice Studio recording disclosure');
    }
    if (!architecture.includes('Voice Studio') ||
        !architecture.includes('Sentinel-owned VoIP media path')) {
      errors.push('architecture: missing Voice Studio carrier/VoIP trust boundary');
    }
  }

  const remoteEnrichmentImplemented =
    remoteCaller.includes('callerNumber') && remoteCaller.includes('egressGate');
  if (remoteEnrichmentImplemented) {
    const disclosures = {
      'strings: about': [
        extractAndroidString(strings, 'about_doesnt_5'),
        /Caller Reputation/iu,
        /activation explicite/iu
      ],
      'strings: compliance': [
        extractAndroidString(strings, 'compliance_gdpr'),
        /Caller Reputation/iu,
        /désactivé par défaut/iu
      ],
      listing: [listing, /Caller Reputation/iu, /activation explicite/iu],
      privacy: [privacy, /Enrichissement Caller ID distant facultatif/iu, /numéro entrant normalisé/iu],
      architecture: [architecture, /opt-in Caller Reputation/iu, /transmit/iu]
    };
    for (const [name, [text, ...patterns]] of Object.entries(disclosures)) {
      if (!text || patterns.some((pattern) => !pattern.test(text))) {
        errors.push(name + ': incomplete opt-in remote caller-number disclosure');
      }
    }
  }

  return errors;
}

function extractAndroidString(xml, name) {
  const opening = '<string name="' + name + '">';
  const start = xml.indexOf(opening);
  if (start < 0) return '';
  const end = xml.indexOf('</string>', start + opening.length);
  return end < 0 ? '' : xml.slice(start + opening.length, end);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const errors = auditProductTruth(loadProductTruthSources());
  if (errors.length) {
    console.error('Android product truth check failed:');
    for (const error of errors) console.error('- ' + error);
    process.exitCode = 1;
  } else {
    console.log('Android product truth contract: OK');
  }
}
