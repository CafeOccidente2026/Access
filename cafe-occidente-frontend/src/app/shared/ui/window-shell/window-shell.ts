import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';

/**
 * Ventana de escritorio de nivel superior (marco de la aplicacion),
 * igual a la pantalla inicial: barra de titulo con boton de cierre.
 */
@Component({
  selector: 'app-window-shell',
  standalone: true,
  templateUrl: './window-shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WindowShellComponent {
  @Input() appTitle = '';

  private readonly location = inject(Location);

  onClose(): void {
    this.location.back();
  }
}
