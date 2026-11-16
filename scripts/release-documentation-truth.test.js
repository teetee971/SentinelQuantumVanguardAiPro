import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const read = (path) => readFileSync(resolve(path), 'utf8');

function extract(pattern, text, label) {
  const value = text.match(pattern)?.[1];
  assert.ok(value, `missing source value: ${label}`);
  return value;
}

test('release documentation follows the executable Android build baseline', () => {
  const rootGradle = read('native-android-app/build.gradle');
  const appGradle = read('native-android-app/app/build.gradle');
  const wrapper = read('native-android-app/gradle/wrapper/gradle-wrapper.properties');

  const agp = extract(/com\.android\.application' version '([^']+)'/, rootGradle, 'AGP');
  const kotlin = extract(/org\.jetbrains\.kotlin\.plugin\.compose' version '([^']+)'/, rootGradle, 'Kotlin');
  const gradle = extract(/gradle-([0-9.]+)-bin\.zip/, wrapper, 'Gradle');
  const compileSdk = extract(/compileSdk\s+(\d+)/, appGradle, 'compileSdk');
  const targetSdk = extract(/targetSdk\s+(\d+)/, appGradle, 'targetSdk');
  const minSdk = extract(/minSdk\s+(\d+)/, appGradle, 'minSdk');
  const versionCode = extract(/versionCode\s+(\d+)/, appGradle, 'versionCode');
  const versionName = extract(/versionName\s+"([^"]+)"/, appGradle, 'versionName');

  const paths = [
    'AUDIT.md',
    'CI_VALIDATION_CHECKLIST.md',
    'GRADLE_BUILD_AUDIT.md',
    'RELEASE_CHECKLIST.md',
    'RELEASE_STATUS.md',
    'docs/PRODUCTION_RELEASE_GUIDE.md',
    'docs/WORKFLOWS.md',
    'native-android-app/BUILD_GUIDE.md',
    'native-android-app/README.md',
  ];
  const baselineDocs = paths.map((path) => [path, read(path)]);

  for (const [path, source] of baselineDocs) {
    assert.doesNotMatch(source, /Gradle 9\.7\.1/, `${path} still claims Gradle 9.7.1`);
    assert.doesNotMatch(source, /AGP 9\.4\.0/, `${path} still claims AGP 9.4.0`);
    assert.doesNotMatch(source, /SDK 37\/36\/23/, `${path} still claims minSdk 23`);
  }

  const joined = baselineDocs.map(([, source]) => source).join('\n');
  for (const expected of [
    `AGP ${agp}`,
    `Gradle ${gradle}`,
    `minSdk ${minSdk}`,
    `versionCode ${versionCode}`,
    `versionName ${versionName}`,
    `compileSdk ${compileSdk}`,
    `targetSdk ${targetSdk}`,
  ]) {
    assert.ok(joined.includes(expected), `release docs missing current source value: ${expected}`);
  }
  assert.ok(
    joined.includes(`Kotlin ${kotlin}`) || joined.includes(`Kotlin Compose ${kotlin}`),
    'release docs missing current Kotlin version'
  );
});

test('release documentation preserves evidence boundaries and historical PR truth', () => {
  const audit = read('AUDIT.md');
  const checklist = read('CI_VALIDATION_CHECKLIST.md');
  const releaseChecklist = read('RELEASE_CHECKLIST.md');
  const guide = read('docs/PRODUCTION_RELEASE_GUIDE.md');

  assert.doesNotMatch(audit, /PR #216 reste ouverte|PR #216.*non fusionnable/i);
  assert.match(audit, /PR #216 est fermée/);
  assert.match(audit, /PR #223, fusionnée/);

  for (const source of [checklist, releaseChecklist, guide]) {
    assert.match(source, /release-dependencies\.json/);
    assert.match(source, /release-evidence\.json/);
  }

  assert.match(checklist, /code présent ≠ build réussi ≠ release signée ≠ test physique ≠ publication validée/);
  assert.match(guide, /ne constitue pas à lui seul une preuve de validité/);
});
