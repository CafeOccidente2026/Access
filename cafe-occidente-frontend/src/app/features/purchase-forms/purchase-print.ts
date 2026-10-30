import { WritableSignal } from '@angular/core';
import type { TDocumentDefinitions } from 'pdfmake/interfaces';
import { Observable, Observer } from 'rxjs';

import { PurchasePayment } from '../../core/models/payment-method.model';
import { openPdf } from '../../shared/utils/pdf-preview';
import { saveOnce } from '../../shared/utils/save-once';

/** Lo que Imprimir necesita de un formulario de compra (seco, verde o pasilla). */
export interface PrintablePurchaseForm {
  readonly saving: WritableSignal<boolean>;
  readonly payment: WritableSignal<PurchasePayment | null>;
  readonly savedNoticeOpen: WritableSignal<boolean>;
  readonly errorMessage: WritableSignal<string | null>;
  reset(): void;
}

export interface PurchasePrint<T> {
  /** Guarda la compra (POST). */
  readonly save: () => Observable<T>;
  /** Arma la factura de la compra guardada con el pago del panel. */
  readonly invoice: (purchase: T, payment: PurchasePayment | null) => Promise<TDocumentDefinitions>;
  /** "No se pudo generar la factura". */
  readonly invoiceError: () => string | null;
  readonly saveError: Observer<T>['error'];
}

/**
 * Imprimir como Access: guarda una sola vez (saveOnce), abre la factura con el pago guardado y deja el
 * formulario limpio, con la siguiente factura, para otra compra. El pago se toma antes de reiniciar.
 */
export function saveAndPrint<T>(form: PrintablePurchaseForm, print: PurchasePrint<T>): void {
  form.errorMessage.set(null);
  saveOnce(form.saving, print.save, {
    next: (purchase) => {
      const payment = form.payment();
      form.reset();
      form.savedNoticeOpen.set(true);
      openPdf(print.invoice(purchase, payment), () => form.errorMessage.set(print.invoiceError()));
    },
    error: print.saveError,
  });
}

/** Como Café Seco: sin todos los datos requeridos no se pide el cálculo ni se guarda. */
export function hasRequired(model: Record<string, string | undefined>, required: readonly string[]): boolean {
  return required.every((k) => (model[k] ?? '').trim() !== '');
}
