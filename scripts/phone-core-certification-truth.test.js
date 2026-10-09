import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

test('Phone Core roadmap and diagnostic copy match certification schema v5', () => {
  const source = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCorePhysicalValidation.kt'),
    'utf8'
  );
  const diagnostic = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreDiagnosticActivity.kt'),
    'utf8'
  );
  const activation = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt'),
    'utf8'
  );
  const dialer = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt'),
    'utf8'
  );
  const uiStateTest = readFileSync(
    resolve('native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/PhoneCoreUiStateTest.kt'),
    'utf8'
  );
  const roadmap = readFileSync(resolve('docs/ROADMAP.md'), 'utf8');
  const registry = readFileSync(resolve('config/product-capabilities.json'), 'utf8');
  const publicRoadmap = readFileSync(resolve('public/roadmap.html'), 'utf8');

  assert.match(source, /CERTIFICATION_SCHEMA_VERSION\s*=\s*5/);
  const required = source.match(/requiredCount:\s*Int\s*get\(\)\s*=\s*(\d+)/)?.[1];
  assert.equal(required, '14');

  assert.match(roadmap, /certificat Phone Core v5 comporte \*\*exactement 14 preuves\*\*/);
  assert.match(roadmap, /Wi-Fi reste un diagnostic réseau indépendant/);
  assert.match(roadmap, /ne modifie jamais le compteur 14\/14/);

  const parsedRegistry = JSON.parse(registry);
  const phoneCore = parsedRegistry.capabilities.find((entry) => entry.id === 'phone_core_android');
  assert.ok(phoneCore, 'phone_core_android capability must be registered');
  assert.equal(phoneCore.requires_physical_validation, true);
  assert.equal(phoneCore.physically_validated, false);
  assert.equal(phoneCore.customer_available, false);

  assert.match(publicRoadmap, /certificat Phone Core v5 porte sur 14 preuves distinctes/);
  assert.match(publicRoadmap, /scanner Wi-Fi reste un diagnostic réseau séparé/);
  assert.match(publicRoadmap, /Validation physique 14\/14 requise/);
  assert.doesNotMatch(publicRoadmap, /--progress:\d+%/);

  // Certification detail is a technical diagnostic concern, not a customer setup journey.
  assert.match(diagnostic, /Certification Phone Core · technique/);
  assert.match(diagnostic, /Preuves observées/);
  assert.match(diagnostic, /qualification technique de Sentinel/);
  assert.match(diagnostic, /Le détail x\/14 reste volontairement limité à ce diagnostic technique/);
  assert.match(diagnostic, /PhoneCorePhysicalValidation\.criterionLabel\(id\)/);

  for (const [surface, text] of [
    ['activation', activation],
    ['dialer', dialer],
  ]) {
    assert.doesNotMatch(
      text,
      /TESTS \$\{physicalEvidence\.completedCount\}\/\$\{physicalEvidence\.requiredCount\}/,
      `${surface} must not expose raw certification counts to customers`
    );
    assert.doesNotMatch(
      text,
      /Validation Phone Core[^\n]*\$\{physicalEvidence\.completedCount\}\/\$\{physicalEvidence\.requiredCount\}/,
      `${surface} must keep certification progress out of the customer UI`
    );
    assert.doesNotMatch(
      text,
      /SentinelEvidenceProgress\(/,
      `${surface} must not render the technical certification progress component`
    );
  }

  // Customer-facing Phone Core status stays resource-backed so this UX pass does not create new
  // repository-i18n debt while hiding the certification counters.
  assert.match(dialer, /import androidx\.compose\.ui\.res\.pluralStringResource/);
  assert.match(dialer, /R\.plurals\.phone_core_ready_validation_count/);
  assert.match(dialer, /R\.plurals\.phone_core_configuration_validation_count/);
  assert.doesNotMatch(dialer, /"Configuration téléphone prête"/);
  assert.doesNotMatch(dialer, /"Configuration téléphone à terminer"/);

  assert.match(uiStateTest, /completePhysicalProofIsReady/);
  assert.match(uiStateTest, /thirteenOfFourteenRemainsLimited/);
  assert.match(
    dialer,
    /physicalDeviceValidated\s*=\s*physicalEvidence\.physicalDeviceValidated/,
    'customer Phone Core state must receive physical validation separately from local technical evidence'
  );

  assert.doesNotMatch(source, /wifiScanFresh|SIGNAL_WIFI_SCAN_FRESH|WIFI_SCAN_FRESH|Kind\.WIFI/);
});

