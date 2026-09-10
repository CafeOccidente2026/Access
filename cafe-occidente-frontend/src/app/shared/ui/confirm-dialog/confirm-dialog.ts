import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { AppButtonComponent } from '../app-button/app-button';

/** Diálogo modal reutilizable. Sin `cancelLabel` funciona como aviso de un solo botón. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './confirm-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  @Input() message = '';
  @Input() confirmLabel = 'Aceptar';
  @Input() cancelLabel = '';
  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly cancelled = new EventEmitter<void>();
}
