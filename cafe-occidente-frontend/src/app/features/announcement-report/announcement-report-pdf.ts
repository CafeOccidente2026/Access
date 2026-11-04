import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { PurchaseReportRow } from '../../core/models/purchase-report.model';
import { formatThousands, stripAnnouncementPrefix } from '../../shared/utils/number-format';

/**
 * Reportes "RESUMEN ANUNCIO" y "ANUNCIO" (detallado) de Access (macro "Imprimir Anuncio",
 * reports/RESUMEN ANUNCIO.txt y reports/ANUNCIO.txt) en un solo PDF: primero el resumen y luego el
 * detalle (RN-24.4). Access pedia un numero de anuncio; RF-24 pide todas las compras del dia (seco,
 * verde y pasilla), asi que sale un bloque por anuncio y tipo de cafe (RN-24.3), y el detalle trae la
 * columna Anun (RN-24.2). Lo que no aplica a un modulo (KV fuera del verde) queda vacio.
 */

type Row = PurchaseReportRow;

/** Textos del PDF (announcement-report.json, "pdf"). */
export interface AnnouncementReportText {
  readonly cooperative: string;
  readonly nit: string;
  readonly detailTitle: string;
  readonly summaryTitle: string;
  readonly agencyLabel: string;
  readonly dateLabel: string;
  readonly detailColumns: readonly string[];
  readonly summaryColumns: readonly string[];
  readonly groupSummary: string;
  readonly oneRecord: string;
  readonly manyRecords: string;
  readonly sum: string;
  readonly detailTotal: string;
  readonly summaryTotal: string;
  readonly trusted: string;
  readonly signatures: readonly string[];
  readonly page: string;
}

interface ProductGroup {
  readonly productCode: string;
  readonly rows: Row[];
}

interface AnnouncementBlock {
  readonly announcementNumber: string;
  readonly specialType: string;
  readonly agencyName: string;
  readonly groups: ProductGroup[];
  readonly rows: Row[];
}

const FONT_SIZE = 7;
const THIN = {
  hLineWidth: () => 0.5, vLineWidth: () => 0,
  paddingLeft: () => 1, paddingRight: () => 1, paddingTop: () => 1, paddingBottom: () => 1,
};

/** Peso entero con punto de miles (Format=Standard, DecimalPlaces=0 en Access). */
const peso = (v: number): string => Math.round(v).toLocaleString('es-CO');
/** 2 decimales con coma (KN, KV y Factor, Format=Standard); vacio si no aplica. */
const dec2 = (v: number | null): string =>
  v == null ? '' : v.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
