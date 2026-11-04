import type { TDocumentDefinitions } from 'pdfmake/interfaces';

import { DryCoffeePurchaseResponse } from '../../../core/models/dry-coffee-purchase.model';
import { GreenCoffeePurchaseResponse } from '../../../core/models/green-coffee-purchase.model';
import { WithPayment, buildInvoiceDoc, dryCoffeeMainTable, paymentCells } from '../dry-coffee/dry-coffee-invoice';

/**
 * Reporte "Factura verdes" de Access (reports/Factura verdes.txt): el mismo de "Factura" (seco) con la
 * fila Kilos Verdes. Los pesos y porcentajes salen de la muestra fija del Registro de Control guardada
 * con la compra (HU-23 CA-2): Peso Tot Alm / Porc Merma, Peso Alm Sana / Factor y Peso Tot Pasilla / %.
 * Descuento Financiero es el Descuento Fro del verde (shrinkageDiscount).
 */
export function buildGreenCoffeeInvoiceDocDefinition(
  purchase: GreenCoffeePurchaseResponse & WithPayment,
  logoDataUrl: string | null,
): TDocumentDefinitions {
  const sample = purchase.sample;
  const asDry = {
    ...purchase,
    freightDiscount: purchase.shrinkageDiscount,
    totalStoredWeight: sample?.totalStoredWeight ?? 0,
    wastePercentage: sample?.wastePercentage ?? 0,
    defectiveStoredWeight: sample?.huskWeight ?? 0,
    defectivePercentage: sample?.huskPercentage ?? 0,
    healthyStoredWeight: sample?.healthyStoredWeight ?? 0,
    healthyPercentage: sample?.factor ?? 0,
  } as unknown as DryCoffeePurchaseResponse & WithPayment;
  const [cashRow, checkRow, transferRow, cardTerminalRow] = paymentCells(asDry);
  return buildInvoiceDoc(asDry, logoDataUrl,
    dryCoffeeMainTable(asDry, cashRow, checkRow, transferRow, cardTerminalRow, purchase.greenKg));
}
