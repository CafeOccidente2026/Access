import { describe, expect, it } from 'vitest';

import { MIN_REASON_LENGTH, validReason } from './annulment-reason';

describe('annulment reason (26-nov)', () => {
  it('is mandatory, with at least 10 characters without the spaces at the ends', () => {
    expect(MIN_REASON_LENGTH).toBe(10);
    expect(validReason('')).toBe(false);
    expect(validReason('corto')).toBe(false);
    expect(validReason('   123456789   ')).toBe(false);
    expect(validReason('1234567890')).toBe(true);
    expect(validReason('  Error de digitación  ')).toBe(true);
  });
});
