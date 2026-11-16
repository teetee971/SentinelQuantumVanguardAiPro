import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const rowSource = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/PhoneCoreWorkspace.kt',
  'utf8'
);
const composeSource = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);

test('conversation swipe exposes delete only from end to start and never accepts dismissal', () => {
  assert.match(rowSource, /rememberSwipeToDismissBoxState/);
  assert.match(rowSource, /SwipeToDismissBoxValue\.EndToStart/);
  assert.match(rowSource, /enableDismissFromStartToEnd\s*=\s*false/);
  assert.match(rowSource, /enableDismissFromEndToStart\s*=\s*true/);
  assert.match(
    rowSource,
    /if \(value == SwipeToDismissBoxValue\.EndToStart\)[\s\S]{0,500}onDelete\(\)[\s\S]{0,500}\n\s*}\n\s*false/,
    'swipe must request confirmation but reject the dismiss transition'
  );
});

test('swipe delete still lands on explicit confirm/cancel before provider deletion', () => {
  assert.match(composeSource, /pendingDeleteThread = thread/);
  assert.match(composeSource, /Text\("Supprimer cette conversation \?"/);
  assert.match(composeSource, /Text\("Annuler"\)/);
  assert.match(composeSource, /conversations\.deleteThread\(pending\.threadId\)/);
  assert.doesNotMatch(
    rowSource,
    /deleteThread\(/,
    'presentation row must never call the SMS provider deletion API directly'
  );
});
