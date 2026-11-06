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
  paymentMethod: 'CHEQUE', checkNumber: '5320',
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
      ['43901', 'EL TAMBO', 'RP', new Date(Date.UTC(2026, 5, 14)), '87304051', 'WILSON TOBIAS LEGARDA', 0, 6016407.5, 'CHEQUE', '5320'],
    );
    expect(sheet).toHaveLength(4);
    const blob = await writeExcelFile(sheet).toBlob();
    expect(new TextDecoder().decode(new Uint8Array(await blob.arrayBuffer()).slice(0, 2))).toBe('PK');
  });

  // Columnas de los CA de HU-37 a HU-49. Rel Formas de Pago lleva "Transaccion" y "Cedula" sin tilde, como Access.
  const movement = ['Trans', 'Fd', 'Fecha', 'Detalle', 'Entradas', 'Salidas', 'Saldo'];
  const supplies = ['Transacción', 'Fondo', 'Fecha', 'Detalle', 'Vr Suministro', 'Gastos o Comp', 'Saldo'];
  const checks = ['Fecha', 'Cédula', 'Nombre', 'Apellidos', 'Cheque', 'Por Sum', 'Por Comp'];
  const rfColumns: Record<string, string[]> = {
    cash: movement,
    pettyCash: movement,
    suppliesRp: supplies,
    suppliesLf: supplies,
    packaging: ['Transac', 'Fecha', 'Detalle', 'Entradas', 'Salidas', 'Saldo'],
    checks,
    checksSpecial: checks,
    paymentMethods: ['Transaccion', 'Fdo', 'Fecha', 'Cedula', 'Detalle', 'Entradas', 'Salidas', 'Forma de Pago'],
  };
  const texts = (cells: unknown[]) => cells.map((c) => (typeof c === 'string' ? c : (c as { text?: string }).text ?? ''));
  const tableRows = (node: unknown): string[][] => {
    if (Array.isArray(node)) return node.flatMap(tableRows);
    if (node === null || typeof node !== 'object') return [];
    const table = (node as { table?: { body: unknown[][] } }).table;
    return table ? table.body.map(texts) : Object.values(node).flatMap(tableRows);
  };

  it.each(Object.entries(rfColumns))('prints the RF columns of %s', (key, columns) => {
    const doc = buildSuppliesReportDoc(data.reports[key], data.text, rows, 'USADO');
    expect(tableRows(doc.content)).toContainEqual(columns);
  });

  it('puts the purchase point in the header of each payment-method group', () => {
    // HU-40 CA-1: "Punto de Compra" en el encabezado.
    const doc = buildSuppliesReportDoc(data.reports['paymentMethods'], data.text, rows);
    expect(tableRows(doc.content).map((r) => r.find((t) => t.startsWith(data.text.paymentAgency)))
      .filter(Boolean)).toEqual(['Punto de Compra:  EL TAMBO', 'Punto de Compra:  SANDONA']);
  });

  it('titles the packaging report with the chosen type', () => {
    // HU-43 CA-1: "Empaque USADO" o "Empaque NUEVO".
    const doc = buildSuppliesReportDoc(data.reports['packaging'], data.text, rows, 'USADO');
    const title = (doc.content as { columns: { text: string }[] }[])[0].columns.map((c) => c.text).join(' ');
    expect(title).toBe('Empaque USADO');
  });

  it('renders every report layout', async () => {
    pdfMake.vfs = pdfFonts;
    for (const report of Object.values(data.reports)) {
      const size = await new Promise<number>((resolve) =>
        pdfMake.createPdf(buildSuppliesReportDoc(report, data.text, rows, 'NUEVO')).getBuffer((b: Uint8Array) => resolve(b.length)),
      );
      expect(size, report.windowTitle).toBeGreaterThan(1000);
    }
  }, 20_000); // genera un PDF por informe: con cobertura pasa de los 5 s por defecto
});
