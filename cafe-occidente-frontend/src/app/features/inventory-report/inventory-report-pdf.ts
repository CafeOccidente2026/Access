import type { Content, ContentStack, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { InventoryReportRow } from '../../core/models/inventory.model';
import { shortDate } from '../announcement-report/announcement-report-pdf';
import { money } from '../purchase-forms/dry-coffee/dry-coffee-invoice';

/** Fila con los controles calculados del reporte (RunningSum = "Sobre todo" en INVENTARIO.txt). */
export interface BalancedRow extends InventoryReportRow {
  readonly weightedPercentage: number;
  readonly balanceKg: number;
  readonly balanceValue: number;
  readonly unitValue: number;
}

/**
 * Formulas de reports/INVENTARIO.txt e INVXCODPROD.txt (identicas en los dos):
 *   Saldo_Cantidad      = suma corrida de [Kilos_Netos]-[Cantidad]
 *   Saldo_Vr_Inventario = suma corrida de [Vr_Inventario]-[Valor_Salida]
 *   Valor_unitario      = [Saldo_Vr_Inventario]/[Saldo_Cantidad]
 *   % pond (Texto43)    = [Acum]/[Saldo_Cantidad], Acum = suma corrida de PorcAlmSana*Kilos_Netos - Cantidad*PorcAlmSanaSl
 * En Access los campos vacios de INVENTARIO valen 0 (inventario_migrar.csv). Con saldo 0 Access
 * mostraria "#¡Div/0!"; aca 0, mismo criterio que el factor ponderado del Reporte de Anuncio.
 */
export function withBalances(rows: readonly InventoryReportRow[]): BalancedRow[] {
  let kg = 0;
  let value = 0;
  let acum = 0;
  return rows.map((r) => {
    kg += (r.netKg ?? 0) - (r.quantity ?? 0);
    value += (r.inventoryValue ?? 0) - (r.outputValue ?? 0);
    acum += (r.healthyPercentage ?? 0) * (r.netKg ?? 0) - (r.quantity ?? 0) * (r.exitPercentage ?? 0);
    return {
      ...r,
      balanceKg: kg,
      balanceValue: value,
      unitValue: kg ? value / kg : 0,
      weightedPercentage: kg ? acum / kg : 0,
    };
  });
}

// Anchos en twips de INVENTARIO.txt (Left/Width de cada control), escalados a 752pt (carta horizontal).
// Los dos huecos (6913-7590 y 11843-12525) separan Entradas / Salidas / Saldo como en el reporte.
const TWIPS = [1020, 756, 1026, 921, 1353, 1022, 791, 677, 1038, 1041, 836, 1358, 682, 854, 1309, 982];
const TOTAL = TWIPS.reduce((a, b) => a + b, 0);
const WIDTHS = TWIPS.map((t) => (t * 752) / TOTAL - 2);

const LAYOUT = {
  hLineWidth: (i: number) => (i === 0 || i === 2 ? 0.75 : 0),
  vLineWidth: () => 0,
  paddingLeft: () => 1,
  paddingRight: () => 1,
  paddingTop: () => 1,
  paddingBottom: () => 1,
};

const label = (text: string, alignment: 'left' | 'center' | 'right' = 'center'): TableCell => ({ text, fontSize: 8, alignment });
const num = (value: number, alignment: 'center' | 'right' = 'right'): TableCell => ({ text: money(value), alignment });
const txt = (value: string | number | null | undefined, alignment: 'left' | 'center' = 'center'): TableCell =>
  ({ text: value == null ? '' : String(value), alignment });

function detailRow(r: BalancedRow): TableCell[] {
  return [
    txt(shortDate(r.date), 'left'),
    txt(r.invoiceNumber),
    num(r.netKg ?? 0),
    num(r.healthyPercentage ?? 0, 'center'),
    num(r.inventoryValue ?? 0),
    txt(r.specialType),
    num(r.weightedPercentage),
    '',
    txt(r.remissionNumber),
    num(r.quantity ?? 0),
    num(r.exitPercentage ?? 0),
    num(r.outputValue ?? 0),
    '',
    num(r.balanceKg),
    num(r.balanceValue),
    num(r.unitValue),
  ];
}

/**
 * "INVENTARIO" (por Esp., paginado) e "INVXCODPROD" (por Cod.): mismo diseño. continuous = vista
 * continua (OpenReport vista Informe en DialogoInvXCod): una sola pagina tan larga como haga falta.
 */
export function buildInventoryReportDoc(rows: readonly InventoryReportRow[], continuous: boolean, today = new Date()): TDocumentDefinitions {
  const balanced = withBalances(rows);
  const header: TableCell[][] = [
    [
      { text: 'Entradas', fontSize: 8, alignment: 'center', colSpan: 7 }, '', '', '', '', '', '',
      '',
      { text: 'Salidas', fontSize: 8, alignment: 'center', colSpan: 4 }, '', '', '',
      '',
      { text: 'Saldo', fontSize: 8, alignment: 'center', colSpan: 3 }, '', '',
    ],
    [
      label('Fecha', 'left'), label('Factura', 'left'), label('Kilos'), label('porcent'), label('Vr. Inventario'),
      label('Especial'), label('% pond'), '', label('Remisión'), label('Kilos'), label('% salida'), label('Valor'),
      '', label('Kilos'), label('Valor', 'right'), label('Vr. Unit.', 'right'),
    ],
  ];
  const title: Content = {
    columns: [
      { text: 'INVENTARIO', fontSize: 20, width: 'auto' },
      { text: balanced[0]?.specialType ?? '', fontSize: 20, bold: true, width: 'auto', margin: [12, 0, 0, 0] },
    ],
    margin: [0, 0, 0, 8],
  };
  const longDate = today.toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  const footer = (currentPage: number, pageCount: number): ContentStack => ({
    stack: [
      { canvas: [{ type: 'line', x1: 0, y1: 0, x2: 445, y2: 0, lineWidth: 0.5 }] },
      {
        columns: [
          { text: longDate, bold: true, fontSize: 9, width: 222 },
          { text: `Página ${currentPage} de ${pageCount}`, bold: true, fontSize: 9, alignment: 'right', width: 222 },
        ],
        margin: [0, 2, 0, 0],
      },
    ],
  });
  const body: Content[] = [
    title,
    {
      table: { headerRows: 2, widths: WIDTHS, body: [...header, ...balanced.map(detailRow)] },
      layout: LAYOUT,
    },
  ];

  if (continuous) {
    // Pagina de alto 'auto': pdfmake no puede ubicar un pie de pagina (quedaria en Infinity), asi
    // que el pie va al final del contenido - en la vista continua de Access tambien sale una vez.
    return {
      pageSize: { width: 792, height: 'auto' },
      pageMargins: [20, 20, 20, 20],
      defaultStyle: { font: 'Roboto', fontSize: 8 },
      content: [...body, { ...footer(1, 1), margin: [0, 12, 0, 0] }],
    };
  }
  return {
    pageSize: 'LETTER',
    pageOrientation: 'landscape',
    pageMargins: [20, 20, 20, 40],
    defaultStyle: { font: 'Roboto', fontSize: 8 },
    content: body,
    footer: (currentPage, pageCount) => ({ ...footer(currentPage, pageCount), margin: [20, 10, 20, 0] }),
  };
}
