import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormFieldDefinition } from '../../../core/models';
import { formatDisplayNumber } from '../../utils/number-format';

/**
 * Renderiza un unico campo (etiqueta + control) segun su definicion.
 * Emite `valueChange`/`committed` de forma opcional: las pantallas de solo lectura simplemente
 * no los escuchan y el comportamiento visual no cambia.
 */
@Component({
  selector: 'app-form-field',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './form-field.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormFieldComponent {
  @Input({ required: true }) field!: FormFieldDefinition;
  @Input() labelClass = 'text-slate-700';
  @Input() labelWidthClass = 'w-32';
  @Input() inputWidthClass = 'min-w-[100px]';
  @Input() highlightClass = 'bg-yellow-100';

  /** Nuevo valor escrito/seleccionado por el usuario. */
  @Output() readonly valueChange = new EventEmitter<string | number>();
  /** El usuario terminó de editar el campo (blur o Enter). */
  @Output() readonly committed = new EventEmitter<void>();

  inputValue: string | number = '';
  private selectCommitTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnChanges(): void {
    const raw = this.field.value ?? '';
    const isNumeric =
      this.field.type === 'currency' ||
      this.field.type === 'count' ||
      this.field.type === 'percentage' ||
      this.field.type === 'number';
    // Solo se formatea de solo-lectura: mientras el campo sigue editable siempre se ve el valor
    // crudo (para no pelear con puntos de miles mientras se escribe). Los formularios de compra
    // bloquean (readonly = true) cada campo apenas se confirma (blur/Enter), asi que esto alcanza
    // para "formatear al confirmar, crudo mientras se edita" sin logica extra en cada formulario.
    // `integer` (Sacos, Bonificacion, Costos) pide 'count' (sin decimales forzados); el resto siempre
    // fuerza 2 decimales, igual que 'currency'. `rawDisplay` (Destare, Castigo, Descuento Fro, Otros
    // Desctos) se salta el formateo entero: se ve tal cual se tipeo, sin agregar ni quitar nada.
    this.inputValue =
      this.field.readonly && isNumeric && !this.field.rawDisplay
        ? formatDisplayNumber(raw, this.field.integer ? 'count' : 'currency')
        : raw;
  }

  onInput(value: string | number): void {
    this.inputValue = value;
    this.valueChange.emit(value);
  }

  /** Un <select> nativo dispara 'change' en cada letra durante el typeahead del navegador (no solo
   *  al elegir con el mouse/Enter): confirmar en el acto bloqueaba el campo a mitad de tipeo (ver
   *  bug "No hay un anuncio activo..." en Especial). Se espera a que el valor deje de moverse antes
   *  de confirmar; Enter/blur (una eleccion con mouse+click afuera, o Tab) siguen confirmando ya
   *  mismo, sin esperar el debounce, igual que en los inputs de texto. */
  onSelectChange(): void {
    if (this.selectCommitTimer) {
      clearTimeout(this.selectCommitTimer);
    }
    this.selectCommitTimer = setTimeout(() => {
      this.selectCommitTimer = null;
      this.committed.emit();
    }, 300);
  }

  commitSelectNow(): void {
    if (this.selectCommitTimer) {
      clearTimeout(this.selectCommitTimer);
      this.selectCommitTimer = null;
    }
    this.committed.emit();
  }
}
