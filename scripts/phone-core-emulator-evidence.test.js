// Keep the historical evidence regressions and the hardened SEND_SMS collector/oracle
// regressions under the single entry point executed by the emulator host gate.
import './phone-core-emulator-evidence-base.test.js';
import './phone-core-emulator-schema3-evidence.test.js';
import './phone-core-emulator-revocation-hardening.test.js';
