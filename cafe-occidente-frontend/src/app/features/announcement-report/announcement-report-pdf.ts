import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { DryCoffeePurchaseResponse } from '../../core/models/dry-coffee-purchase.model';
import { formatThousands, stripAnnouncementPrefix } from '../../shared/utils/number-format';

/**
 * Reportes "ANUNCIO" (detallado) y "RESUMEN ANUNCIO" de Access (macro "Imprimir Anuncio",
 * reports/ANUNCIO.txt y reports/RESUMEN ANUNCIO.txt): compras agrupadas por Cod_Prod. En Access
 * cada corrida era de un solo anuncio; aca se elige una fecha, asi que sale un bloque por anuncio
 * usado ese dia (cada bloque = un reporte de Access, con su encabezado y su "Suma total").
 */

type Purchase = DryCoffeePurchaseResponse;

interface ProductGroup {
  readonly productCode: string;
  readonly rows: Purchase[];
}

interface AnnouncementBlock {
  readonly announcementNumber: string;
  readonly specialType: string;
  readonly agencyName: string;
  readonly groups: ProductGroup[];
  readonly rows: Purchase[];
}

const COOPERATIVE = 'COOPERATIVA DE CAFICULTORES DE OCCIDENTE DE NARIÑO LTDA.';
const NIT = 'NIT. 891.200.986-8';
const FONT_SIZE = 7;
const THIN = {
  hLineWidth: () => 0.5, vLineWidth: () => 0,
  paddingLeft: () => 1, paddingRight: () => 1, paddingTop: () => 1, paddingBottom: () => 1,
};

