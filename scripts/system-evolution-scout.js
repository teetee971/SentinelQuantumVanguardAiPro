#!/usr/bin/env node

import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
} from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { classifyModuleUsage, rootDir } from './report-module-usage.js';
import { validateInventory } from './check-module-inventory.js';

export function normalizeOutdated(report) {
  if (!report || typeof report !== 'object' || Array.isArray(report)) return [];

  return Object.entries(report)
    .map(([name, value]) => ({
      name,
      current: typeof value?.current === 'string' ? value.current : null,
      wanted: typeof value?.wanted === 'string' ? value.wanted : null,
      latest: typeof value?.latest === 'string' ? value.latest : null,
      type: typeof value?.type === 'string' ? value.type : null,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

export function extractAndroidToolchain(buildGradleSource) {
  const source = typeof buildGradleSource === 'string' ? buildGradleSource : '';
  const pluginVersion = (pluginId) => {
    const escaped = pluginId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const match = source.match(new RegExp(`id\\s+['\"]${escaped}['\"]\\s+version\\s+['\"]([^'\"]+)['\"]`));
    return match?.[1] ?? null;
  };

  return {
    android_gradle_plugin: pluginVersion('com.android.application'),
    kotlin_compose_plugin: pluginVersion('org.jetbrains.kotlin.plugin.compose'),
  };
}

function countModuleClassifications(records) {
  const counts = { ENTRYPOINT: 0, IMPORTED: 0, ORPHAN_CANDIDATE: 0 };
  for (const record of records) {
    if (Object.hasOwn(counts, record.classification)) counts[record.classification] += 1;
  }
  return counts;
}

function loadJsonFile(path, fallback = {}) {
  if (!path || !existsSync(path)) return fallback;
  return JSON.parse(readFileSync(path, 'utf8'));
}

export function buildEvolutionReport(baseDir = rootDir, options = {}) {
  const generatedAt = options.generatedAt ?? new Date().toISOString();
  const packageJson = JSON.parse(readFileSync(join(baseDir, 'package.json'), 'utf8'));
  const nodePin = readFileSync(join(baseDir, '.node-version'), 'utf8').trim();
  const androidGradlePath = join(baseDir, 'native-android-app', 'build.gradle');
  const androidToolchain = extractAndroidToolchain(
    existsSync(androidGradlePath) ? readFileSync(androidGradlePath, 'utf8') : '',
  );
  const outdated = normalizeOutdated(options.outdatedReport ?? {});
  const moduleRecords = classifyModuleUsage(baseDir);
  const moduleCounts = countModuleClassifications(moduleRecords);
  const inventory = validateInventory(baseDir);

  const recommendations = [];
  if (outdated.length > 0) {
    recommendations.push({
      kind: 'DEPENDENCY_UPDATE',
      count: outdated.length,
      action: 'Review and validate dependency update PRs; never bypass CI or security gates.',
    });
  }
  if (moduleCounts.ORPHAN_CANDIDATE > 0) {
    recommendations.push({
      kind: 'MODULE_CONTINUITY_REVIEW',
      count: moduleCounts.ORPHAN_CANDIDATE,
      action: 'Keep orphan candidates explicitly classified; never auto-delete modules from this report.',
    });
  }
  if (inventory.errors.length > 0) {
    recommendations.push({
      kind: 'MODULE_INVENTORY_DRIFT',
      count: inventory.errors.length,
      action: 'Resolve inventory drift before treating structural evolution evidence as valid.',
    });
  }

  return {
    schema_version: 1,
    generated_at: generatedAt,
    project: {
      name: packageJson.name ?? null,
      version: packageJson.version ?? null,
      node_pin: nodePin,
      npm_runtime_engine: packageJson.engines?.node ?? null,
    },
    dependency_updates: {
      npm: outdated,
      discovery_note: 'npm candidates are discovered by the scheduled workflow; GitHub Actions and Gradle updates are delegated to Dependabot PRs.',
    },
    android_toolchain: androidToolchain,
    modules: {
      total: moduleRecords.length,
      entrypoints: moduleCounts.ENTRYPOINT,
      imported: moduleCounts.IMPORTED,
      orphan_candidates: moduleCounts.ORPHAN_CANDIDATE,
      inventory_entries: inventory.inventoryCount,
      inventory_errors: inventory.errors,
    },
    recommendations,
    guardrails: {
      modifies_source: false,
      deletes_modules: false,
      merges_pull_requests: false,
      deploys_releases: false,
      weakens_security_controls: false,
      purpose: 'Observe, classify and report evolution candidates for independently validated changes.',
    },
  };
}

export function renderMarkdown(report) {
  const lines = [
    '# Sentinel System Evolution Scout',
    '',
    `Generated: ${report.generated_at}`,
    `Project: ${report.project.name}@${report.project.version}`,
    `Node pin: ${report.project.node_pin}`,
    '',
    '## Update candidates',
    '',
    `npm candidates: ${report.dependency_updates.npm.length}`,
  ];

  for (const candidate of report.dependency_updates.npm) {
    lines.push(`- ${candidate.name}: ${candidate.current ?? 'unknown'} -> wanted ${candidate.wanted ?? 'unknown'} / latest ${candidate.latest ?? 'unknown'}`);
  }

  lines.push(
    '',
    '## Module continuity',
    '',
    `- total: ${report.modules.total}`,
    `- entrypoints: ${report.modules.entrypoints}`,
    `- imported: ${report.modules.imported}`,
    `- orphan candidates: ${report.modules.orphan_candidates}`,
    `- inventory entries: ${report.modules.inventory_entries}`,
    `- inventory errors: ${report.modules.inventory_errors.length}`,
    '',
    '## Android toolchain observed',
    '',
    `- Android Gradle Plugin: ${report.android_toolchain.android_gradle_plugin ?? 'not detected'}`,
    `- Kotlin Compose plugin: ${report.android_toolchain.kotlin_compose_plugin ?? 'not detected'}`,
    '',
    '## Guardrail',
    '',
    'This scout is observation-only. It does not edit source, delete modules, merge pull requests, deploy releases, or weaken security controls.',
    '',
  );

  return `${lines.join('\n')}\n`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const outdatedFile = process.env.SENTINEL_NPM_OUTDATED_FILE;
    const outdatedReport = outdatedFile ? loadJsonFile(outdatedFile, {}) : {};
    const report = buildEvolutionReport(rootDir, { outdatedReport });
    const outputDir = join(rootDir, 'artifacts', 'system-evolution');
    mkdirSync(outputDir, { recursive: true });
    writeFileSync(join(outputDir, 'report.json'), `${JSON.stringify(report, null, 2)}\n`);
    writeFileSync(join(outputDir, 'summary.md'), renderMarkdown(report));

    console.log(renderMarkdown(report));

    if (report.modules.inventory_errors.length > 0) {
      console.error('System evolution scout blocked: module inventory drift detected.');
      process.exit(1);
    }
  } catch (error) {
    console.error(`System evolution scout failed: ${error.message}`);
    process.exit(1);
  }
}
