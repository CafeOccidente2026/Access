import { Directive, ElementRef, effect, inject, input, output } from '@angular/core';

import { applyLiveThousands, formatMoneyInput, moneyDigits, skipThousandsSeparator } from '../../utils/live-thousands';

/**
 * Campo de plata con puntos de miles mientras se digita: `<input [appLiveThousands]="valor" (liveThousands)="set($event)">`.
 * Muestra el valor con puntos, entrega el número limpio y no mueve el cursor. Formatea antes que cualquier `(input)`
 * de la pantalla (escucha en captura), así el valor que lee ya viene con puntos.
 */
@Directive({
  selector: 'input[appLiveThousands]',
  standalone: true,
  host: { '(keydown)': 'onKeydown($event)' },
})
export class LiveThousandsDirective {
  /** Valor de la pantalla (número o texto); vacío deja el campo vacío. */
  readonly appLiveThousands = input<string | number | null | undefined>();
  /** Castigo y valores de corrección: el menos se respeta. */
  readonly allowNegative = input(false);
  /** Número limpio en cada tecla ("45690", "-2300"). */
  readonly liveThousands = output<string>();

  private readonly element: HTMLInputElement = inject(ElementRef<HTMLInputElement>).nativeElement;

  constructor() {
    this.element.addEventListener(
      'input',
      () => this.liveThousands.emit(applyLiveThousands(this.element, this.allowNegative())),
      { capture: true },
    );
    effect(() => {
      const clean = moneyDigits(this.appLiveThousands(), this.allowNegative());
      // Solo si cambió el número: reescribir lo mismo que se está digitando movería el cursor.
      if (moneyDigits(this.element.value, this.allowNegative()) !== clean) {
        this.element.value = formatMoneyInput(clean);
      }
    });
  }

  onKeydown(event: KeyboardEvent): void {
    skipThousandsSeparator(event, this.element);
  }
}
