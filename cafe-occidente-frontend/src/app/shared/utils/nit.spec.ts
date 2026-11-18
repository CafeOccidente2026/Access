import { describe, expect, it } from 'vitest';

import company from '../../../../public/assets/data/company.json';
import { cooperativeNit, formatNit } from './nit';

describe('NIT', () => {
  it('se guarda sin separadores (6C)', () => {
    expect(company.nit).toMatch(/^\d+$/);
  });

  it('formatNit pone puntos de miles y el guion antes del digito de verificacion', () => {
    expect(formatNit('8912009868')).toBe('891.200.986-8');
    expect(formatNit('9001234567')).toBe('900.123.456-7');
    expect(formatNit('8001')).toBe('800-1');
    expect(formatNit('891.200.986-8')).toBe('891.200.986-8');
  });

  it('deja igual lo que no tiene al menos dos digitos', () => {
    expect(formatNit('')).toBe('');
    expect(formatNit('7')).toBe('7');
  });

  it('el de la cooperativa sale como hoy', () => {
    expect(cooperativeNit()).toBe('891.200.986-8');
  });
});
