import { describe, expect, it } from 'vitest';

import { GreenCoffeePurchaseResponse } from '../../../core/models/green-coffee-purchase.model';
import { buildGreenCoffeeInvoiceDocDefinition } from './green-coffee-invoice';

const purchase = {
  id: 1, purchaseDate: '2026-10-29', invoiceNumber: 27875, agencyName: 'Buesaco 1', fundCode: 'RP', specialType: 'CV',
  productCode: '1', announcementNumber: 'SDBU-5', announcementDate: '2026-10-29', basePriceLoad: 1546900,
  idNumber: '990006003', firstName: 'PRUEBA', lastName: 'FECHAS', growerType: 'C', address: 'VEREDA', cellphone: '300',
  bagsCount: 5, grossKg: 1000, tareKg: 0, greenKg: 1000, netKg: 500, healthyUnitPrice: 0, defectiveUnitPrice: 6000,
  bonus: 80000, costs: 0, penalty: 0, compKgPrice: 12000, unitPrice: 24000, grossValue: 12000000, inventoryValue: 12000000,
  associateContribution: 0, cooperativeDiscount: 96000, withholding: 60000, shrinkageDiscount: 0, otherDiscounts: 0,
  netToPay: 11844000, paymentMethod: 'EFECTIVO', checkNumber: null,
  sample: { totalStoredWeight: 140, wastePercentage: 44, huskWeight: 12.5, huskPercentage: 5, healthyStoredWeight: 186.18, factor: 94 },
  purchasePoint: 'BUESACO', prefix: 'SDBU', dianResolution: '1', resolutionDate: '2026-01-01', resolutionFrom: 1,
  resolutionTo: 99999, validity: 12,
} as GreenCoffeePurchaseResponse;

/** Etiquetas de la columna izquierda de la tabla central, en orden. */
function labels(doc: ReturnType<typeof buildGreenCoffeeInvoiceDocDefinition>): string[] {
  const text = JSON.stringify(doc.content);
  return ['Sacos', 'Kilos Brutos', 'Destare', 'Kilos Verdes', 'Kilos Netos Pergamino'].sort(
    (a, b) => text.indexOf(`"${a}"`) - text.indexOf(`"${b}"`),
  );
}

describe('buildGreenCoffeeInvoiceDocDefinition ("Factura verdes")', () => {
  it('has the dry coffee layout with Kilos Verdes between Destare and Kilos Netos, like Access', () => {
    const doc = buildGreenCoffeeInvoiceDocDefinition(purchase, null);
    expect(labels(doc)).toEqual(['Sacos', 'Kilos Brutos', 'Destare', 'Kilos Verdes', 'Kilos Netos Pergamino']);
    expect(JSON.stringify(doc.content)).toContain('"1.000,00"');
  });

  it('takes the sample of the Registro de Control and the header of the invoice', () => {
    const text = JSON.stringify(buildGreenCoffeeInvoiceDocDefinition(purchase, null).content);
    expect(text).toContain('SDBU - 27.875');
    expect(text).toContain('186,18');
    expect(text).toContain('94,00');
    expect(text).toContain('11.844.000,00');
  });

  it('a purchase saved before the sample (V15) prints the sample rows in zero', () => {
    const text = JSON.stringify(buildGreenCoffeeInvoiceDocDefinition({ ...purchase, sample: null }, null).content);
    expect(text).toContain('"Kilos Verdes"');
    expect(text).not.toContain('186,18');
  });

  it('prints two copies on separate pages, like the dry coffee invoice', () => {
    const content = buildGreenCoffeeInvoiceDocDefinition(purchase, null).content as unknown[];
    expect((content.at(-1) as { pageBreak: string }).pageBreak).toBe('before');
  });
});
