import { describe, expect, it } from 'vitest';

import { Associate } from '../../core/models/vendor.model';
import associatesContent from '../../../../public/assets/data/associates.json';
import menu from '../../../../public/assets/data/vendors-menu.json';
import { AssociatesContent, displayValue } from '../associates/associates';
import { quotaCells } from './ness-quotas';

describe('ness quotas', () => {
  it('formats CuposNess like Access: CEDULA General Number, CUPO Standard', () => {
    expect(quotaCells({ idNumber: '210514', names: 'Paz', program: 'NESPRESSO', quota: 30000 })).toEqual(
      ['210514', 'Paz', 'NESPRESSO', '30.000,00'],
    );
  });

  it('adds FACTURADOS and SALDO in the balances view', () => {
    expect(quotaCells({ idNumber: '1', names: 'X', program: 'REGIONAL NARIÑO 4C', quota: 300, invoicedKg: 70.5, balance: 229.5 }))
      .toEqual(['1', 'X', 'REGIONAL NARIÑO 4C', '300,00', '70,50', '229,50']);
  });
});

describe('vendors menu and associates', () => {
  it('keeps the 8 Access buttons in order, with the two pending ones disabled', () => {
    const options = menu.columns[0];
    expect(options).toHaveLength(8);
    expect(options.filter((o) => 'disabled' in o && o.disabled).map((o) => o.label)).toEqual(['Actualizar Vendedores', 'Actualiza Cupos NESS']);
  });

  it('shows the 28 Access controls with True/False as Verdadero/Falso', () => {
    const content = associatesContent as AssociatesContent;
    expect(content.columns.flat()).toHaveLength(28);
    const associate = { accepted: false, exported: true, affiliationDate: '2010-03-07', observation: null } as unknown as Associate;
    const field = (key: string) => content.columns.flat().find((f) => f.key === key)!;
    expect(displayValue(associate, field('accepted'), content)).toBe('Falso');
    expect(displayValue(associate, field('exported'), content)).toBe('Verdadero');
    expect(displayValue(associate, field('affiliationDate'), content)).toBe('07/03/2010');
    expect(displayValue(associate, field('observation'), content)).toBe('');
  });
});
