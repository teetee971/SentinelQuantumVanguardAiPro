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
