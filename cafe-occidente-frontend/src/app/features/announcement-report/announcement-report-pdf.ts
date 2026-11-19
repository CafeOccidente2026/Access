import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { PurchaseReportRow } from '../../core/models/purchase-report.model';
import { cooperativeNit } from '../../shared/utils/nit';
import { announcementDisplay, formatDate, formatDisplayNumber, formatMoney, formatThousands } from '../../shared/utils/number-format';

/**
 * Reportes "RESUMEN ANUNCIO" y "ANUNCIO" (detallado) de Access (macro "Imprimir Anuncio",
 * reports/RESUMEN ANUNCIO.txt y reports/ANUNCIO.txt). Prueba del 19-nov (fotos del 3590 NESPRESSO en
 * docs/referencia-reportes): Generar Reporte Anuncio da cada uno como documento aparte, un anuncio por hoja, sin la
 * columna Anun. El Informe Anuncios a Futuro sigue con los dos en un PDF (buildAnnouncementReportDoc).
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
  /** Linea bajo el NIT (Compras a Futuro: "COMPRAS A FUTURO"). */
  readonly subtitle?: string;
  /** false = el detalle sin la columna Anun (Informe Anuncios a Futuro: es de un solo anuncio). */
  readonly announcementColumn?: boolean;
}

/** Lo minimo que necesitan las firmas y el pie de pagina (tambien los usa el informe Saldo Anuncios). */
export type ReportFooterText = Pick<AnnouncementReportText, 'trusted' | 'signatures' | 'page'>;

interface ProductGroup {
  readonly productCode: string;
  readonly rows: Row[];
}

interface AnnouncementBlock {
  readonly announcementNumber: string;
  /** El numero sin prefijo, como el encabezado de Access ("3590"). */
  readonly number: string;
  readonly specialType: string;
  readonly agencyName: string;
  /** Fecha del anuncio (o de la compra si no la trae). */
  readonly date: string;
  readonly trustedName: string | null;
  readonly groups: ProductGroup[];
  readonly rows: Row[];
}

const FONT_SIZE = 7;
const THIN = {
  hLineWidth: () => 0.5, vLineWidth: () => 0,
  paddingLeft: () => 1, paddingRight: () => 1, paddingTop: () => 1, paddingBottom: () => 1,
};

/** Peso entero con punto de miles (Format=Standard, DecimalPlaces=0 en Access). */
const peso = formatMoney;
/** 2 decimales fijos con coma, cortando hacia cero (KN, KV y Factor, Format=Standard: "200,00", "89,29"); vacio si no
 *  aplica. */
const dec2 = (v: number | null): string => (v == null ? '' : formatDisplayNumber(v, 'currency'));
/** "2026-05-09" -> "09/05/2026": dd/mm/aaaa en todos los reportes (hallazgo #1 del 16-nov). */
export function shortDate(iso: string): string {
  return formatDate(iso);
}
const sum = (rows: Row[], f: (p: Row) => number): number => rows.reduce((acc, p) => acc + f(p), 0);
const fill = (template: string, values: Record<string, string | number>): string =>
  template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? ''));

/** KV del grupo: la suma de Kilos Verdes; 0 fuera del verde, como Access (foto del 3590). */
function greenKg(rows: Row[]): number {
  return sum(rows, (p) => p.greenKg ?? 0);
}

/** KV de una compra: con decimales en el verde, 0 en los demas cafes (Access mostraba 0). */
const kv = (v: number | null): string => (v == null ? '0' : dec2(v));

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
    const first = blockRows[0];
    return {
      announcementNumber: announcementDisplay(first.announcementNumber),
      number: first.announcementNumber.slice(first.announcementNumber.lastIndexOf('-') + 1),
      specialType: first.specialType,
      agencyName: first.agencyName,
      date: first.announcementDate ?? first.purchaseDate,
      trustedName: first.trustedName ?? null,
      rows: blockRows,
      groups: [...groups.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([productCode, groupRows]) => ({ productCode, rows: groupRows })),
    };
  });
}

function reportHeader(title: string, block: AnnouncementBlock, date: string, pageBreak: boolean, text: AnnouncementReportText): Content {
  return pageHeader(title, `${block.announcementNumber} ${block.specialType}`, block.agencyName,
    [{ text: `${text.dateLabel}  `, bold: true }, date], pageBreak, text);
}

/** Encabezado de Access: linea, cooperativa, NIT, titulo, rotulo grande (anuncio o rango), agencia y fecha. */
function pageHeader(title: string, heading: string, agencyName: string, date: string | Content[], pageBreak: boolean,
  text: AnnouncementReportText): Content {
  return {
    pageBreak: pageBreak ? 'before' : undefined,
    margin: [0, 0, 0, 4],
    stack: [
      { canvas: [{ type: 'line', x1: 0, y1: 0, x2: 752, y2: 0, lineWidth: 1 }] },
      { text: text.cooperative, bold: true, fontSize: 9, alignment: 'center', margin: [0, 3, 0, 0] },
      { text: `${text.nit} ${cooperativeNit()}`, bold: true, fontSize: 8, alignment: 'center' },
      ...(text.subtitle ? [{ text: text.subtitle, bold: true, fontSize: 9, alignment: 'center' as const }] : []),
      {
        columns: [
          { text: title, bold: true, fontSize: 16, width: 170 },
          { text: heading, bold: true, fontSize: 14, width: '*' },
          { text: [{ text: `${text.agencyLabel}  `, bold: true }, agencyName], fontSize: 8, width: 180, margin: [0, 5, 0, 0] },
          { text: date, bold: typeof date === 'string', fontSize: 8, width: 110, margin: [0, 5, 0, 0] },
        ],
      },
    ],
  };
}

