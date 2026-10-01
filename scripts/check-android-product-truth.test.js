import test from 'node:test';
import assert from 'node:assert/strict';
import { auditProductTruth, loadProductTruthSources } from './check-android-product-truth.js';

const source = () => loadProductTruthSources();

test('current Android user-facing claims match declared role-gated capabilities', () => {
  assert.deepEqual(auditProductTruth(source()), []);
});

test('detects obsolete unconditional call-log denial', () => {
  const s = source();
  s.strings += "\n<string name=\"obsolete\">Ne lit pas le journal d'appels</string>";
  assert.ok(auditProductTruth(s).some((e) => e.includes('call-log reader')));
});

test('detects obsolete unconditional SMS denial', () => {
  const s = source();
  s.strings += "\n<string name=\"obsolete\">Ne lit ni journal d’appels, ni boîte SMS.</string>";
  assert.ok(auditProductTruth(s).some((e) => e.includes('SMS access')));
});

test('detects outdated architecture claiming unavailable READ_CALL_LOG', () => {
  const s = source();
  s.architecture += "\nNo \x60READ_CALL_LOG\x60 permission is requested.";
  assert.ok(auditProductTruth(s).some((e) => e.includes('call-log reader')));
});

test('requires Play listing to disclose role-gated system call-log access', () => {
  const s = source();
  s.listing = s.listing.replaceAll('READ_CALL_LOG', 'CALL_HISTORY_PERMISSION_NOT_DISCLOSED');
  assert.ok(auditProductTruth(s).some((e) => e.includes('missing disclosure')));
});

test('detects false denial of multi-SIM phone-state permission', () => {
  const s = source();
  s.architecture += "\nNo \x60READ_PHONE_STATE\x60 permission is requested.";
  assert.ok(auditProductTruth(s).some((e) => e.includes('phone-state access')));
});

test('requires explicit opt-in remote Caller Reputation wording', () => {
  const s = source();
  s.strings = s.strings.replace(
    'N’envoie aucun numéro au moteur Caller Reputation par défaut ; l’enrichissement distant exige une activation explicite.',
    'Aucun numéro transmis.'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('strings: about')));
});


test('rejects a premature paid carrier-call voice claim', () => {
  const s = source();
  s.voicePolicy = s.voicePolicy.replace(
    'paidCheckoutAllowed = false',
    'paidCheckoutAllowed = true'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('paid carrier-call claim')));
});

test('requires disclosure for the local Voice Studio microphone path', () => {
  const s = source();
  s.privacy = s.privacy.replaceAll('cache privé', 'stockage temporaire');
  assert.ok(auditProductTruth(s).some((e) => e.includes('local Voice Studio recording disclosure')));
});

test('requires Play disclosure for live Sentinel VoIP microphone transmission', () => {
  const s = source();
  s.listing = s.listing
    .replaceAll('microphone transformé', 'audio traité')
    .replaceAll('appel Sentinel VoIP', 'appel compatible');
  assert.ok(auditProductTruth(s).some((e) => e.includes('live Sentinel VoIP microphone disclosure')));
});

