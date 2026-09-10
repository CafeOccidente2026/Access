import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NavigationService } from '../../../core/services/navigation.service';

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
  /** Si se indica, la X navega siempre a esta ruta en vez de volver atras (ej: menu principal -> login). */
  @Input() closeRoute?: string;
  /**
   * Si alguien escucha este evento, la X lo emite y NO navega: el padre decide (ej: pedir
   * confirmacion antes de cerrar). Si nadie escucha, la X se comporta como siempre.
   */
  @Output() readonly closeRequested = new EventEmitter<void>();

  private readonly location = inject(Location);
  private readonly navigation = inject(NavigationService);

  /** La X cierra la pantalla volviendo a la anterior, como al cerrar una ventana de Access. */
  onClose(): void {
    if (this.closeRequested.observed) {
      this.closeRequested.emit();
      return;
    }
    if (this.closeRoute) {
      this.navigation.goTo(this.closeRoute);
      return;
    }
    this.location.back();
  }
}
