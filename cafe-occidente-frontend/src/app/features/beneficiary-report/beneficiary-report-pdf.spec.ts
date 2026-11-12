import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import type { ContentTable } from 'pdfmake/interfaces';
import { afterEach, describe, expect, it } from 'vitest';

import { BeneficiaryRow } from '../../core/models/vendor.model';
import content from '../../../../public/assets/data/beneficiary-report.json';
import { buildBeneficiaryDoc, groupByIdNumber } from './beneficiary-report-pdf';
import { BeneficiaryReportContent } from './beneficiary-report.model';

const data = content as BeneficiaryReportContent;
const row = (
  idNumber: string, invoiceNumber: number, netKg: number, netToPay: number, productCode = '0110002000002', firstName = 'BENEFICIARIO',
): BeneficiaryRow => ({
  agencyName: 'El Tambo', purchaseDate: '2026-06-03', fundCode: 'RP', invoiceNumber, idNumber, firstName,
  lastName: 'PRUEBA', greenKg: 0, netKg, healthyPercentage: 80.5, grossValue: netToPay, associateContribution: 0,
  cooperativeDiscount: 0, freightDiscount: 0, withholding: 0, otherDiscounts: 0, netToPay, specialType: 'NESPRESSO - FTUSA',
  productCode,
});
const rows = [row('99374104', 43388, 100, 1000), row('99374104', 43390, 50, 500), row('9948772', 43400, 10, 100)];

const table = (report: string, list: BeneficiaryRow[] = rows) =>
  (buildBeneficiaryDoc(data.reports[report], data.text, list).content as unknown[]).find(
    (c) => (c as ContentTable).table,
  ) as ContentTable;
const texts = (cells: unknown[]) => cells.map((c) => (typeof c === 'string' ? c : (c as { text: string }).text));

describe('beneficiary reports', () => {
  it('groups by cedula in backend order', () => {
    expect(groupByIdNumber(rows).map(([id, g]) => [id, g.length])).toEqual([['99374104', 2], ['9948772', 1]]);
  });

  it('prints the Access group summary, Suma per cedula and Suma total', () => {
    const body = table('beneficiary').table.body;
    const flat = body.map((r) => texts(r as unknown[]).join('|'));
    expect(flat).toContain(`Resumir por 'Cedula' =  99374104 (2 registros de detalle)${'|'.repeat(14)}`);
    expect(flat.some((l) => l.startsWith("Resumir por 'Cedula' =  9948772 (1 registro de detalle)"))).toBe(true);
    const grand = texts(body[body.length - 1] as unknown[]);
    expect(grand[0]).toBe('Suma total');
    expect(grand[5]).toBe('160,00');
    expect(grand[13]).toBe('1.600');
    expect(body.every((r) => (r as unknown[]).length === 15)).toBe(true);
  });

  it('HU-52: one beneficiary has a Suma per Cod_Prod and 440 + 1.000 + 250 = 1.690 KNetos in Suma total', () => {
    const one = [row('99374104', 1, 440, 4400), row('99374104', 3, 250, 2500), row('99374104', 2, 1000, 10000, '0110002000010')];
    const lines = table('beneficiary', one).table.body.map((r) => texts(r as unknown[]));
    const sums = lines.filter((l) => String(l[0]).startsWith('Suma'));
    expect(sums.map((l) => [l[0], l[5]])).toEqual([
      ['Suma 0110002000002', '690,00'],
      ['Suma 0110002000010', '1.000,00'],
      ['Suma total', '1.690,00'],
    ]);
    // Un solo beneficiario: sin "Resumir por 'Cedula'" (la Suma total ya es la suya).
    expect(lines.some((l) => String(l[0]).startsWith('Resumir'))).toBe(false);
  });

  it('RN-28.2.5: the header shows the cedula and name of the beneficiary', () => {
    const doc = JSON.stringify(buildBeneficiaryDoc(data.reports['beneficiary'], data.text, [row('99374104', 1, 10, 100)]));
    expect(doc).toContain('99.374.104   BENEFICIARIO PRUEBA');
  });

  it('with all the beneficiaries each cedula has its own name, not the first one', () => {
    const all = [row('99374104', 1, 10, 100), row('9948772', 2, 20, 200, '0110002000002', 'MARIA')];
    const doc = buildBeneficiaryDoc(data.reports['beneficiary'], data.text, all);
    const lines = table('beneficiary', all).table.body.map((r) => texts(r as unknown[])[0]);
    expect(lines).toContain('Cedula   99.374.104   BENEFICIARIO PRUEBA');
    expect(lines).toContain('Cedula   9.948.772   MARIA PRUEBA');
    // El título ya no toma el nombre de la primera fila.
    expect(JSON.stringify((doc.content as unknown[])[1])).not.toContain('BENEFICIARIO');
  });

  it('writes the NIT with dots, as the RF does', () => {
    // Access usaba comas (891,200,986-8); en Colombia se escribe con puntos.
    const doc = JSON.stringify(buildBeneficiaryDoc(data.reports['beneficiary'], data.text, rows));
    expect(doc).toContain('NIT. 891.200.986-8');
    expect(doc).not.toContain('891,200');
  });

  describe('colors from styles.css', () => {
    afterEach(() => document.documentElement.style.removeProperty('--color-report-teal'));

    it('paints the title and the group summary teal, like before', () => {
      // El mismo token del informe de Suministros, con el valor que tenia fijo (#008080).
      document.documentElement.style.setProperty('--color-report-teal', '#008080');
      const doc = JSON.stringify(buildBeneficiaryDoc(data.reports['beneficiary'], data.text, rows));
      expect(doc.match(/"color":"([^"]+)"/g)?.every((c) => c === '"color":"#008080"')).toBe(true);
      expect(doc).toContain(`"text":"${data.reports['beneficiary'].pdfTitle}","fontSize":20,"bold":true,"color":"#008080"`);
    });
  });

  it('summary layout has a Cedula header per group and 16 columns', () => {
    const body = table('summary').table.body;
    expect(texts(body[1] as unknown[])[0]).toBe('Cedula   99.374.104');
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
