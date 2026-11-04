import { Location } from '@angular/common';
import { inject, signal } from '@angular/core';

/**
 * Cierre de un formulario de compra con datos sin imprimir: la X (onClose) y las salidas por menú o atrás
 * (canDeactivate, del guard de la ruta) piden confirmación; sin datos, cierran de una.
 */
export abstract class UnsavedCloseForm {
  protected readonly location = inject(Location);
  abstract model: Record<string, string>;
  /** Señal: el guard de la ruta la cambia fuera de un evento de la pantalla y la app no usa zone.js. */
  readonly pendingClose = signal(false);
  private closeResolver: ((value: boolean) => void) | null = null;

  protected abstract hasUnsavedInput(): boolean;

  /** La X del formulario: si hay datos sin imprimir pide confirmación; si no, cierra. */
  onClose(): void {
    if (this.hasUnsavedInput()) {
      this.pendingClose.set(true);
    } else {
      this.location.back();
    }
  }

  /** Guard CanDeactivate: cubre las salidas que no pasan por la X (menú, back, etc.). */
  canDeactivate(): boolean | Promise<boolean> {
    if (!this.hasUnsavedInput()) {
      return true;
    }
    this.pendingClose.set(true);
    return new Promise<boolean>((resolve) => (this.closeResolver = resolve));
  }

  confirmClose(): void {
    this.pendingClose.set(false);
    if (this.closeResolver) {
      this.closeResolver(true);
      this.closeResolver = null;
      return;
    }
    // Descarta los datos antes de navegar: si no, canDeactivate() vuelve a encontrar
    // input sin guardar y reabre este mismo dialogo, obligando a un segundo clic.
    this.model = {};
    this.location.back();
  }

  cancelClose(): void {
    this.pendingClose.set(false);
    this.closeResolver?.(false);
    this.closeResolver = null;
  }
}
