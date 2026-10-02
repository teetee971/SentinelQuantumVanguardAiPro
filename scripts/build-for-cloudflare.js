#!/usr/bin/env node

/**
 * Build script for the public Cloudflare Pages artifact.
 *
 * Security boundary:
 * - copies only the client-facing static surface;
 * - excludes test/spec/source-map artifacts from deployment;
 * - publishes an artifact-content fingerprint, never a Git commit SHA.
 */

import {
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync
} from 'fs';
import { createHash } from 'crypto';
import { basename, dirname, join, relative } from 'path';
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
  process.exit(1);
}

const reqMajor = parseVersionPart(versionMatch[1]);
const reqMinor = parseVersionPart(versionMatch[2]);
const reqPatch = parseVersionPart(versionMatch[3]);
const [curMajor, curMinor, curPatch] = process.versions.node.split('.').map(parseVersionPart);

const isCompatible = (
  curMajor > reqMajor ||
  (curMajor === reqMajor && curMinor > reqMinor) ||
  (curMajor === reqMajor && curMinor === reqMinor && curPatch >= reqPatch)
);

if (!isCompatible) {
  console.error(`Error: Node.js ${reqMajor}.${reqMinor}.${reqPatch}+ required, but you have ${process.versions.node}`);
  process.exit(1);
}

const outputDir = join(rootDir, 'frontend', 'dist');

function isDeployableSource(path) {
  const name = basename(path);
  if (name === '.DS_Store') return false;
  if (/\.(?:test|spec)\.[cm]?[jt]sx?$/iu.test(name)) return false;
  if (/\.map$/iu.test(name)) return false;
  if (/(?:^|[\\/])(?:__tests__|test-fixtures|fixtures)(?:[\\/]|$)/iu.test(path)) return false;
  return true;
}

function collectFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...collectFiles(path));
    else if (entry.isFile()) files.push(path);
  }
  return files;
}

function artifactFingerprint(dir) {
  const hash = createHash('sha256');
  const files = collectFiles(dir)
    .filter((path) => basename(path) !== 'deployment-meta.json')
    .sort((a, b) => relative(dir, a).localeCompare(relative(dir, b)));

  for (const path of files) {
    const rel = relative(dir, path).replaceAll('\\', '/');
    const stats = statSync(path);
    hash.update(rel, 'utf8');
    hash.update('\0');
    hash.update(String(stats.size), 'utf8');
    hash.update('\0');
    hash.update(readFileSync(path));
    hash.update('\0');
  }
  return hash.digest('hex');
}

console.log('Building public static artifact...');

if (existsSync(outputDir)) {
  rmSync(outputDir, { recursive: true, force: true });
}
mkdirSync(outputDir, { recursive: true });

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
      cpSync(srcPath, destPath, {
        recursive: true,
        filter: isDeployableSource
      });
      console.log(`Copied deployable surface: ${src}`);
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

const fingerprint = artifactFingerprint(outputDir);
writeFileSync(
  join(outputDir, 'deployment-meta.json'),
  `${JSON.stringify({
    schema_version: 2,
    artifact_fingerprint: fingerprint
  }, null, 2)}\n`,
  'utf8'
);

console.log(`Public artifact fingerprint: ${fingerprint}`);
console.log(`Build summary: ${copiedCount} items copied, ${errorCount} errors.`);
console.log('Output: frontend/dist');
