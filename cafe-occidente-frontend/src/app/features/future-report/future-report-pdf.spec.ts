import type { TableCell } from 'pdfmake/interfaces';
import { describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/future-report.json';
import { FutureBalanceRow, PurchaseReportRow } from '../../core/models/purchase-report.model';
import { AnnouncementReportText, buildAnnouncementReportDoc } from '../announcement-report/announcement-report-pdf';
import { buildFutureBalanceDoc } from './future-balance-pdf';

const announcementText = content.announcementPdf as AnnouncementReportText;

function purchase(over: Partial<PurchaseReportRow>): PurchaseReportRow {
  return {
    module: 'DRY', invoiceNumber: 43155, purchaseDate: '2026-05-09', agencyName: 'EL TAMBO', fundCode: 'LF', prefix: 'SDTA',
    specialType: 'NESPRES', productCode: '0110001000007', productName: 'CAFÉ LFTO NESS - FTUSA', announcementNumber: 'SDTA-3443',
    idNumber: '27286462', firstName: 'MARIA', lastName: 'ESPAÑA', greenKg: null, netKg: 100, factor: 90, unitPrice: 19115,
    grossValue: 1911500, inventoryValue: 1911500, associateContribution: 0, cooperativeDiscount: 0, freightDiscount: 0,
    withholding: 0, otherDiscounts: 0, netToPay: 1911500, paymentMethod: 'EFECTIVO', status: 'VALIDA', ...over,
  };
}

const tables = (doc: { content: unknown }) =>
  (doc.content as { table?: { body: TableCell[][] } }[]).filter((c) => c.table).map((c) => c.table!.body);
const texts = (cells: TableCell[]) => cells.map((c) => (typeof c === 'string' ? c : (c as { text?: string }).text ?? ''));

describe('Informe Anuncios a Futuro (HU-34)', () => {
  // Como la captura de Access: 3.400 kg netos en el grupo y Porcent Ponderado 92,47 (3000 x 92 + 400 x 96) / 3400.
  const rows = [
    purchase({ invoiceNumber: 43155, netKg: 3000, factor: 92 }),
    purchase({ invoiceNumber: 43156, netKg: 400, factor: 96 }),
  ];
  const doc = buildAnnouncementReportDoc(rows, '9/05/2026 - 9/05/2026', announcementText);
  const [summary, detail] = tables(doc);

  it('the summary shows the weighted percentage of the capture, 92,47', () => {
    const groupRow = texts(summary[2]);
    expect(groupRow[2]).toBe('3.400');
    expect(groupRow[3]).toBe('92,47');
  });

  it('with 0 kilos the weighted percentage is 0, not "#Num!" like Access', () => {
    const annulled = buildAnnouncementReportDoc(
      [purchase({ netKg: 0, factor: 0, status: 'ANULADA', paymentMethod: 'ANULADA', netToPay: 0, grossValue: 0 })],
      '9/05/2026 - 9/05/2026', announcementText);
    expect(texts(tables(annulled)[0][2])[3]).toBe('0,00');
    expect(JSON.stringify(annulled.content)).toContain('{"text":"ANULADA","bold":true}');
  });

  it('RN-21.6.4: the detail has no Anun column, groups by Cod_Prod and adds Suma and Suma total', () => {
    expect(texts(detail[0])).toEqual(announcementText.detailColumns);
    expect(detail[0]).toHaveLength(18);
    expect(texts(detail[2])[0]).toBe('43155');
    const labels = detail.map((r) => texts(r)[0]);
    expect(labels).toContain('Suma');
    expect(labels).toContain('Suma total');
  });

  it('RN-21.6.3 and RN-21.6.6: header with COMPRAS A FUTURO, NIT with dots, agency and signatures', () => {
    const json = JSON.stringify(doc.content);
    expect(json).toContain('"text":"COMPRAS A FUTURO"');
    expect(json).toContain('NIT. 891.200.986-8');
    expect(json).toContain('EL TAMBO');
    for (const s of ['CONTABILIDAD', 'OPERACIONES', 'REVISORIA FISCAL']) {
      expect(json).toContain(s);
    }
  });
});

describe('Informe Saldo Anuncios (HU-35)', () => {
  function person(over: Partial<FutureBalanceRow>): FutureBalanceRow {
    return {
      agencyName: 'EL TAMBO', announcementNumber: 'SDTA-3443', announcementDate: '2026-05-09', idNumber: '27286462',
      firstName: 'MARIA', lastName: 'ESPAÑA', announcedKg: 1000, deliveredKg: 400, remainingKg: 600, healthyUnitPrice: 19115,
      bonus: 40, specialType: 'NESPRESSO - FTUSA', deliveryDate: '2026-06-01', ...over,
    };
  }
  const doc = buildFutureBalanceDoc(
    [person({}), person({ idNumber: '98333120', announcedKg: 500, deliveredKg: 500, remainingKg: 0 })],
    content.balancePdf);
  const [body] = tables(doc);

  it('RN-21.7.2: each person with K Anun, K Entr and Saldo Kg = K Anun - K Entr', () => {
    expect(texts(body[0])).toEqual(content.balancePdf.columns);
    expect(texts(body[2])).toEqual(['9/05/2026', '27.286.462', 'MARIA', 'ESPAÑA', '1.000,00', '400,00', '600,00',
      '19.115', '40', 'NESPRESSO - FTUSA', '1/06/2026']);
  });

  it('RN-21.7.4: grouped by announcement with Sum and Suma total of K Anun, K Entr and Saldo Kg', () => {
    expect(texts(body[1])[0]).toBe('Anuncio 3443');
    const sumRow = texts(body[4]);
    const totalRow = texts(body[5]);
    expect([sumRow[0], sumRow[4], sumRow[5], sumRow[6]]).toEqual(['Sum', '1.500,00', '900,00', '600,00']);
    expect([totalRow[0], totalRow[4], totalRow[5], totalRow[6]]).toEqual(['Suma total', '1.500,00', '900,00', '600,00']);
  });

  it('RN-21.7.3 and RN-21.7.5: header COMPRAS A FUTURO with cooperative, NIT, agency and the signatures', () => {
    const json = JSON.stringify(doc.content);
    expect(json).toContain('"text":"COMPRAS A FUTURO"');
    expect(json).toContain('NIT. 891.200.986-8');
    expect(json).toContain('EL TAMBO');
    expect(json).toContain('REVISORIA FISCAL');
    expect(doc.pageOrientation).toBe('landscape');
  });
});
