import { describe, expect, it } from 'vitest';

import { AnnulmentCandidate } from '../../core/models/purchase-annulment.model';
import content from '../../../../public/assets/data/purchase-annulment.json';
import menu from '../../../../public/assets/data/purchases-menu.json';
import { PurchaseAnnulmentContent, fieldValue } from './purchase-annulment';

const data = content as PurchaseAnnulmentContent;
const candidate = {
  module: 'DRY', id: 1, agencyId: 4, agencyName: 'El Tambo', prefix: 'SDTA', invoiceNumber: 43388,
  purchaseDate: '2026-06-03', fundCode: 'RP', idNumber: '79374104', firstName: 'JESUS', lastName: null,
  specialType: 'NESPRESSO - FTUSA', netKg: 1200.5, netToPay: 9267436, paymentMethod: 'MIXTO', status: 'VALIDA',
  exported: false, annulledAt: null,
} as AnnulmentCandidate;

describe('purchase annulment', () => {
  it('the Compras menu button opens the screen', () => {
    expect(menu.options.find((o) => o.label === 'Anular Documento')?.route).toBe('/compras/anular');
  });

  it('shows the Access card values', () => {
    const v = (key: string) => fieldValue(candidate, key, data.fields.find((f) => f.key === key)?.type, data);
    expect(v('module')).toBe('Café Seco');
    expect(v('invoice')).toBe('SDTA 43388');
    expect(v('name')).toBe('JESUS');
    expect(v('purchaseDate')).toBe('03/06/2026');
    expect(v('netKg')).toBe('1.200,50');
    expect(v('netToPay')).toBe('9.267.436');
  });

  it('keeps the Access confirmation and export texts', () => {
    expect(data.confirmMessage).toContain('ESTA SEGURO QUE DESEA ANULAR ESTE DOCUMENTO SOPORTE?');
    expect(data.exportedMessage).toBe('IMPOSIBLE ANULAR EL DOCUMENTO SOPORTE PORQUE YA EXPORTO LA INFORMACION.');
  });
});
