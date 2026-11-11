import { WritableSignal } from '@angular/core';
import { Observable } from 'rxjs';

import { serverError } from '../../shared/utils/server-error';

/** Lo que necesita de Café Seco, Verde y Pasilla para pedir la siguiente factura. */
interface ReservingForm<T> {
  readonly invoiceReservation: WritableSignal<T | null>;
  readonly errorMessage: WritableSignal<string | null>;
  readonly messages: () => { readonly saveError?: string } | undefined;
}

/**
 * Siguiente factura del rango DIAN (igual en los tres formularios): la guarda, avisa si la resolucion esta por
 * vencer y, si falla, muestra el motivo del servidor (prueba del 11-nov, punto 4). {@code refresh} repinta.
 */
export function reserveInvoice<T extends { readonly warning?: string | null }>(
  form: ReservingForm<T>,
  request: Observable<T>,
  refresh: () => void,
): void {
  request.subscribe({
    next: (invoice) => {
      form.invoiceReservation.set(invoice);
      if (invoice.warning) {
        form.errorMessage.set(invoice.warning);
      }
      refresh();
    },
    error: (err) => {
      form.errorMessage.set(serverError(err, form.messages()?.saveError));
      refresh();
    },
  });
}
