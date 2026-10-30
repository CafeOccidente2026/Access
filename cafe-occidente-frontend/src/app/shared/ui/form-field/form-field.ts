import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormFieldDefinition } from '../../../core/models';
import { asPenalty, formatDisplayNumber } from '../../utils/number-format';
import { ComboboxComponent } from '../combobox/combobox';

/**
 * Renderiza un unico campo (etiqueta + control) segun su definicion.
 * Emite `valueChange`/`committed` de forma opcional: las pantallas de solo lectura simplemente
 * no los escuchan y el comportamiento visual no cambia.
 */
@Component({
  selector: 'app-form-field',
  standalone: true,
  imports: [CommonModule, FormsModule, ComboboxComponent],
  templateUrl: './form-field.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormFieldComponent {
  @Input({ required: true }) field!: FormFieldDefinition;
  @Input() labelClass = 'text-slate-700';
  @Input() labelWidthClass = 'w-32';
  @Input() inputWidthClass = 'min-w-[100px]';
  @Input() highlightClass = 'bg-yellow-100';
  /** Etiqueta encima del cuadro (Información Federación de Access): se ve completa, sin cortar. */
  @Input() labelAbove = false;
  /** Pantallas de edicion libre (Registro de Control): el campo numerico editable se ve formateado
   *  mientras no tiene el foco y crudo mientras se escribe, sin reformatear en cada tecla. */
  @Input() formatWhenIdle = false;

  /** Nuevo valor escrito/seleccionado por el usuario. */
  @Output() readonly valueChange = new EventEmitter<string | number>();
  /** El usuario terminó de editar el campo (blur o Enter). */
  @Output() readonly committed = new EventEmitter<void>();

  inputValue: string | number = '';
  private focused = false;

  ngOnChanges(): void {
    if (this.formatWhenIdle && this.focused) {
      return; // el usuario esta escribiendo: reescribir el valor mueve el cursor
    }
    const raw = this.field.value ?? '';
    // Solo se formatea de solo-lectura: mientras el campo sigue editable siempre se ve el valor
    // crudo (para no pelear con puntos de miles mientras se escribe). Los formularios de compra
    // bloquean (readonly = true) cada campo apenas se confirma (blur/Enter), asi que esto alcanza
    // para "formatear al confirmar, crudo mientras se edita" sin logica extra en cada formulario.
    // `integer` (Sacos, Bonificacion, Costos) pide 'count' (sin decimales forzados); el resto siempre
    // fuerza 2 decimales, igual que 'currency'. `rawDisplay` (Destare, Castigo, Descuento Fro, Otros
    // Desctos) se salta el formateo entero: se ve tal cual se tipeo, sin agregar ni quitar nada.
    this.inputValue = this.field.readonly || this.formatWhenIdle ? this.formatted(raw) : raw;
  }

  onFocus(): void {
    this.focused = true;
    if (this.formatWhenIdle && !this.field.readonly) {
      this.inputValue = this.field.value ?? '';
    }
  }

  onBlur(): void {
    this.focused = false;
    if (this.formatWhenIdle) {
      this.inputValue = this.formatted(this.field.value ?? '');
    }
    this.commit();
  }

  /** Blur o Enter. Un campo `negativeOnly` (Castigo) se pasa a negativo antes de avisar que se confirmo. */
  commit(): void {
    if (this.field.negativeOnly && !this.field.readonly) {
      const negative = asPenalty(String(this.inputValue ?? ''));
      this.inputValue = negative;
      this.valueChange.emit(negative);
    }
    this.committed.emit();
  }

  private formatted(raw: string | number): string | number {
    const isNumeric = ['number', 'count', 'currency', 'percentage'].includes(this.field.type);
    return isNumeric && !this.field.rawDisplay
      ? formatDisplayNumber(raw, this.field.integer ? 'count' : 'currency')
      : raw;
  }

  onInput(value: string | number, input?: HTMLInputElement): void {
    const sanitized = this.sanitize(value);
    // Si el modelo ya tenia el valor limpio (p. ej. "1" y se escribe "1a"), ngModel no repinta y la
    // letra quedaba visible aunque no se guardara: se corrige el input a mano.
    if (input && sanitized !== value) {
      input.value = String(sanitized);
    }
    this.inputValue = sanitized;
    this.valueChange.emit(sanitized);
  }

  /** Regla global: los campos numericos (Peso Tot Alm, Sacos, Kilos Brutos, Destare, Castigo,
   *  Descuento Fro, Otros Desctos, etc. - cualquier campo 'number'/'count'/'currency'/'percentage')
   *  solo aceptan digitos y una coma decimal, nunca signo negativo ni letras. Los de texto marcados
   *  `digitsOnly` (Cedula) solo aceptan digitos, ni siquiera coma. El resto de campos de texto
   *  (Nombres, Direccion...) no se tocan. */
  private sanitize(value: string | number): string | number {
    if (typeof value !== 'string') {
      return value;
    }
    if (this.field.digitsOnly) {
      return value.replace(/[^0-9]/g, '');
    }
    const numericTypes = ['number', 'count', 'currency', 'percentage'];
    if (numericTypes.includes(this.field.type)) {
      let cleaned = value.replace(/[^0-9,]/g, '');
      const firstComma = cleaned.indexOf(',');
      if (firstComma !== -1) {
        cleaned = cleaned.slice(0, firstComma + 1) + cleaned.slice(firstComma + 1).replace(/,/g, '');
      }
      return cleaned;
    }
    return value;
  }
}
