import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { auditPhysicalEvidenceCollector } from './check-phone-core-physical-evidence.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const SOURCE_PATHS = Object.freeze({
  callScreening: 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt',
  screeningProjection: 'native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/ScreeningProjection.kt',
  intelligenceStore: 'native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceSnapshotStore.kt',
  intelligenceRuntime: 'native-android-app/app/src/main/java/com/sentinel/quantum/intelligence/IntelligenceCoreRuntime.kt',
  physicalEvidenceCollector: 'scripts/phone-core-physical-evidence.sh'
});

export function loadIntelligenceProductTruthSources(root = ROOT) {
  return Object.fromEntries(
    Object.entries(SOURCE_PATHS).map(([key, relativePath]) => [
      key,
      fs.readFileSync(path.join(root, relativePath), 'utf8')
    ])
  );
}

export function auditIntelligenceProductTruth(sources) {
  const errors = [];
  const {
    callScreening = '',
    screeningProjection = '',
    intelligenceStore = '',
    intelligenceRuntime = '',
    physicalEvidenceCollector = ''
  } = sources;

  const callbackStart = callScreening.indexOf('override fun onScreenCall');
  const responseIndex = callScreening.indexOf('respondToCall(callDetails, response.build())', callbackStart);
  if (callbackStart < 0 || responseIndex < 0) {
    errors.push('call screening critical path: unable to prove the mandatory response boundary');
  } else {
    const criticalPath = callScreening.slice(callbackStart, responseIndex);
    const forbiddenCriticalPathTokens = [
      'IntelligenceCoreRuntime',
      'IntelligenceSnapshotStore',
      'IntelligenceAggregator',
      'BoundedIntelligenceEventSink',
      'LocalContactLookup',
      'CallerReputationClient',
      'snapshotForNumber('
    ];
    if (forbiddenCriticalPathTokens.some((token) => criticalPath.includes(token))) {
      errors.push('call screening critical path: general intelligence, contacts, persistence or remote reputation must remain after respondToCall');
    }
  }

  const forbiddenProjectionFields = /\b(?:communityScore|communitySignals|riskScore|riskBand|contactDisplayName|contactName|displayName|organisationLabel|messageBody|senderAddress)\b/;
  if (forbiddenProjectionFields.test(screeningProjection)) {
    errors.push('ScreeningProjection: community, UI risk, contact identity and message-derived fields are forbidden');
  }
  if (screeningProjection.includes('riskAssessment.enforcementAuthorized') ||
      screeningProjection.includes('enforcementAuthorized')) {
    errors.push('ScreeningProjection: general snapshot enforcement authorization is not a blocking authority');
  }

  const forbiddenPersistenceSlot = /\b(?:val|var)\s+(?:rawPhoneNumber|phoneNumber|rawNumber|messageBody|smsBody|mmsBody|senderAddress|senderLiteral|otp|oneTimePassword|rawUrl|fullUrl)\b/;
  if (forbiddenPersistenceSlot.test(intelligenceStore)) {
    errors.push('intelligence persistence raw PII: phone numbers, message bodies, senders, OTPs and URLs must have no storage slot');
  }

  if (intelligenceRuntime.includes('riskAssessment.enforcementAuthorized') ||
      /autoBlock\s*\([^)]*IntelligenceSnapshot/.test(intelligenceRuntime)) {
    errors.push('intelligence general snapshot must not become direct automatic-blocking authority');
  }

  errors.push(...auditPhysicalEvidenceCollector(physicalEvidenceCollector));
  return errors;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const errors = auditIntelligenceProductTruth(loadIntelligenceProductTruthSources());
  if (errors.length > 0) {
    console.error('Intelligence product truth contract: FAILED');
    for (const error of errors) console.error('- ' + error);
    process.exitCode = 1;
  } else {
    console.log('Intelligence product truth contract: OK');
  }
}
