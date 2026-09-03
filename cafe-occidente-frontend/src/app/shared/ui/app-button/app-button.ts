import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

export type ButtonVariant = 'access' | 'menu' | 'plain';

/** Boton reutilizable con la apariencia de los botones del sistema original. */
@Component({
  selector: 'app-button',
  standalone: true,
  templateUrl: './app-button.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppButtonComponent {
  @Input() label = '';
  @Input() variant: ButtonVariant = 'access';
  @Input() active = false;
  @Output() readonly pressed = new EventEmitter<void>();

  onClick(): void {
    this.pressed.emit();
  }
}
