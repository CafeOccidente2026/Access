import type { Content, ContentStack, ContentTable, CustomTableLayout, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { RemissionResponse } from '../../core/models/inventory.model';
import { money, wholePeso } from '../purchase-forms/dry-coffee/dry-coffee-invoice';

// Encabezado del papel preimpreso (capturas/remision-formato.png). Direccion y NIT como en la factura;
// telefonos leidos de la captura (baja resolucion) - pendientes de confirmar con una remision real.
const COMPANY = 'COOPERATIVA DE CAFICULTORES DE OCCIDENTE DE NARIÑO.';
const NIT = 'NIT. 891.200.986-8';
const ADDRESS = 'CARRERA 32 A No. 18 - 105 - TELEFONO: 602 721 9700 - CELULAR: 311 749 8702 - PASTO (N)';

const T = { fontSize: 6 };
const HEAD = { fontSize: 6, alignment: 'center' as const };
const VALUE = { fontSize: 8, bold: true };
const EMPTY: TableCell = { text: '' };
/** Filas de DESPACHOS del formato; si la remision trae mas lineas, la tabla crece. */
const DISPATCH_ROWS = 7;

/** Rejilla fina con marco grueso, como el formato impreso. */
const GRID: CustomTableLayout = {
  hLineWidth: (i: number, node: ContentTable) => (i === 0 || i === node.table.body.length ? 1.5 : 0.5),
  vLineWidth: (i: number, node: ContentTable) =>
    i === 0 || i === (Array.isArray(node.table.widths) ? node.table.widths.length : 0) ? 1.5 : 0.5,
  paddingLeft: () => 2,
  paddingRight: () => 2,
  paddingTop: () => 1,
  paddingBottom: () => 1,
};

function dateParts(isoDate: string): [string, string, string] {
  const [year, month, day] = isoDate.split('-');
  return [day, month, year];
}

/** Fondo de la remision: todas sus lineas son del mismo fondo (RemissionServiceImpl). */
function fundOf(remission: RemissionResponse): string | null {
  const funds = new Set(remission.lines.map((l) => l.fundCode));
  return funds.size === 1 ? [...funds][0] : null;
}

function header(remission: RemissionResponse, logoDataUrl: string | null): Content {
  return {
    columns: [
      {
        width: '*',
        stack: [
          {
            columns: [
              { text: 'CaféOccidente', bold: true, fontSize: 16, width: 'auto' },
              logoDataUrl ? { image: logoDataUrl, width: 22, margin: [6, 0, 0, 0] } : { text: '' },
            ],
          },
          { text: COMPANY, bold: true, fontSize: 6, margin: [0, 4, 0, 0] },
          { text: NIT, fontSize: 6, margin: [0, 2, 0, 0] },
          { text: ADDRESS, bold: true, fontSize: 5.5, margin: [0, 3, 0, 0] },
        ],
      },
      {
        width: 130,
        table: {
          widths: ['*'],
          heights: [36],
          body: [[{ text: `Nº ${remission.displayNumber}`, color: '#c00000', bold: true, fontSize: 14, alignment: 'center', margin: [0, 10, 0, 0] }]],
        },
        layout: { hLineWidth: () => 1.5, vLineWidth: () => 1.5 },
      },
      // El recuadro del Nº termina donde termina el bloque de fecha (84% del ancho, como el papel).
      { width: 88, text: '' },
    ],
    margin: [0, 0, 0, 8],
  };
}

/** FECHA / ALMACEN DESTINATARIO / LINEA FINANCIAMIENTO - OTROS / Hora Salida / DESPACHOS. */
function dateBlock(remission: RemissionResponse): ContentTable {
  const [day, month, year] = dateParts(remission.remissionDate);
  const fund = fundOf(remission);
  // Confirmado por el negocio: LF -> LINEA FINANCIAMIENTO, RP (recursos propios) -> OTROS.
  const mark = (code: string): TableCell => ({ text: fund === code ? 'X' : '', ...VALUE, fontSize: 12, alignment: 'center', rowSpan: 2 });
  return {
    table: {
      widths: [20, 20, 21, 60, 20, 20, 100, 100, 100],
      body: [
        [
          { text: 'FECHA', ...HEAD, colSpan: 3 }, {}, {},
          { text: 'ALMACÉN DESTINATARIO', ...HEAD, colSpan: 4 }, {}, {}, {},
          { text: 'LÍNEA FINANCIAMIENTO', ...HEAD, rowSpan: 2, margin: [0, 6, 0, 0] },
          mark('LF'),
        ],
        [
          { text: 'Día', ...HEAD }, { text: 'Mes', ...HEAD }, { text: 'Año', ...HEAD },
          { text: remission.destination ?? '', ...VALUE, colSpan: 4, rowSpan: 2 }, {}, {}, {},
          {}, {},
        ],
        [
          { text: day, ...VALUE, alignment: 'center' }, { text: month, ...VALUE, alignment: 'center' }, { text: year, ...VALUE, alignment: 'center' },
          {}, {}, {}, {},
          { text: 'OTROS', ...HEAD, rowSpan: 2, margin: [0, 6, 0, 0] },
          mark('RP'),
        ],
        [
          { text: 'Hora Salida:', ...T, colSpan: 3 }, {}, {},
          { text: 'HH : MM', ...HEAD, color: '#aaaaaa' }, { text: 'am', ...HEAD, color: '#aaaaaa' }, { text: 'pm', ...HEAD, color: '#aaaaaa' },
          { text: 'DESPACHOS', bold: true, fontSize: 10, alignment: 'center' },
          {}, {},
        ],
      ],
    },
    layout: GRID,
  };
}

/** Tabla DESPACHOS + "Por Conducto de:". Casilleros segun reports/Remision.txt de Access. */
function dispatchBlock(remission: RemissionResponse, withFactor: boolean): Content {
  // reports/Remision.txt: cada salida ocupa dos renglones del papel - datos arriba (Top 345) y, en
  // el de abajo (Top 690), "$" + Vr_Salida bajo Kilos Netos (Texto55, DecimalPlaces 0).
  const lineCells = (row: number): TableCell[] => {
    const line = remission.lines[Math.floor(row / 2)];
    if (!line) {
      return [EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY];
    }
    if (row % 2 === 1) {
      return [
        EMPTY, EMPTY, EMPTY,
        { text: '$', ...VALUE, fontSize: 7, alignment: 'right' },
        { text: wholePeso(line.outputValue), ...VALUE, fontSize: 7, alignment: 'right' },
        EMPTY,
      ];
    }
    // Sacos / Kilos Brutos: en blanco solo en lineas anteriores a V31, que no los guardaban.
    // Factor = PorcAlmSanaSl (frpond de Sld3); las lineas anteriores a V32 usan el de su entrada.
    const factor = line.exitPercentage ?? line.healthyPercentage;
    return [
      { text: remission.agencyName, ...VALUE, fontSize: 7 },
      { text: line.specialType ?? '', ...VALUE, fontSize: 7 },
      { text: line.sacos == null ? '' : String(line.sacos), ...VALUE, fontSize: 7, alignment: 'right' },
      { text: line.grossKg == null ? '' : money(line.grossKg), ...VALUE, fontSize: 7, alignment: 'right' },
      { text: money(line.quantity), ...VALUE, fontSize: 7, alignment: 'right' },
      { text: withFactor && factor != null ? money(factor) : '', ...VALUE, fontSize: 7, alignment: 'right' },
    ];
  };
  const labeled = (label: string, value: string | null, extra: Partial<TableCell> = {}): TableCell =>
    ({ stack: [{ text: label, ...T }, { text: value ?? '', ...VALUE }], ...extra }) as TableCell;

  const right: TableCell[][] = [
    [labeled('Empresa Transportadora', remission.transportCompany, { colSpan: 4, rowSpan: 2 }), {}, {}, {}],
    [{}, {}, {}, {}],
    [
      labeled('Vehículo No.', remission.vehiclePlate, { colSpan: 2, rowSpan: 2 }), {},
      labeled('Nombre del Conductor', remission.conductorName, { colSpan: 2, rowSpan: 2 }), {},
    ],
    [{}, {}, {}, {}],
    [
      { text: [{ text: 'C.C. No. ', ...T }, { text: remission.conductorIdNumber ?? '', ...VALUE }], colSpan: 2 }, {},
      { text: 'F L E T E S', ...HEAD, bold: true, colSpan: 2 }, {},
    ],
    [{ text: 'de.', ...T, colSpan: 2 }, {}, { text: 'Tonelada', ...HEAD }, { text: 'Total', ...HEAD }],
    [{ text: 'Pase No.', ...T, colSpan: 2 }, {}, EMPTY, EMPTY],
  ];
  const rows = Math.max(DISPATCH_ROWS, remission.lines.length * 2);
  const body: TableCell[][] = [
    [
      { text: 'PROCEDENCIA', ...HEAD }, { text: 'CLASE DE CAFÉ', ...HEAD }, { text: 'No. Sacos', ...HEAD },
      { text: 'Kilos Brutos', ...HEAD }, { text: 'Kilos Netos', ...HEAD, fontSize: 7 }, { text: 'Factor', ...HEAD },
      { text: 'Por Conducto de:', ...HEAD, colSpan: 4 }, {}, {}, {},
    ],
    ...Array.from({ length: rows }, (_, i) => [
      ...lineCells(i),
      ...(right[i] ?? [{ text: '', colSpan: 4 }, {}, {}, {}]),
    ]),
  ];
  return {
    table: { widths: [80, 80, 36, 36, 40, 36, 80, 36, 45, 55], heights: 14, body },
    layout: GRID,
  };
}

/** Autorizacion, Alistado por y el bloque CAFE RECIBIDO: todo en blanco (se llena a mano). */
function manualBlocks(): Content[] {
  const signature = (caption: string): ContentStack => ({
    stack: [
      { canvas: [{ type: 'line', x1: 20, y1: 0, x2: 190, y2: 0, lineWidth: 0.5 }], margin: [0, 18, 0, 0] },
      { text: caption, ...HEAD },
    ],
  });
  const authorization: Content = {
    table: {
      widths: [255, 200],
      body: [
        [
          {
            stack: [
              { text: 'Autorizo al conductor para retirar el café y el empaque rechazado del presente envío.', ...HEAD },
              { columns: [{ text: 'Nombre:', ...T, width: 30, margin: [0, 26, 0, 0] }, signature('Punto de Compras')] },
            ],
          },
          { stack: [{ text: ' ', ...T }, signature('Firma del Conductor')] },
        ],
      ],
    },
    layout: { hLineWidth: () => 1.5, vLineWidth: () => 1.5 },
  };
  const blankLine = (width: number): Content =>
    ({ canvas: [{ type: 'line', x1: 0, y1: 8, x2: width, y2: 8, lineWidth: 0.5 }], width }) as Content;
  const preparedBy: Content = {
    columns: [
      { text: 'Alistado por', ...T, width: 60 },
      blankLine(170),
      { text: 'No.', ...T, width: 20, margin: [8, 0, 0, 0] },
      blankLine(130),
      { text: 'de', ...T, width: 20, margin: [8, 0, 0, 0] },
      blankLine(90),
    ],
    margin: [0, 10, 0, 6],
  };
  const received: ContentTable = {
    table: {
      widths: [20, 20, 21, 40, 40, 40, 60, 40, 60, 120],
      body: [
        [
          { text: 'BODEGA', ...HEAD, colSpan: 3 }, {}, {}, { text: 'NAVE', ...HEAD }, { text: 'ARRUME', ...HEAD },
          { text: 'SACOS', ...HEAD }, { text: 'KILOS BRUTOS', ...HEAD }, EMPTY, { text: 'NOMBRE DEL RESPONSABLE', ...HEAD, colSpan: 2 }, {},
        ],
        ...Array.from({ length: 8 }, (): TableCell[] => [
          { text: ' ', ...T, colSpan: 3 }, {}, {}, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, { text: '', colSpan: 2 }, {},
        ]),
        [{ text: 'TOTAL RECIBIDO:', ...T, colSpan: 7 }, {}, {}, {}, {}, {}, {}, { text: '', colSpan: 3 }, {}, {}],
        [{ text: 'OBSERVACIONES:', ...T, colSpan: 10, margin: [0, 0, 0, 14] }, {}, {}, {}, {}, {}, {}, {}, {}, {}],
        [
          { text: 'FECHA DE RECIBIDO', ...HEAD, colSpan: 3 }, {}, {},
          { text: '', colSpan: 4, border: [false, false, false, false] }, {}, {}, {},
          { text: '', colSpan: 2 }, {}, { text: 'REGISTRADO', ...HEAD },
        ],
        [
          { text: 'Día', ...HEAD }, { text: 'Mes', ...HEAD }, { text: 'Año', ...HEAD },
          { ...signature('Firma responsable del recibo'), colSpan: 4, rowSpan: 2, border: [false, false, false, false] } as TableCell, {}, {}, {},
          { text: '', colSpan: 2, rowSpan: 2 }, {}, { text: '', rowSpan: 2 },
        ],
        [{ text: ' ', ...T }, EMPTY, EMPTY, {}, {}, {}, {}, {}, {}, {}],
      ],
    },
    layout: GRID,
  };
  return [
    { ...authorization, margin: [0, 0, 0, 0] },
    preparedBy,
    { text: 'CAFÉ RECIBIDO', bold: true, fontSize: 8, alignment: 'center', margin: [0, 0, 0, 2] },
    received,
  ];
}

/** Una copia del formato "Despachos" con los datos del sistema. */
function copy(remission: RemissionResponse, logoDataUrl: string | null, withFactor: boolean): Content[] {
  return [header(remission, logoDataUrl), dateBlock(remission), dispatchBlock(remission, withFactor), ...manualBlocks()];
}

/**
 * Remision impresa (formato preimpreso "Despachos"): 4 copias identicas en un solo PDF, la primera
 * con el Factor y las otras tres sin el. Mismo constructor al crear (Registrar Salidas) y al
 * reimprimir.
 */
export function buildRemissionDocDefinition(
  remission: RemissionResponse,
  logoDataUrl: string | null,
): TDocumentDefinitions {
  const withoutFactor = (): Content => ({ stack: copy(remission, logoDataUrl, false), pageBreak: 'before' });
  return {
    pageSize: 'LETTER',
    pageMargins: [30, 30, 30, 30],
    content: [{ stack: copy(remission, logoDataUrl, true) }, withoutFactor(), withoutFactor(), withoutFactor()],
    defaultStyle: { font: 'Roboto' },
  };
}
