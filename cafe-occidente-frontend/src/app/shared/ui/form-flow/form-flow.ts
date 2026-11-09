import { AfterViewInit, Directive, ElementRef, HostListener, OnDestroy, inject } from '@angular/core';

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

  ngAfterViewInit(): void {
    // Los campos pueden llegar despues (JSON, datos del backend): se espera al primero.
    if (!this.focusFirst()) {
      this.observer = new MutationObserver(() => this.focusFirst() && this.observer?.disconnect());
      this.observer.observe(this.host, { childList: true, subtree: true });
    }
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

  /** Opcion elegida en un combobox (clic o Enter): pasa al siguiente campo. */
  @HostListener('flow-next', ['$event'])
  onOptionChosen(event: Event): void {
    this.next(event.target as HTMLElement, false);
  }

  /** Lista nativa: al cambiar la opcion pasa al siguiente campo. */
  @HostListener('change', ['$event'])
  onChange(event: Event): void {
    if ((event.target as HTMLElement).tagName === 'SELECT') {
      this.next(event.target as HTMLElement, false);
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
      const button = this.host.querySelector<HTMLElement>('[data-flow-submit]:not([disabled])');
      if (submit) {
        button?.click();
      } else {
        button?.focus();
      }
    }
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
