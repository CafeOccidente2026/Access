import { describe, expect, it } from 'vitest';
import {
  asPenalty,
  formatDisplayNumber,
  formatThousands,
  parseDisplayNumber,
  stripAnnouncementPrefix,
  validateWholeNumberField,
} from './number-format';

describe('parseDisplayNumber', () => {
  it('parses colombian decimal comma format', () => {
    expect(parseDisplayNumber('1,8')).toBe(1.8);
  });

  it('parses colombian thousands + decimal format', () => {
    expect(parseDisplayNumber('1.234.567,89')).toBe(1234567.89);
  });

  it('parses a plain integer string', () => {
    expect(parseDisplayNumber('1250')).toBe(1250);
  });

  it('returns 0 for empty, null or undefined input (never NaN to the backend)', () => {
    expect(parseDisplayNumber('')).toBe(0);
    expect(parseDisplayNumber('   ')).toBe(0);
    expect(parseDisplayNumber(null)).toBe(0);
    expect(parseDisplayNumber(undefined)).toBe(0);
  });

  it('returns 0 for garbage input instead of NaN', () => {
    expect(parseDisplayNumber('abc')).toBe(0);
  });
});

describe('formatDisplayNumber', () => {
  it('formats currency with exactly 2 decimals', () => {
    expect(formatDisplayNumber(1113500, 'currency')).toBe('1.113.500,00');
  });

  it('Q3: truncates calculated values to 2 decimals instead of rounding', () => {
    expect(formatDisplayNumber(92.118, 'currency')).toBe('92,11');
    expect(formatDisplayNumber(-235.569, 'currency')).toBe('-235,56');
    expect(formatDisplayNumber(1.005, 'currency')).toBe('1,00');
    expect(formatDisplayNumber(94.0001, 'currency')).toBe('94,00');
    expect(formatDisplayNumber(0.009, 'currency')).toBe('0,00');
    expect(formatDisplayNumber(-0.004, 'currency')).toBe('0,00');
    expect(formatDisplayNumber('12,999', 'currency')).toBe('12,99');
  });

  it('formats count without forcing decimals', () => {
    expect(formatDisplayNumber(1250, 'count')).toBe('1.250');
  });

  it('round-trips a comma-decimal string input through normalization', () => {
    expect(formatDisplayNumber('1234,5', 'currency')).toBe('1.234,50');
  });

  it('returns the original string unchanged when it cannot be parsed', () => {
    expect(formatDisplayNumber('N/A', 'count')).toBe('N/A');
  });

  it('returns an empty string for empty input', () => {
    expect(formatDisplayNumber('', 'count')).toBe('');
  });
});

describe('formatThousands', () => {
  it('inserts a dot every 3 digits from the right', () => {
    expect(formatThousands('2500000')).toBe('2.500.000');
  });

  it('leaves short numbers untouched', () => {
    expect(formatThousands('250')).toBe('250');
  });
});

describe('validateWholeNumberField', () => {
  it('allows an empty value (validation only applies once something is typed)', () => {
    expect(validateWholeNumberField('')).toBeNull();
    expect(validateWholeNumberField('   ')).toBeNull();
  });

  it('allows a plain digit string', () => {
    expect(validateWholeNumberField('1200000')).toBeNull();
  });

  it('allows a value already formatted with thousands dots', () => {
    expect(validateWholeNumberField('1.200.000')).toBeNull();
  });

  it('flags letters with the specific message', () => {
    expect(validateWholeNumberField('12a3')).toBe('No se aceptan letras, solo números');
    expect(validateWholeNumberField('abc')).toBe('No se aceptan letras, solo números');
  });

  it('flags a negative value with the specific message', () => {
    expect(validateWholeNumberField('-500')).toBe('No se aceptan valores negativos en este campo');
  });
});

describe('stripAnnouncementPrefix', () => {
  it('keeps only the number after the last dash', () => {
    expect(stripAnnouncementPrefix('SDBU-3500')).toBe('3500');
  });

  it('returns the value unchanged when there is no dash', () => {
    expect(stripAnnouncementPrefix('3500')).toBe('3500');
  });
});

describe('asPenalty', () => {
  it('takes a positive penalty as negative and leaves zero and negatives alone', () => {
    expect(asPenalty('23')).toBe('-23');
    expect(asPenalty('-23')).toBe('-23');
    expect(asPenalty(' 1,5 ')).toBe('-1,5');
    expect(asPenalty('')).toBe('0');
    expect(asPenalty('0')).toBe('0');
  });
});

/** La version de antes (regex con lookahead, S8786): la nueva debe dar exactamente lo mismo. */
const oldFormatThousands = (digits: string): string => digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

describe('formatThousands without regex gives exactly the same as before', () => {
  const fixed = [
    '', '0', '7', '12', '123', '1234', '12345', '123456', '1234567', '1000000', '2500000', '999999999',
    '123456789012345678901234567890', '-1', '-1234', '-1234567', '1234.5', '1234567.891', '0.123456', '-0.5',
    '1234,56', '1.234', '  1234567  ', '12 345 678', 'abc', 'a1234', '1234a', 'a1234567b', '_1234', 'é1234',
    '$1234567', '1234567$', '1234\n5678', '\t123456', '00001234', '1,234,567', '++1234', '12-3456-789',
  ];

  it.each(fixed)('"%s"', (value) => {
    expect(formatThousands(value)).toBe(oldFormatThousands(value));
  });

  it('agrees on thousands of generated values (digits, signs, dots, commas, spaces and letters)', () => {
    const alphabet = '0123456789000111999-.,  a_Z';
    let seed = 20261103;
    const next = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
    for (let n = 0; n < 5000; n++) {
      const length = Math.floor(next() * 22);
      const value = Array.from({ length }, () => alphabet[Math.floor(next() * alphabet.length)]).join('');
      expect(formatThousands(value), JSON.stringify(value)).toBe(oldFormatThousands(value));
    }
    for (let n = 0; n <= 100000; n += 7) {
      expect(formatThousands(String(n))).toBe(oldFormatThousands(String(n)));
      expect(formatThousands(String(-n * 1013))).toBe(oldFormatThousands(String(-n * 1013)));
    }
  });

  it('like before, null and undefined are not accepted', () => {
    for (const value of [null, undefined]) {
      expect(() => oldFormatThousands(value as unknown as string)).toThrow();
      expect(() => formatThousands(value as unknown as string)).toThrow();
    }
  });
});
