const DEFAULT_KEYWORDS = Object.freeze([
  'cyber', 'sécurité', 'securite', 'vulnérabilité', 'vulnerabilite', 'entreprise', 'cadastre', 'téléphone', 'telephone', 'numérotation', 'numerotation', 'fraude', 'arnaque'
]);

function decodeXml(value = '') {
  return String(value)
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}

function pick(block, tag) {
  const match = block.match(new RegExp(`<${tag}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tag}>`, 'i'));
  return match ? decodeXml(match[1].replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').trim()) : '';
}

export function parseRecentAtom(xml) {
  if (typeof xml !== 'string' || !xml.includes('<feed')) return [];
  const entries = xml.match(/<entry\b[\s\S]*?<\/entry>/gi) || [];
  return entries.map((entry) => {
    const link = entry.match(/<link\b[^>]*href=["']([^"']+)["'][^>]*>/i)?.[1] || '';
    return {
      id: pick(entry, 'id'),
      title: pick(entry, 'title'),
      summary: pick(entry, 'summary') || pick(entry, 'content'),
      updated: pick(entry, 'updated') || pick(entry, 'published'),
      link
    };
  }).filter((entry) => entry.id || entry.link || entry.title);
}

export function isRelevantPublicDataset(entry, keywords = DEFAULT_KEYWORDS) {
  const haystack = `${entry?.title || ''} ${entry?.summary || ''}`.toLocaleLowerCase('fr');
  return keywords.some((keyword) => haystack.includes(String(keyword).toLocaleLowerCase('fr')));
}

export function selectRelevantRecentDatasets(xml, keywords = DEFAULT_KEYWORDS) {
  return parseRecentAtom(xml)
    .filter((entry) => isRelevantPublicDataset(entry, keywords))
    .sort((a, b) => String(b.updated).localeCompare(String(a.updated)));
}

export { DEFAULT_KEYWORDS };
