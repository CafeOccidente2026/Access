import { describe, expect, it } from 'vitest';

import { PurchaseReportRow } from '../../core/models/purchase-report.model';
import content from '../../../../public/assets/data/date-range-dialog.json';
import { buildPurchasesByDateDoc, sumKg, sumPesos } from './purchases-by-date-pdf';

const text = content.pdf;

function row(over: Partial<PurchaseReportRow>): PurchaseReportRow {
  return {
    module: 'DRY', invoiceNumber: 27869, purchaseDate: '2026-10-29', agencyName: 'Buesaco 1', fundCode: 'RP', prefix: 'SDBU',
    specialType: 'RN', productCode: '0110002000002', productName: null, announcementNumber: 'SDBU-4', idNumber: '990006002',
    firstName: 'PRUEBA', lastName: 'REPORTE', greenKg: null, netKg: 1000, factor: 92.11, unitPrice: 12417, grossValue: 12417000,
    inventoryValue: 12417000, associateContribution: 0, cooperativeDiscount: 99336, freightDiscount: 0, withholding: 62085,
    otherDiscounts: 0, netToPay: 12255579, paymentMethod: 'EFECTIVO', status: 'VALIDA', ...over,
  };
}

type Body = (string | { text?: string; colSpan?: number })[][];
const NOW = new Date(2026, 9, 29, 9, 30);

function table(rows: PurchaseReportRow[]): Body {
  const doc = buildPurchasesByDateDoc(rows, '2026-10-01', '2026-10-29', text, NOW);
  return (doc.content as { table?: { body: Body } }[]).find((c) => c.table)!.table!.body;
}
const cell = (c: string | { text?: string }) => (typeof c === 'string' ? c : c.text ?? '');

describe('Compras por fecha (RF-25)', () => {
  it('has the 16 columns of the RF in their order', () => {
    expect(table([row({})])[0].map(cell)).toEqual([
      'Anun', 'Fecha Compra', 'Fdo', 'Factura', 'Cédula', 'KN', 'Porc', 'VR Kilo', 'Vr Inventario', 'Ap Socio',
      'Dto Coop', 'Dto Fro', 'Retefuente', 'Ot Dsctos', 'Neto a Pag', 'Especial',
    ]);
  });

  it('shows a purchase with its prefix, its factor as Porc and empty Porc when it does not apply', () => {
    const body = table([row({}), row({ invoiceNumber: 27871, module: 'GREEN', specialType: 'CV', factor: null })]);
    const dry = body[2].map(cell);
    expect(dry.slice(0, 8)).toEqual(['4', '29/10/2026', 'RP', 'SDBU 27869', '990006002', '1.000,00', '92,11', '12.417']);
    expect(dry[14]).toBe('12.255.579');
    expect(body[3].map(cell)[6]).toBe('');
  });

  it('RN-25.3: groups by agency with its sums and a grand total', () => {
    const body = table([row({ agencyName: 'Buesaco 1' }), row({ invoiceNumber: 2, agencyName: 'Buesaco 1', netKg: 500.5, netToPay: 100 })]);
    expect(cell(body[1][0])).toBe('Buesaco 1');
    const texts = body.map((r) => r.map(cell));
    expect(texts.some((r) => r[0] === "Resumir por 'Agencia' = Buesaco 1 (2 registros de detalle)")).toBe(true);
    const sum = texts.find((r) => r[0] === 'Suma')!;
    const total = texts.find((r) => r[0] === 'Suma total')!;
    for (const r of [sum, total]) {
      expect(r[5]).toBe('1.500,50');
      expect(r[14]).toBe('12.255.679');
    }
  });

  it('an annulled purchase shows its values in zero and ANULADA', () => {
    const body = table([row({ status: 'ANULADA', paymentMethod: 'ANULADA', netKg: 0, netToPay: 0, inventoryValue: 0 })]);
    const r = body[2].map(cell);
    expect(r[5]).toBe('0,00');
    expect(r[14]).toBe('0');
    expect(r[15]).toBe('RN ANULADA');
  });

  it('RN-25.3: shows the generation date and the page number', () => {
    const doc = buildPurchasesByDateDoc([row({})], '2026-10-01', '2026-10-29', text, NOW);
    const footer = JSON.stringify((doc.footer as (p: number, n: number) => unknown)(2, 5));
    expect(footer).toContain('Página 2 de 5');
    expect(footer).toContain('29/10/2026');
    expect(JSON.stringify(doc.content)).toContain('Desde 1/10/2026 hasta 29/10/2026');
    expect(doc.pageOrientation).toBe('landscape');
  });

  it('adds money in whole pesos and kilos in hundredths, without floating point drift', () => {
    const rows = [row({ netKg: 0.1, netToPay: 0.4 }), row({ netKg: 0.2, netToPay: 0.4 })];
    expect(sumKg(rows)).toBe(0.3);
    expect(sumPesos(rows, (p) => p.netToPay)).toBe(0);
  });
});
