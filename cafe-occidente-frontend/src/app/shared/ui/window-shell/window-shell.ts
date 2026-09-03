import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Ventana de escritorio de nivel superior (marco de la aplicacion),
 * igual a la pantalla inicial: barra de titulo con controles y barra de estado.
 */
@Component({
  selector: 'app-window-shell',
  standalone: true,
  templateUrl: './window-shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WindowShellComponent {
  @Input() appTitle = '';
  @Input() leftStatus = '';
  @Input() rightStatus = '';
}
