import type { Content } from 'pdfmake/interfaces';
import { describe, expect, it } from 'vitest';

import { buildHuskInvoiceDocDefinition } from './husk-invoice';
import { HuskPurchaseResponse } from '../../../core/models/husk-purchase.model';

describe('buildHuskInvoiceDocDefinition', () => {
  const purchase: HuskPurchaseResponse = {
    id: 1, purchaseDate: '2026-09-28', invoiceNumber: 27900, agencyName: 'Buesaco', fundCode: 'RP',
    specialType: 'PASILLA', productCode: '0110001000007', announcementNumber: '5', announcementDate: '2026-09-28',
    basePriceDryLoad: 1113500, idNumber: '123456', firstName: 'Juan', lastName: 'Perez', growerType: 'S',
    pointPrice: 5000, costs: 692, almondWeight: 120, almondPercentage: 48, bagsCount: 2, grossKg: 100, tareKg: 2,
    netKg: 98, unitPrice: 4108, grossValue: 402584, inventoryValue: 402584, associateContribution: 8052,
    cooperativeDiscount: 0, withholding: 0, shrinkageDiscount: 0, otherDiscounts: 0, netToPay: 394532,
    address: 'Vereda', cellphone: '3001234567', paymentMethod: 'EFECTIVO', purchasePoint: 'PC', prefix: 'RN',
    dianResolution: '1', resolutionDate: '2026-01-01', resolutionFrom: 1, resolutionTo: 99999, validity: 12,
  };

  it('prints two identical copies on separate pages, like macro "Imprime Factura Pasilla"', () => {
    const content = buildHuskInvoiceDocDefinition(purchase, null).content as Content[];
    const second = content.at(-1) as { stack: Content[]; pageBreak: string };
    expect(second.pageBreak).toBe('before');
    expect(second.stack).toEqual(content.slice(0, -1));
  });

  it('uses the Pasilla rows of reports/Factura Pasilla.txt', () => {
    const text = JSON.stringify(buildHuskInvoiceDocDefinition(purchase, null).content);
    for (const label of ['Kilos Netos Pasilla', 'Precio Por Punto $:', 'VALOR KILO PASILLA $:', 'NETO A PAGAR $']) {
      expect(text).toContain(label);
    }
    expect(text).toContain('Documento Soporte de compra de café pergamino seco tipo PASILLA');
    expect(text).toContain('4.108'); // Vr_Kilo DecimalPlaces=0
    expect(text).toContain('48,00'); // PorcAlmSana DecimalPlaces=2
  });
});
