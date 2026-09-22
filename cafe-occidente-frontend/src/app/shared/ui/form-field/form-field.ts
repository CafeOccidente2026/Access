import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormFieldDefinition } from '../../../core/models';
import { formatDisplayNumber } from '../../utils/number-format';
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

  /** Nuevo valor escrito/seleccionado por el usuario. */
  @Output() readonly valueChange = new EventEmitter<string | number>();
  /** El usuario terminó de editar el campo (blur o Enter). */
  @Output() readonly committed = new EventEmitter<void>();

  inputValue: string | number = '';

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
    const sanitized = this.sanitize(value);
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
