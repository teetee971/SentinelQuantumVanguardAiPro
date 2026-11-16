const FSMA_SOURCE_ID = 'fsma-belgium-warnings';
const FSMA_LIST_URL = 'https://www.fsma.be/en/warnings/companies-operating-unlawfully-in-belgium';
const FSMA_EXPORT_URL = 'https://www.fsma.be/en/warnings/de-companies-operating-unlawfully-in-belgium?_format=csv&order=field_ct_date_time&page=&sort=desc';
const FSMA_LICENSE_URL = 'https://www.fsma.be/en/disclaimer-copyright';
const MAX_CSV_BYTES = 4 * 1024 * 1024;
const MAX_ROWS = 10_000;
const MAX_COLUMNS = 16;
const MAX_FIELD_CHARS = 64 * 1024;
const MAX_DOMAINS_PER_WARNING = 256;

function boundedText(value, max) {
  if (typeof value !== 'string') return '';
  return value.replace(/\u0000/g, '').trim().slice(0, max);
}

function parseDelimitedCsv(raw, { delimiter = ';' } = {}) {
  if (typeof raw !== 'string') throw new Error('FSMA_CSV_REQUIRED');
  if (Buffer.byteLength(raw, 'utf8') > MAX_CSV_BYTES) throw new Error('FSMA_CSV_TOO_LARGE');
  if (delimiter.length !== 1) throw new Error('FSMA_CSV_DELIMITER_INVALID');

  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;

  const pushField = () => {
    if (field.length > MAX_FIELD_CHARS) throw new Error('FSMA_CSV_FIELD_TOO_LARGE');
    row.push(field);
    field = '';
    if (row.length > MAX_COLUMNS) throw new Error('FSMA_CSV_TOO_MANY_COLUMNS');
  };
  const pushRow = () => {
    pushField();
    if (row.some(value => value.length > 0)) rows.push(row);
    row = [];
    if (rows.length > MAX_ROWS) throw new Error('FSMA_CSV_TOO_MANY_ROWS');
  };

  for (let index = 0; index < raw.length; index += 1) {
    const char = raw[index];
    if (quoted) {
      if (char === '"') {
        if (raw[index + 1] === '"') {
          field += '"';
          index += 1;
        } else {
          quoted = false;
        }
      } else {
        field += char;
      }
      continue;
    }

    if (char === '"') {
      if (field.length !== 0) throw new Error('FSMA_CSV_QUOTE_INVALID');
      quoted = true;
    } else if (char === delimiter) {
      pushField();
    } else if (char === '\n') {
      pushRow();
    } else if (char === '\r') {
      if (raw[index + 1] === '\n') index += 1;
      pushRow();
    } else {
      field += char;
    }
  }

  if (quoted) throw new Error('FSMA_CSV_UNTERMINATED_QUOTE');
  if (field.length > 0 || row.length > 0) pushRow();
  return rows;
}

