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

const bottomNavStringsPath = 'native-android-app/app/src/main/res/values/strings.xml';
const bottomNavStringsSource = readRequired(bottomNavStringsPath);
if (
  bottomNavStringsSource &&
  !bottomNavStringsSource.includes('<string name="nav_communications">Échanges</string>')
) {
  errors.push(
    `bottom navigation communications label regressed to a layout-breaking long label: ${bottomNavStringsPath}`
  );
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

const diagnosticPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreDiagnosticActivity.kt';
const diagnosticSource = readRequired(diagnosticPath);
if (diagnosticSource) {
  for (const marker of [
    'Fact("Étapes Android", "$readyStepCount/8 prêtes")',
    'PhoneCoreSetupWizardStore.stepLabel(nextSetupStep)',
    'Restent aussi à configurer',
    'NON · À ACTIVER',
  ]) {
    if (!diagnosticSource.includes(marker)) {
      errors.push(`phone-core diagnostic truth marker missing (${marker}): ${diagnosticPath}`);
    }
  }
  if (diagnosticSource.includes('Blocage actuel : autoriser l’affichage plein écran des appels')) {
    errors.push(`diagnostic reintroduced a hard-coded current blocker: ${diagnosticPath}`);
  }
}

const smartHomePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SmartHomeScreen.kt';
const smartHomeSource = readRequired(smartHomePath);
if (smartHomeSource) {
  for (const marker of [
    'Scanner les appareils à proximité',
    'Catalogue technique, pas inventaire de votre maison',
    'Aucune topologie vérifiée dans cet écran',
    'Screen.NetworkSurveillance.route',
    'showCompatibilityCatalog',
  ]) {
    if (!smartHomeSource.includes(marker)) {
      errors.push(`smart-home truth marker missing (${marker}): ${smartHomePath}`);
    }
  }
  if (smartHomeSource.includes('└─ Routeur / point d’accès WiFi')) {
    errors.push(`smart-home reintroduced synthetic topology: ${smartHomePath}`);
  }
}

const vpnPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/VpnScreen.kt';
const vpnSource = readRequired(vpnPath);
if (vpnSource) {
  for (const marker of [
    '"Client intégré"',
    '"Passerelle absente"',
    'aucune passerelle Sentinel disponible et validée',
    'handshake WireGuard réellement établi',
    'Une action de connexion ne sera affichée que lorsque toute la chaîne sera réellement vérifiable.',
  ]) {
    if (!vpnSource.includes(marker)) {
      errors.push(`vpn truth marker missing (${marker}): ${vpnPath}`);
    }
  }
  if (vpnSource.includes('"Client prêt"') || vpnSource.includes('Sentinel AVAILABLE')) {
    errors.push(`vpn reintroduced misleading/technical public copy: ${vpnPath}`);
  }
  if (/onClick\s*=\s*\{\s*\}/.test(vpnSource)) {
    errors.push(`vpn reintroduced an empty click handler / dead control: ${vpnPath}`);
  }
}

const settingsPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt';
const settingsSource = readRequired(settingsPath);
if (settingsSource) {
  for (const marker of [
    'Notifications de veille OSINT',
    'Sentinel-backup.json',
    'Synchronisation sécurisée indisponible',
    'Aucune règle distante n’est téléchargée',
  ]) {
    if (!settingsSource.includes(marker) &&
        !(marker === 'Notifications de veille OSINT')) {
      errors.push(`settings usability marker missing (${marker}): ${settingsPath}`);
    }
  }
  if (settingsSource.includes('enabled = ruleSyncAvailable')) {
    errors.push(`settings reintroduced a dead disabled sync switch: ${settingsPath}`);
  }
}

const stringsPath = 'native-android-app/app/src/main/res/values/strings.xml';
const stringsSource = readRequired(stringsPath);
if (stringsSource && !stringsSource.includes('Notifications de veille OSINT')) {
  errors.push(`ambiguous OSINT notification label reintroduced: ${stringsPath}`);
}

const settingsVoicePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt';
const settingsVoiceSource = readRequired(settingsVoicePath);
if (settingsVoiceSource) {
  for (const marker of [
    'transformation en appel Sentinel obligatoire',
    'moteur + client WebRTC intégrés',
  ]) {
    if (!settingsVoiceSource.includes(marker)) {
      errors.push(`mandatory live-voice settings marker missing (${marker}): ${settingsVoicePath}`);
    }
  }
  if (settingsVoiceSource.includes('futur add-on optionnel')) {
    errors.push(`settings reintroduced optional/future wording for mandatory live voice: ${settingsVoicePath}`);
  }
}

const voiceStudioPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/VoiceStudioActivity.kt';
const voiceStudioSource = readRequired(voiceStudioPath);
if (voiceStudioSource) {
  for (const marker of [
    '.verticalScroll(rememberScrollState())',
    'var playing by remember',
    'Arrêter la lecture',
    'onCompleted = {',
    'terminée.',
    'post-traitement LiveKit/WebRTC',
    'Session d’appel/PSTN non raccordée',
    'Aucun bouton d’appel n’est affiché',
  ]) {
    if (!voiceStudioSource.includes(marker)) {
      errors.push(`voice-studio lifecycle/usability marker missing (${marker}): ${voiceStudioPath}`);
    }
  }
  if (/onClick\s*=\s*\{\s*\}/.test(voiceStudioSource)) {
    errors.push(`voice-studio reintroduced an empty click handler / dead control: ${voiceStudioPath}`);
  }
}

const mainPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt';
const mainSource = readRequired(mainPath);
if (mainSource.includes('BottomNavTarget.CALLS') || mainSource.includes('BottomNavTarget.MESSAGES')) {
  errors.push(`legacy Calls/Messages bottom-nav targets returned: ${mainPath}`);
}
if (mainSource.includes('startActivity(Intent(this@MainActivity, SentinelDialerActivity::class.java))') ||
    mainSource.includes('startActivity(Intent(this@MainActivity, SmsComposeActivity::class.java))')) {
  errors.push(`bottom navigation returned to external Activity launches instead of the persistent Communications hub: ${mainPath}`);
}

if (mainSource) {
  assertNoLegacyTopBar(mainPath, mainSource);
  for (const marker of [
    'NavigationBar(',
    'SentinelD1.Panel',
    'NavigationBarItemDefaults.colors(',
    'BottomNavTarget.COMMUNICATIONS',
    'Screen.CommunicationsHub',
    'R.string.nav_communications',
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

const contactPresentationPolicyPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/ContactPresentationPolicy.kt';
const contactPresentationPolicySource = readRequired(contactPresentationPolicyPath);
if (contactPresentationPolicySource) {
  for (const marker of [
    'trimmed.codePointAt(0)',
    'Character.isLetter(normalizedCodePoint)',
    'String(Character.toChars(normalizedCodePoint))',
    'fun sectionOrderKey(displayName: String): String',
  ]) {
    if (!contactPresentationPolicySource.includes(marker)) {
      errors.push(
        `Unicode-safe contact section marker missing (${marker}): ${contactPresentationPolicyPath}`
      );
    }
  }
}

const inCallZonePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt';
const inCallZoneSource = readRequired(inCallZonePath);
if (inCallZoneSource) {
  for (const marker of [
    'PhoneCountryPrefixCatalog.resolveNumber(handle)',
    'Zone d’indicatif uniquement · ne localise pas l’appelant',
  ]) {
    if (!inCallZoneSource.includes(marker)) {
      errors.push(`in-call calling-zone truth marker missing (${marker}): ${inCallZonePath}`);
    }
  }
}

const callLogReaderPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SystemCallLogReader.kt';
const callLogReaderSource = readRequired(callLogReaderPath);
if (callLogReaderSource) {
  for (const marker of [
    'CallLog.Calls.CACHED_NAME',
    'val cachedName: String? = null',
    '?.take(MAX_CACHED_NAME_CHARS)',
    'const val MAX_CACHED_NAME_CHARS = 160',
  ]) {
    if (!callLogReaderSource.includes(marker)) {
      errors.push(`call-log cached-name marker missing (${marker}): ${callLogReaderPath}`);
    }
  }
}

const stateChipPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelD1.kt';
const stateChipSource = readRequired(stateChipPath);
if (stateChipSource) {
  for (const marker of [
    'onClick: (() -> Unit)? = null',
    'R.string.phone_core_open_validations',
    'Modifier.clickable(',
    'onClickLabel = actionLabel',
    'role = Role.Button',
    'onClick = onClick',
  ]) {
    if (!stateChipSource.includes(marker)) {
      errors.push(`actionable state-chip marker missing (${marker}): ${stateChipPath}`);
    }
  }
}

const stateChipStringsPath =
  'native-android-app/app/src/main/res/values/strings.xml';
const stateChipStringsSource = readRequired(stateChipStringsPath);
if (
  stateChipStringsSource &&
  !stateChipStringsSource.includes('name="phone_core_open_validations"')
) {
  errors.push(`Phone Core state-chip action resource missing: ${stateChipStringsPath}`);
}

const activationFriendlyPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt';
const activationFriendlySource = readRequired(activationFriendlyPath);
if (activationFriendlySource) {
  for (const marker of [
    'Finaliser la configuration du téléphone',
    'Sentinel vérifie directement ce qu’Android autorise réellement sur cet appareil.',
    'if (state.callsReady) "APPELS PRÊTS" else "APPELS À ACTIVER"',
    'if (readiness.softwarePrerequisitesReady) "CONFIGURATION PRÊTE" else "CONFIGURATION À TERMINER"',
    '"TESTS ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}"',
    'Tests sur cet appareil',
    'Tests validés :',
    'Vérifier la configuration avancée',
  ]) {
    if (!activationFriendlySource.includes(marker)) {
      errors.push(
        `friendly Phone Core activation marker missing (${marker}): ${activationFriendlyPath}`
      );
    }
  }

  for (const forbiddenMarker of [
    'StatusChip("SMS ${PhoneCoreFrenchLabels.smsState(smsModel.state)}"',
    '"APPAREIL LOCAL ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount}"',
    '"PREUVES ${physicalEvidence.completedCount}/${physicalEvidence.requiredCount} · LOGICIEL À RÉACTIVER"',
  ]) {
    if (activationFriendlySource.includes(forbiddenMarker)) {
      errors.push(
        `legacy first-level Phone Core marker reintroduced (${forbiddenMarker}): ${activationFriendlyPath}`
      );
    }
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
    'CallHistoryPresentationPolicy.labels(',
    'entry.number,',
    'entry.cachedName',
    'recentLabels.primary',
    'recentLabels.secondary?.let { secondary ->',
    'recentRemaining',
    'Afficher ${minOf(CALL_HISTORY_PAGE_SIZE, recentRemaining)} de plus',
    'contacts.listWithState()',
    'PhoneCoreActivationActivity::class.java',
    'SentinelStateChip(',
    'onClick = {',
    'ContactDialNumberPolicy.fromProvider(phoneNumber)',
    'entry.number?.let(ContactDialNumberPolicy::fromProvider)',
    'ContactSearchPolicy.matches(',
    'ContactPresentationPolicy.displayNumbers(contact.phoneNumbers)',
    'ContactPresentationPolicy.include(',
    'WhatsAppClickToChatPolicy.urlFor(phoneNumber)',
    'listOf("Clavier", "Récents", "Répertoire", "Réglages")',
    'Vos contacts restent sur cet appareil.',
    'label = { Text("Appelables", maxLines = 1) }',
    'label = { Text("Sans numéro", maxLines = 1) }',
    'contactVisibleLimit',
    'val sectionedContacts = remember(filteredContacts)',
    'ContactPresentationPolicy.sectionOrderKey(it.value.displayName)',
    'val visibleContacts = sectionedContacts.take(contactVisibleLimit)',
    'ContactPresentationPolicy.sectionLabel(contact.displayName)',
    'previousSection',
    'HorizontalDivider(modifier = Modifier.weight(1f))',
    'Afficher ${minOf(CONTACTS_PAGE_SIZE, remaining)} de plus',
  ]) {
    if (!dialerContactsSource.includes(marker)) {
      errors.push(`complete contact-directory UI marker missing (${marker}): ${dialerContactsPath}`);
    }
  }
  if (!/Text\(\s*"Votre répertoire"/.test(dialerContactsSource)) {
    errors.push(`complete contact-directory UI marker missing (Votre répertoire heading): ${dialerContactsPath}`);
  }
  if (/contacts\.listWithState\(500\)/.test(dialerContactsSource)) {
    errors.push(`dialer reintroduced a 500-contact read cap: ${dialerContactsPath}`);
  }
  if (dialerContactsSource.includes('Text("Tout afficher")')) {
    errors.push(`dialer reintroduced render-all for the full contact provider: ${dialerContactsPath}`);
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
    'text = { Text("Conversations") }',
    'text = { Text("Écrire") }',
    'label = { Text("Répondre") }',
    'submitSms(replyAddress, draft)',
    'LiveRegionMode.Polite',
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

const emailSecurityPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/EmailSecurityScreen.kt';
const emailSecuritySource = readRequired(emailSecurityPath);
if (emailSecuritySource) {
  for (const marker of ['rememberCoroutineScope()', 'withContext(Dispatchers.Default)', 'isAnalyzing']) {
    if (!emailSecuritySource.includes(marker)) errors.push(`email analysis off-main marker missing (${marker}): ${emailSecurityPath}`);
  }
  if (emailSecuritySource.includes('Button({ result = analyzer.analyze(rawMessage) }')) errors.push(`email analysis moved back onto the Compose click thread: ${emailSecurityPath}`);
}
const smsScannerPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SmsScannerScreen.kt';
const smsScannerSource = readRequired(smsScannerPath);
if (smsScannerSource) {
  for (const marker of ['rememberCoroutineScope()', 'withContext(Dispatchers.IO)', 'timelineStore.append(event)', 'isAnalyzing']) {
    if (!smsScannerSource.includes(marker)) errors.push(`SMS analysis off-main marker missing (${marker}): ${smsScannerPath}`);
  }
  if (smsScannerSource.includes('val analysis = analyzer.analyze(rawMessage)')) errors.push(`SMS analysis moved back onto the Compose click thread: ${smsScannerPath}`);
}

const systemDoctorPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SystemDoctorScreen.kt';
const systemDoctorSource = readRequired(systemDoctorPath);
if (systemDoctorSource) {
  for (const marker of ['rememberCoroutineScope()', 'withContext(Dispatchers.IO) { doctor.scan() }', 'isScanning', 'enabled = !isScanning']) {
    if (!systemDoctorSource.includes(marker)) errors.push(`System Doctor off-main marker missing (${marker}): ${systemDoctorPath}`);
  }
  if (systemDoctorSource.includes('onClick = { scan = doctor.scan() }')) errors.push(`System Doctor scan moved back onto the Compose click thread: ${systemDoctorPath}`);
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
