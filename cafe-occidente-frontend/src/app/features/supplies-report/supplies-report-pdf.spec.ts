import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { describe, expect, it } from 'vitest';
import writeExcelFile from 'write-excel-file/browser';

import { SuppliesReportRow } from '../../core/models/supplies.model';
import content from '../../../../public/assets/data/supplies-report.json';
import { buildSuppliesReportDoc, groupByAgency, paymentMethodsSheet } from './supplies-report-pdf';
import { SuppliesReportContent } from './supplies-report.model';

const data = content as SuppliesReportContent;
const row = (agencyName: string, transactionId: string, inflow: number, outflow: number): SuppliesReportRow => ({
  agencyName, transactionId, fundCode: 'RP', date: '2026-06-14', idNumber: '87304051', firstNames: 'WILSON TOBIAS',
  lastNames: 'LEGARDA', detail: 'WILSON TOBIAS LEGARDA', inflow, outflow, balance: inflow - outflow,
  paymentMethod: 'CHEQUE', checkNumber: 5320,
});
const rows = [row('EL TAMBO', '43901', 0, 6016407.5), row('EL TAMBO', '43902', 100, 0), row('SANDONA', 'T1', 0, 50)];

describe('supplies reports', () => {
  it('groups rows by agency keeping the backend order', () => {
    expect(groupByAgency(rows).map(([a, g]) => [a, g.length])).toEqual([['EL TAMBO', 2], ['SANDONA', 1]]);
  });

  it('exports FormaPago as a real xlsx with the Access columns and typed cells', async () => {
    const sheet = paymentMethodsSheet(data.reports['paymentMethods'].excel!.columns, rows);
    expect(sheet[0].map((c) => (c as { value: string }).value)).toEqual(
      ['Id_Transaccion', 'Agencia', 'Fondo', 'Fecha', 'Cedula', 'Detalle', 'Entradas', 'Salidas', 'Forma_de_Pago', 'Cheque'],
    );
    expect(sheet[1].map((c) => (c as { value: unknown } | null)?.value ?? null)).toEqual(
      ['43901', 'EL TAMBO', 'RP', new Date(Date.UTC(2026, 5, 14)), '87304051', 'WILSON TOBIAS LEGARDA', 0, 6016407.5, 'CHEQUE', 5320],
    );
    expect(sheet).toHaveLength(4);
    const blob = await writeExcelFile(sheet).toBlob();
    expect(new TextDecoder().decode(new Uint8Array(await blob.arrayBuffer()).slice(0, 2))).toBe('PK');
  });

  it('renders every report layout', async () => {
    pdfMake.vfs = pdfFonts;
    for (const report of Object.values(data.reports)) {
      const size = await new Promise<number>((resolve) =>
        pdfMake.createPdf(buildSuppliesReportDoc(report, data.text, rows, 'NUEVO')).getBuffer((b: Uint8Array) => resolve(b.length)),
      );
      expect(size, report.windowTitle).toBeGreaterThan(1000);
    }
  });
});
