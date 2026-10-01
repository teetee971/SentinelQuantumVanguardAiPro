#!/usr/bin/env node
'use strict';

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

const androidRoot = path.join(
  root,
  'native-android-app/app/src/main/java/com/sentinel/quantum'
);
const screenRoot = path.join(androidRoot, 'ui/screens');

const errors = [];

function relativeFromRoot(absolutePath) {
  return path.relative(root, absolutePath).split(path.sep).join('/');
}

function readRequired(relativePath) {
  const absolutePath = path.join(root, relativePath);
  if (!fs.existsSync(absolutePath)) {
    errors.push(`missing UI surface: ${relativePath}`);
    return null;
  }
  return fs.readFileSync(absolutePath, 'utf8');
}

function discoverScreens(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const absolutePath = path.join(directory, entry.name);
      if (entry.isDirectory()) return discoverScreens(absolutePath);
      return entry.isFile() && entry.name.endsWith('Screen.kt') ? [absolutePath] : [];
    })
    .sort();
}

function assertNoLegacyTopBar(relativePath, source) {
  if (/\bCenterAlignedTopAppBar\s*\(/.test(source)) {
    errors.push(`legacy CenterAlignedTopAppBar reintroduced: ${relativePath}`);
  }
  if (/\bTopAppBar\s*\(/.test(source)) {
    errors.push(`legacy TopAppBar reintroduced: ${relativePath}`);
  }
}

function assertSharedTopBar(relativePath, source) {
  if (!source.includes('SentinelTopBar(')) {
    errors.push(`shared SentinelTopBar missing: ${relativePath}`);
  }
  assertNoLegacyTopBar(relativePath, source);
}

function assertImmersiveSurface(relativePath, markers) {
  const source = readRequired(relativePath);
  if (!source) return;

  assertNoLegacyTopBar(relativePath, source);

  if (source.includes('SentinelTopBar(')) {
    errors.push(`immersive surface must not use the standard app top bar: ${relativePath}`);
  }

  for (const marker of markers) {
    if (!source.includes(marker)) {
      errors.push(`immersive design marker missing (${marker}): ${relativePath}`);
    }
  }
}

if (!fs.existsSync(screenRoot)) {
  errors.push(`Android screen directory missing: ${relativeFromRoot(screenRoot)}`);
}

const discoveredScreens = discoverScreens(screenRoot);

if (discoveredScreens.length === 0) {
  errors.push('no Android *Screen.kt surfaces discovered');
}

for (const absolutePath of discoveredScreens) {
  const relativePath = relativeFromRoot(absolutePath);
  const source = fs.readFileSync(absolutePath, 'utf8');
  assertSharedTopBar(relativePath, source);
}

const topBarActivities = [
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreDiagnosticActivity.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt',
];

for (const relativePath of topBarActivities) {
  const source = readRequired(relativePath);
  if (source) assertSharedTopBar(relativePath, source);
}

const homePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt';
const homeSource = readRequired(homePath);
if (homeSource) {
  for (const marker of [
    'Chercher une fonction',
    'QuickToolCard(',
    'SentinelDialerActivity.EXTRA_OPEN_CONTACTS',
    'matchingTools.chunked(2)',
    'Continuer l’activation',
    'étapes prêtes',
    'étapes d’activation Android prêtes',
    'PhoneCoreSetupWizardStore.stepLabel(nextPhoneCoreStep)',
    'context.startActivity(Intent(context, SentinelDialerActivity::class.java))',
    'maxLines = 3',
  ]) {
    if (!homeSource.includes(marker)) {
      errors.push(`task-first home marker missing (${marker}): ${homePath}`);
    }
  }
}

const auditUiPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SecurityAuditScreen.kt';
const auditUiSource = readRequired(auditUiPath);
if (auditUiSource) {
  for (const marker of [
    'PermissionSection("À activer", runtimeMissing)',
    'PermissionSection("Accordées", runtimeGranted)',
    'SecurityAudit.userFacingPermissionLabel(permission.name)',
    'overflow = TextOverflow.Ellipsis',
    'withContext(Dispatchers.IO)',
  ]) {
    if (!auditUiSource.includes(marker)) {
      errors.push(`readable security-audit marker missing (${marker}): ${auditUiPath}`);
    }
  }
}

const communicationsPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CommunicationsHubScreen.kt';
const communicationsSource = readRequired(communicationsPath);
if (communicationsSource) {
  for (const marker of [
    'PhoneCoreRuntimeFacts.read(context.applicationContext)',
    'SmsActivationDiagnostics(context.applicationContext).snapshot()',
    'SMS bloqué · choisissez Sentinel comme application SMS par défaut.',
    'actionLabel = if (smsSnapshot.smsRoleState',
    'Voir les canaux externes (',
    'showExternalChannels',
  ]) {
    if (!communicationsSource.includes(marker)) {
      errors.push(`runtime communications-status marker missing (${marker}): ${communicationsPath}`);
    }
  }
}

const numberSearchPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NumberSearchScreen.kt';
const numberSearchSource = readRequired(numberSearchPath);
if (numberSearchSource) {
  for (const marker of [
    '.verticalScroll(rememberScrollState())',
    'Ouvrir le composeur et rechercher',
  ]) {
    if (!numberSearchSource.includes(marker)) {
      errors.push(`number-search accessibility marker missing (${marker}): ${numberSearchPath}`);
    }
  }
}

const mainPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt';
const mainSource = readRequired(mainPath);
if (mainSource) {
  assertNoLegacyTopBar(mainPath, mainSource);
  for (const marker of [
    'NavigationBar(',
    'SentinelD1.Panel',
    'NavigationBarItemDefaults.colors(',
    'BottomNavTarget.CALLS',
    'BottomNavTarget.MESSAGES',
    'SentinelDialerActivity::class.java',
    'SmsComposeActivity::class.java',
    'R.string.nav_calls',
    'R.string.nav_messages',
    'R.string.nav_more',
  ]) {
    if (!mainSource.includes(marker)) {
      errors.push(`primary navigation marker missing (${marker}): ${mainPath}`);
    }
  }
}

assertImmersiveSurface(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt',
  [
    'PhoneCoreBrand(',
    'CallerHero(',
    'OngoingPrimaryControls(',
    'CallActionCircle(',
    'IncomingActions(snapshot)',
    'DialpadPanel(snapshot)',
    'DtmfPad(snapshot.id)',
  ]
);

assertImmersiveSurface(
  'native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt',
  [
    'PhoneCoreBrand(',
    'CallerCard(',
    'SentinelNumberCard.build(',
    'EvidenceFact(',
    'onPrepareReport',
    'onDismiss',
    'WhatsAppClickToChatPolicy.urlFor(number)',
    'Text("Ouvrir dans WhatsApp")',
    'val timelineSummary by produceState(',
    'lifecycleScope.launch',
    'withContext(Dispatchers.IO)',
  ]
);

const callerIdPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt';
const callerIdSource = readRequired(callerIdPath);
if (callerIdSource) {
  if (callerIdSource.includes('remember(context) { PhonePrivateTimelineStore(context).read() }')) {
    errors.push(`Caller ID timeline read moved back onto the Compose thread: ${callerIdPath}`);
  }
  if (/val stored\s*=\s*PhonePrivateTimelineStore\(applicationContext\)\.append\(/.test(callerIdSource)) {
    errors.push(`Caller ID evidence persistence moved back onto onResume main thread: ${callerIdPath}`);
  }
  for (const marker of [
    'LocalContactLookup(applicationContext).find(number)',
    'var localName by remember(number)',
    'name = localName',
  ]) {
    if (!callerIdSource.includes(marker)) {
      errors.push(`Caller ID asynchronous local-contact marker missing (${marker}): ${callerIdPath}`);
    }
  }
}

const callScreeningPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallScreeningService.kt';
const callScreeningSource = readRequired(callScreeningPath);
if (callScreeningSource) {
  if (callScreeningSource.includes('LocalContactLookup(this).find(')) {
    errors.push(`call-screening callback performs a synchronous Contacts provider lookup before Caller ID UI: ${callScreeningPath}`);
  }
}


const contactLookupPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/LocalContactLookup.kt';
const contactLookupSource = readRequired(contactLookupPath);
if (contactLookupSource) {
  for (const marker of [
    'fun listWithState(limit: Int = Int.MAX_VALUE)',
    'ContactsContract.Contacts.CONTENT_URI',
    'ContactsContract.Contacts.HAS_PHONE_NUMBER',
    'ContactsContract.CommonDataKinds.Phone.CONTENT_URI',
    'ContactDirectoryPolicy.merge(',
    'providerPhoneMismatchCount',
  ]) {
    if (!contactLookupSource.includes(marker)) {
      errors.push(`complete contact-provider marker missing (${marker}): ${contactLookupPath}`);
    }
  }
  if (/coerceIn\(1,\s*500\)/.test(contactLookupSource)) {
    errors.push(`silent 500-contact provider cap reintroduced: ${contactLookupPath}`);
  }
}

const dialerContactsPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt';
const dialerContactsSource = readRequired(dialerContactsPath);
if (dialerContactsSource) {
  for (const marker of [
    'CONTACTS_PAGE_SIZE',
    'CALL_HISTORY_PAGE_SIZE',
    'CALL_HISTORY_LOAD_LIMIT',
    'requestDialerRoleForRecents()',
    'openRecentsAfterDialerRoleGrant',
    'openRecentsAfterCallLogPermissionGrant',
    'fun refreshRecents()',
    'callLog.recent(CALL_HISTORY_LOAD_LIMIT)',
    'val lookupNumber = number',
    'contacts.find(lookupNumber)',
    'val physicalEvidence by produceState(',
    'Lecture bornée aux $CALL_HISTORY_LOAD_LIMIT appels les plus récents',
    'recentVisibleLimit',
    'recentItems.take(recentVisibleLimit)',
    'recentRemaining',
    'Afficher ${minOf(CALL_HISTORY_PAGE_SIZE, recentRemaining)} de plus',
    'contacts.listWithState()',
    'ContactDialNumberPolicy.fromProvider(phoneNumber)',
    'entry.number?.let(ContactDialNumberPolicy::fromProvider)',
    'ContactSearchPolicy.matches(',
    'WhatsAppClickToChatPolicy.urlFor(phoneNumber)',
    'contact.phoneNumbers.isEmpty()',
    'contact.phoneNumbers.forEach',
    'contacts sans numéro inclus',
    'profil Android courant',
    'contactVisibleLimit',
    'filteredContacts.take(contactVisibleLimit)',
    'Text("Tout afficher")',
    'affiché(s) sur',
    'Afficher ${minOf(CONTACTS_PAGE_SIZE, remaining)} de plus',
  ]) {
    if (!dialerContactsSource.includes(marker)) {
      errors.push(`complete contact-directory UI marker missing (${marker}): ${dialerContactsPath}`);
    }
  }
  if (/contacts\.listWithState\(500\)/.test(dialerContactsSource)) {
    errors.push(`dialer reintroduced a 500-contact read cap: ${dialerContactsPath}`);
  }
  if (/contactItems[\s\S]{0,500}\.take\(30\)\.forEach\s*\{\s*contact/.test(dialerContactsSource)) {
    errors.push(`dialer reintroduced the silent 30-contact render cap: ${dialerContactsPath}`);
  }
  if (/recentItems\.take\(\d+\)/.test(dialerContactsSource)) {
    errors.push(`dialer reintroduced a silent fixed call-history render cap: ${dialerContactsPath}`);
  }
  if (/if \(!holdsDialerRole\(\)\)\s*requestDialerRole\(number\)\s*else if \(!callLogPermissionGranted\)/.test(dialerContactsSource)) {
    errors.push(`recents role request is coupled to call-placement pendingNumber: ${dialerContactsPath}`);
  }
  if (/recentItems\s*=\s*callLog\.recent\(/.test(dialerContactsSource)) {
    errors.push(`call-log provider read moved back onto the Compose click path: ${dialerContactsPath}`);
  }
  if (dialerContactsSource.includes('contactStatus = contacts.find(number)?.let')) {
    errors.push(`contact provider lookup moved back onto the Compose action path: ${dialerContactsPath}`);
  }
  if (dialerContactsSource.includes('val physicalEvidence = remember(resumeEpoch) {')) {
    errors.push(`physical-evidence provider probes moved back onto the Compose thread: ${dialerContactsPath}`);
  }
}

const activationPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt';
const activationSource = readRequired(activationPath);
if (activationSource) {
  for (const marker of [
    'val physicalEvidence by produceState(',
    'withContext(Dispatchers.IO)',
    'LocalContactLookup(applicationContext).listWithState(1)',
    'SystemCallLogReader(applicationContext).accessState()',
  ]) {
    if (!activationSource.includes(marker)) {
      errors.push(`activation provider-probe marker missing (${marker}): ${activationPath}`);
    }
  }
  if (activationSource.includes('val physicalEvidence = remember(epoch) {')) {
    errors.push(`activation physical-evidence provider probes moved back onto the Compose thread: ${activationPath}`);
  }
}


const inCallActivityPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt';
const inCallActivitySource = readRequired(inCallActivityPath);
if (inCallActivitySource) {
  if (inCallActivitySource.includes('val stored = physicalTimeline.append(')) {
    errors.push(`InCall UI evidence persistence moved back onto the Compose main dispatcher: ${inCallActivityPath}`);
  }
  if (!inCallActivitySource.includes('val stored = withContext(Dispatchers.IO) {')) {
    errors.push(`InCall UI evidence IO dispatcher marker missing: ${inCallActivityPath}`);
  }
}

const inCallServicePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt';
const inCallServiceSource = readRequired(inCallServicePath);
if (inCallServiceSource) {
  for (const marker of [
    'private val timelineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)',
    'connectedEvidenceInFlight',
    'incomingNotificationEvidenceInFlight',
    'timelineScope.launch',
  ]) {
    if (!inCallServiceSource.includes(marker)) {
      errors.push(`InCall async evidence marker missing (${marker}): ${inCallServicePath}`);
    }
  }
  if (inCallServiceSource.includes('PhonePrivateTimelineStore(this).append(')) {
    errors.push(`InCall service reintroduced synchronous timeline persistence: ${inCallServicePath}`);
  }
}

const respondViaMessagePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelRespondViaMessageService.kt';
const respondViaMessageSource = readRequired(respondViaMessagePath);
if (respondViaMessageSource) {
  for (const marker of [
    'Executors.newSingleThreadExecutor',
    'WORKER.execute',
    'SentinelSmsSender(appContext).send(destination, body)',
    'MAIN_HANDLER.post',
    'stopSelfResult(startId)',
  ]) {
    if (!respondViaMessageSource.includes(marker)) {
      errors.push(`quick SMS reply off-main marker missing (${marker}): ${respondViaMessagePath}`);
    }
  }
  if (respondViaMessageSource.includes('SentinelSmsSender(applicationContext).send(destination, body)')) {
    errors.push(`quick SMS reply moved back onto Service.onStartCommand main thread: ${respondViaMessagePath}`);
  }
}

const incomingCallNotificationPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt';
const incomingCallNotificationSource = readRequired(incomingCallNotificationPath);
if (incomingCallNotificationSource) {
  for (const marker of [
    'CallTrustIndicator.assess(',
    'PhoneNumberRiskRules::isKnownPremiumRatePrefix',
    'setName("$label · ${quickTrust.title}")',
    'if (!isChannelEnabled(context)) return false',
  ]) {
    if (!incomingCallNotificationSource.includes(marker)) {
      errors.push(`incoming-call trust indicator marker missing (${marker}): ${incomingCallNotificationPath}`);
    }
  }
}

const smsNotificationPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsNotificationHelper.kt';
const smsNotificationSource = readRequired(smsNotificationPath);
if (smsNotificationSource) {
  for (const marker of [
    'NotificationManagerCompat.from(context).areNotificationsEnabled()',
    'if (!isChannelEnabled(context)) return false',
  ]) {
    if (!smsNotificationSource.includes(marker)) {
      errors.push(`SMS notification truth marker missing (${marker}): ${smsNotificationPath}`);
    }
  }
}

const smsComposePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt';
const smsComposeSource = readRequired(smsComposePath);
if (smsComposeSource) {
  for (const marker of [
    'EXTRA_OPEN_CONVERSATIONS',
    'openConversationsOnLaunch',
    'label = { Text("Conversations") }',
    'label = { Text("Nouveau SMS") }',
    'activationSnapshot.state != SmsActivationDiagnostics.State.READY',
    'activeProviderMessageId',
    'LaunchedEffect(activeSendToken, activeProviderMessageId)',
    'event.providerMessageId != activeProviderMessageId',
    'activeProviderMessageId = result.providerMessageId',
  ]) {
    if (!smsComposeSource.includes(marker)) {
      errors.push(`conversation-first SMS marker missing (${marker}): ${smsComposePath}`);
    }
  }
}

const appPermissionAnalyzerPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/AppPermissionAnalyzerScreen.kt';
const appPermissionAnalyzerSource = readRequired(appPermissionAnalyzerPath);
if (appPermissionAnalyzerSource) {
  for (const marker of [
    'withContext(Dispatchers.IO)',
    'analyzer.analyzeInstalledApps()',
  ]) {
    if (!appPermissionAnalyzerSource.includes(marker)) {
      errors.push(`installed-app analysis off-main marker missing (${marker}): ${appPermissionAnalyzerPath}`);
    }
  }
  if (/profiles\s*=\s*analyzer\.analyzeInstalledApps\(\)/.test(appPermissionAnalyzerSource)) {
    errors.push(`installed-app PackageManager analysis moved back onto the Compose main dispatcher: ${appPermissionAnalyzerPath}`);
  }
}

const osintFeedPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/OsintFeedScreen.kt';
const osintFeedSource = readRequired(osintFeedPath);
if (osintFeedSource &&
    !osintFeedSource.includes('withContext(Dispatchers.IO) { repository.loadCached() }')) {
  errors.push(`OSINT cache decoding moved back onto the Compose main dispatcher: ${osintFeedPath}`);
}

const customerHandoffChecks = [
  [
    'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SmartHomeScreen.kt',
    ['openSystemSettings(', 'handoffStatus'],
  ],
  [
    'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt',
    ['openExternalPage(', 'externalLinkStatus'],
  ],
  [
    'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CommunicationsHubScreen.kt',
    ['Actions essentielles', 'Canaux externes'],
  ],
  [
    'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt',
    ['Wi-Fi à proximité', 'Comprendre les résultats'],
  ],
];
for (const [screenPath, markers] of customerHandoffChecks) {
  const source = readRequired(screenPath);
  if (!source) continue;
  for (const marker of markers) {
    if (!source.includes(marker)) {
      errors.push(`customer-action UX marker missing (${marker}): ${screenPath}`);
    }
  }
}

const chromePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelChrome.kt';
const chrome = readRequired(chromePath);
if (chrome) {
  for (const component of [
    'SentinelTopBar',
    'SentinelHero',
    'SentinelPanel',
    'SentinelSectionHeader',
  ]) {
    if (!chrome.includes(`fun ${component}(`)) {
      errors.push(`shared UI component missing: ${component}`);
    }
  }
}

if (errors.length) {
  console.error('Android UI consistency check failed:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(
  `Android UI consistency check passed for ${discoveredScreens.length} discovered screens, ` +
    `${topBarActivities.length} top-bar activities, primary navigation, and 2 immersive Phone Core surfaces.`
);
