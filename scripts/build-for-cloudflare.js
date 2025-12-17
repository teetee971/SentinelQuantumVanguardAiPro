#!/usr/bin/env node

/**
 * Build script for Cloudflare Pages deployment
 * Copies static files to frontend/dist directory as expected by Cloudflare Pages
 * Requires Node.js 18.0.0+ (matches package.json engines requirement)
 */

import { cpSync, existsSync, mkdirSync, rmSync, readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');

// Check Node.js version (read requirement from package.json)
const packageJson = JSON.parse(readFileSync(join(rootDir, 'package.json'), 'utf-8'));
const requiredVersion = packageJson.engines?.node?.replace('>=', '') || '18.0.0';
const [requiredMajor] = requiredVersion.split('.').map(Number);
const nodeVersion = process.versions.node;
const [major] = nodeVersion.split('.').map(Number);

if (major < requiredMajor) {
  console.error(`❌ Error: Node.js ${requiredVersion}+ required, but you have ${nodeVersion}`);
  console.error('Please upgrade Node.js: https://nodejs.org/');
  process.exit(1);
}

const outputDir = join(rootDir, 'frontend', 'dist');

console.log('🚀 Building for Cloudflare Pages...\n');

// Clean output directory
if (existsSync(outputDir)) {
  console.log('🧹 Cleaning output directory...');
  rmSync(outputDir, { recursive: true, force: true });
}

// Create output directory
console.log('📁 Creating output directory: frontend/dist');
mkdirSync(outputDir, { recursive: true });

// Copy files and directories
const filesToCopy = [
  { src: 'index.html', dest: 'index.html', required: true },
  { src: 'public', dest: 'public', required: true },
  { src: 'assets', dest: 'assets', required: false },
  { src: 'cinematic-mode.css', dest: 'cinematic-mode.css', required: false },
  { src: 'cinematic-mode.js', dest: 'cinematic-mode.js', required: false },
];

let copiedCount = 0;
let errorCount = 0;

filesToCopy.forEach(({ src, dest, required }) => {
  const srcPath = join(rootDir, src);
  const destPath = join(outputDir, dest);

  if (existsSync(srcPath)) {
    try {
      cpSync(srcPath, destPath, { recursive: true });
      console.log(`✅ Copied: ${src}`);
      copiedCount++;
    } catch (error) {
      console.error(`❌ Error copying ${src}:`, error.message);
      errorCount++;
      if (required) process.exit(1);
    }
  } else if (required) {
    console.error(`❌ Required file/directory not found: ${src}`);
    errorCount++;
    process.exit(1);
  } else {
    console.log(`⚠️ Optional file not found (skipped): ${src}`);
  }
});

console.log('\n📊 Build Summary:');
console.log(`   ✅ ${copiedCount} items copied`);
console.log(`   ❌ ${errorCount} errors`);
console.log(`   📂 Output: frontend/dist`);
console.log('\n✨ Build complete!\n');
