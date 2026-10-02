import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';

/** Sin tildes/mayusculas, para que "narino" encuentre "NARIÑO" mientras se escribe. */
function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

/**
 * Reemplaza al <select> nativo para campos de opciones con texto libre (Especial, y cualquier otro
 * campo 'select'). Motivo (ver bug "No hay un anuncio activo..."): un <select> nativo dispara cada
 * letra del typeahead del navegador como un cambio de valor real - no hay forma de "escribir para
 * filtrar" sin que eso ya cuente como una seleccion. Este componente es un <input> de texto propio:
 * mientras se escribe solo filtra la lista (nunca confirma), y recien confirma con Enter sobre la
 * opcion resaltada o con click/mousedown en una opcion de la lista - igual que un combobox real.
 * Mouse y flechas siguen sirviendo para recorrer la lista (ahora la lista filtrada, propia, no la
 * nativa del navegador).
 */
@Component({
  selector: 'app-combobox',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './combobox.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComboboxComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) options: readonly string[] = [];
  @Input() value: string | number = '';
  @Input() disabled = false;
  @Input() dataFieldKey = '';
  @Input() inputClass = '';

  @Output() readonly valueChange = new EventEmitter<string>();
  @Output() readonly committed = new EventEmitter<void>();

  @ViewChild('inputEl') inputEl?: ElementRef<HTMLInputElement>;
  @ViewChild('listEl') listEl?: ElementRef<HTMLUListElement>;

  inputText = '';
  isOpen = false;
  filteredOptions: readonly string[] = [];
  highlightedIndex = -1;

  /**
   * Posicion fija de la lista, medida desde el input: asi no la recorta ninguna ventana con overflow
   * (bug de las listas cortadas en los dialogos de informes). Si no entra abajo se abre hacia arriba.
   */
  listBox: { left: number; width: number; top: number | null; bottom: number | null } = {
    left: 0, width: 0, top: 0, bottom: null,
  };
  private readonly reposition = () => {
    this.placeList();
    this.changeDetector.markForCheck();
  };

  private closeTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly changeDetector = inject(ChangeDetectorRef);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value'] && !this.isOpen) {
      this.inputText = this.value == null ? '' : String(this.value);
    }
    if (changes['options']) {
      this.filteredOptions = this.options;
      // Opciones que llegan despues de escribir (busqueda en el backend, ej. cedula del conductor):
      // resaltar la primera para que Enter la confirme. Con listas fijas nunca pasa con la lista abierta.
      if (this.isOpen) {
        this.highlightedIndex = this.options.length ? 0 : -1;
      }
    }
  }

  onFocus(): void {
    if (this.closeTimer) {
      clearTimeout(this.closeTimer);
      this.closeTimer = null;
    }
    this.filteredOptions = this.options;
    this.highlightedIndex = this.options.findIndex((o) => o === this.inputText);
    this.isOpen = true;
    this.placeList();
    // Captura: tambien el scroll de cualquier contenedor, no solo el de la pagina.
    window.addEventListener('scroll', this.reposition, true);
    window.addEventListener('resize', this.reposition);
  }

  ngOnDestroy(): void {
    this.stopTracking();
  }

  private stopTracking(): void {
    window.removeEventListener('scroll', this.reposition, true);
    window.removeEventListener('resize', this.reposition);
  }

  /** max-h-48 = 192px de lista; 2px de separacion con el input. */
  private placeList(): void {
    const rect = this.inputEl?.nativeElement.getBoundingClientRect();
    if (!rect) {
      return;
    }
    const listHeight = 192;
    const below = window.innerHeight - rect.bottom;
    const openUp = below < listHeight + 4 && rect.top > below;
    this.listBox = {
      left: rect.left,
      width: rect.width,
      top: openUp ? null : rect.bottom + 2,
      bottom: openUp ? window.innerHeight - rect.top + 2 : null,
    };
  }

  onInputChange(text: string): void {
    this.inputText = text;
    if (!this.isOpen) {
      this.placeList();
    }
    this.filteredOptions = text.trim()
      ? this.options.filter((o) => normalize(o).includes(normalize(text)))
      : this.options;
    this.highlightedIndex = this.filteredOptions.length ? 0 : -1;
    this.isOpen = true;
    // Solo actualiza el modelo en vivo (para que otras pantallas que miren el valor crudo lo vean) -
    // nunca confirma/avanza mientras se sigue escribiendo.
    this.valueChange.emit(text);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (!this.isOpen) {
        this.onFocus();
        return;
      }
      this.highlightedIndex = Math.min(this.highlightedIndex + 1, this.filteredOptions.length - 1);
      this.scrollHighlightedIntoView();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (!this.isOpen) {
        this.onFocus();
        return;
      }
      this.highlightedIndex = Math.max(this.highlightedIndex - 1, 0);
      this.scrollHighlightedIntoView();
    } else if (event.key === 'Enter') {
      if (this.isOpen && this.highlightedIndex >= 0 && this.filteredOptions[this.highlightedIndex]) {
        event.preventDefault();
        this.selectOption(this.filteredOptions[this.highlightedIndex]);
      } else {
        this.commitIfValid();
      }
    } else if (event.key === 'Escape') {
      if (this.isOpen) {
        event.preventDefault();
        event.stopPropagation();
        this.isOpen = false;
        this.inputText = this.value == null ? '' : String(this.value);
      }
    } else if (event.key === 'Tab') {
      this.isOpen = false;
      this.commitIfValid();
    }
  }

  /** mousedown (no click) + preventDefault: evita que el input pierda foco antes del click, que es
   *  la causa clasica de que un combobox "no deje" clickear la lista. */
  onOptionMouseDown(event: MouseEvent, option: string): void {
    event.preventDefault();
    this.selectOption(option);
  }

  onBlur(): void {
    // Delay corto: si el blur vino de un mousedown sobre una opcion, ese ya se resolvio antes por
    // preventDefault (nunca llega a disparar blur). Esto solo cubre Tab/click afuera.
    this.closeTimer = setTimeout(() => {
      this.isOpen = false;
      this.stopTracking();
      this.commitIfValid();
      // OnPush: el timeout no marca la vista; sin esto la lista quedaba abierta al salir del campo
      // (en Compras no se notaba porque el campo se bloquea al confirmarse).
      this.changeDetector.markForCheck();
    }, 150);
  }

  private selectOption(option: string): void {
    this.inputText = option;
    this.isOpen = false;
    this.valueChange.emit(option);
    this.committed.emit();
  }

  /** Solo confirma si lo tipeado coincide EXACTO (sin importar mayus/tildes) con una opcion real -
   *  si no, se queda tal cual esta, editable, sin avanzar (igual que la cedula no encontrada). */
  private commitIfValid(): void {
    const typed = this.inputText.trim();
    const match = this.options.find((o) => normalize(o) === normalize(typed));
    if (!match) {
      return;
    }
    if (match !== this.inputText) {
      this.inputText = match;
    }
    this.valueChange.emit(match);
    this.committed.emit();
  }

  private scrollHighlightedIntoView(): void {
    setTimeout(() => {
      const list = this.listEl?.nativeElement;
      const item = list?.children.item(this.highlightedIndex) as HTMLElement | null;
      item?.scrollIntoView({ block: 'nearest' });
    });
  }
}
