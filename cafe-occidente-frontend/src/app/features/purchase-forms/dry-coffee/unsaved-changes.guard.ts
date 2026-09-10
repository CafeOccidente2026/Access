import { CanDeactivateFn } from '@angular/router';

/** Componente que sabe decidir si se puede abandonar (Compras Café Seco). */
export interface HasUnsavedGuard {
  canDeactivate(): boolean | Promise<boolean>;
}

/** Avisa antes de salir del formulario si hay datos capturados sin imprimir. */
export const dryCoffeeUnsavedChangesGuard: CanDeactivateFn<HasUnsavedGuard> = (component) =>
  component.canDeactivate();
