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
    /var setupPersistenceError by remember \{ mutableStateOf\(false\) \}/,
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
