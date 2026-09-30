#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

const harmonizedSurfaces = [
  'native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NumberSearchScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/LocalLogsScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/DigitalExposureScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/VpnScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SmartHomeScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SystemDoctorScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SecurityAuditScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/OsintFeedScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/OsintDetailScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SmsScannerScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/EmailSecurityScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/AppPermissionAnalyzerScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/AboutScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/ComplianceScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CallBlockingScreen.kt',
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CallFilterHistoryScreen.kt',
];

const errors = [];

for (const relativePath of harmonizedSurfaces) {
  const absolutePath = path.join(root, relativePath);
  if (!fs.existsSync(absolutePath)) {
    errors.push(`missing harmonized surface: ${relativePath}`);
    continue;
  }

  const source = fs.readFileSync(absolutePath, 'utf8');

  if (!source.includes('SentinelTopBar(') && relativePath !== 'native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt') {
    errors.push(`shared SentinelTopBar missing: ${relativePath}`);
  }

  if (/\bCenterAlignedTopAppBar\s*\(/.test(source)) {
    errors.push(`legacy CenterAlignedTopAppBar reintroduced: ${relativePath}`);
  }

  if (/\bTopAppBar\s*\(/.test(source)) {
    errors.push(`legacy TopAppBar reintroduced: ${relativePath}`);
  }
}

const chromePath = path.join(
  root,
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelChrome.kt'
);
if (!fs.existsSync(chromePath)) {
  errors.push('shared SentinelChrome.kt missing');
} else {
  const chrome = fs.readFileSync(chromePath, 'utf8');
  for (const component of ['SentinelTopBar', 'SentinelHero', 'SentinelPanel', 'SentinelSectionHeader']) {
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

console.log(`Android UI consistency check passed for ${harmonizedSurfaces.length} harmonized surfaces.`);
