import type { TDocumentDefinitions } from 'pdfmake/interfaces';

import { PurchaseQueryDetail } from '../../core/models/purchase-query.model';
import { buildDryCoffeeInvoiceDocDefinition } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { buildGreenCoffeeInvoiceDocDefinition } from '../purchase-forms/green-coffee/green-coffee-invoice';
import { buildHuskInvoiceDocDefinition } from '../purchase-forms/husk/husk-invoice';

/**
 * "Reimprimir Dcto Equiv" (macro "Reimprime Factura" -> reports/Factura Reimpresa.txt, el mismo diseño
 * de la factura): la factura del modulo de la compra con las formas de pago guardadas. Una anulada se
 * puede reimprimir con la marca "ANULADA" bien visible en cada pagina (decision del usuario, 6g).
 */
export function buildReprintDoc(
  detail: PurchaseQueryDetail,
  logoDataUrl: string | null,
  annulledText: string,
  annulledColor: string | undefined,
): TDocumentDefinitions {
  const payment = detail.payment;
  const doc = detail.module === 'DRY'
    ? buildDryCoffeeInvoiceDocDefinition({ ...detail.purchase, payment }, logoDataUrl)
    : detail.module === 'GREEN'
      ? buildGreenCoffeeInvoiceDocDefinition({ ...detail.purchase, payment }, logoDataUrl)
      : buildHuskInvoiceDocDefinition({ ...detail.purchase, payment }, logoDataUrl);
  return detail.status === 'ANULADA'
    ? { ...doc, watermark: { text: annulledText, color: annulledColor, opacity: 0.35, bold: true, fontSize: 110, angle: -30 } }
    : doc;
}
