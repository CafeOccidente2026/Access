import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { BeneficiaryRow } from '../../core/models/vendor.model';
import { formatThousands } from '../../shared/utils/number-format';
import { shortDate } from '../announcement-report/announcement-report-pdf';
import { BeneficiaryReport, BeneficiaryReportText } from './beneficiary-report.model';

const TEAL = '#008080';
const peso = (v: number): string => Math.round(v).toLocaleString('es-CO');
const dec2 = (v: number): string => v.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const fill = (template: string, values: Record<string, string | number>) =>
  Object.entries(values).reduce((t, [k, v]) => t.replace(`{${k}}`, String(v)), template);
const sum = (rows: readonly BeneficiaryRow[], f: (r: BeneficiaryRow) => number) => rows.reduce((a, r) => a + f(r), 0);
const right = (text: string, extra: Partial<Record<'bold' | 'color', unknown>> = {}): TableCell => ({ text, alignment: 'right', ...extra }) as TableCell;
const dashed = (width: number): Content => ({
  canvas: [{ type: 'line', x1: 0, y1: 0, x2: width, y2: 0, lineWidth: 1.5, lineColor: TEAL, dash: { length: 2, space: 1 } }],
});

/** Grupos por Cedula en el orden del backend (Cedula, Factura). */
export function groupByIdNumber(rows: readonly BeneficiaryRow[]): [string, BeneficiaryRow[]][] {
  const groups = new Map<string, BeneficiaryRow[]>();
  rows.forEach((r) => groups.set(r.idNumber, [...(groups.get(r.idNumber) ?? []), r]));
  return [...groups];
}

/** KVerdes, KNetos, Porc y los 7 valores (Porc no se suma en Access). */
function amounts(r: BeneficiaryRow): TableCell[] {
  return [
    right(peso(r.greenKg)), right(dec2(r.netKg)), right(dec2(r.healthyPercentage)), right(peso(r.grossValue)),
    right(peso(r.associateContribution)), right(peso(r.cooperativeDiscount)), right(peso(r.freightDiscount)),
    right(peso(r.withholding)), right(peso(r.otherDiscounts)), right(peso(r.netToPay)),
  ];
}

function totals(label: string, rows: readonly BeneficiaryRow[], lead: number): TableCell[] {
  const bold = { bold: true };
  return [
    { text: label, bold: true, color: TEAL, colSpan: lead },
    ...Array.from({ length: lead - 1 }, () => ''),
    right(peso(sum(rows, (r) => r.greenKg)), bold), right(dec2(sum(rows, (r) => r.netKg)), bold), '',
    right(peso(sum(rows, (r) => r.grossValue)), bold), right(peso(sum(rows, (r) => r.associateContribution)), bold),
    right(peso(sum(rows, (r) => r.cooperativeDiscount)), bold), right(peso(sum(rows, (r) => r.freightDiscount)), bold),
    right(peso(sum(rows, (r) => r.withholding)), bold), right(peso(sum(rows, (r) => r.otherDiscounts)), bold),
    right(peso(sum(rows, (r) => r.netToPay)), bold), '',
  ];
}

/**
 * Reportes "Beneficiario" y "Beneficiario resumen" de Access: grupo por Cedula con "Resumir por
 * 'Cedula'" y "Suma", y "Suma total" al final. El primero lleva cedula y nombre en el encabezado; el
 * resumen pone Cedula en el encabezado de cada grupo y Nombre/Apellidos en cada fila.
 */
export function buildBeneficiaryDoc(
  report: BeneficiaryReport,
  text: BeneficiaryReportText,
  rows: readonly BeneficiaryRow[],
  today = new Date(),
): TDocumentDefinitions {
  const summary = report.layout === 'summary';
  const lead = summary ? 5 : 4;
  const width = summary ? [48, 40, 30, 55, 55, 22, 32, 28, 50, 36, 36, 32, 34, 32, 48, '*'] : [48, 34, 40, 20, 34, 38, 30, 54, 40, 40, 36, 38, 36, 54, '*'];
  const body: TableCell[][] = [report.columns.map((c) => ({ text: c, bold: true, alignment: 'center' }))];
  for (const [idNumber, group] of groupByIdNumber(rows)) {
    if (summary) {
      body.push([{ text: `${text.cedula}   ${formatThousands(idNumber)}`, bold: true, colSpan: width.length }, ...Array.from({ length: width.length - 1 }, () => '')]);
    }
    group.forEach((r) => {
      const head: TableCell[] = summary
        ? [r.agencyName, shortDate(r.purchaseDate), right(String(r.invoiceNumber)), r.firstName, r.lastName ?? '']
        : [r.agencyName, right(String(r.invoiceNumber)), right(shortDate(r.purchaseDate)), r.fundCode];
      body.push([...head, ...amounts(r), r.specialType ?? '']);
    });
    const records = group.length === 1 ? text.recordSingular : text.recordPlural;
    body.push([{ text: fill(text.groupSummary, { id: idNumber, count: group.length, records }), color: TEAL, colSpan: width.length },
      ...Array.from({ length: width.length - 1 }, () => '')]);
    body.push(totals(text.sum, group, lead));
  }
  body.push(totals(text.grandTotal, rows, lead));

  const first = rows[0];
  const title: Content = summary
    ? { text: report.pdfTitle, fontSize: 20, bold: true, color: TEAL }
    : {
        columns: [
          {
            width: 'auto',
            stack: [
              { text: report.pdfTitle, fontSize: 20, bold: true, color: TEAL },
              { text: `${formatThousands(first.idNumber)}   ${first.firstName} ${first.lastName ?? ''}`.trimEnd(), bold: true },
            ],
          },
          { width: '*', stack: [{ text: text.company, bold: true }, { text: text.nit, bold: true }], alignment: 'center', margin: [0, 4, 0, 0] },
        ],
      };
  const longDate = today.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  return {
    pageSize: 'LETTER',
    pageOrientation: 'landscape',
    pageMargins: [30, 30, 30, 40],
    defaultStyle: { font: 'Roboto', fontSize: 7 },
    content: [
      ...(summary ? [] : [dashed(732)]),
      title,
      ...(summary ? [] : [dashed(732)]),
      {
        table: { headerRows: 1, widths: width, body },
        layout: { hLineWidth: (i: number) => (i === 1 ? 1 : 0), hLineColor: () => TEAL, vLineWidth: () => 0, paddingTop: () => 2, paddingBottom: () => 2 },
        margin: [0, 6, 0, 0],
      },
    ],
    footer: (page: number, pages: number): Content => ({
      columns: [
        { text: longDate, fontSize: 7 },
        { text: fill(text.page, { page, pages }), fontSize: 7, alignment: 'right' },
      ],
      margin: [30, 10, 30, 0],
    }),
  };
}
