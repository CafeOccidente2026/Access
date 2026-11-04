import { Location } from '@angular/common';
import { inject } from '@angular/core';

/**
 * Cierre de un formulario de compra con datos sin imprimir: la X (onClose) y las salidas por menú o atrás
 * (canDeactivate, del guard de la ruta) piden confirmación; sin datos, cierran de una.
 */
export abstract class UnsavedCloseForm {
  protected readonly location = inject(Location);
  abstract model: Record<string, string>;
  pendingClose = false;
  private closeResolver: ((value: boolean) => void) | null = null;

  protected abstract hasUnsavedInput(): boolean;

  /** La X del formulario: si hay datos sin imprimir pide confirmación; si no, cierra. */
  onClose(): void {
    if (this.hasUnsavedInput()) {
      this.pendingClose = true;
    } else {
      this.location.back();
    }
  }

  /** Guard CanDeactivate: cubre las salidas que no pasan por la X (menú, back, etc.). */
  canDeactivate(): boolean | Promise<boolean> {
    if (!this.hasUnsavedInput()) {
      return true;
    }
    this.pendingClose = true;
    return new Promise<boolean>((resolve) => (this.closeResolver = resolve));
  }

  confirmClose(): void {
    this.pendingClose = false;
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
    this.pendingClose = false;
    this.closeResolver?.(false);
    this.closeResolver = null;
  }
}
