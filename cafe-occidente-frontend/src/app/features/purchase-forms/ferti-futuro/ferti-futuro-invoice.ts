import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { FertiFuturoPurchaseResponse } from '../../../core/models/ferti-futuro-purchase.model';

function money(value: number): string {
  return value.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function wholePeso(value: number): string {
  return Math.round(value).toLocaleString('es-CO');
}

function longSpanishDate(isoDate: string): string {
  return new Date(isoDate + 'T00:00:00').toLocaleDateString('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

function shortDate(isoDate: string): string {
  const d = new Date(isoDate + 'T00:00:00');
  return `${d.getDate()}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

const CELL = { fontSize: 8, margin: [2, 2, 2, 2] as [number, number, number, number] };
const LABEL_CELL = { ...CELL };
const HEADER_CELL = { fontSize: 8, bold: true, alignment: 'center' as const, margin: [2, 2, 2, 2] as [number, number, number, number] };

/**
 * Documento Soporte de liquidación FERTIFUTURO. El reporte real de Access ("Factura Fertifuturo",
 * referenciado por el macro "Imprime Factura Fertifuturo") no está en el export de VBA disponible
 * (docs/legacy-vba-export/eltambo/reports/ solo trae ReteMesCursoFerti, el reporte de retención
 * mensual, no la factura) - decisión confirmada con el usuario: reusar el layout de Café Seco
 * (sí verificado contra su reporte real), adaptado a los campos propios de Fertifuturo (sin Peso
 * Tot Alm/Pasilla - Peso Alm Sana/Defec son inputs directos - y con la fila extra de Incremento de
 * Calidad, que Seco no tiene).
 */
export function buildFertiFuturoInvoiceDocDefinition(
  purchase: FertiFuturoPurchaseResponse,
  logoDataUrl: string | null,
): TDocumentDefinitions {
  const documentNumber = `${purchase.prefix} - ${purchase.invoiceNumber.toLocaleString('es-CO')}`;
  const title = `Documento Soporte de liquidación Fertifuturo tipo ${purchase.specialType}`;
  const associated = purchase.growerType === 'S' ? 'ASOCIADO' : 'NO ASOCIADO';
  const nit = /^\d+$/.test(purchase.idNumber) ? Number(purchase.idNumber).toLocaleString('es-CO') : purchase.idNumber;

  const headerBlock: Content = {
    table: {
      widths: ['*', 200],
      body: [
        [
          {
            columns: [
              logoDataUrl
                ? { image: logoDataUrl, width: 55, margin: [0, 0, 8, 0] }
                : { text: '', width: 55 },
              {
                stack: [
                  { text: 'COOPERATIVA DE CAFICULTORES', bold: true, fontSize: 10 },
                  { text: 'DE OCCIDENTE DE NARIÑO LTDA.', bold: true, fontSize: 10 },
                  { text: 'NIT 891.200.986-8', fontSize: 9 },
                  { text: 'Pasto, Carrera 32A No. 18-105', fontSize: 8 },
                  { text: 'Tel 6027219788', fontSize: 8 },
                ],
              },
            ],
            border: [false, false, false, false],
          },
          {
            stack: [
              { text: title, bold: true, fontSize: 11, alignment: 'center' },
              { text: documentNumber, bold: true, fontSize: 13, alignment: 'center', margin: [0, 6, 0, 0] },
            ],
            border: [true, true, true, true],
            margin: [4, 8, 4, 8],
          },
        ],
      ],
    },
    layout: 'noBorders',
  };

  const identificationBlock: Content = {
    table: {
      widths: [70, '*', 90, 90],
      body: [
        [
          { text: 'Nombre:', ...LABEL_CELL },
          { text: `${purchase.firstName} ${purchase.lastName}`, colSpan: 2, ...CELL },
          {},
          { text: associated, rowSpan: 2, bold: true, fontSize: 9, alignment: 'center', margin: [2, 8, 2, 2] },
        ],
        [
          { text: 'NIT:', ...LABEL_CELL },
          { text: nit, colSpan: 2, ...CELL },
          {},
          {},
        ],
        [
          { text: 'Dirección:', ...LABEL_CELL },
          { text: purchase.address, colSpan: 3, ...CELL },
          {},
          {},
        ],
        [
          { text: 'Fecha Comprobante', ...LABEL_CELL, alignment: 'center' },
          { text: longSpanishDate(purchase.purchaseDate), ...CELL, alignment: 'center' },
          { text: 'Fondo:', ...LABEL_CELL, alignment: 'center' },
          { text: purchase.fundCode, bold: true, fontSize: 11, alignment: 'center' },
        ],
        [
          { text: 'Punto de Compra:', ...LABEL_CELL },
          { text: purchase.purchasePoint, colSpan: 3, ...CELL },
          {},
          {},
        ],
      ],
    },
    layout: { defaultBorder: true },
  };

  const dataRow = (label: string, value: string, pesoGr?: string, pct?: string): Content[] => [
    { text: label, ...LABEL_CELL },
    { text: value, ...CELL, alignment: 'right' },
    { text: pesoGr ?? '', ...CELL, alignment: 'right' },
    { text: pct ?? '', ...CELL, alignment: 'right' },
  ];

  const liquidationRow = (label: string, value: string, bold = false): Content[] => [
    { text: label, ...LABEL_CELL, ...(bold ? { color: 'black', bold: true } : {}) },
    { text: value, ...CELL, alignment: 'right', bold },
  ];

  const paymentRow = (label: string, value: string): Content[] => [
    { text: label, ...CELL, alignment: 'right' },
    { text: value, ...CELL, alignment: 'right', bold: true },
  ];

  const cash = (purchase.paymentMethod ?? '').toUpperCase() === 'EFECTIVO' ? purchase.netToPay : 0;
  const check = (purchase.paymentMethod ?? '').toUpperCase() === 'CHEQUE' ? purchase.netToPay : 0;
  const cardTerminal = (purchase.paymentMethod ?? '').toUpperCase() === 'DATAFONO' ? purchase.netToPay : 0;

  const mainTable: Content = {
    table: {
      widths: [140, 55, 40, 32, 85, 130],
      body: [
        [
          { text: '', ...CELL },
          { text: '', ...CELL },
          { text: 'Peso gr', ...HEADER_CELL },
          { text: '%', ...HEADER_CELL },
          { text: 'LIQUIDACION', ...HEADER_CELL, colSpan: 2 },
          {},
        ],
        [
          ...dataRow('Sacos', money(purchase.sacos)),
          ...liquidationRow('VALOR BRUTO $', money(purchase.grossValue)),
        ],
        [
          ...dataRow('Kilos Brutos', money(purchase.grossKg)),
          ...liquidationRow('Aporte Socio', money(purchase.associateContribution)),
        ],
        [
          ...dataRow('Kilos Netos', money(purchase.netKg)),
          ...liquidationRow('Descuento Cooperativo', money(purchase.cooperativeDiscount)),
        ],
        [
          ...dataRow('Destare', money(purchase.tareKg)),
          ...liquidationRow('Retención en la Fuente', money(purchase.withholding)),
        ],
        [
          ...dataRow('Peso Almendra Sana', money(purchase.healthyStoredWeight), '', money(purchase.healthyPercentage)),
          ...liquidationRow('Descuento Financiero', money(purchase.freightDiscount)),
        ],
        [
          ...dataRow('Peso Almendra Defec', money(purchase.defectiveStoredWeight), '', money(purchase.defectivePercentage)),
          ...liquidationRow('Otros Descuentos', money(purchase.otherDiscounts)),
        ],
        [
          ...dataRow('Precio Sustentación $:', wholePeso(purchase.healthyUnitPrice)),
          ...liquidationRow('NETO A PAGAR  $', money(purchase.netToPay), true),
        ],
        [
          ...dataRow('Precio Almendra Defec $:', wholePeso(purchase.defectiveUnitPrice)),
          { text: 'FORMAS\nDE\nPAGO', rowSpan: 4, ...CELL, bold: true, alignment: 'center' },
          {
            table: { widths: ['*', 70], body: [paymentRow('EFECTIVO $', money(cash))] },
            layout: 'noBorders',
          },
        ],
        [
          ...dataRow('Bonificación $:', wholePeso(purchase.bonus)),
          {},
          {
            table: { widths: ['*', 70], body: [paymentRow('CHEQUE $', money(check))] },
            layout: 'noBorders',
          },
        ],
        [
          ...dataRow('Incremento Calidad $:', wholePeso(purchase.qualityIncrementAmount)),
          {},
          {
            table: { widths: ['*', 70], body: [paymentRow('DATAFONO $', money(cardTerminal))] },
            layout: 'noBorders',
          },
        ],
        [
          ...dataRow('Castigo $:', wholePeso(purchase.penalty)),
          {},
          {},
        ],
        [
          ...dataRow('VALOR KILO $:', wholePeso(purchase.unitPrice)),
          {},
          {},
        ],
      ],
    },
    layout: { defaultBorder: true },
  };

  const legal =
    'Este documento es un soporte en adquisición de bienes o servicios a sujetos no obligados a expedir ' +
    `facturas de venta. Autorización de facturación No.${purchase.dianResolution} aprobado en ` +
    `${shortDate(purchase.resolutionDate)} vigente ${purchase.validity} meses, prefijo ${purchase.prefix} ` +
    `desde el número ${purchase.resolutionFrom} al ${purchase.resolutionTo}.`;

  const signatures: Content = {
    columns: [
      { text: 'FIRMA VENDEDOR', bold: true, alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
      { text: 'COMPRADO POR:', bold: true, alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
      { text: 'REVISADO POR:', bold: true, alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
    ],
  };

  return {
    pageSize: 'LETTER',
    pageMargins: [30, 30, 30, 30],
    content: [
      headerBlock,
      { text: '', margin: [0, 4, 0, 0] },
      identificationBlock,
      mainTable,
      signatures,
      { text: legal, fontSize: 7, alignment: 'center', margin: [0, 20, 0, 0] },
    ],
    defaultStyle: { font: 'Roboto' },
  };
}

export { loadLogoDataUrl } from '../dry-coffee/dry-coffee-invoice';
