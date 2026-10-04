#!/usr/bin/env node

/**
 * Build script for Cloudflare Pages deployment.
 * Regenerates the public-safe product truth snapshot and public capability
 * views, then copies the validated static web surface to frontend/dist.
 * Requires Node.js 20.19.0+ as declared by package.json.
 */

import { cpSync, existsSync, mkdirSync, rmSync, readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { writePublicProductTruth } from './generate-public-product-truth.js';
import { renderCapabilityConsumers } from './render-product-capabilities.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

const packageJson = JSON.parse(readFileSync(join(rootDir, 'package.json'), 'utf-8'));
const nodeRequirement = packageJson.engines?.node || '>=20.19.0';

const parseVersionPart = (part) => part !== undefined ? parseInt(part, 10) : 0;

const versionMatch = nodeRequirement.match(/^>=(\d+)(?:\.(\d+))?(?:\.(\d+))?$/);
if (!versionMatch) {
  console.error(`Error: Unsupported Node.js version format: ${nodeRequirement}`);
  console.error('Supported format: >=X.Y.Z');
  process.exit(1);
}

const reqMajor = parseVersionPart(versionMatch[1]);
const reqMinor = parseVersionPart(versionMatch[2]);
const reqPatch = parseVersionPart(versionMatch[3]);

const nodeVersion = process.versions.node;
const nodeParts = nodeVersion.split('.');
const curMajor = parseVersionPart(nodeParts[0]);
const curMinor = parseVersionPart(nodeParts[1]);
const curPatch = parseVersionPart(nodeParts[2]);

const isCompatible = (
  curMajor > reqMajor ||
  (curMajor === reqMajor && curMinor > reqMinor) ||
  (curMajor === reqMajor && curMinor === reqMinor && curPatch >= reqPatch)
);

if (!isCompatible) {
  const minVersion = `${reqMajor}.${reqMinor}.${reqPatch}`;
  console.error(`Error: Node.js ${minVersion}+ required, but you have ${nodeVersion}`);
  process.exit(1);
}

const outputDir = join(rootDir, 'frontend', 'dist');

console.log('Building static frontend for Cloudflare Pages...');
console.log('Regenerating public product truth from canonical registries...');
writePublicProductTruth();

console.log('Regenerating public capability views from canonical capability registry...');
const capabilityRegistry = JSON.parse(readFileSync(join(rootDir, 'config', 'product-capabilities.json'), 'utf8'));
const capabilityConsumers = renderCapabilityConsumers(capabilityRegistry, rootDir);
for (const [relativePath, content] of Object.entries(capabilityConsumers)) {
  if (!relativePath.startsWith('public/')) continue;
  const target = join(rootDir, relativePath);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, content, 'utf8');
}

if (existsSync(outputDir)) {
  rmSync(outputDir, { recursive: true, force: true });
}

mkdirSync(outputDir, { recursive: true });

/**
 * Files that Cloudflare Pages must see at the root of the published artifact
 * live at repository root and are copied directly to frontend/dist.
 *
 * Do not move robots.txt, sitemap.xml, _headers or _redirects under
 * frontend/dist/public: Cloudflare would then serve them below /public and
 * the root URLs would fall through to another route.
 */
const filesToCopy = [
  { src: 'index.html', dest: 'index.html', required: true },
  { src: 'robots.txt', dest: 'robots.txt', required: true },
  { src: 'sitemap.xml', dest: 'sitemap.xml', required: true },
  { src: '_headers', dest: '_headers', required: true },
  { src: '_redirects', dest: '_redirects', required: true },
  { src: 'public', dest: 'public', required: true },
  { src: 'assets', dest: 'assets', required: false }
];

let copiedCount = 0;
let errorCount = 0;

for (const { src, dest, required } of filesToCopy) {
  const srcPath = join(rootDir, src);
  const destPath = join(outputDir, dest);

  if (existsSync(srcPath)) {
    try {
      cpSync(srcPath, destPath, { recursive: true });
      console.log(`Copied: ${src}`);
      copiedCount++;
    } catch (error) {
      console.error(`Error copying ${src}: ${error.message}`);
      errorCount++;
      if (required) process.exit(1);
    }
  } else if (required) {
    console.error(`Required file/directory not found: ${src}`);
    errorCount++;
    process.exit(1);
  }
}

function injectTruthRuntimeIntoHtmlTree(directory) {
  const marker = 'data-sentinel-product-truth="build"';
  const runtimeTag = '<script defer src="/public/product-truth.js" data-sentinel-product-truth="build"></script>';
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const fullPath = join(directory, entry.name);
    if (entry.isDirectory()) {
      injectTruthRuntimeIntoHtmlTree(fullPath);
      continue;
    }
    if (!entry.isFile() || !entry.name.toLowerCase().endsWith('.html')) continue;
    const source = readFileSync(fullPath, 'utf8');
    if (source.includes(marker) || source.includes('src="/public/product-truth.js"') || source.includes('src="product-truth.js"')) continue;
    if (!/<\/body\s*>/i.test(source)) throw new Error(`HTML page has no closing body tag: ${fullPath}`);
    writeFileSync(fullPath, source.replace(/<\/body\s*>/i, `${runtimeTag}\n</body>`), 'utf8');
  }
}

injectTruthRuntimeIntoHtmlTree(outputDir);

const deploymentCommit = process.env.CF_PAGES_COMMIT_SHA || process.env.GITHUB_SHA || null;
writeFileSync(
  join(outputDir, 'deployment-meta.json'),
  `${JSON.stringify({ schema_version: 1, commit: deploymentCommit }, null, 2)}\n`,
  'utf8',
);
console.log(`Deployment marker: ${deploymentCommit ?? 'unbound-local-build'}`);

console.log(`Build summary: ${copiedCount} items copied, ${errorCount} errors.`);
console.log('Output: frontend/dist');
