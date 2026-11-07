import type { Content } from 'pdfmake/interfaces';
import { describe, expect, it } from 'vitest';

import { buildFuturePurchaseManifestoDocDefinition } from './future-purchase-invoice';
import { FuturePurchaseResponse } from '../../../core/models/future-purchase.model';

describe('buildFuturePurchaseManifestoDocDefinition', () => {
  const purchase = {
    agencyName: 'Buesaco 1', firstName: 'TERESA', lastName: 'DIAZ', idNumber: '27197019', announcedKg: 500,
    specialType: 'NESPRESSO - FTUSA', deliveryDate: '2026-12-01', healthyUnitPrice: 8908, bonus: 40,
  } as FuturePurchaseResponse;

  it('RN-21.4.8: prints two identical copies on separate pages, like Comando36_Click', () => {
    const content = buildFuturePurchaseManifestoDocDefinition(purchase).content as Content[];
    const second = content.at(-1) as { stack: Content[]; pageBreak: string };

    expect(second.pageBreak).toBe('before');
    expect(second.stack).toEqual(content.slice(0, -1));
  });

  it('dates the letter with the local day, not the UTC one', () => {
    const local = new Date().toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });
    const first = (buildFuturePurchaseManifestoDocDefinition(purchase).content as Content[])[0] as { text: string };

    expect(first.text).toBe(`Buesaco 1, ${local}`);
  });
});
