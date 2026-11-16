const STOPWORDS = new Set([
  'the', 'and', 'that', 'this', 'with', 'from', 'pour', 'dans', 'avec', 'une', 'des', 'les', 'sur', 'que',
  'est', 'sont', 'qui', 'mais', 'los', 'las', 'una', 'con', 'por', 'que', 'para', 'und', 'der', 'die', 'das'
]);

function normalizeText(text) {
  return String(text || '')
    .normalize('NFKC')
    .toLowerCase()
    .replace(/https?:\/\/\S+/g, ' URL ')
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function tokenize(text) {
  return normalizeText(text).split(' ').filter((token) => token.length >= 4 && !STOPWORDS.has(token));
}

function fingerprint(text) {
  const tokens = [...new Set(tokenize(text))].sort();
  return tokens.slice(0, 120).join('|');
}

function similarity(a, b) {
  const A = new Set(tokenize(a));
  const B = new Set(tokenize(b));
  if (!A.size || !B.size) return 0;
  let intersection = 0;
  for (const token of A) if (B.has(token)) intersection += 1;
  return Number((intersection / new Set([...A, ...B]).size).toFixed(4));
}

function extractNarrative(text, language = 'und') {
  const tokens = tokenize(text);
  const frequencies = new Map();
  for (const token of tokens) frequencies.set(token, (frequencies.get(token) || 0) + 1);
  const markers = [...frequencies.entries()]
    .filter(([, count]) => count >= 2)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 20)
    .map(([token]) => token);

  return {
    language,
    normalized_length: normalizeText(text).length,
    fingerprint: fingerprint(text),
    markers
  };
}

export { normalizeText, tokenize, fingerprint, similarity, extractNarrative };
