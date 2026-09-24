import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { RemissionResponse } from '../../core/models/inventory.model';

function money(value: number): string {
  return value.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function longSpanishDate(isoDate: string): string {
  return new Date(isoDate + 'T00:00:00').toLocaleDateString('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

const CELL = { fontSize: 9, margin: [2, 2, 2, 2] as [number, number, number, number] };
const HEADER_CELL = { fontSize: 9, bold: true, alignment: 'center' as const, margin: [2, 2, 2, 2] as [number, number, number, number] };

/** "Remisión" (Form_EXITS.bas / reports/Remision.txt): despacho de inventario - "Reimprimir Remisión". */
export function buildRemissionDocDefinition(
  remission: RemissionResponse,
  logoDataUrl: string | null,
): TDocumentDefinitions {
  const headerBlock: Content = {
    table: {
      widths: ['*', 200],
      body: [
        [
          {
            columns: [
              logoDataUrl ? { image: logoDataUrl, width: 55, margin: [0, 0, 8, 0] } : { text: '', width: 55 },
              {
                stack: [
                  { text: 'COOPERATIVA DE CAFICULTORES', bold: true, fontSize: 10 },
                  { text: 'DE OCCIDENTE DE NARIÑO LTDA.', bold: true, fontSize: 10 },
                  { text: 'NIT 891.200.986-8', fontSize: 9 },
                  { text: remission.agencyName, fontSize: 8 },
                ],
              },
            ],
            border: [false, false, false, false],
          },
          {
            stack: [
              { text: 'REMISIÓN', bold: true, fontSize: 11, alignment: 'center' },
              { text: `N° ${remission.remissionNumber}`, bold: true, fontSize: 13, alignment: 'center', margin: [0, 6, 0, 0] },
            ],
            border: [true, true, true, true],
            margin: [4, 8, 4, 8],
          },
        ],
      ],
    },
    layout: 'noBorders',
  };

  const infoBlock: Content = {
    table: {
      widths: [90, '*'],
      body: [
        [{ text: 'Fecha:', ...CELL, bold: true }, { text: longSpanishDate(remission.remissionDate), ...CELL }],
        [{ text: 'Destino:', ...CELL, bold: true }, { text: remission.destination ?? '', ...CELL }],
        [{ text: 'Conductor:', ...CELL, bold: true }, { text: remission.conductorName ?? '', ...CELL }],
        [{ text: 'Transportadora:', ...CELL, bold: true }, { text: remission.transportCompany ?? '', ...CELL }],
        [{ text: 'Placa:', ...CELL, bold: true }, { text: remission.vehiclePlate ?? '', ...CELL }],
      ],
    },
    layout: { defaultBorder: true },
  };

  const linesTable: Content = {
    table: {
      widths: ['*', 90, 90, 90],
      body: [
        [
          { text: 'Movimiento de Inventario', ...HEADER_CELL },
          { text: 'Cantidad (kg)', ...HEADER_CELL },
          { text: 'Vr. Unitario', ...HEADER_CELL },
          { text: 'Vr. Salida', ...HEADER_CELL },
        ],
        ...remission.lines.map((line) => [
          { text: String(line.inventoryMovementId), ...CELL },
          { text: money(line.quantity), ...CELL, alignment: 'right' as const },
          { text: money(line.unitValue), ...CELL, alignment: 'right' as const },
          { text: money(line.outputValue), ...CELL, alignment: 'right' as const },
        ]),
      ],
    },
    layout: { defaultBorder: true },
  };

  const signatures: Content = {
    columns: [
      { text: 'FIRMA CONDUCTOR', bold: true, alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
      { text: 'DESPACHADO POR', bold: true, alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
    ],
  };

  return {
    pageSize: 'LETTER',
    pageMargins: [30, 30, 30, 30],
    content: [headerBlock, { text: '', margin: [0, 4, 0, 0] }, infoBlock, { text: '', margin: [0, 8, 0, 0] }, linesTable, signatures],
    defaultStyle: { font: 'Roboto' },
  };
}
