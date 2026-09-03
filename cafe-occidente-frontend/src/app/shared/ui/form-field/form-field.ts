import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FormFieldDefinition } from '../../../core/models';

/** Renderiza un unico campo (etiqueta + control) segun su definicion. */
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

  inputValue: string | number = '';

  ngOnChanges(): void {
    this.inputValue = this.field.value ?? '';
  }
}
