import { PurchasePayment } from './payment-method.model';

/**
 * Lo que se manda al corregir (HU-05): los campos editables del modulo y el pago. Fondo, cedula, especial y
 * Vr Kilo Comp van solo porque el request de cada modulo los pide; el servidor usa los de la compra.
 */
export interface PurchaseCorrectionRequest {
  readonly fundId: number;
  readonly idNumber: string;
  readonly specialType?: string;
  readonly compKgPrice?: number;
  readonly address: string;
  readonly cellphone: string;
  readonly payment: PurchasePayment | null;
  readonly [field: string]: unknown;
}

/** Lo que muestra la pantalla del calculo del servidor (vista previa o compra ya corregida). */
export interface PurchaseCorrectionResult {
  readonly netKg: number;
  readonly grossValue: number;
  readonly withholding: number;
  readonly netToPay: number;
  readonly invoiceNumber?: number;
}
