import { describe, expect, it } from 'vitest';

import announcementReport from '../../public/assets/data/announcement-report.json';
import beneficiaryReport from '../../public/assets/data/beneficiary-report.json';
import futureReport from '../../public/assets/data/future-report.json';
import mainMenu from '../../public/assets/data/main-menu.json';
import suppliesEntry from '../../public/assets/data/supplies-entry.json';
import suppliesReport from '../../public/assets/data/supplies-report.json';
import { FuturePurchaseResponse } from './core/models/future-purchase.model';
import { DryCoffeePurchaseResponse } from './core/models/dry-coffee-purchase.model';
import { buildDryCoffeeInvoiceDocDefinition } from './features/purchase-forms/dry-coffee/dry-coffee-invoice';
import { buildFuturePurchaseManifestoDocDefinition } from './features/purchase-forms/future-purchase/future-purchase-invoice';
import { DRY } from './features/purchase-query/purchase-query.testing';

/** Prueba del 11-nov (punto 8): el nombre es "COOPERATIVA DE CAFICULTORES DE OCCIDENTE DE NARIÑO", sin "LTDA". */
describe('nombre de la cooperativa', () => {
  const ltda = /\bltda\b/i;

  it('ningún texto de pantalla, informe o PDF lleva LTDA', () => {
    const texts = [announcementReport, beneficiaryReport, futureReport, mainMenu, suppliesEntry, suppliesReport];
    texts.forEach((json) => expect(JSON.stringify(json)).not.toMatch(ltda));
  });

  it('la factura y el compromiso de compra a futuro tampoco', () => {
    const invoice = JSON.stringify(buildDryCoffeeInvoiceDocDefinition(DRY as DryCoffeePurchaseResponse, null));
    const manifesto = JSON.stringify(buildFuturePurchaseManifestoDocDefinition({
      ...DRY, announcedKg: 1000, remainingKg: 1000, deliveryDate: '2026-11-20', finca: 'LA ESPERANZA',
      municipality: 'EL TAMBO', vereda: 'CENTRO',
    } as unknown as FuturePurchaseResponse));
    expect(invoice).not.toMatch(ltda);
    expect(manifesto).not.toMatch(ltda);
    expect(invoice).toContain('DE OCCIDENTE DE NARIÑO');
  });
});
