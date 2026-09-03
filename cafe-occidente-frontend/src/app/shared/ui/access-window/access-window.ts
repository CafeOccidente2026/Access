import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Envoltorio visual que imita la ventana interna de un formulario de Access:
 * barra de titulo con icono + texto y boton de cierre.
 * El contenido real se proyecta mediante <ng-content>.
 */
@Component({
  selector: 'app-access-window',
  standalone: true,
  templateUrl: './access-window.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessWindowComponent {
  @Input() title = '';
  @Input() bodyClass = 'bg-white';
}
