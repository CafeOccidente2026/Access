import { describe, expect, it } from 'vitest';

import { Associate } from '../../core/models/vendor.model';
import associatesContent from '../../../../public/assets/data/associates.json';
import nessQuotas from '../../../../public/assets/data/ness-quotas.json';
import menu from '../../../../public/assets/data/vendors-menu.json';
import { AssociatesContent, displayValue } from '../associates/associates';
import { quotaCells } from './ness-quotas';

describe('ness quotas', () => {
  it('R2 (17-nov): CEDULA without thousands and the kilos with the #1 format', () => {
    expect(quotaCells({ idNumber: '990514', names: 'Caficultor', program: 'NESPRESSO', quota: 30000 })).toEqual(
      ['990514', 'Caficultor', 'NESPRESSO', '30.000'],
    );
  });

  it('adds FACTURADOS and SALDO in the balances view', () => {
    expect(quotaCells({ idNumber: '1', names: 'X', program: 'REGIONAL NARIÑO 4C', quota: 300, invoicedKg: 70.5, balance: 229.5 }))
      .toEqual(['1', 'X', 'REGIONAL NARIÑO 4C', '300', '70,50', '229,50']);
  });
});

describe('vendors menu and associates', () => {
  it('demo del 23-nov: 7 buttons, without Actualizar Vendedores (it does not apply with a single database)', () => {
    const options = menu.columns[0] as { label: string; disabled?: boolean; hint?: string }[];
    expect(options).toHaveLength(7);
    expect(options.map((o) => o.label)).not.toContain('Actualizar Vendedores');
    expect(options.some((o) => o.disabled || o.hint)).toBe(false);
  });

  it('demo del 23-nov: Consulta Saldos Cupos Ness no longer shows the note about the Especial', () => {
    expect('note' in nessQuotas.views.balances).toBe(false);
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
