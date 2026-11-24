import { describe, expect, it } from 'vitest';

import { PurchaseReportRow } from '../../core/models/purchase-report.model';
import content from '../../../../public/assets/data/announcement-report.json';
import {
  buildAnnouncementDetailDoc, buildAnnouncementReportDoc, buildAnnouncementSummaryDoc, groupByAnnouncement, shortDate,
  weightedFactor,
} from './announcement-report-pdf';

const text = content.pdf;

function row(over: Partial<PurchaseReportRow>): PurchaseReportRow {
  return {
    module: 'DRY', invoiceNumber: 1, purchaseDate: '2026-05-09', agencyName: 'EL TAMBO', fundCode: 'LF', prefix: 'SDTA',
    specialType: 'NESPRESSO', productCode: '0110001000007', productName: null, announcementNumber: 'SDTA-3443',
    idNumber: '99286462', firstName: 'MARIA', lastName: 'PRUEBA', greenKg: null, netKg: 100, factor: 90, unitPrice: 19115,
    grossValue: 1911500, inventoryValue: 1911500, associateContribution: 0, cooperativeDiscount: 0, freightDiscount: 0,
    withholding: 0, otherDiscounts: 0, netToPay: 1911500, paymentMethod: 'EFECTIVO', status: 'VALIDA', announcementDate: '2026-05-09', ...over,
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

    expect(blocks.map((b) => `${b.announcementNumber} ${b.specialType}`)).toEqual(['TA-3443 NESPRESSO', 'TA-3444 CV', 'TA-3445 PASILLA']);
    expect(blocks[0].groups.map((g) => [g.productCode, g.rows.map((p) => p.invoiceNumber)])).toEqual([
      ['A', [1, 4]],
      ['B', [3]],
    ]);
  });

  it('6C: prints the cooperative NIT stored without separators, formatted as before', () => {
    expect(json([row({})])).toContain('NIT. 891.200.986-8');
  });

  it('weights the factor by KN only over rows with a factor, and is empty when none has one', () => {
    expect(weightedFactor([row({ netKg: 100, factor: 90 }), row({ netKg: 300, factor: 94 })])).toBe(93);
    expect(weightedFactor([row({ netKg: 0 })])).toBe(0);
    expect(weightedFactor([row({ factor: null })])).toBeNull();
  });

  it('formats the date as dd/mm/aaaa (hallazgo #1)', () => {
    expect(shortDate('2026-05-09')).toBe('09/05/2026');
  });

  it('prueba del 19-nov: the detail starts with Factura (no Anun column: each announcement goes on its own sheet)', () => {
    const doc = buildAnnouncementReportDoc([row({})], '9/05/2026', text);
    const tables = (doc.content as { table?: { body: unknown[][] } }[]).filter((c) => c.table);
    const detail = tables[1].table!.body;
    expect(detail[0][0]).toMatchObject({ text: 'Factura' });
    expect(detail[2][0]).toMatchObject({ text: '1' });
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

  it('shows KV 0 outside green coffee, like Access, and adds it up in green coffee', () => {
    const dry = buildAnnouncementReportDoc([row({})], '9/05/2026', text);
    const detail = (dry.content as { table?: { body: unknown[][] } }[]).filter((c) => c.table)[1].table!.body;
    expect(detail[2][6]).toMatchObject({ text: '0' });
    expect(json([row({ module: 'GREEN', specialType: 'CV', greenKg: 1250.5 })])).toContain('"1.250,50"');
  });

  it('titles each Cod_Prod group with the code and its Nom, like Access', () => {
    expect(json([row({ productName: 'CAFÉ LFTO NESS - FTUSA' })])).toContain('"0110001000007 CAFÉ LFTO NESS - FTUSA"');
    expect(json([row({ productCode: '999', productName: null })])).toContain('"999"');
  });
});


