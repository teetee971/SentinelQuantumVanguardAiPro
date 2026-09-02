const fs = require('node:fs');
const path = require('node:path');

const file = path.join(process.cwd(), 'security-validation', 'scenarios.json');
const doc = JSON.parse(fs.readFileSync(file, 'utf8'));

const required = ['id', 'category', 'name', 'input', 'expected', 'severity'];
const allowedSeverity = new Set(['low', 'medium', 'high', 'critical']);
const ids = new Set();
const failures = [];

if (doc.mode !== 'authorized-lab-only') failures.push('mode must be authorized-lab-only');
for (const [key, value] of Object.entries(doc.safety || {})) {
  if (value !== false) failures.push(`safety.${key} must be false`);
}
if (!Array.isArray(doc.scenarios) || doc.scenarios.length === 0) {
  failures.push('scenarios must be a non-empty array');
} else {
  for (const scenario of doc.scenarios) {
    for (const key of required) {
      if (typeof scenario[key] !== 'string' || scenario[key].length === 0) {
        failures.push(`${scenario.id || '<unknown>'}: missing ${key}`);
      }
    }
    if (ids.has(scenario.id)) failures.push(`duplicate scenario id: ${scenario.id}`);
    ids.add(scenario.id);
    if (!allowedSeverity.has(scenario.severity)) failures.push(`${scenario.id}: invalid severity`);
  }
}

const acceptance = doc.acceptance || {};
if (acceptance.crash_count_max !== 0) failures.push('crash_count_max must be 0');
if (acceptance.external_target_access !== false) failures.push('external_target_access must be false');
if (acceptance.real_malware_execution !== false) failures.push('real_malware_execution must be false');

if (failures.length) {
  console.error('SECURITY VALIDATION CATALOG: FAIL');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`SECURITY VALIDATION CATALOG: PASS (${doc.scenarios.length} scenarios)`);
