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
