import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const screen = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt',
  'utf8'
);

test('Bluetooth scan completion reports the results from the same scan', () => {
  const start = screen.indexOf('    fun runBluetoothScan()');
  const end = screen.indexOf('\n    val permissionLauncher', start);
  assert.ok(start >= 0 && end > start, 'Bluetooth scan function must remain inspectable');
  const scan = screen.slice(start, end);
  const finish = scan.slice(scan.indexOf('onScanFinished = {'));

  assert.match(
    scan,
    /var observedBluetoothDevices: List<DiscoveredBluetoothDevice> = emptyList\(\)/,
    'the scan needs a callback-local observation instead of stale Compose state'
  );
  assert.match(
    scan,
    /onResults = \{ results ->[\s\S]*observedBluetoothDevices = results[\s\S]*bluetoothDevices = results/,
    'the observed callback result must feed both the local status and the rendered state'
  );
  assert.match(
    finish,
    /observedBluetoothDevices\.isEmpty\(\)/,
    'completion status must use the result delivered by this scan'
  );
  assert.doesNotMatch(
    finish,
    /bluetoothDevices\.isEmpty\(\)/,
    'completion must not read a stale Compose snapshot'
  );
});
