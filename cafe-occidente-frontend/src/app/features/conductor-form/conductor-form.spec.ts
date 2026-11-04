import { describe, expect, it } from 'vitest';

import { conductorLabel, dateTime, shortDate } from './conductor-form';

describe('conductor label', () => {
  it('skips a null last name instead of showing "null"', () => {
    expect(conductorLabel({ idNumber: '900723205', firstName: 'FUNDACION SUYUSAMA', lastName: null }))
      .toBe('900723205 - FUNDACION SUYUSAMA');
    expect(conductorLabel({ idNumber: '555', firstName: 'Luis', lastName: 'Rosero' })).toBe('555 - Luis Rosero');
  });
});

describe('conductor form dates', () => {
  it('shows the affiliation date as dd/mm/aaaa', () => {
    expect(shortDate('2010-05-01')).toBe('01/05/2010');
    expect(shortDate(null)).toBe('');
  });

  it('shows "Actualizado" as dd/mm/aaaa hh:mm in local time', () => {
    const local = new Date(2026, 8, 29, 7, 5);
    expect(dateTime(local.toISOString())).toBe('29/09/2026 07:05');
    expect(dateTime(null)).toBe('');
  });
});
