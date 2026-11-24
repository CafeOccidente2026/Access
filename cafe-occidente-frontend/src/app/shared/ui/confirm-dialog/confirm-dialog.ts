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

/** Avisos abiertos, el último encima. Solo ese cuida el foco: con dos a la vez (Compra registrada + No se pudo
 *  generar la factura) cada uno le quitaba el foco al otro sin fin (PR #82). */
const openDialogs: ConfirmDialogComponent[] = [];

/** Primer campo que el usuario puede llenar (a donde va el foco si el de antes ya no existe). */
const EDITABLE_FIELD = 'input:not([readonly]):not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([readonly])';

/**
 * Diálogo modal reutilizable. Sin `cancelLabel` funciona como aviso de un solo botón.
 * Teclado (18-nov): Enter elige el botón con el foco (o el de por defecto si el foco se perdió); Esc es Cancelar/No, o
 * Aceptar en un aviso de un solo botón. La pantalla de atrás no recibe esas teclas.
 */
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
  /** En captura sobre window: llega antes que los atajos de la pantalla (Esc cierra el formulario, Enter avanza). */
  private readonly onKeydown = (event: KeyboardEvent) => this.handleKey(event);

  @Input() message = '';
  @Input() confirmLabel = 'Aceptar';
  @Input() cancelLabel = '';
  /** Acción que no se puede deshacer (anular, cerrar sin guardar): el foco abre en Cancelar/No. */
  @Input() safeFocus = false;
  /** Mientras se guarda: botones deshabilitados y las teclas no hacen nada (no se envía dos veces). */
  @Input() busy = false;
  /** Compra registrada: al cerrar, el cursor va al primer campo del formulario (Fondo) para la siguiente compra. */
  @Input() focusFirstFieldOnClose = false;
  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly cancelled = new EventEmitter<void>();

  /** Prueba del 9-nov: el aviso abre con el foco en Aceptar (o en el botón seguro), así Enter no va al campo de atrás. */
  ngAfterViewInit(): void {
    openDialogs.push(this);
    window.addEventListener('keydown', this.onKeydown, true);
    this.focusDefault();
  }

  /** Prueba del 10-nov: mientras está abierto, el salto de campo de la pantalla no le quita el foco al botón. */
  @HostListener('document:focusin', ['$event'])
  keepFocus(event: FocusEvent): void {
    const target = event.target as HTMLElement;
    if (this.onTop() && !this.host.contains(target)) {
      this.returnTo = target;
      this.focusDefault();
    }
  }

  /** Al volver de la ventana de impresión del navegador el foco quedaba en ninguna parte. */
  @HostListener('window:focus')
  regainFocus(): void {
    if (this.onTop() && !this.host.contains(document.activeElement)) {
      this.focusDefault();
    }
  }

  /**
   * Prueba del 19-nov: Imprimir pone el foco en el marco oculto de la factura y, al cerrar la ventana de impresión,
   * Chrome lo devuelve a ese marco (la página no recibe focus). Enter le llegaba al visor de PDF y no a Aceptar.
   */
  @HostListener('window:blur')
  leavePrintFrame(): void {
    if (this.onTop() && document.activeElement instanceof HTMLIFrameElement) {
      setTimeout(() => this.focusDefault());
    }
  }

  /** Al cerrar, el foco no se pierde: vuelve al campo de antes o, si ya no está, al primer campo de la pantalla. */
  ngOnDestroy(): void {
    openDialogs.splice(openDialogs.indexOf(this), 1);
    window.removeEventListener('keydown', this.onKeydown, true);
    const firstField = this.focusFirstFieldOnClose;
    const target = firstField ? null : this.returnTo;
    setTimeout(() => {
      if (!firstField && document.activeElement && document.activeElement !== document.body) {
        return;
      }
      const back = target?.isConnected && target !== document.body ? target : null;
      (back ?? document.querySelector<HTMLElement>(EDITABLE_FIELD))?.focus();
    });
  }

  private handleKey(event: KeyboardEvent): void {
    if (!this.onTop() || (event.key !== 'Enter' && event.key !== 'Escape')) {
      return;
    }
    event.stopPropagation();
    // Enter sobre un botón del diálogo: el navegador hace clic en ese botón.
    if (event.key === 'Enter' && this.host.contains(document.activeElement)) {
      return;
    }
    event.preventDefault();
    if (this.busy) {
      return;
    }
    const cancel = event.key === 'Escape' || this.safeFocus;
    (cancel && this.cancelLabel ? this.cancelled : this.confirmed).emit();
  }

  private onTop(): boolean {
    return openDialogs.at(-1) === this;
  }

  private focusDefault(): void {
    const buttons = [...this.host.querySelectorAll('button')];
    (this.safeFocus && this.cancelLabel ? buttons[0] : buttons.at(-1))?.focus();
  }
}