/** Firmas de Access: "Fiel:" con su linea (y, debajo, el Fiel del Registro de Control) y las tres firmas. */
export function signatures(text: ReportFooterText, trustedName?: string | null): Content {
  return {
    unbreakable: true,
    margin: [0, 18, 0, 0],
    stack: [
      { text: text.trusted, bold: true, fontSize: 9 },
      ...(trustedName ? [{ text: trustedName, fontSize: 8, margin: [30, 1, 0, 0] as [number, number, number, number] }] : []),
      { margin: [0, 30, 0, 0], columns: text.signatures.map((s) => ({ text: s, bold: true, fontSize: 9 })) },
    ],
  };
}

const r = (value: string, extra: object = {}): TableCell => ({ text: value, alignment: 'right', ...extra });
const b = { bold: true };

/** Fila de totales del RESUMEN: en Access el "SUMA TOTAL" no trae Porcent Ponderado (no hay control en esa posicion). */
function summaryTotalRow(label: string, rows: Row[]): TableCell[] {
  const total = summarySums(rows);
  total[2] = '';
  return [{ text: label, bold: true, fontSize: 6 }, ...total];
}

function summarySums(rows: Row[]): TableCell[] {
  return [
    r(peso(greenKg(rows))), r(peso(sum(rows, (p) => p.netKg))), r(dec2(weightedFactor(rows))),
    r(peso(sum(rows, (p) => p.grossValue))), r(peso(sum(rows, (p) => p.associateContribution))),
    r(peso(sum(rows, (p) => p.cooperativeDiscount))), r(peso(sum(rows, (p) => p.freightDiscount))),
    r(peso(sum(rows, (p) => p.withholding))), r(peso(sum(rows, (p) => p.netToPay))),
  ];
}

const SUMMARY_WIDTHS = [110, 40, 55, 60, 75, 65, 65, 55, 65, 75];

function table(widths: number[], body: TableCell[][], headerRows = 1): Content {
  return { table: { headerRows, widths, body }, layout: { ...THIN, hLineWidth: (row: number) => (row <= 1 ? 0.5 : 0) } };
}

/** RESUMEN ANUNCIO de un bloque: una fila por Cod_Prod y "SUMA TOTAL" (o el subtotal del anuncio, por rango). */
function summaryTable(block: AnnouncementBlock, text: AnnouncementReportText, totalLabel = text.summaryTotal): Content {
  const head: TableCell[] = text.summaryColumns.map((t) => ({ text: t, bold: true, fontSize: 6, alignment: 'right' }));
  const body: TableCell[][] = [head];
  for (const group of block.groups) {
    body.push([{ text: groupTitle(group), bold: true, fontSize: 9, colSpan: 10, margin: [0, 3, 0, 0] }, ...Array(9).fill('')]);
    body.push(['', ...summarySums(group.rows)]);
  }
  body.push(summaryTotalRow(totalLabel, block.rows));
  return table(SUMMARY_WIDTHS, body);
}

interface DetailLayout {
  readonly widths: number[];
  readonly columns: number;
  readonly firstNumber: number;
  readonly withAnnouncement: boolean;
}

function detailLayout(text: AnnouncementReportText): DetailLayout {
  // Anchos relativos tomados de los Width (twips) de los controles de reports/ANUNCIO.txt (con la columna Anun al
  // frente si se pide). FP se ensancha porque "ANULADA" en negrilla tiene que caber en una linea; Access la cortaba
  // ("VA" en la foto del 3590).
  const withAnnouncement = text.announcementColumn !== false;
  const twips = [600, 651, 1299, 1032, 561, 1371, 1350, 737, 885, 750, 840, 1020, 855, 900, 855, 900, 960, 1185, 1100]
    .slice(withAnnouncement ? 0 : 1);
  const total = twips.reduce((a, t) => a + t, 0);
  const widths = twips.map((t) => (t * 752) / total - 2);
  return { widths, columns: twips.length, firstNumber: withAnnouncement ? 7 : 6, withAnnouncement };
}

