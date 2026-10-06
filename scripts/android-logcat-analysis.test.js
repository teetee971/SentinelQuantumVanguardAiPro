import test from 'node:test';
import assert from 'node:assert/strict';
import { analyzeLogcat } from './android-logcat-analysis.cjs';

test('product background crash and system crash are attributed independently', () => {
  const log = 'I/ActivityManager( 10): Start proc 42:com.sentinel.quantum/u0a1\n' +
    'E/AndroidRuntime( 42): FATAL EXCEPTION: pool-1\nE/AndroidRuntime( 42): Process: com.sentinel.quantum, PID: 42\n';
  assert.equal(analyzeLogcat(log).result, 'FAIL');
  assert.equal(analyzeLogcat(log).failures[0].classification, 'PRODUCT');
  const system = 'E/AndroidRuntime( 7): FATAL EXCEPTION: main\nE/AndroidRuntime( 7): Process: com.android.launcher, PID: 7\n';
  assert.equal(analyzeLogcat(system).result, 'PASS');
  assert.equal(analyzeLogcat(system).findings[0].classification, 'SYSTEM');
});

for (const error of ['SecurityException', 'IllegalStateException', 'NullPointerException', 'NavigationException']) {
  test(`nonfatal ${error} in the product cannot pass`, () => {
    const report = analyzeLogcat(`I/ActivityManager( 1): Start proc 42:com.sentinel.quantum/u0a1\nE/AnyTag( 42): java.lang.${error}: broken\n`);
    assert.equal(report.result, 'FAIL');
  });
}
test('system exceptions stay visible without inheriting an interleaved product stack', () => {
  const report = analyzeLogcat('E/System( 7): java.lang.IllegalStateException\nE/App( 42): at com.sentinel.quantum.MainActivity\n');
  assert.equal(report.result, 'PASS');
  assert.equal(report.findings.length, 1);
});
test('an unrelated event from the same system PID cannot become an exception frame', () => {
  const report = analyzeLogcat('E/DeviceLockServiceImpl( 598): Caused by: java.lang.IllegalStateException: provision state 0\n' +
    'E/DeviceLockServiceImpl( 598): at android.devicelock.ParcelableException.readFromParcel(ParcelableException.java:39)\n' +
    'E/DeviceLockServiceImpl( 598): ... 27 more\n' +
    'I/ActivityTaskManager( 598): Displayed com.sentinel.quantum/.SentinelDialerActivity\n');
  assert.equal(report.result, 'PASS');
  assert.equal(report.findings[0].classification, 'SYSTEM');
  assert.equal(report.findings.length, 1);
});
test('a real product frame still attributes an exception without a process-start record', () => {
  const report = analyzeLogcat('10-06 14:34:09.578 598 601 E Telecom : java.lang.IllegalStateException: service failed\n' +
    '10-06 14:34:09.579 7 8 I Other : interleaved\n' +
    '10-06 14:34:09.580 598 601 E Telecom : at com.sentinel.quantum.SentinelInCallService.onCallAdded(Service.kt:10)\n');
  assert.equal(report.result, 'FAIL');
  assert.equal(report.failures[0].classification, 'PRODUCT');
});
test('product ANR and unexplained death fail; explicit force-stop remains documented', () => {
  assert.equal(analyzeLogcat('E/ActivityManager( 1): ANR in com.sentinel.quantum\n').result, 'FAIL');
  const death = 'I/ActivityManager( 1): Process com.sentinel.quantum (pid 42) has died: fg TOP\n';
  assert.equal(analyzeLogcat(death).result, 'FAIL');
  const report = analyzeLogcat('I/ActivityManager( 1): Killing 42:com.sentinel.quantum/u0a1 (adj 0): stop com.sentinel.quantum due to from pid 7\n' + death);
  assert.equal(report.result, 'PASS');
  assert.equal(report.findings[0].classification, 'EXPECTED_HARNESS_STOP');
});
test('empty logs and unattributed fatal crashes cannot prove health', () => {
  assert.equal(analyzeLogcat('').result, 'FAIL');
  assert.equal(analyzeLogcat('E/AndroidRuntime( 5): FATAL EXCEPTION: unknown\n').result, 'FAIL');
});
