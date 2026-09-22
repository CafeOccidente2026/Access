import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { DryCoffeePurchaseResponse } from '../../../core/models/dry-coffee-purchase.model';

/** Formato colombiano con 2 decimales (Valor Bruto, Aporte Socio, Retefuente, Neto a Pagar, pesos/kilos...). */
function money(value: number): string {
  return value.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** Formato colombiano sin decimales (precios por unidad: Precio Carga, Sustentación, Pasilla, Sobreprecio,
 *  Merma por defecto, Vr. Kilo) - igual que en el PDF de referencia, esos campos no llevan centavos. */
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
const LABEL_CELL = { ...CELL, bold: true };

/**
 * Documento Soporte de compra de café pergamino seco - mismo layout y campos que el PDF de
 * referencia (capturas/prueba de factura sin boton de nube.pdf), mapeados 1:1 contra
 * DryCoffeePurchaseResponse (ninguna formula nueva, solo la que ya calcula el backend).
 * "Merma por defecto en tasa $" = Castigo (penalty): mismo campo y mismo lugar en la cascada que
 * Form_COMPRAS.bas (Castigo_lostFocus), la unica lectura razonable dado el layout del reporte real.
 */
export function buildDryCoffeeInvoiceDocDefinition(
  purchase: DryCoffeePurchaseResponse,
  logoDataUrl: string | null,
): TDocumentDefinitions {
  const documentNumber = `${purchase.prefix} - ${purchase.invoiceNumber.toLocaleString('es-CO')}`;
  const title = `Documento Soporte de compra de café pergamino seco tipo ${purchase.specialType}`;
  const associated = purchase.growerType === 'S' ? 'ASOCIADO' : 'NO ASOCIADO';
  const nit = /^\d+$/.test(purchase.idNumber) ? Number(purchase.idNumber).toLocaleString('es-CO') : purchase.idNumber;

  const paymentMethod = (purchase.paymentMethod ?? '').toUpperCase();
  const cash = paymentMethod === 'EFECTIVO' ? purchase.netToPay : 0;
  const check = paymentMethod === 'CHEQUE' ? purchase.netToPay : 0;
  const transfer = paymentMethod === 'TRANSFERENCIA' || paymentMethod === 'TRANSFER' ? purchase.netToPay : 0;
  const cardTerminal = paymentMethod === 'DATAFONO' ? purchase.netToPay : 0;

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
          { text: 'Teléfono:', ...LABEL_CELL },
          { text: purchase.cellphone, ...CELL },
          { text: 'Fecha Comprobante', ...LABEL_CELL, alignment: 'center' },
          { text: 'Fondo:', ...LABEL_CELL, alignment: 'center' },
        ],
        [
          { text: 'Punto de Compra:', ...LABEL_CELL },
          { text: purchase.purchasePoint, ...CELL },
          { text: longSpanishDate(purchase.purchaseDate), ...CELL, alignment: 'center' },
          { text: purchase.fundCode, bold: true, fontSize: 11, alignment: 'center' },
        ],
      ],
    },
    layout: { defaultBorder: true },
  };

  const dataRow = (
    label: string,
    value: string,
    pesoGr?: string,
    pct?: string,
    factor?: string,
  ): Content[] => [
    { text: label, ...LABEL_CELL },
    { text: value, ...CELL, alignment: 'right' },
    { text: pesoGr ?? '', ...CELL, alignment: 'right' },
    { text: pct ?? '', ...CELL, alignment: 'right' },
    { text: factor ?? '', ...CELL, alignment: 'right' },
  ];

  const liquidationRow = (label: string, value: string, bold = false): Content[] => [
    { text: label, ...LABEL_CELL, bold },
    { text: value, ...CELL, alignment: 'right', bold },
  ];

  const paymentRow = (label: string, value: string): Content[] => [
    { text: label, ...CELL, alignment: 'right' },
    { text: value, ...CELL, alignment: 'right', bold: true },
  ];

  const mainTable: Content = {
    table: {
      widths: [125, 55, 35, 28, 35, 85, 130],
      body: [
        [
          ...dataRow('Sacos', money(purchase.bagsCount), '', 'Peso gr', ''),
          ...liquidationRow('VALOR BRUTO $', money(purchase.grossValue)),
        ],
        [
          ...dataRow('Kilos Brutos', money(purchase.grossKg)),
          ...liquidationRow('Aporte Socio', money(purchase.associateContribution)),
        ],
        [
          ...dataRow('Destare', money(purchase.tareKg)),
          ...liquidationRow('Descuento Cooperativo', money(purchase.cooperativeDiscount)),
        ],
        [
          ...dataRow('Kilos Netos Pergamino', money(purchase.netKg)),
          ...liquidationRow('Retención en la Fuente', money(purchase.withholding)),
        ],
        [
          ...dataRow('Precio Carga Pergamino Seco $:', wholePeso(purchase.basePriceLoad)),
          ...liquidationRow('Descuento Financiero', money(purchase.freightDiscount)),
        ],
        [
          ...dataRow(
            'Peso Total Almendra/Porc Merma',
            '',
            money(purchase.totalStoredWeight),
            money(purchase.wastePercentage),
          ),
          ...liquidationRow('Otros Descuentos', money(purchase.otherDiscounts)),
        ],
        [
          ...dataRow(
            'Precio de Sustentación $:',
            wholePeso(purchase.healthyUnitPrice),
            money(purchase.healthyStoredWeight),
            '',
            money(purchase.healthyPercentage),
          ),
          ...liquidationRow('NETO A PAGAR  $', money(purchase.netToPay), true),
        ],
        [
          ...dataRow(
            'Precio Pasilla $:',
            wholePeso(purchase.defectiveUnitPrice),
            money(purchase.defectiveStoredWeight),
            money(purchase.defectivePercentage),
          ),
          { text: 'FORMAS\nDE\nPAGO', rowSpan: 4, ...LABEL_CELL, alignment: 'center' },
          {
            table: { widths: ['*', 70], body: [paymentRow('EFECTIVO $', money(cash))] },
            layout: 'noBorders',
          },
        ],
        [
          ...dataRow('Sobrepr Cafés Esp por Kg $:', wholePeso(purchase.bonus)),
          {},
          {
            table: { widths: ['*', 70], body: [paymentRow('CHEQUE $', money(check))] },
            layout: 'noBorders',
          },
        ],
        [
          ...dataRow('Merma por defecto en tasa $:', wholePeso(purchase.penalty)),
          {},
          {
            table: { widths: ['*', 70], body: [paymentRow('TRANSFER $', money(transfer))] },
            layout: 'noBorders',
          },
        ],
        [
          ...dataRow('VALOR KILO PERGAMINO $:', wholePeso(purchase.unitPrice)),
          {},
          {
            table: { widths: ['*', 70], body: [paymentRow('DATAFONO $', money(cardTerminal))] },
            layout: 'noBorders',
          },
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
      { text: 'FIRMA VENDEDOR', alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
      { text: 'COMPRADO POR:', alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
      { text: 'REVISADO POR:', alignment: 'center', fontSize: 8, margin: [0, 30, 0, 0] },
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

/** Descarga la imagen del logo y la convierte a data URL para incrustarla en el PDF (pdfmake no
 *  puede referenciar rutas relativas del sitio, necesita el contenido embebido). */
export async function loadLogoDataUrl(url: string): Promise<string | null> {
  try {
    const res = await fetch(url);
    const blob = await res.blob();
    return await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  } catch {
    return null;
  }
}
