import { CommonModule } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, Output, inject } from '@angular/core';

import { AppButtonComponent } from '../app-button/app-button';

/** Diálogo modal reutilizable. Sin `cancelLabel` funciona como aviso de un solo botón. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './confirm-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent implements AfterViewInit {
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;

  @Input() message = '';
  @Input() confirmLabel = 'Aceptar';
  @Input() cancelLabel = '';
  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly cancelled = new EventEmitter<void>();

  /** Prueba del 9-nov: el aviso abre con el foco en Aceptar (el ultimo boton), asi Enter lo acepta y no va al
   *  campo que quedo detras. */
  ngAfterViewInit(): void {
    [...this.host.querySelectorAll('button')].at(-1)?.focus();
  }
}
