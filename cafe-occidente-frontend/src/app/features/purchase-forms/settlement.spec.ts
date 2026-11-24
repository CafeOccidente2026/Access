import { describe, expect, it } from 'vitest';

import { growerTypeOf, settlementLabel, settlementValues } from './settlement';

describe('settlement', () => {
  const calc = { unitPrice: 8000, withholding: 0, grossValue: 3976000, associateContribution: 79520, cooperativeDiscount: 39760, netToPay: 3896480 };

  it('type S shows the Aporte Socio, type C the Descuento Coop', () => {
    expect(settlementValues(calc, 'S')).toEqual({
      kgPrice: 8000, grossValue: 3976000, contribution: 79520, withholding: 0, netToPay: 3896480,
    });
    expect(settlementValues(calc, 'C')['contribution']).toBe(39760);
  });

  it('stays empty until the server calculates', () => {
    expect(settlementValues(null, 'S')).toEqual({ kgPrice: '', grossValue: '', contribution: '', withholding: '', netToPay: '' });
  });

  it('the contribution label says Descuento Coop only for type C', () => {
    const contribution = { key: 'contribution', label: 'Aporte Socio', type: 'currency' as const };
    expect(settlementLabel(contribution, 'C', 'Descuento Coop')).toBe('Descuento Coop');
    expect(settlementLabel(contribution, 'C')).toBe('Aporte Socio');
    expect(settlementLabel(contribution, 'S', 'Descuento Coop')).toBe('Aporte Socio');
    expect(settlementLabel({ ...contribution, key: 'grossValue', label: 'Vr. Bruto' }, 'C', 'Descuento Coop')).toBe('Vr. Bruto');
  });
});

describe('growerTypeOf', () => {
  it('reads S or C from the Tipo field, empty before the cédula is looked up', () => {
    expect(growerTypeOf(' c ')).toBe('C');
    expect(growerTypeOf(undefined)).toBe('');
  });
});
