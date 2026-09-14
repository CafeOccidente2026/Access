export type FieldType = 'text' | 'number' | 'date' | 'select' | 'currency' | 'percentage';

/** Definicion declarativa de un campo de formulario, usada para renderizar
 *  dinamicamente sin repetir marcado en cada pantalla. */
export interface FormFieldDefinition {
  readonly key: string;
  readonly label: string;
  readonly type: FieldType;
  readonly value?: string | number;
  readonly options?: string[];
  readonly readonly?: boolean;
  readonly highlighted?: boolean;
  /** Sobrescribe el highlightClass de la fila solo para este campo (p.ej. distinguirlo de sus vecinos). */
  readonly highlightClass?: string;
  /** Sobrescribe el inputWidthClass de la fila solo para este campo (p.ej. fechas que necesitan mas ancho). */
  readonly inputWidthClass?: string;
  readonly columnSpan?: 1 | 2 | 3;
}

export interface FormFieldSection {
  readonly heading?: string;
  readonly fields: FormFieldDefinition[];
}
