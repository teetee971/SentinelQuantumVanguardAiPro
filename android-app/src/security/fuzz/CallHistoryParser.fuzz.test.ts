import fc from 'fast-check';
import { parseStoredCallHistory } from '../../modules/phone/CallHistoryParser';

describe('CallHistoryParser fuzz campaign', () => {
  test('never throws on arbitrary UTF-16 strings and always returns a bounded array', () => {
    fc.assert(
      fc.property(fc.string(), input => {
        const result = parseStoredCallHistory(input);
        expect(Array.isArray(result)).toBe(true);
        expect(result.length).toBeLessThanOrEqual(1000);
      }),
      { numRuns: 5000, endOnFailure: true }
    );
  });

  test('never throws on arbitrary JSON values', () => {
    fc.assert(
      fc.property(fc.jsonValue(), value => {
        const result = parseStoredCallHistory(JSON.stringify(value));
        expect(Array.isArray(result)).toBe(true);
        expect(result.length).toBeLessThanOrEqual(1000);
      }),
      { numRuns: 5000, endOnFailure: true }
    );
  });

  test('rejects malformed or schema-invalid entries without poisoning valid history', () => {
    const valid = {
      id: 'call_seed_1',
      phoneNumber: '+59000000000',
      timestamp: 1700000000000,
      type: 'INCOMING',
      duration: 12,
      action: 'ALLOWED',
      riskLevel: 'LOW',
    };

    fc.assert(
      fc.property(fc.jsonValue(), junk => {
        const result = parseStoredCallHistory(JSON.stringify([junk, valid]));
        expect(result).toEqual([valid]);
      }),
      { numRuns: 2000, endOnFailure: true }
    );
  });
});
