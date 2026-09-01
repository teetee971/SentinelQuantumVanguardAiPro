import { parseStoredCallHistory } from '../../modules/phone/CallHistoryParser';

function pseudoRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return (state >>> 0) / 0x100000000;
  };
}

function fuzzString(random: () => number, maxLength = 256): string {
  const length = Math.floor(random() * maxLength);
  let value = '';
  for (let i = 0; i < length; i += 1) {
    const pools = [
      String.fromCharCode(Math.floor(random() * 0x80)),
      String.fromCharCode(0xd800 + Math.floor(random() * 0x800)),
      String.fromCharCode(Math.floor(random() * 0x10000)),
    ];
    value += pools[Math.floor(random() * pools.length)];
  }
  return value;
}

describe('CallHistoryParser fuzz campaign', () => {
  test('never throws on 12000 deterministic adversarial strings', () => {
    const random = pseudoRandom(0x53454e54);
    for (let i = 0; i < 12000; i += 1) {
      const result = parseStoredCallHistory(fuzzString(random));
      expect(Array.isArray(result)).toBe(true);
      expect(result.length).toBeLessThanOrEqual(1000);
    }
  });

  test('never throws on adversarial JSON values', () => {
    const random = pseudoRandom(0x514a534f4e);
    const values: unknown[] = [null, true, false, 0, -1, Number.MAX_SAFE_INTEGER, '', {}, [], 'null', '\\u0000'];
    for (let i = 0; i < 4000; i += 1) {
      const value = values[i % values.length];
      const serialized = i % 3 === 0 ? fuzzString(random, 120) : JSON.stringify(value);
      const result = parseStoredCallHistory(serialized);
      expect(Array.isArray(result)).toBe(true);
      expect(result.length).toBeLessThanOrEqual(1000);
    }
  });

  test('rejects malformed entries without poisoning valid history', () => {
    const valid = {
      id: 'call_seed_1',
      phoneNumber: '+59000000000',
      timestamp: 1700000000000,
      type: 'INCOMING',
      duration: 12,
      action: 'ALLOWED',
      riskLevel: 'LOW',
    };
    const malformed = [
      null,
      {},
      { ...valid, timestamp: NaN },
      { ...valid, duration: -1 },
      { ...valid, type: 'UNKNOWN' },
      { ...valid, action: 'EXECUTE' },
      { ...valid, riskLevel: 'ROOT' },
      { ...valid, phoneNumber: 123 },
      { ...valid, notes: { injected: true } },
    ];
    for (const junk of malformed) {
      expect(parseStoredCallHistory(JSON.stringify([junk, valid]))).toEqual([valid]);
    }
  });
});
