import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  Output,
  inject,
} from '@angular/core';

import { AppButtonComponent } from '../app-button/app-button';

/** Primer campo que el usuario puede llenar (a donde va el foco si el de antes ya no existe). */
const EDITABLE_FIELD = 'input:not([readonly]):not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([readonly])';

/** Diálogo modal reutilizable. Sin `cancelLabel` funciona como aviso de un solo botón. */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './confirm-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent implements AfterViewInit, OnDestroy {
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  /** A donde vuelve el foco al cerrar: donde estaba al abrir, o a donde lo mandó la pantalla mientras tanto. */
  private returnTo = document.activeElement as HTMLElement | null;

  @Input() message = '';
  @Input() confirmLabel = 'Aceptar';
  @Input() cancelLabel = '';
  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly cancelled = new EventEmitter<void>();

  /** Prueba del 9-nov: el aviso abre con el foco en Aceptar (el ultimo boton), asi Enter lo acepta y no va al
   *  campo que quedo detras. */
  ngAfterViewInit(): void {
    this.focusAccept();
  }

  /** Prueba del 10-nov: mientras está abierto, el salto de campo de la pantalla no le quita el foco a Aceptar. */
  @HostListener('document:focusin', ['$event'])
  keepFocus(event: FocusEvent): void {
    const target = event.target as HTMLElement;
    if (!this.host.contains(target)) {
      this.returnTo = target;
      this.focusAccept();
    }
  }

  /** Al cerrar, el foco no se pierde: vuelve al campo de antes o, si ya no está, al primer campo de la pantalla. */
  ngOnDestroy(): void {
    const target = this.returnTo;
    setTimeout(() => {
      if (document.activeElement && document.activeElement !== document.body) {
        return;
      }
      const back = target?.isConnected && target !== document.body ? target : null;
      (back ?? document.querySelector<HTMLElement>(EDITABLE_FIELD))?.focus();
    });
  }

  private focusAccept(): void {
    [...this.host.querySelectorAll('button')].at(-1)?.focus();
  }
}