test('first-run completion includes the same secure MMS prerequisite as Phone Core readiness', () => {
  const activation = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt'),
    'utf8'
  );
  const setupStore = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreSetupWizardStore.kt'),
    'utf8'
  );
  const runtimeFacts = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreRuntimeFacts.kt'),
    'utf8'
  );
  assert.match(setupStore, /mmsSafePreviewValidated:\s*Boolean\s*,/);
  assert.doesNotMatch(setupStore, /mmsSafePreviewValidated:\s*Boolean\s*=\s*true/);
  for (const field of ['dialerRoleAvailable', 'callScreeningRoleAvailable', 'smsRoleAvailable']) {
    assert.match(setupStore, new RegExp(`${field}:\\s*Boolean\\s*,`));
    assert.doesNotMatch(setupStore, new RegExp(`${field}:\\s*Boolean\\s*=\\s*true`));
  }
  assert.match(setupStore, /facts\.mmsSafePreviewValidated/);
  assert.match(runtimeFacts, /MmsSafePreviewReadiness\.softwareValidated/);
  assert.match(
    activation,
    /status = when \{[\s\S]*!mmsSafePreviewValidated[\s\S]*Phone Core bloqué · aperçu MMS sécurisé indisponible/
  );
});

test('first-run assistant never requests an Android capability before durable state commits', () => {
  const main = readFileSync(resolve('native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt'), 'utf8');
  const activation = readFileSync(resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt'), 'utf8');
  const setupStore = readFileSync(resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreSetupWizardStore.kt'), 'utf8');

  assert.match(setupStore, /fun markInProgress\(\): Boolean/);
  assert.match(setupStore, /private fun setLifecycleState\(state: LifecycleState\): Boolean/);
  assert.match(
    main,
    /if \(!wizard\.markOffered\(\)\) \{[\s\S]*launchPhoneCoreSetup\(persistenceError = true\)/,
    'a failed OFFERED write must open only the retryable error surface'
  );
  assert.match(
    main,
    /if \(!wizard\.markInProgress\(\)\) \{[\s\S]*launchPhoneCoreSetup\(persistenceError = true\)/,
    'a failed IN_PROGRESS write must open only the retryable error surface'
  );
  assert.match(
    main,
    /private fun launchPhoneCoreSetup\(persistenceError: Boolean = false\)[\s\S]*EXTRA_SETUP_PERSISTENCE_ERROR/,
    'the error surface must carry an explicit persistence-failure marker'
  );
  assert.match(
    activation,
    /if \(!setupWizard\.markAttemptedTarget\(setupTargetKey\)\) \{[\s\S]*setupPersistenceError = true[\s\S]*return\s*\}/,
    'the setup target must be durably recorded before launching Android and failures must stay visible'
  );
});


test('Phone Core readiness no longer owns the Wi-Fi scanner capability', () => {
  const diagnostics = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCoreDiagnostics.kt'),
    'utf8'
  );
  const activation = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt'),
    'utf8'
  );
  const labels = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCoreFrenchLabels.kt'),
    'utf8'
  );
  const wifiScanner = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/WifiScanner.kt'),
    'utf8'
  );

  assert.doesNotMatch(diagnostics, /"WIFI_SCAN"/);
  assert.doesNotMatch(diagnostics, /wifiScanServiceAvailable|wifiScanPermissionGranted|locationEnabledForWifiScan/);
  assert.doesNotMatch(activation, /wifiScanServiceAvailable\s*=|wifiScanPermissionGranted\s*=|locationEnabledForWifiScan\s*=|wifiScanFresh|Réseau Wi‑Fi/);
  assert.doesNotMatch(labels, /"WIFI_SCAN"\s*->/);
  assert.match(wifiScanner, /SIGNAL_WIFI_SCAN_FRESH\s*=\s*"WIFI_SCAN_FRESH"/);
  assert.doesNotMatch(wifiScanner, /PhoneCorePhysicalValidation\.SIGNAL_WIFI_SCAN_FRESH/);
});


