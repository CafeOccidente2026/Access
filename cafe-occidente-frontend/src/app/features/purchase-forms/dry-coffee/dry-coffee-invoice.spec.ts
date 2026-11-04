import { describe, expect, it } from 'vitest';

import { buildDryCoffeeInvoiceDocDefinition, paymentAmounts } from './dry-coffee-invoice';
import { DryCoffeePurchaseResponse } from '../../../core/models/dry-coffee-purchase.model';

describe('buildDryCoffeeInvoiceDocDefinition', () => {
  it('prints two identical copies on separate pages, like macro "Imprime Factura"', () => {
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
    const second = content.at(-1) as { stack: unknown[]; pageBreak: string };

    expect(second.pageBreak).toBe('before');
    expect(second.stack).toEqual(content.slice(0, -1));
  });

  it('prints FORMAS DE PAGO from the payment split, falling back to the whole net in its method', () => {
    const payment = { cashAmount: 1944624, checkAmount: 3000000, transferAmount: 0, cardAmount: 0, checkNumber: '5379' };
    expect(paymentAmounts({ paymentMethod: 'MIXTO', netToPay: 4944624, payment }))
      .toEqual({ cash: 1944624, check: 3000000, transfer: 0, cardTerminal: 0 });
    expect(paymentAmounts({ paymentMethod: 'CHEQUE', netToPay: 500 }))
      .toEqual({ cash: 0, check: 500, transfer: 0, cardTerminal: 0 });
  });
});
