import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormFieldDefinition } from '../../../core/models';
import { FormFieldComponent } from '../form-field/form-field';

/** Fila de campos (grupo horizontal) reutilizada en todos los formularios de compra. */
@Component({
  selector: 'app-field-row',
  standalone: true,
  imports: [CommonModule, FormFieldComponent],
  templateUrl: './field-row.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FieldRowComponent {
  @Input() fields: FormFieldDefinition[] = [];
  @Input() labelClass = 'text-slate-700';
  @Input() highlightClass = 'bg-yellow-100';
  @Input() fieldWidthClass = 'min-w-[240px] flex-1';
  @Input() labelWidthClass = 'w-32';
  @Input() inputWidthClass = 'min-w-[100px]';

  @Output() readonly fieldValueChange = new EventEmitter<{ key: string; value: string | number }>();
  @Output() readonly fieldCommitted = new EventEmitter<string>();

  trackByKey(_: number, field: FormFieldDefinition): string {
    return field.key;
  }
}
