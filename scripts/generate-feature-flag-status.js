import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { getSystemStatus, verifyZeroTrustCompliance } from '../config/feature-flags.js';

export function buildFeatureFlagStatus() {
  const systemStatus = getSystemStatus();
  const compliance = verifyZeroTrustCompliance();
  return {
    schema_version: 1,
    generated_at: new Date().toISOString(),
    informational_only: true,
    system: systemStatus,
    governance: {
      compliant: compliance.compliant,
      risk_level: compliance.riskLevel,
      checks: compliance.checks
    }
  };
}

export function writeFeatureFlagStatus(outputPath) {
  mkdirSync(dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, `${JSON.stringify(buildFeatureFlagStatus(), null, 2)}\n`, 'utf8');
}

if (process.argv[1] && process.argv[1].endsWith('generate-feature-flag-status.js')) {
  const outputPath = process.argv[2];
  if (!outputPath) throw new Error('An output path is required');
  writeFeatureFlagStatus(outputPath);
}
