import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root = 'native-android-app/app/src/main/java/com/sentinel/quantum/';
const read = path => readFileSync(root + path, 'utf8');

test('the commercial graph exposes only Phone Core and local privacy routes', () => {
  const graph = read('navigation/CommercialPhoneCoreNavGraph.kt');
  const routes = [...graph.matchAll(/composable\(Screen\.(\w+)\.route\)/g)].map(match => match[1]);
  assert.deepEqual(routes.sort(), [
    'CallBlocking', 'CallFilterHistory', 'CommunicationsHub', 'Home',
    'LocalLogs', 'PhoneLists', 'PhoneSecurity', 'Search', 'Settings'
  ]);
  const main = read('MainActivity.kt');
  assert.match(main, /if \(commercialV1\)\s*\{\s*CommercialPhoneCoreNavGraph\(/);
});

test('the commercial launcher ignores legacy share and optional notification destinations', () => {
  const main = read('MainActivity.kt');
  assert.match(main, /if \(!commercialV1\)\s*\{\s*handleShareIntent\(intent\)/);
  assert.match(main, /val startDestination = when \{\s*commercialV1 -> Screen.Home.route/);
  assert.match(main, /LaunchedEffect\(Unit\) \{\s*if \(commercialV1\) return@LaunchedEffect/);
});

test('retained optional WorkManager jobs exit before touching their inputs in V1', () => {
  assert.match(read('background/OsintRefreshWorker.kt'), /doWork\(\): Result \{\s*if \(BuildConfig.COMMERCIAL_PHONE_CORE_V1\) return Result.success\(\)/);
  assert.match(read('background/CollectiveDefenseWatchWorker.kt'), /withContext\(Dispatchers.IO\) \{\s*if \(BuildConfig.COMMERCIAL_PHONE_CORE_V1\) return@withContext Result.success\(\)/);
  assert.match(read('background/TrackerWatch.kt'), /doWork\(\): Result \{\s*if \(BuildConfig.COMMERCIAL_PHONE_CORE_V1\) return Result.success\(\)/);
  assert.match(read('SentinelApplication.kt'), /if \(!BuildConfig.COMMERCIAL_PHONE_CORE_V1\) launchWearableObservation\(\)/);
});

test('retained reputation preferences cannot enable commercial egress', () => {
  const store = read('data/SettingsStore.kt');
  assert.match(store, /fun isRuleSyncEnabled\(\): Boolean =\s*!BuildConfig.COMMERCIAL_PHONE_CORE_V1/);
  assert.match(store, /var protectionMode: ProtectionMode\s*get\(\) \{\s*if \(BuildConfig.COMMERCIAL_PHONE_CORE_V1\) return ProtectionMode.LOCAL_ONLY/);
  assert.match(store, /var callerReputationEnrichmentEnabled: Boolean\s*get\(\) =\s*!BuildConfig.COMMERCIAL_PHONE_CORE_V1/);
});
