import type { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { HuskPurchaseResponse } from '../../../core/models/husk-purchase.model';
import {
  CELL,
  HEADER_CELL,
  LABEL_CELL,
  buildInvoiceDoc,
  money,
  paymentCells,
  wholePeso,
} from '../dry-coffee/dry-coffee-invoice';

/**
 * Reporte "Factura Pasilla" del export de Access (reports/Factura Pasilla.txt): mismo encabezado,
 * identificacion, formas de pago y pie que la factura de Cafe Seco; solo cambia la tabla central.
 * Formatos segun DecimalPlaces del export: Sacos/Kilos/Peso/% con 2, Pr_AlmSana y Vr_Kilo en 0.
 */
export function buildHuskInvoiceDocDefinition(
  purchase: HuskPurchaseResponse,
  logoDataUrl: string | null,
): TDocumentDefinitions {
  const dataRow = (label: string, value: string, pesoGr = '', pct = ''): Content[] => [
    { text: label, ...LABEL_CELL },
    { text: value, ...CELL, alignment: 'right' },
    { text: pesoGr, ...CELL, alignment: 'right' },
    { text: pct, ...CELL, alignment: 'right' },
    { text: '', ...CELL },
  ];
  const liquidationRow = (label: string, value: string, bold = false): Content[] => [
    { text: label, ...LABEL_CELL, bold },
    { text: value, ...CELL, alignment: 'right', bold },
  ];
  // Debajo de NETO A PAGAR el reporte no tiene rejilla a la izquierda (las lineas verticales
  // internas terminan en Top=6690), solo el borde exterior.
  const blankLeft: TableCell[] = [
    { text: '', colSpan: 5, border: [true, false, false, false] },
    {}, {}, {}, {},
  ];
  const [cashRow, checkRow, transferRow, cardTerminalRow] = paymentCells(purchase);

  const mainTable: Content = {
    table: {
      widths: [125, 55, 35, 28, 35, 85, 130],
      body: [
        [
          { text: '', ...CELL },
          { text: '', ...CELL },
          { text: 'Peso gr', ...HEADER_CELL },
          { text: '%', ...HEADER_CELL },
          { text: 'Factor', ...HEADER_CELL },
          { text: 'LIQUIDACION', ...HEADER_CELL, colSpan: 2 },
          {},
        ],
        [...dataRow('Sacos', money(purchase.bagsCount)), ...liquidationRow('VALOR BRUTO $', money(purchase.grossValue))],
        [...dataRow('Kilos Brutos', money(purchase.grossKg)), ...liquidationRow('Aporte Socio', money(purchase.associateContribution))],
        [...dataRow('Destare', money(purchase.tareKg)), ...liquidationRow('Descuento Cooperativo', money(purchase.cooperativeDiscount))],
        [...dataRow('Kilos Netos Pasilla', money(purchase.netKg)), ...liquidationRow('Retención en la Fuente', money(purchase.withholding))],
        [
          ...dataRow(
            'Precio Por Punto $:',
            wholePeso(purchase.pointPrice),
            money(purchase.almondWeight),
            money(purchase.almondPercentage),
          ),
          ...liquidationRow('Descuento Financiero', money(purchase.shrinkageDiscount)),
        ],
        [...dataRow('VALOR KILO PASILLA $:', wholePeso(purchase.unitPrice)), ...liquidationRow('Otros Descuentos', money(purchase.otherDiscounts))],
        [...dataRow('', ''), ...liquidationRow('NETO A PAGAR $', money(purchase.netToPay), true)],
        [...blankLeft, { text: 'FORMAS\nDE\nPAGO', rowSpan: 4, ...CELL, bold: true, alignment: 'center' }, cashRow],
        [...blankLeft, {}, checkRow],
        [...blankLeft, {}, transferRow],
        [...blankLeft, {}, cardTerminalRow],
      ],
    },
    layout: { defaultBorder: true },
  };

  // Macro "Imprime Factura Pasilla" abre "Factura Pasilla" dos veces -> buildInvoiceDoc ya arma las
  // dos copias identicas, igual que Seco.
  return buildInvoiceDoc(purchase, logoDataUrl, mainTable);
}
