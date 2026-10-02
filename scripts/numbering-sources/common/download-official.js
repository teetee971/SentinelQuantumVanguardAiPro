// Every caller supplies a fixed official-host allowlist; never fetch arbitrary discovered hosts.
export async function fetchOfficialBytes(rawUrl, {
  allowedOrigins, maxBytes, fetchImpl = fetch, timeoutMs = 30_000, maxRedirects = 0
} = {}) {
  if (!Array.isArray(allowedOrigins) || !allowedOrigins.length || !Number.isSafeInteger(maxBytes) || maxBytes < 1 || !Number.isInteger(maxRedirects) || maxRedirects < 0 || maxRedirects > 3) {
    throw new Error('OFFICIAL_FETCH_CONFIGURATION');
  }
  const check = value => {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password || url.port || !allowedOrigins.includes(url.origin)) {
      throw new Error('OFFICIAL_SOURCE_NOT_ALLOWED');
    }
    return url;
  };
  let url = check(rawUrl);
  const signal = AbortSignal.timeout(timeoutMs);
  for (let redirects = 0; redirects <= maxRedirects; redirects++) {
    const response = await fetchImpl(url.href, {
      redirect: 'manual', signal,
      headers: { 'User-Agent': 'SentinelQuantumVanguardAiPro/1.0 official-numbering-refresh' }
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      await response.body?.cancel();
      if (maxRedirects === 0) throw new Error('OFFICIAL_REDIRECT_NOT_ALLOWED');
      const location = response.headers.get('location');
      if (!location) throw new Error('OFFICIAL_REDIRECT_WITHOUT_LOCATION');
      url = check(new URL(location, url).href);
      continue;
    }
    if (!response.ok) { await response.body?.cancel(); throw new Error(`OFFICIAL_HTTP_${response.status}`); }
    if (Number(response.headers.get('content-length')) > maxBytes) {
      await response.body?.cancel(); throw new Error('OFFICIAL_INPUT_TOO_LARGE');
    }
    if (!response.body) throw new Error('OFFICIAL_EMPTY_RESPONSE');
    const reader = response.body.getReader();
    const chunks = [];
    let size = 0;
    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > maxBytes) throw new Error('OFFICIAL_INPUT_TOO_LARGE');
        chunks.push(Buffer.from(value));
      }
    } finally { await reader.cancel(); }
    if (!size) throw new Error('OFFICIAL_EMPTY_RESPONSE');
    return { bytes: Buffer.concat(chunks), url: url.href };
  }
  throw new Error('OFFICIAL_REDIRECT_LIMIT');
}
