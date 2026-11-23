import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { PurchaseReportRow } from '../../core/models/purchase-report.model';
import { ExportCell, ExportRow, ExportTable } from '../../shared/utils/export-table';
import { announcementDisplay, formatKilos, formatMoney, formatTyped } from '../../shared/utils/number-format';
import { shortDate } from '../announcement-report/announcement-report-pdf';

/**
 * Informe "COMPRAS POR FECHA" de Access (reports/COMPRAS POR FECHA.txt, desde el "Diálogo Compras"):
 * las compras entre dos fechas agrupadas por Agencia, con "Resumir por 'Agencia'", "Suma" y
 * "Suma total", y al pie la fecha de generacion y "Página x de y" (RN-25.3). Columnas en el orden del
 * RF-25 (Access ponia Ot Dsctos al final). Seco, verde y pasilla; una anulada sale en 0 y "ANULADA".
 */

type Row = PurchaseReportRow;

/** Textos del PDF (date-range-dialog.json, "pdf"). */
export interface PurchasesByDateText {
  readonly title: string;
  readonly range: string;
  readonly columns: readonly string[];
  readonly groupSummary: string;
  readonly oneRecord: string;
  readonly manyRecords: string;
  readonly sum: string;
  readonly total: string;
  readonly annulled: string;
  readonly page: string;
}

const COLUMNS = 16;
/** Columnas con suma: KN y los 7 montos (Vr Inventario ... Neto a Pag). */
const KN = 5;
const MONEY: readonly (keyof Row)[] = [
  'inventoryValue', 'associateContribution', 'cooperativeDiscount', 'freightDiscount', 'withholding', 'otherDiscounts', 'netToPay',
];

const fill = (template: string, values: Record<string, string | number>): string =>
  template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? ''));
const kg = (v: number | null): string => (v == null ? '' : formatKilos(v));
const pesos = formatMoney;
const r = (value: string, extra: object = {}): TableCell => ({ text: value, alignment: 'right', ...extra });

/** Suma en pesos enteros (Q3: los montos se guardan en pesos), sin arrastrar decimales de coma flotante. */
export function sumPesos(rows: readonly Row[], f: (p: Row) => number): number {
  return rows.reduce((acc, p) => acc + Math.round(f(p)), 0);
}

/** Suma de kilos netos en centesimas (2 decimales, Q3) y vuelta a kilos. */
export function sumKg(rows: readonly Row[]): number {
  return rows.reduce((acc, p) => acc + Math.round(p.netKg * 100), 0) / 100;
}

function line(p: Row, text: PurchasesByDateText): TableCell[] {
  return [
    announcementDisplay(p.announcementNumber), shortDate(p.purchaseDate), p.fundCode,
    `${p.prefix ?? ''} ${p.invoiceNumber}`.trim(), p.idNumber, r(kg(p.netKg)), r(formatTyped(p.factor)), r(pesos(p.unitPrice)),
    ...MONEY.map((f) => r(pesos(p[f] as number))),
    p.status === 'ANULADA' ? { text: `${p.specialType} ${text.annulled}`, bold: true } : p.specialType,
  ];
}

function sums(label: string, rows: readonly Row[]): TableCell[] {
  return [
    { text: label, bold: true, colSpan: KN }, '', '', '', '',
    r(kg(sumKg(rows)), { bold: true }), '', '',
    ...MONEY.map((f) => r(pesos(sumPesos(rows, (p) => p[f] as number)), { bold: true })),
    '',
  ];
}

/** Agencias en orden alfabetico y, dentro de cada una, compras por factura (como el informe de Access). */
function byAgency(rows: readonly Row[]): [string, Row[]][] {
  const groups = new Map<string, Row[]>();
  for (const p of [...rows].sort((a, b) => a.agencyName.localeCompare(b.agencyName) || a.invoiceNumber - b.invoiceNumber)) {
    groups.set(p.agencyName, [...(groups.get(p.agencyName) ?? []), p]);
  }
  return [...groups.entries()];
}

