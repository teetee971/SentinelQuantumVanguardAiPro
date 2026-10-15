import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

test('UI consistency guard checks the canonical shared glass component source', () => {
  const source = fs.readFileSync(new URL('./check-android-ui-consistency.js', import.meta.url), 'utf8');

  assert.match(
    source,
    /SentinelGlassComponents\.kt/,
    'the guard must follow the canonical D.1 shared component file'
  );
});
