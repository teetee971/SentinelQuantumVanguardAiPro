// RTR block allocations are not a caller-identity or current-carrier database.
export const RTR_DIRECTORY_URL = '/public/data/rtr-numbering.json';
export const RTR_SOURCE_URL = 'https://www.rtr.at/TKP/service/rufnummernsuche/Rufnummernsuche.de.html';
export const RTR_TERMS_URL = 'https://www.rtr.at/rtr/service/opendata/OD_Nutzungsbedingungen.de.html';
export const RTR_STATUSES = Object.freeze(['unallocated', 'outside-allocation', 'not-issued', 'partial', 'not-allocatable']);
const MAX_BYTES = 4 * 1024 * 1024;
const MAX_ROWS = 100_000;

const boundedText = (value, max = 300) => typeof value === 'string' && value.length <= max && !/[\u0000-\u001f\u007f]/.test(value);

export function createRtrLookup(directory) {
  const invalid = () => { throw new Error('INVALID_RTR_DIRECTORY'); };
  if (directory?.schemaVersion !== 1 || directory.country !== 'AT' ||
      !Array.isArray(directory.holders) || directory.holders.length > 20_000 ||
      !directory.groups || typeof directory.groups !== 'object' || Array.isArray(directory.groups) ||
      typeof directory.generatedAt !== 'string' || !Number.isFinite(Date.parse(directory.generatedAt)) ||
      !(directory.sourcePublishedAt == null || (typeof directory.sourcePublishedAt === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(directory.sourcePublishedAt)))) invalid();
  for (const holder of directory.holders) {
    if (!Array.isArray(holder) || holder.length !== 2 || !boundedText(holder[0]) || !holder[0] ||
        !(holder[1] === null || (typeof holder[1] === 'string' && /^\d{1,12}$/.test(holder[1])))) invalid();
  }
  const groups = new Map();
  let count = 0;
  for (const [key, group] of Object.entries(directory.groups)) {
    const parts = /^([1-9]\d{0,3})\/(\d{1,2})$/.exec(key);
    if (!parts || !['geographic', 'service'].includes(group?.kind) ||
        !boundedText(group.category) || !group.category || !boundedText(group.area) ||
        !Array.isArray(group.ranges) || !group.ranges.length) invalid();
    const width = Number(parts[2]);
    if (width < 2 || width + parts[1].length > 13) invalid();
    let previous = '';
    for (const range of group.ranges) {
      if (!Array.isArray(range) || range.length !== 3) invalid();
      const [start, end, holder] = range;
      if (typeof start !== 'string' || typeof end !== 'string' ||
          !/^\d+$/.test(start) || !/^\d+$/.test(end) || start.length !== width || end.length !== width ||
          start > end || start < previous || !Number.isInteger(holder) ||
          holder < -RTR_STATUSES.length || holder >= directory.holders.length) invalid();
      previous = start;
      if (++count > MAX_ROWS) invalid();
    }
    groups.set(key, group);
  }
  if (!count || count !== directory.recordCount) invalid();

  return (number) => {
    if (typeof number !== 'string' || !/^\+43[1-9]\d{4,12}$/.test(number)) return null;
    const national = number.slice(3);
    const matches = [];
    // Compare only published lengths: do not pad shortened numbers or truncate extensions.
    for (let length = 1; length <= 4; length += 1) {
      const prefix = national.slice(0, length);
      const suffix = national.slice(length);
      const group = groups.get(`${prefix}/${suffix.length}`);
      if (!group) continue;
      for (const [start, end, holderIndex] of group.ranges) {
        if (start > suffix) break;
        if (end < suffix) continue;
        const holder = holderIndex >= 0 ? directory.holders[holderIndex] : null;
        matches.push({
          start: `+43${prefix}${start}`, end: `+43${prefix}${end}`,
          category: group.category, area: group.area || null, kind: group.kind,
          status: holder ? 'allocated' : RTR_STATUSES[-holderIndex - 1],
          allocationHolder: holder?.[0] ?? null, holderId: holder?.[1] ?? null
        });
      }
    }
    // Conflicting/nested records remain ambiguous; never choose the first holder silently.
    return { status: matches.length > 1 ? 'ambiguous' : matches[0]?.status ?? 'no-match', matches };
  };
}

export function createRtrLoader(fetcher = (...args) => fetch(...args)) {
  let pending;
  return function load() {
    if (pending) return pending;
    pending = (async () => {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15_000);
      let reader;
      try {
        const response = await fetcher(RTR_DIRECTORY_URL, {
          credentials: 'omit', referrerPolicy: 'no-referrer', redirect: 'error',
          headers: { Accept: 'application/json' }, signal: controller.signal
        });
        if (!response.ok || !response.body || Number(response.headers.get('content-length')) > MAX_BYTES) throw new Error('RTR_UNAVAILABLE');
        reader = response.body.getReader();
        const chunks = [];
        let total = 0;
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          total += value.byteLength;
          if (total > MAX_BYTES) throw new Error('RTR_TOO_LARGE');
          chunks.push(value);
        }
        const bytes = new Uint8Array(total);
        let offset = 0;
        for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
        const directory = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
        return { directory, lookup: createRtrLookup(directory) };
      } catch {
        if (reader) await reader.cancel().catch(() => {});
        return null;
      } finally {
        clearTimeout(timeout);
      }
    })();
    const current = pending;
    current.then((value) => { if (!value && pending === current) pending = undefined; });
    return current;
  };
}

export const loadRtrDirectory = createRtrLoader();
