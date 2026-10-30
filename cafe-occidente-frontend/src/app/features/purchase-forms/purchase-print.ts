import { WritableSignal } from '@angular/core';
import type { TDocumentDefinitions } from 'pdfmake/interfaces';
import { Observable, Observer } from 'rxjs';

import { PurchasePayment } from '../../core/models/payment-method.model';
import { openPdf } from '../../shared/utils/pdf-preview';
import { saveOnce } from '../../shared/utils/save-once';
import { loadLogoDataUrl } from './dry-coffee/dry-coffee-invoice';

const LOGO_URL = 'assets/images/cafe-occidente-logo.png';

/** Lo que Imprimir necesita de un formulario de compra (seco, verde o pasilla). */
export interface PrintablePurchaseForm {
  readonly saving: WritableSignal<boolean>;
  readonly payment: WritableSignal<PurchasePayment | null>;
  readonly savedNoticeOpen: WritableSignal<boolean>;
  readonly errorMessage: WritableSignal<string | null>;
  reset(): void;
}

export interface PurchasePrint<T> {
  /** Nombre de la compra para la consola ("Cafe Verde", "Pasilla"...). */
  readonly name: string;
  /** Guarda la compra (POST). */
  readonly save: () => Observable<T>;
  /** Arma la factura del módulo con la compra guardada, su pago y el logo. */
  readonly invoice: (purchase: T & { payment: PurchasePayment | null }, logo: string | null) => TDocumentDefinitions;
  /** Mensajes de la pantalla: saveError ("No se pudo registrar...") e invoiceError ("No se pudo generar la factura"). */
  readonly messages: () => { readonly saveError?: string; readonly invoiceError?: string } | undefined;
  /** Repinta el formulario después del error. */
  readonly refresh: () => void;
  /** Solo si la pantalla maneja errores propios (el seco, por el umbral de especiales). */
  readonly saveError?: Observer<T>['error'];
}

/**
 * Imprimir como Access: guarda una sola vez (saveOnce), abre la factura con el pago guardado y deja el
 * formulario limpio, con la siguiente factura, para otra compra. El pago se toma antes de reiniciar.
 */
export function saveAndPrint<T>(form: PrintablePurchaseForm, print: PurchasePrint<T>): void {
  form.errorMessage.set(null);
  // El logo se pide mientras se guarda, no después: así la factura abre en cuanto vuelve la compra.
  let logo: Promise<string | null> = Promise.resolve(null);
  const save = () => {
    logo = loadLogoDataUrl(LOGO_URL);
    return print.save();
  };
  saveOnce(form.saving, save, {
    next: (purchase) => {
      const payment = form.payment();
      form.reset();
      form.savedNoticeOpen.set(true);
      openPdf(
        logo.then((l) => print.invoice({ ...purchase, payment }, l)),
        () => form.errorMessage.set(print.messages()?.invoiceError ?? null),
      );
    },
    error: print.saveError ?? ((err) => defaultSaveError(form, print, err)),
  });
}

/** Error al guardar: el detalle va a la consola y al cajero solo el mensaje de la pantalla. */
function defaultSaveError<T>(form: PrintablePurchaseForm, print: PurchasePrint<T>, err: { error?: unknown } | undefined): void {
  console.error(`Error al registrar compra ${print.name}:`, err?.error ?? err);
  form.errorMessage.set(print.messages()?.saveError ?? null);
  print.refresh();
}

/** Como Café Seco: sin todos los datos requeridos no se pide el cálculo ni se guarda. */
export function hasRequired(model: Record<string, string | undefined>, required: readonly string[]): boolean {
  return required.every((k) => (model[k] ?? '').trim() !== '');
}
