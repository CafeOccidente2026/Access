import { WritableSignal } from '@angular/core';

/**
 * Prueba del 10-nov (fallas 2 y 3): el servidor no calcula sin el celular del vendedor. Si TERCEROS no lo tiene, al
 * traer la cédula se avisa; al aceptar el aviso el foco va solo a Celular, y al confirmarlo se calcula y se sigue con
 * el primer campo pendiente. Lo usan Café Seco, Verde y Pasilla.
 */
export const CELLPHONE = 'cellphone';

export class MissingCellphone {
  private pending = false;

  /** true si falta el celular: deja el aviso en pantalla (el foco va a Celular cuando se acepte). */
  check(model: Record<string, string>, errorMessage: WritableSignal<string | null>, warning: string | undefined): boolean {
    this.pending = (model[CELLPHONE] ?? '').trim() === '';
    if (this.pending) {
      errorMessage.set(warning ?? null);
    }
    return this.pending;
  }

  /** Al aceptar el aviso: lleva el foco a Celular una sola vez. */
  afterNotice(focus: (key: string) => void): void {
    if (this.pending) {
      this.pending = false;
      focus(CELLPHONE);
    }
  }
}
