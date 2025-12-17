#!/usr/bin/env node

/**
 * QA Validation Script
 * 
 * Vérifie:
 * - Existence des pages clés
 * - Existence des assets critiques
 * - Liens internes valides
 * - Build réussi
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const COLORS = {
  reset: '\x1b[0m',
  green: '\x1b[32m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m'
};

function log(message, color = 'reset') {
  console.log(`${COLORS[color]}${message}${COLORS.reset}`);
}

function checkFileExists(filePath) {
  const fullPath = path.join(__dirname, '..', filePath);
  return fs.existsSync(fullPath);
}

function checkPagesExist() {
  log('\n📄 Vérification des pages clés...', 'blue');
  
  const keyPages = [
    'index.html',
    'public/soc-live/index.html',
    'public/threat-intelligence/index.html',
    'public/world-cyber-map/index.html',
    'public/phone-security/index.html',
    'public/reviews/index.html',
    'public/download/index.html',
    'public/system-status.html',
    'public/glossary.html',
    'public/faq.html',
    'public/usages-institutionnels.html',
    'public/institutional.html'
  ];
  
  let allPagesExist = true;
  
  keyPages.forEach(page => {
    const exists = checkFileExists(page);
    if (exists) {
      log(`  ✓ ${page}`, 'green');
    } else {
      log(`  ✗ ${page} - MANQUANT`, 'red');
      allPagesExist = false;
    }
  });
  
  return allPagesExist;
}

function checkAssetsExist() {
  log('\n🖼️  Vérification des assets critiques...', 'blue');
  
  const criticalAssets = [
    'assets/images/modules/soc-monitoring.svg',
    'assets/images/modules/defense-infrastructure.svg',
    'assets/images/modules/audit-analysis.svg',
    'assets/images/modules/ai-orchestration.svg',
    'assets/images/modules/compliance-governance.svg',
    'public/manifest.json',
    'public/shared-styles.css',
    'public/shared-navigation.js'
  ];
  
  let allAssetsExist = true;
  
  criticalAssets.forEach(asset => {
    const exists = checkFileExists(asset);
    if (exists) {
      log(`  ✓ ${asset}`, 'green');
    } else {
      log(`  ✗ ${asset} - MANQUANT`, 'red');
      allAssetsExist = false;
    }
  });
  
  return allAssetsExist;
}

function checkBrokenLinks() {
  log('\n🔗 Vérification des liens internes...', 'blue');
  
  const indexHtml = path.join(__dirname, '..', 'index.html');
  if (!fs.existsSync(indexHtml)) {
    log('  ✗ index.html non trouvé', 'red');
    return false;
  }
  
  const content = fs.readFileSync(indexHtml, 'utf-8');
  
  // Extract internal links
  const linkRegex = /href=["']([^"']+)["']/g;
  const links = [];
  let match;
  
  while ((match = linkRegex.exec(content)) !== null) {
    const link = match[1];
    // Only check internal links (not # anchors or external URLs)
    if (!link.startsWith('#') && !link.startsWith('http') && !link.startsWith('//')) {
      links.push(link);
    }
  }
  
  let allLinksValid = true;
  const checkedLinks = new Set();
  
  links.forEach(link => {
    if (checkedLinks.has(link)) return;
    checkedLinks.add(link);
    
    const exists = checkFileExists(link);
    if (exists) {
      log(`  ✓ ${link}`, 'green');
    } else {
      log(`  ✗ ${link} - 404`, 'red');
      allLinksValid = false;
    }
  });
  
  return allLinksValid;
}

function checkBuildSuccess() {
  log('\n🏗️  Vérification du build...', 'blue');
  
  try {
    // Check if node_modules exists
    const nodeModulesPath = path.join(__dirname, '..', 'node_modules');
    if (!fs.existsSync(nodeModulesPath)) {
      log('  ⚠️  node_modules non trouvé, installation des dépendances...', 'yellow');
      execSync('npm ci', { cwd: path.join(__dirname, '..'), stdio: 'inherit' });
    }
    
    // Run build
    log('  🔨 Exécution de npm run build...', 'blue');
    execSync('npm run build', { cwd: path.join(__dirname, '..'), stdio: 'pipe' });
    
    // Check if dist folder was created
    const distPath = path.join(__dirname, '..', 'dist');
    if (fs.existsSync(distPath)) {
      const distFiles = fs.readdirSync(distPath);
      log(`  ✓ Build réussi - ${distFiles.length} fichiers dans dist/`, 'green');
      return true;
    } else {
      log('  ✗ dist/ non créé après build', 'red');
      return false;
    }
  } catch (error) {
    log(`  ✗ Build échoué: ${error.message}`, 'red');
    return false;
  }
}

function checkNoEmojis() {
  log('\n😀 Vérification absence emojis dans contenus...', 'blue');
  
  const filesToCheck = [
    'public/soc-live/index.html',
    'public/threat-intelligence/index.html',
    'public/world-cyber-map/index.html',
    'public/phone-security/index.html'
  ];
  
  let noEmojis = true;
  
  // Emoji regex pattern
  const emojiRegex = /[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F1E0}-\u{1F1FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/u;
  
  filesToCheck.forEach(file => {
    const fullPath = path.join(__dirname, '..', file);
    if (fs.existsSync(fullPath)) {
      const content = fs.readFileSync(fullPath, 'utf-8');
      
      // Exclude specific allowed emojis in buttons (like download buttons)
      const contentWithoutButtons = content.replace(/<button[^>]*>[\s\S]*?<\/button>/g, '');
      
      if (emojiRegex.test(contentWithoutButtons)) {
        log(`  ✗ ${file} contient des emojis dans le contenu`, 'red');
        noEmojis = false;
      } else {
        log(`  ✓ ${file}`, 'green');
      }
    }
  });
  
  return noEmojis;
}

function runQA() {
  log('═══════════════════════════════════════════════════', 'blue');
  log('  🔍 QA Validation Script - Sentinel Quantum', 'blue');
  log('═══════════════════════════════════════════════════', 'blue');
  
  const results = {
    pages: checkPagesExist(),
    assets: checkAssetsExist(),
    links: checkBrokenLinks(),
    emojis: checkNoEmojis(),
    build: checkBuildSuccess()
  };
  
  log('\n═══════════════════════════════════════════════════', 'blue');
  log('  📊 Résumé', 'blue');
  log('═══════════════════════════════════════════════════', 'blue');
  
  const statusSymbol = (passed) => passed ? '✓' : '✗';
  const statusColor = (passed) => passed ? 'green' : 'red';
  
  log(`  ${statusSymbol(results.pages)} Pages clés`, statusColor(results.pages));
  log(`  ${statusSymbol(results.assets)} Assets critiques`, statusColor(results.assets));
  log(`  ${statusSymbol(results.links)} Liens internes`, statusColor(results.links));
  log(`  ${statusSymbol(results.emojis)} Absence emojis`, statusColor(results.emojis));
  log(`  ${statusSymbol(results.build)} Build`, statusColor(results.build));
  
  const allPassed = Object.values(results).every(r => r === true);
  
  if (allPassed) {
    log('\n✅ Tous les tests QA ont réussi!', 'green');
    process.exit(0);
  } else {
    log('\n❌ Certains tests QA ont échoué', 'red');
    process.exit(1);
  }
}

// Run QA
runQA();