/** "2026-05-09" -> "9/05/2026" (Short Date de Access en la captura). */
export function shortDate(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${Number(d)}/${m}/${y}`;
}
const sum = (rows: Row[], f: (p: Row) => number): number => rows.reduce((acc, p) => acc + f(p), 0);
const fill = (template: string, values: Record<string, string | number>): string =>
  template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? ''));

/** KV del grupo: la suma si alguna fila es de verde; vacio si ninguna lo es. */
function greenKg(rows: Row[]): number | null {
  return rows.some((p) => p.greenKg != null) ? sum(rows, (p) => p.greenKg ?? 0) : null;
}

/**
 * Porcent Ponderado = Sum(KN*Factor)/Sum(KN) de las filas con Factor. Sin Factor: null (vacio).
 * Access mostraba "#¡Núm!" con KN = 0; aca 0.
 */
export function weightedFactor(rows: Row[]): number | null {
  const withFactor = rows.filter((p) => p.factor != null);
  if (withFactor.length === 0) {
    return null;
  }
  const kn = sum(withFactor, (p) => p.netKg);
  return kn === 0 ? 0 : sum(withFactor, (p) => p.netKg * (p.factor ?? 0)) / kn;
}

/** Encabezado de grupo de Access: Cod_Prod y a su lado Nom (Texto65). */
function groupTitle(group: ProductGroup): string {
  return `${group.productCode} ${group.rows[0].productName ?? ''}`.trimEnd();
}

/** Un bloque por anuncio y tipo de cafe, grupos por Cod_Prod, filas por factura ascendente. */
export function groupByAnnouncement(rows: Row[]): AnnouncementBlock[] {
  const blocks = new Map<string, Row[]>();
  for (const p of [...rows].sort((a, b) => a.invoiceNumber - b.invoiceNumber)) {
    const key = `${p.announcementNumber}|${p.specialType}`;
    blocks.set(key, [...(blocks.get(key) ?? []), p]);
  }
  return [...blocks.values()].map((blockRows) => {
    const groups = new Map<string, Row[]>();
    for (const p of blockRows) {
      groups.set(p.productCode, [...(groups.get(p.productCode) ?? []), p]);
    }
    return {
      announcementNumber: stripAnnouncementPrefix(blockRows[0].announcementNumber),
      specialType: blockRows[0].specialType,
      agencyName: blockRows[0].agencyName,
      rows: blockRows,
      groups: [...groups.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([productCode, groupRows]) => ({ productCode, rows: groupRows })),
    };
  });
}

function reportHeader(title: string, block: AnnouncementBlock, date: string, pageBreak: boolean, text: AnnouncementReportText): Content {
  return {
    pageBreak: pageBreak ? 'before' : undefined,
    margin: [0, 0, 0, 4],
    stack: [
      { canvas: [{ type: 'line', x1: 0, y1: 0, x2: 752, y2: 0, lineWidth: 1 }] },
      { text: text.cooperative, bold: true, fontSize: 9, alignment: 'center', margin: [0, 3, 0, 0] },
      { text: text.nit, bold: true, fontSize: 8, alignment: 'center' },
      {
        columns: [
          { text: title, bold: true, fontSize: 16, width: 170 },
          { text: `${block.announcementNumber} ${block.specialType}`, bold: true, fontSize: 14, width: '*' },
          { text: [{ text: `${text.agencyLabel}  `, bold: true }, block.agencyName], fontSize: 8, width: 180, margin: [0, 5, 0, 0] },
          { text: [{ text: `${text.dateLabel}  `, bold: true }, date], fontSize: 8, width: 110, margin: [0, 5, 0, 0] },
        ],
      },
    ],
  };
}

function signatures(text: AnnouncementReportText): Content {
  return {
    unbreakable: true,
    margin: [0, 18, 0, 0],
    stack: [
      { text: text.trusted, bold: true, fontSize: 9 },
      { margin: [0, 30, 0, 0], columns: text.signatures.map((s) => ({ text: s, bold: true, fontSize: 9 })) },
    ],
  };
}

const r = (value: string, extra: object = {}): TableCell => ({ text: value, alignment: 'right', ...extra });
const b = { bold: true };

/** RESUMEN ANUNCIO de un bloque: una fila por Cod_Prod y "SUMA TOTAL". */
function summaryTable(block: AnnouncementBlock, text: AnnouncementReportText): Content {
  const head: TableCell[] = text.summaryColumns.map((t) => ({ text: t, bold: true, fontSize: 6, alignment: 'right' }));
  const sums = (rows: Row[]): TableCell[] => [
    r(dec2(greenKg(rows))), r(peso(sum(rows, (p) => p.netKg))), r(dec2(weightedFactor(rows))),
    r(peso(sum(rows, (p) => p.grossValue))), r(peso(sum(rows, (p) => p.associateContribution))),
    r(peso(sum(rows, (p) => p.cooperativeDiscount))), r(peso(sum(rows, (p) => p.freightDiscount))),
    r(peso(sum(rows, (p) => p.withholding))), r(peso(sum(rows, (p) => p.netToPay))),
  ];
  const body: TableCell[][] = [head];
  for (const group of block.groups) {
    body.push([{ text: groupTitle(group), bold: true, fontSize: 9, colSpan: 10, margin: [0, 3, 0, 0] }, ...Array(9).fill('')]);
    body.push(['', ...sums(group.rows)]);
  }
  // En Access el "SUMA TOTAL" no trae Porcent Ponderado (no hay control en esa posicion).
  const total = sums(block.rows);
  total[2] = '';
  body.push([{ text: text.summaryTotal, bold: true, fontSize: 6 }, ...total]);
  return {
    table: { headerRows: 1, widths: [110, 40, 55, 60, 75, 65, 65, 55, 65, 75], body },
    layout: { ...THIN, hLineWidth: (row: number) => (row <= 1 ? 0.5 : 0) },
  };
}

/** ANUNCIO de un bloque: una fila por compra, subtotales por Cod_Prod y "Suma total". */
function detailTable(block: AnnouncementBlock, text: AnnouncementReportText): Content {
  // Anchos relativos tomados de los Width (twips) de los controles de reports/ANUNCIO.txt, con la
  // columna Anun (RN-24.2) al frente. FP se ensancha (a costa de Nombre y Apellidos) porque guarda la
  // forma de pago completa y "ANULADA" en negrilla tiene que caber en una linea.
  const twips = [600, 651, 1299, 1032, 561, 1371, 1350, 737, 885, 750, 840, 1020, 855, 900, 855, 900, 960, 1185, 1100];
  const total = twips.reduce((a, t) => a + t, 0);
  const widths = twips.map((t) => (t * 752) / total - 2);
  const columns = twips.length;
  const head: TableCell[] = text.detailColumns.map((t, i) => ({
    text: t, bold: true, fontSize: 6, alignment: i >= 7 && i < columns - 1 ? 'right' : 'left',
  }));
  const sums = (rows: Row[]): TableCell[] => [
    r(dec2(greenKg(rows)), b), r(peso(sum(rows, (p) => p.netKg)), b), '', '', r(peso(sum(rows, (p) => p.grossValue)), b),
    r(peso(sum(rows, (p) => p.associateContribution)), b), r(peso(sum(rows, (p) => p.cooperativeDiscount)), b),
    r(peso(sum(rows, (p) => p.freightDiscount)), b), r(peso(sum(rows, (p) => p.withholding)), b),
    r(peso(sum(rows, (p) => p.otherDiscounts)), b), r(peso(sum(rows, (p) => p.netToPay)), b), '',
  ];
  const label = (value: string): TableCell[] => [{ text: value, bold: true, colSpan: 7 }, '', '', '', '', '', ''];

  const body: TableCell[][] = [head];
  for (const group of block.groups) {
    body.push([{ text: groupTitle(group), bold: true, fontSize: 9, colSpan: columns, margin: [0, 3, 0, 0] }, ...Array(columns - 1).fill('')]);
    for (const p of group.rows) {
      body.push([
        stripAnnouncementPrefix(p.announcementNumber), r(String(p.invoiceNumber)),
        r(/^\d+$/.test(p.idNumber) ? formatThousands(p.idNumber) : p.idNumber), r(shortDate(p.purchaseDate)),
        p.fundCode, p.firstName, p.lastName ?? '', r(dec2(p.greenKg)), r(dec2(p.netKg)), r(dec2(p.factor)),
        r(peso(p.unitPrice)), r(peso(p.grossValue)), r(peso(p.associateContribution)), r(peso(p.cooperativeDiscount)),
        r(peso(p.freightDiscount)), r(peso(p.withholding)), r(peso(p.otherDiscounts)), r(peso(p.netToPay)),
        p.status === 'ANULADA' ? { text: p.paymentMethod, bold: true } : p.paymentMethod,
      ]);
    }
    const n = group.rows.length;
    body.push([
      {
        text: fill(text.groupSummary, { code: group.productCode, count: n, records: n === 1 ? text.oneRecord : text.manyRecords }),
        bold: true, colSpan: columns, margin: [0, 2, 0, 0],
      },
      ...Array(columns - 1).fill(''),
    ]);
    body.push([...label(text.sum), ...sums(group.rows)]);
  }
  body.push([...label(text.detailTotal), ...sums(block.rows)]);
  return {
    table: { headerRows: 1, widths, body },
    layout: { ...THIN, hLineWidth: (row: number) => (row <= 1 ? 0.5 : 0) },
  };
}

function footer(text: AnnouncementReportText): TDocumentDefinitions['footer'] {
  const printed = new Date().toLocaleDateString('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });
  return (currentPage: number, pageCount: number) => ({
    margin: [20, 0, 20, 0],
    fontSize: 6,
    columns: [
      { text: printed, width: 250 },
      { text: fill(text.page, { page: currentPage, pages: pageCount }), width: 250, alignment: 'center' },
    ],
  });
}

/**
 * Un solo PDF (RN-24.4): todos los RESUMEN ANUNCIO y despues todos los ANUNCIO, una pagina por bloque,
 * cada uno con su encabezado y firmas, como cada reporte de Access.
 */
export function buildAnnouncementReportDoc(rows: Row[], date: string, text: AnnouncementReportText): TDocumentDefinitions {
  const blocks = groupByAnnouncement(rows);
  const section = (title: string, table: (block: AnnouncementBlock) => Content, offset: number): Content[] =>
    blocks.flatMap((block, i): Content[] => [
      reportHeader(title, block, date, offset + i > 0, text),
      table(block),
      signatures(text),
    ]);
  return {
    pageSize: 'LETTER',
    pageOrientation: 'landscape',
    pageMargins: [20, 20, 20, 30],
    content: [
      ...section(text.summaryTitle, (block) => summaryTable(block, text), 0),
      ...section(text.detailTitle, (block) => detailTable(block, text), blocks.length),
    ],
    footer: footer(text),
    defaultStyle: { font: 'Roboto', fontSize: FONT_SIZE },
  };
}
