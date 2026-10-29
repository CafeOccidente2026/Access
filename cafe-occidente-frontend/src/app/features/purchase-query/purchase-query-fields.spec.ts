import { describe, expect, it } from 'vitest';

import { PurchaseFormContent } from '../../core/models/purchase-form-content.model';
import content from '../../../../public/assets/data/purchase-query.json';
import { fillQueryContent } from './purchase-query-fields';
import { GREEN, HUSK, MIXED, detail } from './purchase-query.testing';

const base = content as unknown as PurchaseFormContent;

/** Valor mostrado de un campo, por su key, en cualquier grupo de la ficha. */
function value(filled: PurchaseFormContent, key: string): unknown {
  const groups = [filled.topFields, filled.identificationFields, filled.contactFields, filled.qualityFields,
    filled.weightFields, filled.netWeightFields, filled.settlementFields, filled.additionalDiscountFields,
    [filled.discountField, filled.statusField]];
  return groups.flat().find((f) => f?.key === key)?.value;
}

describe('fillQueryContent (RF-26: all the data of the purchase, read only)', () => {
  it('fills the header, the seller, the quality, the kilos and the settlement of a dry coffee purchase', () => {
    const filled = fillQueryContent(base, detail({ remissions: ['SDBU-RP-0001', 'SDBU-RP-0002'] }));
    expect(value(filled, 'agency')).toBe('Buesaco 1');
    expect(value(filled, 'purchaseDate')).toBe('2026-10-29');
    expect(value(filled, 'announcement')).toBe('4');
    expect(value(filled, 'remission')).toBe('SDBU-RP-0001, SDBU-RP-0002');
    expect(value(filled, 'invoice')).toBe('SDBU');
    expect(value(filled, 'invoiceNumber')).toBe('27869');
    expect(value(filled, 'basePriceDryLoad')).toBe(1546900);
    expect(value(filled, 'idNumber')).toBe('990.006.003');
    expect(value(filled, 'factor')).toBe(92.11);
    expect(value(filled, 'netKg')).toBe(1000);
    expect(value(filled, 'greenKg')).toBe('');
    expect(value(filled, 'netToPay')).toBe(12255579);
    expect(value(filled, 'status')).toBe('VALIDA');
  });

  it('dates go in ISO and whole prices are marked as integers, so each field formats them by its type', () => {
    const fields = Object.fromEntries(
      [base.identificationFields, base.qualityFields ?? [], base.netWeightFields ?? []].flat().map((f) => [f.key, f]),
    );
    for (const key of ['basePriceDryLoad', 'healthyStoredPrice', 'bonus', 'costs', 'bags', 'penalty']) {
      expect(fields[key].integer).toBe(true);
    }
    expect(fields['invoiceNumber'].type).toBe('text');
  });

  it('RN-26.4: no field is editable', () => {
    const filled = fillQueryContent(base, detail());
    const fields = [filled.topFields, filled.identificationFields, filled.contactFields ?? [], filled.qualityFields ?? [],
      filled.weightFields ?? [], filled.netWeightFields ?? [], filled.settlementFields ?? [], filled.additionalDiscountFields ?? []].flat();
    expect(fields.every((f) => f.readonly)).toBe(true);
    expect(filled.paymentLocked).toBe(true);
  });

  it('shows the saved payment methods, including Transferencia', () => {
    expect(fillQueryContent(base, detail()).paymentSaved).toEqual(MIXED);
  });

  it('RN-26.5: an annulled purchase says ANULADA', () => {
    expect(value(fillQueryContent(base, detail({ status: 'ANULADA' })), 'status')).toBe('ANULADA');
  });

  it('green coffee shows its Kilos Verdes and its sample; husk its almond as Peso Alm Sana and %', () => {
    const green = fillQueryContent(base, detail({ module: 'GREEN', purchase: GREEN }));
    expect(value(green, 'greenKg')).toBe(1000);
    expect(value(green, 'factor')).toBe(94);
    expect(value(green, 'totalStoredWeight')).toBe(140);

    const husk = fillQueryContent(base, detail({ module: 'HUSK', purchase: HUSK }));
    expect(value(husk, 'healthyStoredWeight')).toBe(200);
    expect(value(husk, 'factor')).toBe(48);
    expect(value(husk, 'basePriceDryLoad')).toBe(1546900);
    expect(value(husk, 'greenKg')).toBe('');
  });
});
