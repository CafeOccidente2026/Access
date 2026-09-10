import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormFieldDefinition } from '../../../core/models';

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
  @Input() highlightClass = 'bg-yellow-100';

  /** Nuevo valor escrito/seleccionado por el usuario. */
  @Output() readonly valueChange = new EventEmitter<string | number>();
  /** El usuario terminó de editar el campo (blur o Enter). */
  @Output() readonly committed = new EventEmitter<void>();

  inputValue: string | number = '';

  ngOnChanges(): void {
    this.inputValue = this.field.value ?? '';
  }

  onInput(value: string | number): void {
    this.inputValue = value;
    this.valueChange.emit(value);
  }
}
