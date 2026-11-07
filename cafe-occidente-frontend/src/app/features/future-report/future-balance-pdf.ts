import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { FutureBalanceRow } from '../../core/models/purchase-report.model';
import { formatThousands, stripAnnouncementPrefix } from '../../shared/utils/number-format';
import { ReportFooterText, footer, shortDate, signatures } from '../announcement-report/announcement-report-pdf';

/** Textos del informe (future-report.json, "balancePdf"). */
export interface FutureBalanceText extends ReportFooterText {
  readonly cooperative: string;
  readonly nit: string;
  readonly title: string;
  readonly agencyLabel: string;
  readonly announcementLabel: string;
  readonly columns: readonly string[];
  readonly sum: string;
  readonly total: string;
}

const kg = (v: number): string => v.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const peso = (v: number): string => Math.round(v).toLocaleString('es-CO');
const r = (value: string, extra: object = {}): TableCell => ({ text: value, alignment: 'right', ...extra });
const sumOf = (rows: FutureBalanceRow[], f: (p: FutureBalanceRow) => number): number => rows.reduce((a, p) => a + f(p), 0);

/** Sum y Suma total de K Anun, K Entr y Saldo Kg (RN-21.7.4). */
function totals(label: string, rows: FutureBalanceRow[]): TableCell[] {
  const bold = { bold: true };
  return [{ text: label, bold: true, colSpan: 4 }, '', '', '',
    r(kg(sumOf(rows, (p) => p.announcedKg)), bold), r(kg(sumOf(rows, (p) => p.deliveredKg)), bold),
    r(kg(sumOf(rows, (p) => p.remainingKg)), bold), '', '', '', ''];
}

/**
 * Informe "COMPRAS A FUTURO" de Access (Generar Informe Saldo Anuncios, reports/COMPRAS A FUTURO.txt): las personas
 * de un anuncio con lo anunciado, lo entregado y el saldo, agrupadas por anuncio con "Sum" y "Suma total" (RN-21.7).
 */
export function buildFutureBalanceDoc(rows: FutureBalanceRow[], text: FutureBalanceText): TDocumentDefinitions {
  const groups = new Map<string, FutureBalanceRow[]>();
  for (const p of rows) {
    groups.set(p.announcementNumber, [...(groups.get(p.announcementNumber) ?? []), p]);
  }
  const head: TableCell[] = text.columns.map((t, i) => ({
    text: t, bold: true, fontSize: 7, alignment: i >= 4 && i <= 8 ? 'right' : 'left',
  }));
  const body: TableCell[][] = [head];
  for (const [announcement, groupRows] of groups) {
    body.push([{ text: `${text.announcementLabel} ${stripAnnouncementPrefix(announcement)}`, bold: true, fontSize: 9,
      colSpan: 11, margin: [0, 3, 0, 0] }, ...Array(10).fill('')]);
    for (const p of groupRows) {
      body.push([shortDate(p.announcementDate), r(/^\d+$/.test(p.idNumber) ? formatThousands(p.idNumber) : p.idNumber),
        p.firstName, p.lastName ?? '', r(kg(p.announcedKg)), r(kg(p.deliveredKg)), r(kg(p.remainingKg)),
        r(peso(p.healthyUnitPrice)), r(peso(p.bonus)), p.specialType, shortDate(p.deliveryDate)]);
    }
    body.push(totals(text.sum, groupRows));
  }
  body.push(totals(text.total, rows));

  const header: Content = {
    margin: [0, 0, 0, 6],
    stack: [
      { canvas: [{ type: 'line', x1: 0, y1: 0, x2: 752, y2: 0, lineWidth: 1 }] },
      { text: text.title, bold: true, fontSize: 16, margin: [0, 3, 0, 0] },
      { text: text.cooperative, bold: true, fontSize: 9, alignment: 'center' },
      { text: text.nit, bold: true, fontSize: 8, alignment: 'center' },
      { text: [{ text: `${text.agencyLabel}  `, bold: true }, rows[0]?.agencyName ?? ''], fontSize: 8 },
    ],
  };
  return {
    pageSize: 'LETTER',
    pageOrientation: 'landscape',
    pageMargins: [20, 20, 20, 30],
    content: [
      header,
      {
        table: { headerRows: 1, widths: [50, 60, 110, 110, 55, 55, 55, 55, 45, '*', 50], body },
        layout: {
          hLineWidth: (row: number) => (row <= 1 ? 0.5 : 0), vLineWidth: () => 0,
          paddingLeft: () => 2, paddingRight: () => 2, paddingTop: () => 1, paddingBottom: () => 1,
        },
      },
      signatures(text),
    ],
    footer: footer(text),
    defaultStyle: { font: 'Roboto', fontSize: 7 },
  };
}
