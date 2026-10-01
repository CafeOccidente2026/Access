import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import type { ContentTable } from 'pdfmake/interfaces';
import { describe, expect, it } from 'vitest';

import { BeneficiaryRow } from '../../core/models/vendor.model';
import content from '../../../../public/assets/data/beneficiary-report.json';
import { buildBeneficiaryDoc, groupByIdNumber } from './beneficiary-report-pdf';
import { BeneficiaryReportContent } from './beneficiary-report.model';

const data = content as BeneficiaryReportContent;
const row = (idNumber: string, invoiceNumber: number, netKg: number, netToPay: number): BeneficiaryRow => ({
  agencyName: 'El Tambo', purchaseDate: '2026-06-03', fundCode: 'RP', invoiceNumber, idNumber, firstName: 'JESUS',
  lastName: 'BENAVIDES', greenKg: 0, netKg, healthyPercentage: 80.5, grossValue: netToPay, associateContribution: 0,
  cooperativeDiscount: 0, freightDiscount: 0, withholding: 0, otherDiscounts: 0, netToPay, specialType: 'NESPRESSO - FTUSA',
});
const rows = [row('79374104', 43388, 100, 1000), row('79374104', 43390, 50, 500), row('5248772', 43400, 10, 100)];

const table = (report: string) =>
  (buildBeneficiaryDoc(data.reports[report], data.text, rows).content as unknown[]).find(
    (c) => (c as ContentTable).table,
  ) as ContentTable;
const texts = (cells: unknown[]) => cells.map((c) => (typeof c === 'string' ? c : (c as { text: string }).text));

describe('beneficiary reports', () => {
  it('groups by cedula in backend order', () => {
    expect(groupByIdNumber(rows).map(([id, g]) => [id, g.length])).toEqual([['79374104', 2], ['5248772', 1]]);
  });

  it('prints the Access group summary, Suma per cedula and Suma total', () => {
    const body = table('beneficiary').table.body;
    const flat = body.map((r) => texts(r as unknown[]).join('|'));
    expect(flat).toContain(`Resumir por 'Cedula' =  79374104 (2 registros de detalle)${'|'.repeat(14)}`);
    expect(flat.some((l) => l.startsWith("Resumir por 'Cedula' =  5248772 (1 registro de detalle)"))).toBe(true);
    const grand = texts(body[body.length - 1] as unknown[]);
    expect(grand[0]).toBe('Suma total');
    expect(grand[5]).toBe('160,00');
    expect(grand[13]).toBe('1.600');
    expect(body.every((r) => (r as unknown[]).length === 15)).toBe(true);
  });

  it('summary layout has a Cedula header per group and 16 columns', () => {
    const body = table('summary').table.body;
    expect(texts(body[1] as unknown[])[0]).toBe('Cedula   79.374.104');
    expect(body.every((r) => (r as unknown[]).length === 16)).toBe(true);
  });

  it('renders both reports', async () => {
    pdfMake.vfs = pdfFonts;
    for (const report of Object.values(data.reports)) {
      const size = await new Promise<number>((resolve) =>
        pdfMake.createPdf(buildBeneficiaryDoc(report, data.text, rows)).getBuffer((b: Uint8Array) => resolve(b.length)),
      );
      expect(size, report.pdfTitle).toBeGreaterThan(1000);
    }
  });
});
