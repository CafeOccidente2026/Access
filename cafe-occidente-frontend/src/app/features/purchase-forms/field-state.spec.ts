import { describe, expect, it } from 'vitest';

import dry from '../../../../public/assets/data/purchase-form-dry.json';
import green from '../../../../public/assets/data/purchase-form-green.json';
import { FormFieldDefinition, PurchaseFormContent } from '../../core/models';
import { PurchaseFieldForm, fieldRows, purchaseFieldMap } from './field-state';

describe('fieldRows', () => {
  const defs: FormFieldDefinition[] = [
    { key: 'bags', label: 'Sacos', type: 'count' },
    { key: 'grossKg', label: 'Kilos Brutos', type: 'number' },
    { key: 'tare', label: 'Destare', type: 'number' },
    { key: 'grossValue', label: 'Vr. Bruto', type: 'currency' },
    { key: 'contribution', label: 'Aporte Socio', type: 'currency' },
    { key: 'agency', label: 'Agencia', type: 'text' },
  ];
  const bases = new Map(defs.map((d) => [d.key, d]));
  const calc = { unitPrice: 8000, withholding: 0, grossValue: 3976000, associateContribution: 79520, cooperativeDiscount: 39760, netToPay: 3896480 };

  function form(idType?: string): PurchaseFieldForm {
    return {
      model: { bags: '10', grossKg: '520', ...(idType ? { idType } : {}) },
      locked: new Set(['bags']),
      fieldCache: new Map(),
      messages: () => ({ cooperativeDiscountLabel: 'Descuento Coop' }),
    };
  }

  const build = (f: PurchaseFieldForm, withCalc = true) =>
    fieldRows(bases, ['grossValue', 'contribution', 'agency'], ['bags', 'grossKg', 'tare'], f, { agency: 'Buesaco' }, withCalc ? calc : null);

  it('a confirmed field shows what was typed, read only; the next one is open and the following ones wait their turn', () => {
    const { row } = build(form('S'));
    const [bags, grossKg, tare] = row(defs)!;
    expect(bags).toMatchObject({ value: '10', readonly: true });
    expect(grossKg.readonly).toBeUndefined();
    expect(tare.readonly).toBe(true);
  });

  it('the settlement comes from the server: Aporte Socio for type S, Descuento Coop for type C', () => {
    expect(build(form('S')).field('contribution')).toMatchObject({ value: 79520, label: 'Aporte Socio', readonly: true });
    expect(build(form('C')).field('contribution')).toMatchObject({ value: 39760, label: 'Descuento Coop' });
    expect(build(form('S')).field('grossValue').value).toBe(3976000);
    expect(build(form('S')).field('agency').value).toBe('Buesaco');
  });

  it('stays empty before the server calculates or the cédula is looked up', () => {
    const f = form();
    expect(build(f, false).field('grossValue').value).toBe('');
    expect(build(f, false).field('contribution').label).toBe('Aporte Socio');
  });

  it('reuses the same field while nothing changes, and a new one when the value changes', () => {
    const f = form('S');
    const first = build(f).field('grossValue');
    expect(build(f).field('grossValue')).toBe(first);
    expect(build(f, false).field('grossValue')).not.toBe(first);
  });
});

describe('purchaseFieldMap', () => {
  it('has every field of the screen JSON by key, with the settlement and Descuento Fro', () => {
    const map = purchaseFieldMap(dry as PurchaseFormContent);
    for (const key of ['fund', 'idNumber', 'grossKg', 'kgPrice', 'contribution', 'otherDiscounts', 'shrinkageDiscount']) {
      expect(map.get(key)?.key).toBe(key);
    }
  });

  it('includes the rows only some screens have (Vr. Kilo Comp of Café Verde)', () => {
    expect(purchaseFieldMap(green as PurchaseFormContent).has('compKgPrice')).toBe(true);
  });

  it('works without optional rows', () => {
    expect([...purchaseFieldMap({ topFields: [{ key: 'agency', label: 'Agencia', type: 'text' }] } as unknown as PurchaseFormContent).keys()])
      .toEqual(['agency']);
  });
});