test('counter-free customer Phone Core resources cover every supported Android version', () => {
  const buildGradle = readFileSync(resolve('native-android-app/app/build.gradle'), 'utf8');
  const minSdk = Number(buildGradle.match(/\bminSdk\s+(\d+)/)?.[1]);
  assert.ok(Number.isInteger(minSdk), 'Android minSdk must remain statically auditable');
  assert.ok(
    minSdk >= 24,
    'counter-free values-v24 Phone Core resources no longer cover the supported SDK floor'
  );

  const names = [
    'phone_core_ready_validation_count',
    'phone_core_configuration_validation_count',
  ];
  const pluralBlock = (text, name) =>
    text.match(
      new RegExp(
        `<plurals\\b(?=[^>]*\\bname\\s*=\\s*["']${name}["'])[^>]*>[\\s\\S]*?<\\/plurals>`,
        'i'
      )
    )?.[0] ?? null;
  const androidFormatPlaceholder = /%(?!%)(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[bBhHsScCdoxXeEfgGaAtTn]/;
  const assertCustomerStatusIsCounterFree = (block, location) => {
    assert.ok(block, `${location} must define the customer Phone Core status plural`);
    assert.doesNotMatch(
      block,
      androidFormatPlaceholder,
      `${location} reintroduces a format placeholder in counter-free customer status`
    );
    assert.doesNotMatch(
      block,
      /\d+\s*\/\s*\d+|\bvalidation(?:s)?\b/i,
      `${location} reintroduces certification progress in customer status`
    );
  };

  const v24 = readFileSync(
    resolve('native-android-app/app/src/main/res/values-v24/phone_core_status.xml'),
    'utf8'
  );
  for (const name of names) {
    assertCustomerStatusIsCounterFree(pluralBlock(v24, name), `values-v24/phone_core_status.xml:${name}`);
  }

  // A future locale-qualified override can outrank the generic values-v24 fallback for that locale.
  // Attribute order, quote style and line breaks are intentionally tolerated. If a qualified resource
  // redefines either customer status plural, only that element is inspected; unrelated XML copy must
  // not create false positives.
  const resRoot = resolve('native-android-app/app/src/main/res');
  for (const directory of readdirSync(resRoot, { withFileTypes: true })) {
    if (!directory.isDirectory() || !directory.name.startsWith('values-')) continue;
    const directoryPath = resolve(resRoot, directory.name);
    for (const file of readdirSync(directoryPath, { withFileTypes: true })) {
      if (!file.isFile() || !file.name.endsWith('.xml')) continue;
      const text = readFileSync(resolve(directoryPath, file.name), 'utf8');
      for (const name of names) {
        const block = pluralBlock(text, name);
        if (block == null) continue;
        assertCustomerStatusIsCounterFree(block, `${directory.name}/${file.name}:${name}`);
      }
    }
  }
});


test('Phone Core activation uses the shared version-aware call-screening truth', () => {
  const activation = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt'),
    'utf8'
  );

  assert.match(activation, /import com\.sentinel\.quantum\.security\.CallScreeningActivationPolicy/);
  assert.match(
    activation,
    /val screening = CallScreeningActivationPolicy\.read\(this\) == CallScreeningActivationPolicy\.State\.HELD/
  );
  assert.doesNotMatch(
    activation,
    /Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.Q && holdsRole\(RoleManager\.ROLE_CALL_SCREENING\)/
  );
  assert.match(
    activation,
    /RoleManager\.ROLE_DIALER -> Intent\(TelecomManager\.ACTION_CHANGE_DEFAULT_DIALER\)/
  );
  assert.match(
    activation,
    /RoleManager\.ROLE_CALL_SCREENING ->[\s\S]*?CallScreeningActivationPolicy\.read\(this\) == CallScreeningActivationPolicy\.State\.AVAILABLE_NOT_HELD[\s\S]*?Intent\(TelecomManager\.ACTION_CHANGE_DEFAULT_DIALER\)/
  );
  assert.match(activation, /when \(callScreeningState\)/);
  assert.doesNotMatch(activation, /Disponible à partir d’Android 10/);

  const coreProgress = activation.match(
    /PhoneCoreSetupWizardStore\.Step\.CORE_PERMISSIONS -> \{[\s\S]*?checks\.count \{ it \} to checks\.size/
  )?.[0];
  assert.ok(coreProgress, 'CORE_PERMISSIONS progress block must remain statically auditable');
  assert.doesNotMatch(
    coreProgress,
    /state\.contactsPermission/,
    'optional contacts must never re-enter the essential setup progress denominator'
  );
});


test('legacy call-screening implementation, CI scope and customer copy stay truthfully separated', () => {
  const readme = readFileSync(resolve('native-android-app/README.md'), 'utf8');
  const physicalProtocol = readFileSync(resolve('docs/PHONE_CORE_PHYSICAL_VALIDATION.md'), 'utf8');
  const legacyUiCopy = readFileSync(
    resolve('native-android-app/app/src/main/res/values-v24/call_screening_truth.xml'),
    'utf8'
  );
  const activation = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt'),
    'utf8'
  );
  const policyTests = readFileSync(
    resolve('native-android-app/app/src/test/java/com/sentinel/quantum/security/CallScreeningActivationPolicyTest.kt'),
    'utf8'
  );

  assert.match(readme, /API 24–28[\s\S]*ACTION_CHANGE_DEFAULT_DIALER/);
  assert.match(readme, /connectedDebugAndroidTest[\s\S]*API 24, 36 et 37/);
  assert.doesNotMatch(readme, /parcours legacy API 24–28 n’est pas implémenté/);
  assert.match(physicalProtocol, /chemin legacy API 24–28 est implémenté via `ACTION_CHANGE_DEFAULT_DIALER`/);
  assert.match(physicalProtocol, /ne prouve pas le basculement réel du rôle/);
  assert.match(legacyUiCopy, /configuration actuelle/);
  assert.doesNotMatch(legacyUiCopy, /Android 10/);
  assert.match(
    activation,
    /RoleManager\.ROLE_CALL_SCREENING ->[\s\S]*?Intent\(TelecomManager\.ACTION_CHANGE_DEFAULT_DIALER\)/
  );
  assert.match(policyTests, /api24To28CanBecomeAvailableViaDefaultDialerPath/);
});
