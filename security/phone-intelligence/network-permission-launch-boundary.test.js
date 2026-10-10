import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const network = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt',
  'utf8'
);

test('network scan permission prompts fail visibly and stop scanning', () => {
  assert.match(
    network,
    /fun requestScanPermissions\(permissions: Array<String>\)[\s\S]*?permissionLauncher\.launch\(permissions\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'network permission prompts must contain OEM/framework failures'
  );
  assert.match(
    network,
    /requestScanPermissions\(wifiScanner\.requiredPermissions\)/,
    'Wi-Fi scans must use the guarded permission prompt'
  );
  assert.match(
    network,
    /requestScanPermissions\(bluetoothScanner\.requiredPermissions\)/,
    'Bluetooth scans must use the guarded permission prompt'
  );
  assert.match(
    network,
    /catch \(_: ActivityNotFoundException\)[\s\S]*?isScanning = false[\s\S]*?permissionDenied = true[\s\S]*?statusMessage = /,
    'permission prompt failures must leave a truthful stopped state'
  );
});
