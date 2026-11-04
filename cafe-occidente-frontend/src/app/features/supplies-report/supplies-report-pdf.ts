import type { Content, ContentStack, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import type { SheetData } from 'write-excel-file/browser';

import { SuppliesReportRow } from '../../core/models/supplies.model';
import { formatDisplayNumber, formatThousands } from '../../shared/utils/number-format';
import { shortDate } from '../announcement-report/announcement-report-pdf';
import { money } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { SuppliesReport, SuppliesReportText } from './supplies-report.model';

const TEAL = '#008080';
const count = (n: number) => formatDisplayNumber(n, 'count');
const fill = (template: string, values: Record<string, string | number>) =>
  Object.entries(values).reduce((t, [k, v]) => t.replace(`{${k}}`, String(v)), template);
const dashed = (width: number): Content => ({
  canvas: [{ type: 'line', x1: 0, y1: 0, x2: width, y2: 0, lineWidth: 1.5, lineColor: TEAL, dash: { length: 2, space: 1 } }],
});
const sum = (rows: readonly SuppliesReportRow[], f: (r: SuppliesReportRow) => number) => rows.reduce((a, r) => a + f(r), 0);

/** Filas agrupadas por agencia, en el orden en que llegan (el backend ya ordena por agencia). */
export function groupByAgency(rows: readonly SuppliesReportRow[]): [string, SuppliesReportRow[]][] {
  const groups = new Map<string, SuppliesReportRow[]>();
  rows.forEach((r) => groups.set(r.agencyName, [...(groups.get(r.agencyName) ?? []), r]));
  return [...groups];
}

function footer(text: SuppliesReportText, today: Date, width: number) {
  const longDate = today.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  return (page: number, pages: number): ContentStack => ({
    stack: [
      dashed(width),
      {
        columns: [
          { text: longDate, fontSize: 8 },
          { text: fill(text.page, { page, pages }), fontSize: 8, alignment: 'right' },
        ],
        margin: [0, 2, 0, 0],
      },
    ],
    margin: [40, 10, 40, 0],
  });
}

/** MovimientoCaja / Mov Caja Menor / SuministrosRP-LF / Mov Empaque: titulo, columnas y Saldo corrido. */
function movementBody(report: SuppliesReport, rows: readonly SuppliesReportRow[], packagingType: string | null): Content[] {
  const num = report.layout === 'packaging' ? count : money;
  const detail = (r: SuppliesReportRow): TableCell[] => {
    const tail: TableCell[] = [
      { text: r.detail ?? '' },
      { text: num(r.inflow), alignment: 'right' },
      { text: num(r.outflow), alignment: 'right' },
      { text: num(r.balance ?? 0), alignment: 'right' },
    ];
    return report.layout === 'packaging'
      ? [r.transactionId, shortDate(r.date), ...tail]
      : [r.transactionId, { text: r.fundCode ?? '', alignment: 'center' }, { text: shortDate(r.date), alignment: 'right' }, ...tail];
  };
  const widths = report.layout === 'packaging' ? [60, 55, '*', 60, 60, 60] : [60, 30, 55, '*', 75, 75, 75];
  const header = report.columns.map((c, i) => ({ text: c, bold: true, fontSize: 7, alignment: i >= report.columns.length - 3 ? 'right' : 'left' }) as TableCell);
  return [
    {
      columns: [
        { text: report.pdfTitle ?? '', fontSize: 20, bold: true, color: TEAL, width: 'auto' },
        { text: packagingType ?? '', fontSize: 14, bold: true, color: TEAL, width: 'auto', margin: [12, 6, 0, 0] },
      ],
    },
    {
      table: { headerRows: 1, widths, body: [header, ...rows.map(detail)] },
      layout: {
        hLineWidth: (i: number) => (i === 1 ? 1.5 : 0),
        hLineColor: () => TEAL,
        hLineStyle: () => ({ dash: { length: 2, space: 1 } }),
        vLineWidth: () => 0,
        paddingTop: (i: number) => (i === 1 ? 8 : 3),
        paddingBottom: () => 3,
      },
    },
  ];
}

/** "RELACION CHEQUES CAJA": grupos por agencia con resumen, Suma y Suma total. */
function checksBody(report: SuppliesReport, text: SuppliesReportText, rows: readonly SuppliesReportRow[]): Content[] {
  const header: TableCell[] = report.columns.map((c, i) => ({ text: c, bold: true, color: i >= 4 ? '#b45309' : TEAL, alignment: i >= 4 ? 'right' : 'left' }));
  const body: TableCell[][] = [header];
  for (const [agency, group] of groupByAgency(rows)) {
    body.push([{ text: `${text.agencyGroup}  ${agency}`, bold: true, fontSize: 9, colSpan: 7 }, '', '', '', '', '', '']);
    group.forEach((r) =>
      body.push([
        shortDate(r.date),
        { text: formatThousands(r.idNumber), alignment: 'right' },
        r.firstNames ?? '',
        r.lastNames ?? '',
        { text: String(r.checkNumber ?? ''), alignment: 'right' },
        { text: money(r.inflow), alignment: 'right' },
        { text: money(r.outflow), alignment: 'right' },
      ]),
    );
    body.push([{ text: fill(text.checksGroupSummary, { agency, count: group.length }), colSpan: 7, color: TEAL, margin: [0, 6, 0, 0] }, '', '', '', '', '', '']);
    body.push(totalRow(text.sum, group, 5));
  }
  body.push(totalRow(text.grandTotal, rows, 5));
  return [
    dashed(515),
    { text: text.company, bold: true, color: TEAL, alignment: 'center', margin: [0, 2, 0, 0] },
    { text: text.checksTitle, bold: true, color: TEAL, alignment: 'center', margin: [0, 0, 0, 2] },
    dashed(515),
    { table: { headerRows: 1, widths: [50, 55, '*', '*', 40, 70, 70], body }, layout: underHeader() },
  ];
}

/** "FormaPago": grupos por punto de compra con resumen, Suma y Suma total. */
function paymentBody(report: SuppliesReport, text: SuppliesReportText, rows: readonly SuppliesReportRow[]): Content[] {
  const header: TableCell[] = report.columns.map((c) => ({ text: c, bold: true }));
  const body: TableCell[][] = [];
  const groups = groupByAgency(rows);
  groups.forEach(([agency, group]) => {
    body.push([
      { text: text.paymentTitle, fontSize: 16, color: '#6b7280', colSpan: 5 }, '', '', '', '',
      { text: `${text.paymentAgency}  ${agency}`, bold: true, colSpan: 3, alignment: 'right', margin: [0, 5, 0, 0] }, '', '',
    ]);
    body.push(header);
    group.forEach((r) =>
      body.push([
        r.transactionId,
        { text: r.fundCode ?? '', color: '#b91c1c' },
        { text: shortDate(r.date), alignment: 'right' },
        { text: r.idNumber, alignment: 'right' },
        r.detail ?? '',
        { text: money(r.inflow), alignment: 'right' },
        { text: money(r.outflow), alignment: 'right' },
        r.paymentMethod ?? '',
      ]),
    );
    body.push([{ text: fill(text.paymentGroupSummary, { agency, count: group.length }), colSpan: 8, margin: [10, 4, 0, 0] }, '', '', '', '', '', '', '']);
    body.push(totalRow(text.sum, group, 5, 8));
  });
  body.push(totalRow(text.grandTotal, rows, 5, 8));
  return [{ table: { widths: [55, 25, 55, 60, '*', 70, 70, 75], body }, layout: 'noBorders' }];
}

/** Fila de Suma: etiqueta en la primera celda y Entradas / Salidas en las columnas at, at+1. */
function totalRow(label: string, rows: readonly SuppliesReportRow[], at: number, columns = 7): TableCell[] {
  const cells: TableCell[] = Array.from({ length: columns }, () => '');
  cells[0] = { text: label, bold: true, colSpan: at };
  cells[at] = { text: money(sum(rows, (r) => r.inflow)), bold: true, alignment: 'right' };
  cells[at + 1] = { text: money(sum(rows, (r) => r.outflow)), bold: true, alignment: 'right' };
  return cells;
}

function underHeader() {
  return {
    hLineWidth: (i: number) => (i === 1 ? 1.5 : 0),
    hLineColor: () => TEAL,
    hLineStyle: () => ({ dash: { length: 2, space: 1 } }),
    vLineWidth: () => 0,
  };
}

/** Documento del informe segun el layout del JSON. packagingType: "Tipo" que se imprime en Empaque. */
export function buildSuppliesReportDoc(
  report: SuppliesReport,
  text: SuppliesReportText,
  rows: readonly SuppliesReportRow[],
  packagingType: string | null = null,
  today = new Date(),
): TDocumentDefinitions {
  const landscape = report.layout === 'paymentMethods';
  const content =
    report.layout === 'checks'
      ? checksBody(report, text, rows)
      : report.layout === 'paymentMethods'
        ? paymentBody(report, text, rows)
        : movementBody(report, rows, packagingType);
  return {
    pageSize: 'LETTER',
    pageOrientation: landscape ? 'landscape' : 'portrait',
    pageMargins: [40, 40, 40, 50],
    defaultStyle: { font: 'Roboto', fontSize: 8 },
    content,
    footer: footer(text, today, landscape ? 712 : 532),
  };
}

/** Rel Formas de Pago: la consulta FormaPago como hoja de Excel (fecha y montos como celdas tipadas). */
export function paymentMethodsSheet(columns: readonly string[], rows: readonly SuppliesReportRow[]): SheetData {
  const date = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number);
    return { value: new Date(Date.UTC(y, m - 1, d)), format: 'd/mm/yyyy' };
  };
  return [
    columns.map((c) => ({ value: c, fontWeight: 'bold' as const })),
    ...rows.map((r) => [
      r.transactionId, r.agencyName, r.fundCode, date(r.date), r.idNumber, r.detail,
      r.inflow, r.outflow, r.paymentMethod, r.checkNumber,
    ].map((cell) => (cell == null || cell === '' ? null : typeof cell === 'object' ? cell : { value: cell }))),
  ];
}