/** Prueba del 19-nov: los dos reportes como las fotos del 3590 NESPRESSO de El Ingenio (docs/referencia-reportes). */
describe('Generar Reporte Anuncio como Access', () => {
  const rows = [
    row({ invoiceNumber: 31307, announcementNumber: 'SDIN-3590', specialType: 'NESPRESSO - FTUSA', agencyName: 'El Ingenio',
      productCode: '0110002000001', productName: 'CAFÉ R.P. NESS - FTUSA', netKg: 200, factor: 89.29, firstName: 'EDILIO JOSE',
      lastName: 'AGREDA PAZ', growerFirstName: 'EDILIO', growerFirstLastName: 'AGREDA', announcementDate: '2026-11-15',
      purchaseDate: '2026-11-15', trustedName: 'LUIS FIEL ROSERO', grossValue: 3521200, netToPay: 3493030 }),
    row({ invoiceNumber: 31308, announcementNumber: 'SDIN-3590', specialType: 'NESPRESSO - FTUSA', agencyName: 'El Ingenio',
      productCode: '0110002000001', productName: 'CAFÉ R.P. NESS - FTUSA', netKg: 45, factor: 102.94, firstName: 'HERNAN',
      lastName: 'MARTINEZ', announcementDate: '2026-11-15', purchaseDate: '2026-11-15', trustedName: 'LUIS FIEL ROSERO',
      grossValue: 740565, netToPay: 725754, status: 'ANULADA', paymentMethod: 'ANULADA' }),
    row({ invoiceNumber: 31400, announcementNumber: 'SDIN-3591', specialType: 'RN', agencyName: 'El Ingenio',
      announcementDate: '2026-11-16', purchaseDate: '2026-11-16' }),
  ];
  const content = (doc: { content: unknown }) => JSON.stringify(doc.content);
  const tables = (doc: { content: unknown }) =>
    (doc.content as { table?: { body: { text?: string }[][] } }[]).filter((c) => c.table).map((c) => c.table!.body);

  it('resumen and detallado are separate documents, each announcement on its own landscape sheet', () => {
    const summary = buildAnnouncementSummaryDoc(rows, text);
    const detail = buildAnnouncementDetailDoc(rows, text);

    for (const doc of [summary, detail]) {
      expect(doc.pageOrientation).toBe('landscape');
      expect((doc.content as { pageBreak?: string }[]).filter((c) => c.pageBreak === 'before')).toHaveLength(1);
    }
    expect(tables(summary)[0][0].map((c) => c.text)).toEqual(text.summaryColumns);
    expect(tables(detail)[0][0].map((c) => c.text)).toEqual(text.detailColumns);
  });

  it('heads each sheet with ANUNCIO, the number and the special, the agency and the date of the announcement', () => {
    const json = content(buildAnnouncementSummaryDoc(rows, text));
    expect(json).toContain('"text":"ANUNCIO"');
    expect(json).toContain('"text":"3590 NESPRESSO - FTUSA"');
    expect(json).toContain('EL INGENIO');
    expect(json).toContain('15/11/2026');
    expect(json).toContain('"text":"3591 RN"');
    expect(json).not.toContain('SDIN-3590');
  });

  it('detallado: no Anun column, first name and first surname as in Access, FP is the state of the purchase', () => {
    const body = tables(buildAnnouncementDetailDoc(rows, text))[0];
    const first = body[2] as unknown as { text?: string }[];
    expect(text.detailColumns).not.toContain('Anun');
    expect(first[0]).toMatchObject({ text: '31307' });
    expect([first[4], first[5]]).toEqual(['EDILIO', 'AGREDA']);
    expect(first[7]).toMatchObject({ text: '200,00' });
    expect(first[8]).toMatchObject({ text: '89,29' });
    expect(first.at(-1)).toBe('VALIDA');
    expect(body[3].at(-1)).toMatchObject({ text: 'ANULADA', bold: true });
    expect(JSON.stringify(body)).toContain("Resumir por 'Cod_Prod' =  0110002000001 (2 registros de detalle)");
  });

  it('resumen: weighted factor Sum(KN x Factor) / Sum(KN), money without decimals and SUMA TOTAL', () => {
    const body = tables(buildAnnouncementSummaryDoc(rows, text))[0];
    const group = body[2] as unknown as { text?: string }[];
    expect(group.slice(1, 4).map((c) => c.text)).toEqual(['0', '245', '91,79']);
    expect(group[4]).toMatchObject({ text: '4.261.765' });
    expect(body.at(-1)![0]).toMatchObject({ text: 'SUMA TOTAL' });
  });

  it('signs with Fiel and the name of the trusted person of the Control Record, and the three signatures', () => {
    const json = content(buildAnnouncementDetailDoc(rows, text));
    expect(json).toContain('LUIS FIEL ROSERO');
    for (const signature of ['CONTABILIDAD', 'OPERACIONES', 'REVISORIA FISCAL']) {
      expect(json).toContain(signature);
    }
  });
});
