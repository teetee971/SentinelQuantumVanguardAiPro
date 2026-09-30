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
];

for (const relativePath of topBarActivities) {
  const source = readRequired(relativePath);
  if (source) assertSharedTopBar(relativePath, source);
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
    'IncomingActions()',
    'DialpadPanel()',
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
  ]
);


const contactLookupPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/LocalContactLookup.kt';
const contactLookupSource = readRequired(contactLookupPath);
if (contactLookupSource) {
  if (!contactLookupSource.includes('fun listWithState(limit: Int = Int.MAX_VALUE)')) {
    errors.push(`contact provider must default to the complete readable directory: ${contactLookupPath}`);
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
    'contacts.listWithState()',
    'contactVisibleLimit',
    'filteredContacts.take(contactVisibleLimit)',
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
}


const incomingCallNotificationPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt';
const incomingCallNotificationSource = readRequired(incomingCallNotificationPath);
if (incomingCallNotificationSource) {
  for (const marker of [
    'CallTrustIndicator.assess(',
    'PhoneNumberRiskRules::isKnownPremiumRatePrefix',
    'setName("$label · ${quickTrust.title}")',
  ]) {
    if (!incomingCallNotificationSource.includes(marker)) {
      errors.push(`incoming-call trust indicator marker missing (${marker}): ${incomingCallNotificationPath}`);
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
  ]) {
    if (!smsComposeSource.includes(marker)) {
      errors.push(`conversation-first SMS marker missing (${marker}): ${smsComposePath}`);
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
