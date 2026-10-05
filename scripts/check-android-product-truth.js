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
  liveVoiceEngine: 'native-android-app/app/src/main/java/com/sentinel/quantum/voice/LiveVoiceTransformEngine.kt',
  liveKitVoiceProcessor: 'native-android-app/app/src/main/java/com/sentinel/quantum/voice/LiveKitVoiceAudioProcessor.kt',
  liveKitCallTransport: 'native-android-app/app/src/main/java/com/sentinel/quantum/voice/SentinelLiveKitCallTransport.kt',
  androidBuild: 'native-android-app/app/build.gradle',
  androidSettings: 'native-android-app/settings.gradle',
  voipVoicePipeline: 'native-android-app/app/src/main/java/com/sentinel/quantum/voice/SentinelVoipVoicePipeline.kt',
  voiceStudio: 'native-android-app/app/src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt',
  timelineStore: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/PhonePrivateTimelineStore.kt',
  callScreening: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt',
  phoneCoreActivation: 'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  localLogger: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/LocalLogger.kt',
  phoneCoreFrenchLabels: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCoreFrenchLabels.kt'
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
    callLogReader, smsStore, remoteCaller, voicePolicy, liveVoiceEngine, liveKitVoiceProcessor, liveKitCallTransport, androidBuild, androidSettings, voipVoicePipeline, voiceStudio, timelineStore,
    callScreening, localLogger, phoneCoreFrenchLabels, phoneCoreActivation
  } = sources;

  const presented = { strings, listing, architecture };
  if (!phoneCoreActivation.includes('physicalDeviceValidated = physicalEvidence.physicalDeviceValidated') ||
      /physicalDeviceValidated\s*=\s*physicalEvidence\.fullyValidated/.test(phoneCoreActivation)) {
    errors.push('local technical certificate: runtime observations must not manufacture physical-device validation');
  }

  if (!phoneCoreFrenchLabels.includes('\"MMS_ATTACHMENTS\" -> \"MMS entrants · aperçu sécurisé\"')) {
    errors.push('Phone Core MMS label must remain scoped to incoming safe preview until outgoing MMS is implemented and validated');
  }

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
        !voicePolicy.includes('liveTransformRequired = true') ||
        !voicePolicy.includes('liveTransformEngineIntegrated = true') ||
        !voicePolicy.includes('webRtcClientIntegrated = true') ||
        !voicePolicy.includes('runtimeCallFlowIntegrated = false') ||
        !voicePolicy.includes('SENTINEL_WEBRTC_CLIENT_INTEGRATED_SERVICE_PENDING') ||
        !voicePolicy.includes('CARRIER_SIM_BLOCKED_BY_ANDROID')) {
      errors.push('voice add-on: paid carrier-call claim must remain fail-closed; live VoIP transform must be mandatory and integrated');
    }
    const connectStart = liveKitCallTransport.indexOf('suspend fun connect(');
    const disconnectStart = liveKitCallTransport.indexOf('suspend fun disconnect()', connectStart);
    const connectBody = connectStart >= 0 && disconnectStart > connectStart
      ? liveKitCallTransport.slice(connectStart, disconnectStart)
      : '';
    const permissionGateIndex = connectBody.indexOf('if (!permissionGranted())');
    const securityFailureIndex = connectBody.indexOf('SecurityException("Microphone permission is required');
    const roomFactoryIndex = connectBody.indexOf('roomFactory()');
    const roomConnectIndex = connectBody.indexOf('connectedRoom.connect(');
    const microphonePublishIndex = connectBody.indexOf('setMicrophoneEnabled(true)');
    const orderedMicrophonePreflight =
      permissionGateIndex >= 0 &&
      securityFailureIndex > permissionGateIndex &&
      roomFactoryIndex > securityFailureIndex &&
      roomConnectIndex > roomFactoryIndex &&
      microphonePublishIndex > roomConnectIndex;
    const productionPermissionGate =
      liveKitCallTransport.includes('microphonePermissionCheck(context.applicationContext)') &&
      liveKitCallTransport.includes('ContextCompat.checkSelfPermission(') &&
      liveKitCallTransport.includes('Manifest.permission.RECORD_AUDIO') &&
      liveKitCallTransport.includes('== PackageManager.PERMISSION_GRANTED') &&
      liveKitCallTransport.includes('liveKitRoomFactory(context.applicationContext, voiceProcessor)');
    const pendingRoomCleanupCount =
      (liveKitCallTransport.match(/disposeRoomBestEffort\(pendingRoom\)/g) ?? []).length;
    const activeRoomCleanupCount =
      (liveKitCallTransport.match(/disposeRoomBestEffort\(room\)/g) ?? []).length;
    const connectedRoomCleanupCount =
      (liveKitCallTransport.match(/disposeRoomBestEffort\(connectedRoom\)/g) ?? []).length;
    const bestEffortRoomTeardown =
      liveKitCallTransport.includes('private fun disposeRoomBestEffort(target: Room?)') &&
      liveKitCallTransport.includes('runCatching { target.disconnect() }') &&
      liveKitCallTransport.includes('runCatching { target.release() }') &&
      pendingRoomCleanupCount === 2 &&
      activeRoomCleanupCount === 2 &&
      connectedRoomCleanupCount === 1 &&
      !liveKitCallTransport.includes('pendingRoom?.disconnect()') &&
      !liveKitCallTransport.includes('pendingRoom?.release()') &&
      !liveKitCallTransport.includes('connectedRoom?.disconnect()') &&
      !liveKitCallTransport.includes('connectedRoom?.release()');
    const invalidCallbackFailsClosed =
      /if\s*\(numBands\s*<=\s*0\s*\|\|\s*numFrames\s*<=\s*0\)\s*\{\s*silenceRemaining\(buffer\)\s*return\s*\}/s
        .test(liveKitVoiceProcessor);
    const exactCallbackShapeFailsClosed =
      liveKitVoiceProcessor.includes('val remainingBytes = buffer.remaining()') &&
      liveKitVoiceProcessor.includes('remainingBytes % Float.SIZE_BYTES != 0') &&
      liveKitVoiceProcessor.includes('remainingBytes.toLong() != exactFrameBytes') &&
      /if\s*\([\s\S]*remainingBytes\.toLong\(\)\s*!=\s*exactFrameBytes[\s\S]*\)\s*\{\s*silenceRemaining\(buffer\)\s*return\s*\}/s
        .test(liveKitVoiceProcessor);

    if (!liveVoiceEngine.includes('fun processFloat32Into(') ||
        !liveVoiceEngine.includes('same sample rate and frame length') ||
        !liveKitVoiceProcessor.includes('AudioProcessorInterface') ||
        !liveKitVoiceProcessor.includes('override fun processAudio(') ||
        !liveKitVoiceProcessor.includes('capturePostProcessor = this') ||
        !liveKitCallTransport.includes('LiveKit.create(') ||
        !liveKitCallTransport.includes('connectedRoom.connect(') ||
        !liveKitCallTransport.includes('setMicrophoneEnabled(true)') ||
        !orderedMicrophonePreflight ||
        !productionPermissionGate ||
        !bestEffortRoomTeardown ||
        !liveKitCallTransport.includes('catch (cancelled: CancellationException)') ||
        !liveKitCallTransport.includes('uri.scheme.equals("wss"') ||
        liveKitCallTransport.includes('SharedPreferences') ||
        liveKitCallTransport.includes('Log.') ||
        !androidBuild.includes("io.livekit:livekit-android:2.29.0") ||
        !androidSettings.includes("https://jitpack.io") ||
        !androidSettings.includes("includeGroup 'com.github.davidliu'") ||
        !voipVoicePipeline.includes('fun processOutgoingMicFrameInto(') ||
        !voipVoicePipeline.includes('FloatArray') ||
        !liveKitVoiceProcessor.includes('pipeline.processOutgoingMicFrameInto(') ||
        !liveKitVoiceProcessor.includes('Float.SIZE_BYTES') ||
        !liveKitVoiceProcessor.includes('ByteOrder.nativeOrder()') ||
        !liveKitVoiceProcessor.includes('require(numChannels == 1)') ||
        !invalidCallbackFailsClosed ||
        !exactCallbackShapeFailsClosed ||
        !liveKitVoiceProcessor.includes('buffer.getFloat(') ||
        !liveKitVoiceProcessor.includes('buffer.putFloat(') ||
        liveKitVoiceProcessor.includes('buffer.getShort(') ||
        liveKitVoiceProcessor.includes('buffer.putShort(') ||
        liveKitVoiceProcessor.includes('Short.SIZE_BYTES') ||
        !liveKitCallTransport.includes('voiceProcessor.liveKitOverrides()') ||
        !liveVoiceEngine.includes('sample.isFinite()')) {
      errors.push('voice add-on: missing concrete LiveKit capture path through the Sentinel VoIP transform pipeline');
    }
    if (
      voiceStudio.includes('le client d’appel sont intégrés') ||
      !voiceStudio.includes('Aucun parcours utilisateur ne lance encore une session d’appel Sentinel réelle') ||
      !voiceStudio.includes('Session d’appel/PSTN non raccordée')
    ) {
      errors.push('voice studio: runnable Sentinel call flow must not be claimed before runtime session wiring exists');
    }
    if (!listing.includes('RECORD_AUDIO') ||
        !listing.includes('appel SIM') ||
        !listing.includes('microphone transformé') ||
        !listing.includes('appel Sentinel VoIP')) {
      errors.push('listing: missing Voice Studio preview and live Sentinel VoIP microphone disclosure');
    }
    if (!privacy.includes('Studio voix') ||
        !privacy.includes('cache privé') ||
        !privacy.includes('transport WebRTC') ||
        !privacy.includes('passerelle VoIP/PSTN') ||
        !privacy.includes('ne les persiste ni ne les journalise')) {
      errors.push('privacy: missing local Voice Studio recording disclosure or future live-call media disclosure');
    }
    if (!architecture.includes('Voice Studio') ||
        !architecture.includes('Sentinel-owned VoIP media path') ||
        !architecture.includes('FloatS16 amplitude domain') ||
        architecture.includes('normalized Float32 samples')) {
      errors.push('architecture: missing or inaccurate Voice Studio carrier/VoIP trust boundary');
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
