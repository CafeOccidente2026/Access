import { describe, expect, it } from 'vitest';

import { PurchaseReportRow } from '../../core/models/purchase-report.model';
import content from '../../../../public/assets/data/announcement-report.json';
import { buildAnnouncementReportDoc, groupByAnnouncement, shortDate, weightedFactor } from './announcement-report-pdf';

const text = content.pdf;

function row(over: Partial<PurchaseReportRow>): PurchaseReportRow {
  return {
    module: 'DRY', invoiceNumber: 1, purchaseDate: '2026-05-09', agencyName: 'EL TAMBO', fundCode: 'LF', prefix: 'SDTA',
    specialType: 'NESPRESSO', productCode: '0110001000007', productName: null, announcementNumber: 'SDTA-3443',
    idNumber: '27286462', firstName: 'MARIA', lastName: 'ESPAÑA', greenKg: null, netKg: 100, factor: 90, unitPrice: 19115,
    grossValue: 1911500, inventoryValue: 1911500, associateContribution: 0, cooperativeDiscount: 0, freightDiscount: 0,
    withholding: 0, otherDiscounts: 0, netToPay: 1911500, paymentMethod: 'EFECTIVO', status: 'VALIDA', ...over,
  };
}

const json = (rows: PurchaseReportRow[]) => JSON.stringify(buildAnnouncementReportDoc(rows, '9/05/2026', text).content);

describe('announcement report', () => {
  it('RN-24.3: one block per announcement and type of coffee, grouped by Cod_Prod and ordered by invoice', () => {
    const blocks = groupByAnnouncement([
      row({ invoiceNumber: 3, productCode: 'B' }),
      row({ invoiceNumber: 1, productCode: 'A' }),
      row({ invoiceNumber: 2, module: 'GREEN', announcementNumber: 'SDTA-3444', specialType: 'CV' }),
      row({ invoiceNumber: 4, productCode: 'A' }),
      row({ invoiceNumber: 5, module: 'HUSK', announcementNumber: 'SDTA-3445', specialType: 'PASILLA' }),
    ]);

    expect(blocks.map((b) => `${b.announcementNumber} ${b.specialType}`)).toEqual(['3443 NESPRESSO', '3444 CV', '3445 PASILLA']);
    expect(blocks[0].groups.map((g) => [g.productCode, g.rows.map((p) => p.invoiceNumber)])).toEqual([
      ['A', [1, 4]],
      ['B', [3]],
    ]);
  });

  it('weights the factor by KN only over rows with a factor, and is empty when none has one', () => {
    expect(weightedFactor([row({ netKg: 100, factor: 90 }), row({ netKg: 300, factor: 94 })])).toBe(93);
    expect(weightedFactor([row({ netKg: 0 })])).toBe(0);
    expect(weightedFactor([row({ factor: null })])).toBeNull();
  });

  it('formats the date like Access Short Date', () => {
    expect(shortDate('2026-05-09')).toBe('9/05/2026');
  });

  it('RN-24.2: the detail has the announcement number column', () => {
    const doc = buildAnnouncementReportDoc([row({})], '9/05/2026', text);
    const tables = (doc.content as { table?: { body: unknown[][] } }[]).filter((c) => c.table);
    const detail = tables[1].table!.body;
    expect(detail[0][0]).toMatchObject({ text: 'Anun' });
    expect(detail[2][0]).toBe('3443');
  });

  it('RN-24.4: one PDF, summary first and detail after, each block on its own page', () => {
    const doc = buildAnnouncementReportDoc([row({}), row({ invoiceNumber: 2, announcementNumber: 'SDTA-3444' })], '9/05/2026', text);
    const titles = JSON.stringify(doc.content).match(/"text":"(RESUMEN ANUNCIO|ANUNCIO)"/g);
    expect(titles).toEqual([
      '"text":"RESUMEN ANUNCIO"', '"text":"RESUMEN ANUNCIO"', '"text":"ANUNCIO"', '"text":"ANUNCIO"',
    ]);
    expect(doc.pageOrientation).toBe('landscape');
    expect((doc.content as { pageBreak?: string }[]).filter((c) => c.pageBreak === 'before')).toHaveLength(3);
  });

  it('an annulled purchase shows its values in zero and ANULADA', () => {
    expect(json([row({ status: 'ANULADA', paymentMethod: 'ANULADA', netKg: 0, netToPay: 0, grossValue: 0 })]))
      .toContain('{"text":"ANULADA","bold":true}');
  });

  it('leaves KV empty outside green coffee and adds it up in green coffee', () => {
    const dry = buildAnnouncementReportDoc([row({})], '9/05/2026', text);
    const detail = (dry.content as { table?: { body: unknown[][] } }[]).filter((c) => c.table)[1].table!.body;
    expect(detail[2][7]).toMatchObject({ text: '' });
    expect(json([row({ module: 'GREEN', specialType: 'CV', greenKg: 1250.5 })])).toContain('"1.250,50"');
  });

  it('titles each Cod_Prod group with the code and its Nom, like Access', () => {
    expect(json([row({ productName: 'CAFÉ LFTO NESS - FTUSA' })])).toContain('"0110001000007 CAFÉ LFTO NESS - FTUSA"');
    expect(json([row({ productCode: '999', productName: null })])).toContain('"999"');
  });
});
