import { describe, expect, it } from 'vitest';
import {
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
