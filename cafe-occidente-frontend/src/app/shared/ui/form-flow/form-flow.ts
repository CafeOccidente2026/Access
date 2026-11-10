import { AfterViewInit, Directive, ElementRef, HostListener, Input, OnDestroy, inject } from '@angular/core';
import { changedByKeyboard } from '../../utils/select-keys';

const FIELDS = 'input:not([type=hidden]):not([type=checkbox]), select, textarea';

/** Campo editable (los readonly/disabled, como la agencia fija de USER, se saltan). Los ocultos con
 *  *ngIf no estan en el DOM. */
function editable(el: HTMLElement): boolean {
  const field = el as HTMLInputElement;
  return !field.disabled && !field.readOnly;
}

/**
 * Teclado de las pantallas de captura, igual que Compras (advanceFocus / focusField): al aparecer
 * los campos el foco queda en el primero, Enter pasa al siguiente en el orden de la pantalla y en
 * el ultimo dispara el boton marcado con data-flow-submit. Cada radio group cuenta como un campo.
 */
@Directive({
  selector: '[appFormFlow]',
  standalone: true,
  host: { class: 'form-flow' },
})
export class FormFlowDirective implements AfterViewInit, OnDestroy {
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private observer?: MutationObserver;
  /** [appFormFlow]="false": segundo grupo de la pantalla (Remesa); maneja Enter pero no toma el foco al abrir. */
  @Input() appFormFlow: boolean | '' = '';

  ngAfterViewInit(): void {
    if (this.appFormFlow === false) {
      return;
    }
    // En el siguiente turno, ya pintada y en el documento. Los campos pueden llegar despues (JSON, datos del
    // backend): se espera al primero.
    setTimeout(() => {
      if (!this.focusFirst()) {
        this.observer = new MutationObserver(() => this.focusFirst() && this.observer?.disconnect());
        this.observer.observe(this.host, { childList: true, subtree: true });
      }
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  @HostListener('keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const target = event.target as HTMLElement;
    if (event.key !== 'Enter' || target.tagName === 'TEXTAREA' || !target.matches(FIELDS)) {
      return;
    }
    // Combobox: avanza solo si eligio una opcion, y eso lo avisa el mismo combobox con flow-next.
    if (target.getAttribute('role') === 'combobox') {
      return;
    }
    event.preventDefault();
    this.next(target, true);
  }

  /** Opcion elegida en un combobox (clic o Enter): pasa al siguiente campo cuando la pantalla ya se repinto (puede
   *  mostrar campos nuevos, como Actualizar conductor al elegir la cedula). */
  @HostListener('flow-next', ['$event'])
  onOptionChosen(event: Event): void {
    const target = event.target as HTMLElement;
    setTimeout(() => this.next(target, false));
  }

  /** Lista nativa: elegir con el mouse pasa al siguiente campo; con la flecha solo cambia (decision del 10-nov). */
  @HostListener('change', ['$event'])
  onChange(event: Event): void {
    const target = event.target as HTMLElement;
    if (target.tagName === 'SELECT' && !changedByKeyboard(target)) {
      setTimeout(() => this.next(target, false));
    }
  }

  /** Siguiente campo; despues del ultimo, Enter guarda y elegir una opcion solo deja el foco en el boton. */
  private next(target: HTMLElement, submit: boolean): void {
    const fields = this.fields();
    const index = fields.findIndex((f) => f === target || (isRadio(f) && isRadio(target) && f.name === (target as HTMLInputElement).name));
    if (index < 0) {
      return;
    }
    const next = fields[index + 1];
    if (next) {
      flash(next);
    } else {
      const button = this.submitButton();
      if (submit) {
        button?.click();
      } else {
        button?.focus();
      }
    }
  }

  /** El boton marcado data-flow-submit: un <button> o un app-button (su <button> de adentro), si esta habilitado. */
  private submitButton(): HTMLButtonElement | null {
    const marked = this.host.querySelector<HTMLElement>('[data-flow-submit]');
    const button = marked instanceof HTMLButtonElement ? marked : (marked?.querySelector('button') ?? null);
    return button && !button.disabled ? button : null;
  }

  /** Formulario vaciado para el siguiente registro: el foco vuelve al primer campo. */
  restart(): void {
    setTimeout(() => this.focusFirst());
  }

  private focusFirst(): boolean {
    const first = this.fields()[0];
    if (first) {
      flash(first);
    }
    return !!first;
  }

  /** Campos en orden visual; de cada radio group queda uno solo (el marcado o el primero). */
  private fields(): HTMLElement[] {
    const all = [...this.host.querySelectorAll<HTMLElement>(FIELDS)].filter(editable);
    return all.filter((el) => {
      if (!isRadio(el)) {
        return true;
      }
      const group = all.filter(isRadio).filter((o) => o.name === el.name);
      return el === (group.find((o) => o.checked) ?? group[0]);
    });
  }
}

function isRadio(el: HTMLElement): el is HTMLInputElement {
  return el instanceof HTMLInputElement && el.type === 'radio';
}

/** Mismo marcado que Compras al saltar de campo (focusField: clase field-flash por 1 s). */
function flash(el: HTMLElement): void {
  el.focus();
  el.classList.add('field-flash');
  setTimeout(() => el.classList.remove('field-flash'), 1000);
}
