import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Cuanto se ve el mensaje de confirmacion de los anuncios (RN-08.7, RN-09.2: 3 segundos). */
export const SUCCESS_MESSAGE_DURATION_MS = 3000;

/**
 * Pantalla de confirmacion sobre el formulario ("Anuncio actualizado"). La pantalla que la usa decide
 * cuando se cierra; con {@link SUCCESS_MESSAGE_DURATION_MS} queda igual en Anuncio con Factor y Pasilla.
 */
@Component({
  selector: 'app-success-overlay',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      *ngIf="message()"
      class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/30"
      role="status"
      data-testid="success-overlay"
    >
      <p class="rounded border border-green-600 bg-white px-8 py-6 text-lg font-semibold text-green-700 shadow-lg">
        {{ message() }}
      </p>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuccessOverlayComponent {
  readonly message = input<string | null>(null);
}
