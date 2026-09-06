import test from 'node:test';
import assert from 'node:assert/strict';
import {
  extractAndroidToolchain,
  normalizeOutdated,
} from './system-evolution-scout.js';

test('normalizes npm outdated results deterministically', () => {
  const result = normalizeOutdated({
    vite: { current: '7.3.6', wanted: '7.4.0', latest: '8.0.0', type: 'devDependencies' },
    alpha: { current: '1.0.0', wanted: '1.0.1', latest: '1.1.0', type: 'dependencies' },
  });

  assert.deepEqual(result, [
    { name: 'alpha', current: '1.0.0', wanted: '1.0.1', latest: '1.1.0', type: 'dependencies' },
    { name: 'vite', current: '7.3.6', wanted: '7.4.0', latest: '8.0.0', type: 'devDependencies' },
  ]);
});

test('treats malformed outdated input as no candidates', () => {
  assert.deepEqual(normalizeOutdated(null), []);
  assert.deepEqual(normalizeOutdated([]), []);
});

test('extracts pinned Android and Kotlin plugin versions', () => {
  const source = `plugins {
    id 'com.android.application' version '9.1.1' apply false
    id 'org.jetbrains.kotlin.plugin.compose' version '2.3.21' apply false
  }`;

  assert.deepEqual(extractAndroidToolchain(source), {
    android_gradle_plugin: '9.1.1',
    kotlin_compose_plugin: '2.3.21',
  });
});

test('does not invent Android toolchain versions', () => {
  assert.deepEqual(extractAndroidToolchain('plugins {}'), {
    android_gradle_plugin: null,
    kotlin_compose_plugin: null,
  });
});
