import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const main = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt',
  'utf8'
);
const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreSetupWizardStore.kt',
  'utf8'
);
const activation = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  'utf8'
);

test('deferred setup is never readiness and can reconcile only from current Android facts', () => {
  assert.match(store, /DEFERRED/);
  assert.match(
    store,
    /LifecycleState\.DEFERRED -> false/,
    'deferred setup must not automatically nag again'
  );
  assert.match(
    main,
    /lifecycleState != PhoneCoreSetupWizardStore\.LifecycleState\.COMPLETED[\s\S]*PhoneCoreSetupWizardStore\.softwarePrerequisitesReady\(runtimeFacts\)[\s\S]*wizard\.markCompleted\(\)/,
    'persisted completion may be reconciled only from current runtime facts'
  );
  assert.match(
    main,
    /wizard\.markDeferred\(\)/,
    'leaving an incomplete user-visible setup must persist DEFERRED rather than completion'
  );
});

test('interrupted setup remains resumable while completed setup reopens on runtime regression', () => {
  assert.match(store, /LifecycleState\.IN_PROGRESS -> true/);
  assert.match(
    store,
    /LifecycleState\.COMPLETED -> !softwarePrerequisitesReady\(facts\)/,
    'runtime revocation must override historical completion'
  );
  assert.match(main, /wizard\.markInProgress\(\)/);
});

test('lifecycle transitions are durable and invalidate legacy completion before Android handoff', () => {
  const setter = store.match(
    /private fun setLifecycleState\(state: LifecycleState\): Boolean \{[\s\S]*?\n    \}/
  )?.[0] ?? '';
  assert.ok(setter, 'setLifecycleState must remain inspectable');
  assert.match(
    setter,
    /putBoolean\(KEY_COMPLETED, false\)/,
    'leaving COMPLETED must invalidate the legacy completion bit'
  );
  assert.match(
    setter,
    /putString\(KEY_LIFECYCLE_STATE, state\.name\)[\s\S]*\.commit\(\)/,
    'IN_PROGRESS/OFFERED/DEFERRED must be synchronously persisted for process-death recovery'
  );
  assert.doesNotMatch(
    setter,
    /\.apply\(\)/,
    'lifecycle transitions must not rely on asynchronous disk persistence'
  );
});

test('setup target persistence failures remain visible and retryable', () => {
  assert.match(
    activation,
    /var setupPersistenceError by remember \{[\s\S]*EXTRA_SETUP_PERSISTENCE_ERROR/,
    'the activation surface must retain a user-visible persistence failure state'
  );
  assert.match(
    activation,
    /if \(!setupWizard\.markAttemptedTarget\(setupTargetKey\)\) \{[\s\S]*setupPersistenceError = true[\s\S]*return\s*\}/,
    'a failed synchronous target write must block Android handoff and expose the failure'
  );
  assert.match(
    activation,
    /setupPersistenceError[\s\S]*Impossible d’enregistrer la progression[\s\S]*Réessayer l’enregistrement/,
    'the user must be told why the action did not start and be offered a retry'
  );
});

test('setup retry action launches the guarded step instead of ignoring a failed clear', () => {
  assert.match(
    activation,
    /onClick = \{ launchSetupStep\(setupStep\) \}/,
    'retry must use the same durable-target guard and launch path as the initial action'
  );
  assert.doesNotMatch(
    activation,
    /onClick = \{ setupWizard\.clearAttempted\(\); epoch\+\+ \}/,
    'retry must not silently ignore a failed preference clear'
  );
});

test('setup completion persistence failures stay visible instead of looping silently', () => {
  assert.match(
    activation,
    /PhoneCoreSetupWizardStore\.Step\.COMPLETE ->[\s\S]*if \(!setupWizard\.markCompleted\(\)\)[\s\S]*setupPersistenceError = true/,
    'the explicit completion retry must surface a failed durable completion write'
  );
  assert.match(
    activation,
    /if \(setupStep == PhoneCoreSetupWizardStore\.Step\.COMPLETE\) \{[\s\S]*if \(!setupWizard\.markCompleted\(\)\)[\s\S]*setupPersistenceError = true/,
    'automatic completion reconciliation must not swallow a failed commit'
  );
});

test('returning from setup does not swallow lifecycle persistence failures', () => {
  const callbackStart = main.indexOf('private val phoneCoreSetupLauncher');
  const callbackEnd = main.indexOf('\n\n    override fun onCreate', callbackStart);
  assert.ok(callbackStart >= 0 && callbackEnd > callbackStart, 'setup result callback must remain inspectable');
  const callback = main.slice(callbackStart, callbackEnd);
  assert.match(
    callback,
    /val persisted = if \(PhoneCoreSetupWizardStore\.softwarePrerequisitesReady\(runtimeFacts\)\) \{[\s\S]*?wizard\.markCompleted\(\)[\s\S]*?\} else \{[\s\S]*?wizard\.markDeferred\(\)/,
    'the callback must retain the durable lifecycle result'
  );
  assert.match(
    callback,
    /if \(!persisted\)[\s\S]*PhoneCoreActivationActivity\.EXTRA_SETUP_PERSISTENCE_ERROR/,
    'a failed callback write must return to a visible retry surface'
  );
});

test('initial setup lifecycle persistence failures reopen a visible retry surface', () => {
  assert.match(
    main,
    /if \(!wizard\.markOffered\(\)\) \{[\s\S]*launchPhoneCoreSetup\(persistenceError = true\)/,
    'a failed OFFERED write must route to the visible setup error surface'
  );
  assert.match(
    main,
    /if \(!wizard\.markInProgress\(\)\) \{[\s\S]*launchPhoneCoreSetup\(persistenceError = true\)/,
    'a failed IN_PROGRESS write must route to the visible setup error surface'
  );
});

test('setup persistence error blocks automatic Android handoff until explicit retry', () => {
  const effectStart = activation.indexOf('LaunchedEffect(firstRunSetup, setupTargetKey, attemptedSetupTargetKey)');
  const effectEnd = activation.indexOf('\n\n                Scaffold', effectStart);
  assert.ok(effectStart >= 0 && effectEnd > effectStart, 'setup auto-launch effect must remain inspectable');
  const effect = activation.slice(effectStart, effectEnd);
  assert.match(
    effect,
    /else if \([\s\S]*!setupPersistenceError[\s\S]*PhoneCoreSetupWizardStore\.shouldAutoLaunch/,
    'a persistence error must suppress automatic permission or role launch'
  );
});

test('contacts card does not expose a dead permission action before the dialer role exists', () => {
  const cardStart = activation.indexOf('Icons.Default.Contacts');
  const cardEnd = activation.indexOf('\n                        if (deniedPermissions.isNotEmpty())', cardStart);
  assert.ok(cardStart >= 0 && cardEnd > cardStart, 'contacts capability card must remain inspectable');
  const card = activation.slice(cardStart, cardEnd);
  assert.match(
    card,
    /if \(!state\.contactsPermission\) "Autoriser les contacts"[\s\S]*else if \(state\.dialerRole && !state\.callLogPermission\)/,
    'the action label must correspond to a permission that can actually be requested'
  );
  assert.doesNotMatch(
    card,
    /if \(!state\.contactsPermission \|\| !state\.callLogPermission\) "Autoriser les données locales"/,
    'a missing call-log permission must not expose an empty action while the dialer role is absent'
  );
});