/** Fila de totales del ANUNCIO: el rotulo ocupa las columnas de texto y luego las sumas en negrilla. */
function detailTotalRow(label: string, rows: Row[], layout: DetailLayout): TableCell[] {
  const sums: TableCell[] = [
    r(peso(greenKg(rows)), b), r(peso(sum(rows, (p) => p.netKg)), b), '', '', r(peso(sum(rows, (p) => p.grossValue)), b),
    r(peso(sum(rows, (p) => p.associateContribution)), b), r(peso(sum(rows, (p) => p.cooperativeDiscount)), b),
    r(peso(sum(rows, (p) => p.freightDiscount)), b), r(peso(sum(rows, (p) => p.withholding)), b),
    r(peso(sum(rows, (p) => p.otherDiscounts)), b), r(peso(sum(rows, (p) => p.netToPay)), b), '',
  ];
  return [{ text: label, bold: true, colSpan: layout.firstNumber }, ...Array(layout.firstNumber - 1).fill(''), ...sums];
}

/** ANUNCIO de un bloque: una fila por compra, subtotales por Cod_Prod y "Suma total" (o el subtotal del anuncio). */
function detailTable(block: AnnouncementBlock, text: AnnouncementReportText, totalLabel = text.detailTotal): Content {
  const layout = detailLayout(text);
  const { columns, firstNumber, withAnnouncement } = layout;
  const head: TableCell[] = text.detailColumns.map((t, i) => ({
    text: t, bold: true, fontSize: 6, alignment: i >= firstNumber && i < columns - 1 ? 'right' : 'left',
  }));

  const body: TableCell[][] = [head];
  for (const group of block.groups) {
    body.push([{ text: groupTitle(group), bold: true, fontSize: 9, colSpan: columns, margin: [0, 3, 0, 0] }, ...Array(columns - 1).fill('')]);
    for (const p of group.rows) {
      const cells: TableCell[] = [
        r(String(p.invoiceNumber)),
        r(/^\d+$/.test(p.idNumber) ? formatThousands(p.idNumber) : p.idNumber), r(shortDate(p.purchaseDate)),
        // Access: 1er Nombre y 1er Apellido de Asociados; si la cedula ya no esta, lo guardado en la compra.
        p.fundCode, p.growerFirstName ?? p.firstName, p.growerFirstLastName ?? p.lastName ?? '', r(kv(p.greenKg)),
        r(dec2(p.netKg)), r(dec2(p.factor)),
        r(peso(p.unitPrice)), r(peso(p.grossValue)), r(peso(p.associateContribution)), r(peso(p.cooperativeDiscount)),
        r(peso(p.freightDiscount)), r(peso(p.withholding)), r(peso(p.otherDiscounts)), r(peso(p.netToPay)),
        // FP de Access es COMPRAS.Forma_de_Pago, donde se guardaba el estado (VALIDA o ANULADA).
        p.status === 'ANULADA' ? { text: p.status, bold: true } : p.status,
      ];
      body.push(withAnnouncement ? [announcementDisplay(p.announcementNumber), ...cells] : cells);
    }
    const n = group.rows.length;
    body.push([
      {
        text: fill(text.groupSummary, { code: group.productCode, count: n, records: n === 1 ? text.oneRecord : text.manyRecords }),
        bold: true, colSpan: columns, margin: [0, 2, 0, 0],
      },
      ...Array(columns - 1).fill(''),
    ]);
    body.push(detailTotalRow(text.sum, group.rows, layout));
  }
  body.push(detailTotalRow(totalLabel, block.rows, layout));
  return table(layout.widths, body);
}

export function footer(text: ReportFooterText): TDocumentDefinitions['footer'] {
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

/** Hoja de Access: encabezado ANUNCIO con numero y especial, la tabla y las firmas con el Fiel. */
function announcementSheets(rows: Row[], text: AnnouncementReportText,
  table: (block: AnnouncementBlock) => Content): TDocumentDefinitions {
  const blocks = groupByAnnouncement(rows).sort((a, c) =>
    a.date.localeCompare(c.date) || Number(a.number) - Number(c.number) || a.specialType.localeCompare(c.specialType));
  return {
    pageSize: 'LETTER',
    pageOrientation: 'landscape',
    pageMargins: [20, 20, 20, 30],
    content: blocks.flatMap((block, i): Content[] => [
      pageHeader(text.detailTitle, `${block.number} ${block.specialType}`, block.agencyName.toUpperCase(),
        [{ text: `${text.dateLabel}  `, bold: true }, shortDate(block.date)], i > 0, text),
      table(block),
      signatures(text, block.trustedName),
    ]),
    footer: footer(text),
    defaultStyle: { font: 'Roboto', fontSize: FONT_SIZE },
  };
}

/** RESUMEN ANUNCIO de Access: una hoja por anuncio, una fila por Cod_Prod y SUMA TOTAL. */
export function buildAnnouncementSummaryDoc(rows: Row[], text: AnnouncementReportText): TDocumentDefinitions {
  return announcementSheets(rows, text, (block) => summaryTable(block, text));
}

/** ANUNCIO (detallado) de Access: una hoja por anuncio, una fila por compra, Suma por Cod_Prod y Suma total. */
export function buildAnnouncementDetailDoc(rows: Row[], text: AnnouncementReportText): TDocumentDefinitions {
  return announcementSheets(rows, text, (block) => detailTable(block, text));
}