function normalizedHeader(value) {
  return boundedText(value, 120)
    .normalize('NFKD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

const HEADER_ALIASES = Object.freeze({
  title: new Set(['title', 'titre', 'titel']),
  company: new Set(['company name', 'nom de la societe', 'naam van de vennootschap']),
  website: new Set(['website', 'site web']),
  fraudType: new Set(['type of fraude', 'type of fraud', 'type de fraude', 'type fraude']),
  date: new Set(['date', 'datum']),
});

function mapHeaders(headerRow) {
  if (!Array.isArray(headerRow)) throw new Error('FSMA_HEADER_REQUIRED');
  const normalized = headerRow.map(normalizedHeader);
  const result = {};
  for (const [field, aliases] of Object.entries(HEADER_ALIASES)) {
    const index = normalized.findIndex(value => aliases.has(value));
    if (index < 0) throw new Error('FSMA_HEADER_MISSING_' + field.toUpperCase());
    result[field] = index;
  }
  return result;
}

function normalizeWarningDate(raw) {
  const value = boundedText(raw, 32);
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) return null;
  const day = Number(match[1]);
  const month = Number(match[2]);
  const year = Number(match[3]);
  const candidate = new Date(Date.UTC(year, month - 1, day));
  if (
    candidate.getUTCFullYear() !== year ||
    candidate.getUTCMonth() !== month - 1 ||
    candidate.getUTCDate() !== day
  ) return null;
  return candidate.toISOString().slice(0, 10);
}

function normalizeDomain(raw) {
  if (typeof raw !== 'string') return null;
  let candidate = raw.trim()
    .replace(/[\u2010\u2011\u2012\u2013\u2014\u2212]/g, '-')
    .replace(/^https?:\/\/\s+/i, match => match.replace(/\s+/g, ''))
    .replace(/^[("'\[]+/, '')
    .replace(/[)"'\],.;]+$/, '');
  if (!candidate) return null;
  if (!/^https?:\/\//i.test(candidate)) candidate = 'https://' + candidate;

  let url;
  try {
    url = new URL(candidate);
  } catch {
    return null;
  }
  if (!['http:', 'https:'].includes(url.protocol)) return null;
  if (url.username || url.password || url.port) return null;
  let host = url.hostname.toLowerCase().replace(/^www\./, '').replace(/\.$/, '');
  if (!host || host.length > 253 || !host.includes('.')) return null;
  if (!/^[a-z0-9.-]+$/.test(host)) return null;
  if (host.split('.').some(label => !label || label.length > 63 || label.startsWith('-') || label.endsWith('-'))) {
    return null;
  }
  return host;
}

function extractDomains(websiteField) {
  const raw = boundedText(websiteField, MAX_FIELD_CHARS)
    .replace(/https?:\/\/\s+/gi, match => match.replace(/\s+/g, ''));
  if (!raw) return [];

  const candidates = raw
    .split(/[\n,;]+/)
    .flatMap(value => value.trim().split(/\s+(?=https?:\/\/|www\.)/i))
    .map(value => value.trim())
    .filter(Boolean);

  const domains = [];
  const seen = new Set();
  for (const value of candidates) {
    if (domains.length >= MAX_DOMAINS_PER_WARNING) break;
    const domain = normalizeDomain(value);
    if (!domain || seen.has(domain)) continue;
    seen.add(domain);
    domains.push(domain);
  }
  return domains.sort();
}

function normalizeFsmaCsv(raw) {
  const rows = parseDelimitedCsv(raw);
  if (rows.length < 1) throw new Error('FSMA_CSV_EMPTY');
  const header = mapHeaders(rows[0]);

  const warnings = [];
  for (const row of rows.slice(1)) {
    const warningDate = normalizeWarningDate(row[header.date]);
    if (!warningDate) continue;
    const domains = extractDomains(row[header.website]);
    warnings.push(Object.freeze({
      title: boundedText(row[header.title], 500),
      company: boundedText(row[header.company], 1000),
      fraudType: boundedText(row[header.fraudType], 160),
      warningDate,
      domains: Object.freeze(domains),
      sourceId: FSMA_SOURCE_ID,
      sourceUrl: FSMA_LIST_URL,
      license: 'CC BY 4.0',
      autoBlock: false,
    }));
  }

  warnings.sort((a, b) =>
    b.warningDate.localeCompare(a.warningDate) ||
    a.title.localeCompare(b.title) ||
    a.company.localeCompare(b.company)
  );
  return Object.freeze(warnings);
}

function buildFsmaDomainIndex(warnings) {
  if (!Array.isArray(warnings)) throw new Error('FSMA_WARNINGS_REQUIRED');
  const index = new Map();
  for (const warning of warnings) {
    if (!warning || warning.sourceId !== FSMA_SOURCE_ID || warning.autoBlock !== false) {
      throw new Error('FSMA_WARNING_INVALID');
    }
    for (const domain of warning.domains || []) {
      const normalized = normalizeDomain(domain);
      if (!normalized || normalized !== domain) throw new Error('FSMA_DOMAIN_INVALID');
      const existing = index.get(domain) || [];
      if (existing.length < 32) existing.push(warning);
      index.set(domain, existing);
    }
  }
  for (const [domain, records] of index.entries()) {
    records.sort((a, b) => b.warningDate.localeCompare(a.warningDate));
    index.set(domain, Object.freeze(records));
  }
  return index;
}

function matchFsmaDomain(index, rawUrlOrHost) {
  if (!(index instanceof Map)) return null;
  const domain = normalizeDomain(rawUrlOrHost);
  if (!domain) return null;
  let current = domain;
  while (current.includes('.')) {
    const records = index.get(current);
    if (records?.length) {
      return Object.freeze({
        matchedDomain: current,
        requestedDomain: domain,
        warning: records[0],
        warningCount: records.length,
        advisoryOnly: true,
      });
    }
    current = current.slice(current.indexOf('.') + 1);
  }
  return null;
}

function fsmaSourceRecord({ reviewedBy, reviewedAt, validUntil } = {}) {
  const reviewer = boundedText(reviewedBy, 120);
  if (!reviewer) throw new Error('FSMA_REVIEWER_REQUIRED');
  const reviewed = new Date(reviewedAt);
  const until = new Date(validUntil);
  if (!Number.isFinite(reviewed.getTime()) || !Number.isFinite(until.getTime()) || until <= reviewed) {
    throw new Error('FSMA_REVIEW_WINDOW_INVALID');
  }
  return Object.freeze({
    source_id: FSMA_SOURCE_ID,
    name: 'Financial Services and Markets Authority (FSMA) — Belgium warnings',
    status: 'authorized',
    authorization_type: 'CC BY 4.0 public-information reuse',
    authorization_reference: 'FSMA Disclaimer & Copyright — CC BY 4.0',
    evidence_url: FSMA_LICENSE_URL,
    reviewed_by: reviewer,
    reviewed_at: reviewed.toISOString(),
    valid_from: reviewed.toISOString(),
    valid_until: until.toISOString(),
    scopes: Object.freeze(['regulatory-domain-warning']),
  });
}

export {
  FSMA_EXPORT_URL,
  FSMA_LICENSE_URL,
  FSMA_LIST_URL,
  FSMA_SOURCE_ID,
  buildFsmaDomainIndex,
  extractDomains,
  fsmaSourceRecord,
  matchFsmaDomain,
  normalizeDomain,
  normalizeFsmaCsv,
  normalizeWarningDate,
  parseDelimitedCsv,
};