export function buildPurchasesByDateDoc(
  rows: readonly Row[],
  from: string,
  to: string,
  text: PurchasesByDateText,
  now: Date = new Date(),
): TDocumentDefinitions {
  const body: TableCell[][] = [
    text.columns.map((c, i) => ({ text: c, bold: true, fontSize: 6, alignment: i >= KN && i < COLUMNS - 1 ? 'right' : 'left' })),
  ];
  for (const [agency, agencyRows] of byAgency(rows)) {
    body.push([{ text: agency, bold: true, fontSize: 9, colSpan: COLUMNS, margin: [0, 3, 0, 0] }, ...Array(COLUMNS - 1).fill('')]);
    body.push(...agencyRows.map((p) => line(p, text)));
    const n = agencyRows.length;
    body.push([
      { text: fill(text.groupSummary, { agency, count: n, records: n === 1 ? text.oneRecord : text.manyRecords }), bold: true, colSpan: COLUMNS },
      ...Array(COLUMNS - 1).fill(''),
    ]);
    body.push(sums(text.sum, agencyRows));
  }
  body.push(sums(text.total, rows));

  const content: Content[] = [
    { text: text.title, bold: true, fontSize: 16 },
    { text: fill(text.range, { from: shortDate(from), to: shortDate(to) }), fontSize: 8, margin: [0, 0, 0, 6] },
    {
      table: { headerRows: 1, widths: [30, 42, 18, 52, 50, 42, 30, 36, 52, 42, 42, 38, 46, 42, 52, '*'], body },
      layout: {
        hLineWidth: (i: number) => (i <= 1 ? 0.5 : 0), vLineWidth: () => 0,
        paddingLeft: () => 1, paddingRight: () => 1, paddingTop: () => 1, paddingBottom: () => 1,
      },
    },
  ];
  const generated = now.toLocaleString('es-CO');
  return {
    pageSize: 'LETTER',
    pageOrientation: 'landscape',
    pageMargins: [20, 20, 20, 30],
    content,
    footer: (page: number, pages: number) => ({
      margin: [20, 0, 20, 0],
      fontSize: 6,
      columns: [
        { text: generated, width: 250 },
        { text: fill(text.page, { page, pages }), width: 250, alignment: 'center' },
      ],
    }),
    defaultStyle: { font: 'Roboto', fontSize: 7 },
  };
}

/** Una compra para Exportar a Excel: los textos como en el PDF; kilos, Porc y pesos como numeros. */
function exportLine(p: Row, text: PurchasesByDateText): ExportCell[] {
  const kg = (value: number | null): ExportCell => (value == null ? null : { value, format: 'kg' });
  return [
    announcementDisplay(p.announcementNumber), shortDate(p.purchaseDate), p.fundCode, `${p.prefix ?? ''} ${p.invoiceNumber}`.trim(),
    p.idNumber, kg(p.netKg), kg(p.factor), { value: p.unitPrice, format: 'money' },
    ...MONEY.map((f): ExportCell => ({ value: p[f] as number, format: 'money' })),
    p.status === 'ANULADA' ? `${p.specialType} ${text.annulled}` : p.specialType,
  ];
}

function exportSums(label: string, rows: readonly Row[]): ExportRow {
  return {
    kind: 'total',
    cells: [label, null, null, null, null, { value: sumKg(rows), format: 'kg' }, null, null,
      ...MONEY.map((f): ExportCell => ({ value: sumPesos(rows, (p) => p[f] as number), format: 'money' })), null],
  };
}

/** Demo del 23-nov: el informe para Exportar a Excel, con las columnas, grupos por agencia y sumas del PDF. */
export function purchasesByDateTable(rows: readonly Row[], from: string, to: string, text: PurchasesByDateText): ExportTable {
  const body: ExportRow[] = [];
  for (const [agency, agencyRows] of byAgency(rows)) {
    const n = agencyRows.length;
    body.push({ kind: 'group', cells: [agency] }, ...agencyRows.map((p) => ({ cells: exportLine(p, text) })),
      { kind: 'summary', cells: [fill(text.groupSummary, { agency, count: n, records: n === 1 ? text.oneRecord : text.manyRecords })] },
      exportSums(text.sum, agencyRows));
  }
  body.push(exportSums(text.total, rows));
  return { title: text.title, subtitle: fill(text.range, { from: shortDate(from), to: shortDate(to) }), columns: text.columns, rows: body };
}
