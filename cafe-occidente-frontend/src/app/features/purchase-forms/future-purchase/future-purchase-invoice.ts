import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { FuturePurchaseResponse } from '../../../core/models/future-purchase.model';

function longSpanishDate(isoDate: string): string {
  return new Date(isoDate + 'T00:00:00').toLocaleDateString('es-CO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

function money(value: number): string {
  return value.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * "Manifiesto" (docs/legacy-vba-export/eltambo/reports/Manifiesto.txt, Comando36_Click en
 * Form_COMPRAS A FUTURO.bas): carta-compromiso de entrega futura, sin cascada de precio ni logo (el
 * reporte real de Access no tiene ninguna imagen, solo texto). Texto37 (nombre "amigable" del
 * Especial en la carta) se verificó contra el cascade real de Cuadro_combinado30_AfterUpdate: para
 * los 5 valores que el combo realmente puede tomar (RowSource real, no las ramas muertas del
 * AfterUpdate que cubren valores que ya no estan en el RowSource), Texto37 es identico al valor de
 * Especial - por eso no hace falta una tabla de traduccion, se usa specialType tal cual.
 */
export function buildFuturePurchaseManifestoDocDefinition(purchase: FuturePurchaseResponse): TDocumentDefinitions {
  const today = new Date().toISOString().slice(0, 10);
  const fullName = `${purchase.firstName} ${purchase.lastName}`;
  const deliveryDate = longSpanishDate(purchase.deliveryDate);

  const body: Content = {
    text: [
      'Mediante la presente, yo, ',
      { text: fullName, bold: true },
      ` identificado con C.C. No. ${purchase.idNumber}, me comprometo para con la Cooperativa de `,
      'Caficultores de Occidente de Nariño Ltda., a entregar la cantidad anunciada de ',
      { text: `${money(purchase.announcedKg)} kilos`, bold: true },
      ` para ${purchase.specialType} el día `,
      { text: deliveryDate, bold: true },
      ' con precio oficial de Sustentación de hoy de $ ',
      { text: money(purchase.healthyUnitPrice), bold: true },
      ' y bonificación de $ ',
      { text: money(purchase.bonus), bold: true },
      '.',
    ],
    fontSize: 12,
    lineHeight: 1.4,
    margin: [0, 40, 0, 60] as [number, number, number, number],
  };

  const signature: Content = {
    stack: [
      { text: '_________________________________________________', bold: true },
      { text: fullName, bold: true, margin: [0, 6, 0, 0] as [number, number, number, number] },
      { text: `C.C. No. ${purchase.idNumber}`, bold: true },
    ],
  };

  return {
    pageSize: 'LETTER',
    pageMargins: [60, 60, 60, 60],
    content: [
      { text: `${purchase.agencyName}, ${longSpanishDate(today)}`, fontSize: 12, alignment: 'right' },
      { text: 'Señores', bold: true, fontSize: 12, margin: [0, 30, 0, 0] as [number, number, number, number] },
      { text: 'COOPERATIVA DE CAFICULTORES DE OCCIDENTE DE NARIÑO LTDA.', bold: true, fontSize: 12 },
      { text: `${purchase.agencyName} NARIÑO`, bold: true, fontSize: 12 },
      body,
      signature,
    ],
    defaultStyle: { font: 'Roboto' },
  };
}
