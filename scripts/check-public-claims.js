import fs from 'node:fs';
import path from 'node:path';

const PUBLIC_ROOT = path.resolve('public');

const RISKY_CLAIMS = [
  { label: 'live surveillance', pattern: /surveillance\s+(?:active\s+)?en\s+temps\s+réel/gi },
  { label: 'active protection', pattern: /protection\s+active/gi },
  { label: 'autonomous agents', pattern: /agents?\s+autonom(?:e|es)/gi },
  { label: 'real operational data', pattern: /(?:données|incidents|événements)\s+(?:réels?|réelle?s?)/gi },
  { label: 'functional SOC', pattern: /SOC\s+(?:live\s+)?fonctionnel/gi },
  { label: 'real cyber map', pattern: /Carte\s+Cyber\s+Mondiale\s+Réelle/gi }
];

const NEGATION_MARKERS = [
  'pas',
  "n'est pas",
  'ne fait pas',
  'aucun',
  'aucune',
  'aucuns',
  'aucunes',
  'non',
  'conceptuel',
  'conceptuels',
  'conceptuelle',
  'théorique',
  'théoriques',
  'simulation',
  'simulé',
  'simulée',
  'futur',
  'future',
  'non implémenté',
  'non implémentée',
  'limite',
  'limites'
];

function stripMarkup(html) {
  return html
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function hasNearbyNegation(text, matchIndex, matchLength) {
  const start = Math.max(0, matchIndex - 120);
  const end = Math.min(text.length, matchIndex + matchLength + 70);
  const context = text.slice(start, end).toLocaleLowerCase('fr-FR');
  return NEGATION_MARKERS.some((marker) => context.includes(marker));
}

export function findUnsupportedClaims(html) {
  const text = stripMarkup(html);
  const findings = [];

  for (const claim of RISKY_CLAIMS) {
    claim.pattern.lastIndex = 0;
    let match;
    while ((match = claim.pattern.exec(text)) !== null) {
      if (hasNearbyNegation(text, match.index, match[0].length)) continue;
      findings.push({
        label: claim.label,
        match: match[0],
        context: text.slice(Math.max(0, match.index - 90), Math.min(text.length, match.index + match[0].length + 140))
      });
    }
  }

  return findings;
}

function walkHtml(root) {
  const results = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) results.push(...walkHtml(fullPath));
    else if (entry.isFile() && entry.name.endsWith('.html')) results.push(fullPath);
  }
  return results;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const findings = [];
  for (const file of walkHtml(PUBLIC_ROOT)) {
    const content = fs.readFileSync(file, 'utf8');
    for (const finding of findUnsupportedClaims(content)) {
      findings.push({ file: path.relative(process.cwd(), file), ...finding });
    }
  }

  if (findings.length) {
    console.error('Unsupported public claims detected:');
    for (const finding of findings) {
      console.error(`- ${finding.file}: ${finding.label}: ${finding.match}`);
      console.error(`  ${finding.context}`);
    }
    process.exitCode = 1;
  } else {
    console.log('Public claim hygiene: OK');
  }
}
