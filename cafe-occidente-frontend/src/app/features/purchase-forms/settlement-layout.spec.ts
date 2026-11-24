import { describe, expect, it } from 'vitest';

import dry from '../../../../public/assets/data/purchase-form-dry.json';
import green from '../../../../public/assets/data/purchase-form-green.json';
import husk from '../../../../public/assets/data/purchase-form-husk.json';
import billing from '../../../../public/assets/data/purchase-form-quota-billing.json';
import quota from '../../../../public/assets/data/purchase-form-quota-purchase.json';
import { FormFieldDefinition, PurchaseFormContent } from '../../core/models';
import { settlementRows } from './settlement';

/** Lo que define cómo se ve la liquidación: en qué fila va cada casilla, su etiqueta, su ancho y si va resaltada. */
function layout(content: Partial<PurchaseFormContent>) {
  const box = (f: FormFieldDefinition) => [f.key, f.label, f.inputWidthClass, !!f.highlighted];
  return {
    heading: content.settlementHeading,
    row1: content.settlementFields?.map(box),
    row2: content.additionalDiscountFields?.map(box),
    right: content.netToPayField && box(content.netToPayField),
  };
}

/** Prueba del 19-nov: Verde y Pasilla tenían las 6 casillas en una fila; Anunciadas y Cupos, en tres filas. */
describe.each([
  ['Café Verde', green],
  ['Pasilla', husk],
  ['Facturar Compras Anunciadas', billing],
  ['Facturar Anuncios con Cupos', quota],
])('%s: liquidación', (_, content) => {
  it('is laid out exactly like Café Seco (Vr. Kilo, Vr. Bruto, Neto a Pagar / Aporte, Retefuente / Otros Desctos)', () => {
    expect(layout(content as Partial<PurchaseFormContent>)).toEqual(layout(dry as Partial<PurchaseFormContent>));
  });
});

describe('Café Seco: liquidación', () => {
  it('row 1 Vr. Kilo, Vr. Bruto and Neto a Pagar highlighted; row 2 Aporte Socio and Retefuente; Otros Desctos right', () => {
    const l = layout(dry as Partial<PurchaseFormContent>);
    expect(l.row1?.map((b) => b[0])).toEqual(['kgPrice', 'grossValue', 'netToPay']);
    expect(l.row1?.[2][3]).toBe(true);
    expect(l.row2?.map((b) => b[0])).toEqual(['contribution', 'withholding']);
    expect(l.right?.slice(0, 2)).toEqual(['otherDiscounts', 'Otros Desctos']);
  });
});

describe('settlementRows', () => {
  it('builds the three parts of the block with the screen field builder', () => {
    const field = (key: string): FormFieldDefinition => ({ key, label: key.toUpperCase(), type: 'currency', readonly: true });
    const rows = settlementRows(dry as Partial<PurchaseFormContent>, field);
    expect(rows.settlementFields?.map((f) => f.label)).toEqual(['KGPRICE', 'GROSSVALUE', 'NETTOPAY']);
    expect(rows.additionalDiscountFields?.map((f) => f.key)).toEqual(['contribution', 'withholding']);
    expect(rows.netToPayField?.label).toBe('OTHERDISCOUNTS');
  });

  it('leaves out what the screen does not have', () => {
    expect(settlementRows({}, () => ({ key: '', label: '', type: 'text' }))).toEqual({
      settlementFields: undefined,
      additionalDiscountFields: undefined,
      netToPayField: undefined,
    });
  });
});
