#!/usr/bin/env node

/**
 * Build script for Cloudflare Pages deployment.
 * Copies the validated static web surface to frontend/dist for deployment.
 * Requires Node.js 20.19.0+ as declared by package.json.
 */

import { cpSync, existsSync, mkdirSync, rmSync, readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

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

if (existsSync(outputDir)) {
  rmSync(outputDir, { recursive: true, force: true });
}

mkdirSync(outputDir, { recursive: true });

const filesToCopy = [
  { src: 'index.html', dest: 'index.html', required: true },
  { src: 'public', dest: 'public', required: true },
  { src: 'assets', dest: 'assets', required: false },
  { src: '_headers', dest: '_headers', required: true }
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

console.log(`Build summary: ${copiedCount} items copied, ${errorCount} errors.`);
console.log(`Output: frontend/dist`);
