import { WritableSignal } from '@angular/core';

import { focusField } from './form-focus';

/** Lo que necesita de Café Seco, Verde y Pasilla: el formulario, el aviso y sus textos. */
interface SellerForm {
  readonly model: Record<string, string>;
  readonly errorMessage: WritableSignal<string | null>;
  readonly messages: () => object | undefined;
}

/**
 * Prueba del 10-nov (fallas 2 y 3): el servidor no calcula sin el celular del vendedor. Si TERCEROS no lo tiene, al
 * traer la cédula se avisa y el foco va a Celular (el aviso lo devuelve ahí al cerrarse); al confirmarlo,
 * advanceFocus sigue con el primer campo pendiente. Si lo tiene, se sigue con next.
 */
export function askCellphoneOr(host: HTMLElement, form: SellerForm, next: () => void): void {
  if ((form.model['cellphone'] ?? '').trim() !== '') {
    next();
    return;
  }
  form.errorMessage.set((form.messages() as { cellphoneMissing?: string } | undefined)?.cellphoneMissing ?? null);
  focusField(host, 'cellphone');
}
