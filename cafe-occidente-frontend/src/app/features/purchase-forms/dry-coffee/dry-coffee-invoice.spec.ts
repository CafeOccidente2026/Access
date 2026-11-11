import { describe, expect, it } from 'vitest';

import { buildDryCoffeeInvoiceDocDefinition, paymentAmounts } from './dry-coffee-invoice';
import { DryCoffeePurchaseResponse } from '../../../core/models/dry-coffee-purchase.model';

/** Primer nodo del documento cuyo texto es exactamente `text`. */
const findText = (node: unknown, text: string): { color?: string; bold?: boolean } | undefined => {
  if (Array.isArray(node)) return node.map((n) => findText(n, text)).find(Boolean);
  if (node === null || typeof node !== 'object') return undefined;
  if ((node as { text?: unknown }).text === text) return node as { color?: string };
  return Object.values(node).map((n) => findText(n, text)).find(Boolean);
};

describe('buildDryCoffeeInvoiceDocDefinition', () => {
  it('RN-18.3: prints the copy for the grower and the copy for Operaciones, same invoice on separate pages', () => {
    const purchase = {
      id: 1, purchaseDate: '2026-09-28', invoiceNumber: 27867, agencyName: 'El Tambo', fundCode: '10',
      specialType: 'A', productCode: '1', announcementNumber: '5', announcementDate: '2026-09-28',
      basePriceLoad: 1113500, idNumber: '123456', firstName: 'Juan', lastName: 'Perez', growerType: 'S',
      address: 'Vereda', cellphone: '300', bagsCount: 10, grossKg: 1250, tareKg: 50, netKg: 1200,
      totalStoredWeight: 240, wastePercentage: 8, defectiveStoredWeight: 20, defectivePercentage: 8,
      healthyStoredWeight: 220, healthyPercentage: 88, healthyUnitPrice: 12000, defectiveUnitPrice: 0,
      bonus: 0, penalty: 0, costs: 692, unitPrice: 10000, grossValue: 12000000, inventoryValue: 12000000,
      associateContribution: 240000, cooperativeDiscount: 0, withholding: 60000, freightDiscount: 0,
      otherDiscounts: 0, netToPay: 11700000, paymentMethod: 'EFECTIVO', checkNumber: null,
      purchasePoint: 'PC', prefix: 'RN', dianResolution: '1', resolutionDate: '2026-01-01',
      resolutionFrom: 1, resolutionTo: 99999, validity: 12,
    } as DryCoffeePurchaseResponse;

    const content = buildDryCoffeeInvoiceDocDefinition(purchase, null).content as unknown[];
    // 6C: el NIT de la cooperativa se guarda sin separadores y sale igual que antes.
    expect(findText(content, 'NIT 891.200.986-8')).toBeTruthy();
    const second = content.at(-1) as { stack: unknown[]; pageBreak: string };

    const first = content.slice(0, -1);
    expect(second.pageBreak).toBe('before');
    expect(first[0]).toEqual(expect.objectContaining({ text: 'Copia Caficultor', alignment: 'right' }));
    expect(second.stack[0]).toEqual(expect.objectContaining({ text: 'Copia Operaciones', alignment: 'right' }));
    expect(second.stack.slice(1)).toEqual(first.slice(1));
    // NETO A PAGAR va en negrita y con el color por defecto (negro), como las demas etiquetas.
    expect(findText(content, 'NETO A PAGAR  $')).toEqual(expect.objectContaining({ bold: true }));
    expect(findText(content, 'NETO A PAGAR  $')?.color).toBeUndefined();
  });

  it('prints FORMAS DE PAGO from the payment split, falling back to the whole net in its method', () => {
    const payment = { cashAmount: 1944624, checkAmount: 3000000, transferAmount: 0, cardAmount: 0, checkNumber: '5379' };
    expect(paymentAmounts({ paymentMethod: 'MIXTO', netToPay: 4944624, payment }))
      .toEqual({ cash: 1944624, check: 3000000, transfer: 0, cardTerminal: 0 });
    expect(paymentAmounts({ paymentMethod: 'CHEQUE', netToPay: 500 }))
      .toEqual({ cash: 0, check: 500, transfer: 0, cardTerminal: 0 });
  });
});