/** Peso entero con punto de miles (Format=Standard, DecimalPlaces=0 en Access). */
const peso = (v: number): string => Math.round(v).toLocaleString('es-CO');
/** 2 decimales con coma (KN y Factor, Format=Standard). */
const dec2 = (v: number): string => v.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
/** "2026-05-09" -> "9/05/2026" (Short Date de Access en la captura). */
export function shortDate(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${Number(d)}/${m}/${y}`;
}
const sum = (rows: Purchase[], f: (p: Purchase) => number): number => rows.reduce((acc, p) => acc + f(p), 0);
/** Porcent Ponderado = Sum(KN*Factor)/Sum(KN); Access mostraba "#¡Núm!" con KN=0, aca 0. */
export function weightedFactor(rows: Purchase[]): number {
  const kn = sum(rows, (p) => p.netKg);
  return kn === 0 ? 0 : sum(rows, (p) => p.netKg * p.healthyPercentage) / kn;
}
// El modulo Seco no guarda Kilos_Verdes (la captura de Access muestra 0 en todas las filas).
const KV = 0;

/** Un bloque por anuncio usado en el dia, grupos por Cod_Prod, filas por factura ascendente. */
export function groupByAnnouncement(purchases: Purchase[]): AnnouncementBlock[] {
  const blocks = new Map<string, Purchase[]>();
  for (const p of [...purchases].sort((a, b) => a.invoiceNumber - b.invoiceNumber)) {
    const key = `${p.announcementNumber}|${p.specialType}`;
    blocks.set(key, [...(blocks.get(key) ?? []), p]);
  }
  return [...blocks.values()].map((rows) => {
    const groups = new Map<string, Purchase[]>();
    for (const p of rows) {
      groups.set(p.productCode, [...(groups.get(p.productCode) ?? []), p]);
    }
    return {
      announcementNumber: stripAnnouncementPrefix(rows[0].announcementNumber),
      specialType: rows[0].specialType,
      agencyName: rows[0].agencyName,
      rows,
      groups: [...groups.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([productCode, groupRows]) => ({ productCode, rows: groupRows })),
    };
  });
}

function reportHeader(block: AnnouncementBlock, date: string, first: boolean): Content {
  return {
    pageBreak: first ? undefined : 'before',
    margin: [0, 0, 0, 4],
    stack: [
      { canvas: [{ type: 'line', x1: 0, y1: 0, x2: 752, y2: 0, lineWidth: 1 }] },
      { text: COOPERATIVE, bold: true, fontSize: 9, alignment: 'center', margin: [0, 3, 0, 0] },
      { text: NIT, bold: true, fontSize: 8, alignment: 'center' },
      {
        columns: [
          { text: 'ANUNCIO', bold: true, fontSize: 16, width: 90 },
          { text: `${block.announcementNumber} ${block.specialType}`, bold: true, fontSize: 14, width: '*' },
          { text: [{ text: 'Agencia:  ', bold: true }, block.agencyName], fontSize: 8, width: 180, margin: [0, 5, 0, 0] },
          { text: [{ text: 'Fecha:  ', bold: true }, date], fontSize: 8, width: 110, margin: [0, 5, 0, 0] },
        ],
      },
    ],
  };
}

function signatures(): Content {
  return {
    unbreakable: true,
    margin: [0, 18, 0, 0],
    stack: [
      { text: 'Fiel: _____________________________________', bold: true, fontSize: 9 },
      {
        margin: [0, 30, 0, 0],
        columns: [
          { text: 'CONTABILIDAD', bold: true, fontSize: 9 },
          { text: 'OPERACIONES', bold: true, fontSize: 9 },
          { text: 'REVISORIA FISCAL', bold: true, fontSize: 9 },
        ],
      },
    ],
  };
}

function footer(): TDocumentDefinitions['footer'] {
  const printed = new Date().toLocaleDateString('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  });
  return (currentPage: number, pageCount: number) => ({
    margin: [20, 0, 20, 0],
    fontSize: 6,
    columns: [
      { text: printed, width: 250 },
      { text: `Página ${currentPage} de ${pageCount}`, width: 250, alignment: 'center' },
    ],
  });
}

function doc(content: Content[]): TDocumentDefinitions {
  return {
    pageSize: 'LETTER',
    pageOrientation: 'landscape',
    pageMargins: [20, 20, 20, 30],
    content,
    footer: footer(),
    defaultStyle: { font: 'Roboto', fontSize: FONT_SIZE },
  };
}

const r = (text: string, extra: object = {}): TableCell => ({ text, alignment: 'right', ...extra });
const b = { bold: true };

/** Reporte ANUNCIO: una fila por compra, subtotales por Cod_Prod y "Suma total" + firmas. */
export function buildAnnouncementDetailDoc(purchases: Purchase[], date: string): TDocumentDefinitions {
  // Anchos relativos tomados de los Width (twips) de los controles de reports/ANUNCIO.txt.
  // FP se ensancha (363 en Access) porque aca guarda la forma de pago completa ("EFECTIVO").
  const twips = [651, 1299, 1032, 561, 1521, 1500, 737, 885, 750, 840, 1020, 855, 900, 855, 900, 960, 1185, 800];
  const total = twips.reduce((a, t) => a + t, 0);
  const widths = twips.map((t) => (t * 752) / total - 2);
  const head: TableCell[] = [
    'Factura', 'Cedula', 'Fecha', 'Fondo', 'Nombre', 'Apellidos', 'KV', 'KN', 'Factor', 'Vr\nKilo', 'Vr\nBruto',
    'Aporte\nSocio', 'Dcto\nCoop', 'Dcto\nFro', 'Retefuente', 'Otros\nDctos', 'Neto a\nPagar', 'FP',
  ].map((t, i) => ({ text: t, bold: true, fontSize: 6, alignment: i >= 6 && i < 17 ? 'right' : 'left' }));
  const sums = (rows: Purchase[]): TableCell[] => [
    r(String(KV), b), r(peso(sum(rows, (p) => p.netKg)), b), '', '', r(peso(sum(rows, (p) => p.grossValue)), b),
    r(peso(sum(rows, (p) => p.associateContribution)), b), r(peso(sum(rows, (p) => p.cooperativeDiscount)), b),
    r(peso(sum(rows, (p) => p.freightDiscount)), b), r(peso(sum(rows, (p) => p.withholding)), b),
    r(peso(sum(rows, (p) => p.otherDiscounts)), b), r(peso(sum(rows, (p) => p.netToPay)), b), '',
  ];

  const content = groupByAnnouncement(purchases).flatMap((block, i): Content[] => {
    const body: TableCell[][] = [head];
    for (const group of block.groups) {
      body.push([{ text: group.productCode, bold: true, fontSize: 9, colSpan: 18, margin: [0, 3, 0, 0] }, ...Array(17).fill('')]);
      for (const p of group.rows) {
        body.push([
          r(String(p.invoiceNumber)), r(/^\d+$/.test(p.idNumber) ? formatThousands(p.idNumber) : p.idNumber),
          r(shortDate(p.purchaseDate)), p.fundCode, p.firstName, p.lastName, r(String(KV)), r(dec2(p.netKg)),
          r(dec2(p.healthyPercentage)), r(peso(p.unitPrice)), r(peso(p.grossValue)), r(peso(p.associateContribution)),
          r(peso(p.cooperativeDiscount)), r(peso(p.freightDiscount)), r(peso(p.withholding)), r(peso(p.otherDiscounts)),
          r(peso(p.netToPay)), p.paymentMethod,
        ]);
      }
      const n = group.rows.length;
      body.push([
        {
          text: `Resumir por 'Cod_Prod' =  ${group.productCode} (${n} ${n === 1 ? 'registro de detalle' : 'registros de detalle'})`,
          bold: true, colSpan: 18, margin: [0, 2, 0, 0],
        },
        ...Array(17).fill(''),
      ]);
      body.push([{ text: 'Suma', bold: true, colSpan: 6 }, '', '', '', '', '', ...sums(group.rows)]);
    }
    body.push([{ text: 'Suma total', bold: true, colSpan: 6 }, '', '', '', '', '', ...sums(block.rows)]);
    return [
      reportHeader(block, date, i === 0),
      {
        table: { headerRows: 1, widths, body },
        layout: { ...THIN, hLineWidth: (row: number) => (row <= 1 ? 0.5 : 0) },
      },
      signatures(),
    ];
  });
  return doc(content);
}

/** Reporte RESUMEN ANUNCIO: una fila por Cod_Prod, "SUMA TOTAL" y firmas. */
export function buildAnnouncementSummaryDoc(purchases: Purchase[], date: string): TDocumentDefinitions {
  const head: TableCell[] = [
    '', 'KV', 'KN', 'Porcent\nPonderado', 'Vr\nBruto', 'Aporte\nSocio', 'Dcto\nCoop', 'Dcto\nFro', 'Retefuente', 'Neto a\nPagar',
  ].map((t) => ({ text: t, bold: true, fontSize: 6, alignment: 'right' }));
  const sums = (rows: Purchase[]): TableCell[] => [
    r(String(KV)), r(peso(sum(rows, (p) => p.netKg))), r(dec2(weightedFactor(rows))),
    r(peso(sum(rows, (p) => p.grossValue))), r(peso(sum(rows, (p) => p.associateContribution))),
    r(peso(sum(rows, (p) => p.cooperativeDiscount))), r(peso(sum(rows, (p) => p.freightDiscount))),
    r(peso(sum(rows, (p) => p.withholding))), r(peso(sum(rows, (p) => p.netToPay))),
  ];

  const content = groupByAnnouncement(purchases).flatMap((block, i): Content[] => {
    const body: TableCell[][] = [head];
    for (const group of block.groups) {
      body.push([{ text: group.productCode, bold: true, fontSize: 9, colSpan: 10, margin: [0, 3, 0, 0] }, ...Array(9).fill('')]);
      body.push(['', ...sums(group.rows)]);
    }
    // En Access el "SUMA TOTAL" no trae Porcent Ponderado (no hay control en esa posicion).
    const total = sums(block.rows);
    total[2] = '';
    body.push([{ text: 'SUMA TOTAL', bold: true, fontSize: 6 }, ...total]);
    return [
      reportHeader(block, date, i === 0),
      {
        table: { headerRows: 1, widths: [110, 40, 55, 60, 75, 65, 65, 55, 65, 75], body },
        layout: { ...THIN, hLineWidth: (row: number) => (row <= 1 ? 0.5 : 0) },
      },
      signatures(),
    ];
  });
  return doc(content);
}