test('rejects a LiveKit transport that removes the microphone permission gate', () => {
  const s = source();
  s.liveKitCallTransport = s.liveKitCallTransport.replace(
    'if (!permissionGranted()) {',
    'if (false) {'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects room creation before the microphone permission gate', () => {
  const s = source();
  s.liveKitCallTransport = s.liveKitCallTransport.replace(
    'if (!permissionGranted()) {',
    'val prematureRoom = roomFactory()\n        if (!permissionGranted()) {'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects LiveKit teardown that can skip release after disconnect failure', () => {
  const s = source();
  s.liveKitCallTransport = s.liveKitCallTransport.replace(
    'runCatching { target.disconnect() }',
    'target.disconnect()'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects LiveKit failure cleanup that stops disposing the pending room', () => {
  const s = source();
  s.liveKitCallTransport = s.liveKitCallTransport.replace(
    'disposeRoomBestEffort(pendingRoom)',
    '// pending room cleanup removed'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects LiveKit failure cleanup that stops disposing the active room', () => {
  const s = source();
  s.liveKitCallTransport = s.liveKitCallTransport.replace(
    'disposeRoomBestEffort(room)',
    '// active room cleanup removed'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects explicit disconnect that stops disposing the connected room', () => {
  const s = source();
  s.liveKitCallTransport = s.liveKitCallTransport.replace(
    'disposeRoomBestEffort(connectedRoom)',
    '// connected room cleanup removed'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('requires privacy disclosure for WebRTC media transport', () => {
  const s = source();
  s.privacy = s.privacy.replaceAll('transport WebRTC', 'transport média');
  assert.ok(auditProductTruth(s).some((e) => e.includes('future live-call media disclosure')));
});

test('requires privacy disclosure for the VoIP/PSTN gateway', () => {
  const s = source();
  s.privacy = s.privacy.replaceAll('passerelle VoIP/PSTN', 'passerelle réseau');
  assert.ok(auditProductTruth(s).some((e) => e.includes('future live-call media disclosure')));
});

test('requires privacy disclosure that LiveKit credentials are not persisted or logged', () => {
  const s = source();
  s.privacy = s.privacy.replaceAll(
    'ne les persiste ni ne les journalise',
    'les traite temporairement'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('future live-call media disclosure')));
});

test('rejects a LiveKit capture path that bypasses the Sentinel VoIP pipeline', () => {
  const s = source();
  s.liveKitVoiceProcessor = s.liveKitVoiceProcessor.replace(
    'pipeline.processOutgoingMicFrameInto(',
    'bypassTransform('
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects architecture docs that claim WebRTC capture floats are normalized', () => {
  const s = source();
  s.architecture = s.architecture
    .replaceAll('FloatS16 amplitude domain', 'normalized Float32 samples');
  assert.ok(auditProductTruth(s).some((e) => e.includes('inaccurate Voice Studio')));
});

test('rejects a multi-channel capture contract that could leave voice untransformed', () => {
  const s = source();
  s.liveKitVoiceProcessor = s.liveKitVoiceProcessor.replace(
    'require(numChannels == 1)',
    'require(numChannels >= 1)'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects invalid LiveKit callback shapes that bypass transformation instead of silencing', () => {
  const s = source();
  s.liveKitVoiceProcessor = s.liveKitVoiceProcessor.replace(
    'if (numBands <= 0 || numFrames <= 0) {\n                silenceRemaining(buffer)\n                return\n            }',
    'if (numBands <= 0 || numFrames <= 0) return'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects undersized LiveKit callbacks that bypass transformation instead of silencing', () => {
  const s = source();
  s.liveKitVoiceProcessor = s.liveKitVoiceProcessor.replace(
    'if (availableFrames < numFrames) {\n                silenceRemaining(buffer)\n                return\n            }',
    'if (availableFrames < numFrames) return'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});

test('rejects treating LiveKit native Float32 audio as PCM16', () => {
  const s = source();
  s.liveKitVoiceProcessor = s.liveKitVoiceProcessor
    .replaceAll('Float.SIZE_BYTES', 'Short.SIZE_BYTES')
    .replaceAll('buffer.getFloat(', 'buffer.getShort(')
    .replaceAll('buffer.putFloat(', 'buffer.putShort(');
  assert.ok(auditProductTruth(s).some((e) => e.includes('concrete LiveKit capture path')));
});


test('rejects asynchronous persistence for physical Phone Core evidence', () => {
  const s = source();
  s.timelineStore = s.timelineStore.replace(
    'putString(KEY, array.toString()).commit()',
    'putString(KEY, array.toString()).apply()'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('durably committed')));
});


test('rejects unscoped physical evidence when certification provenance is unavailable', () => {
  const s = source();
  s.timelineStore = s.timelineStore.replace(
    '?: return@synchronized false',
    '?: null'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('durably committed')));
});


test('rejects instance-only locking of the shared Phone Core evidence timeline', () => {
  const s = source();
  s.timelineStore = s.timelineStore.replaceAll('synchronized(LOCK)', 'synchronized(this)');
  assert.ok(auditProductTruth(s).some((e) => e.includes('durably committed')));
});

test('rejects asynchronous timeline clearing', () => {
  const s = source();
  s.timelineStore = s.timelineStore.replace(
    'prefs.edit().remove(KEY).commit()',
    'prefs.edit().remove(KEY).apply()'
  );
  assert.ok(auditProductTruth(s).some((e) => e.includes('durably committed')));
});


test('rejects call-screening post-response persistence on the callback thread', () => {
  const s = source();
  s.callScreening = s.callScreening
    .replace('POST_RESPONSE_WORKER.execute {', 'run {')
    .replace('PhonePrivateTimelineStore(appContext).append', 'PhonePrivateTimelineStore(this).append');
  assert.ok(auditProductTruth(s).some((e) => e.includes('post-response Room')));
});

test('requires serialized local-log file access and async callback logging', () => {
  const s = source();
  s.localLogger = s.localLogger.replaceAll('synchronized(FILE_LOCK)', 'run');
  assert.ok(auditProductTruth(s).some((e) => e.includes('local logger')));
});
