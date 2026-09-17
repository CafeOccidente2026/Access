export type FieldType = 'text' | 'number' | 'count' | 'date' | 'select' | 'currency' | 'percentage';

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
  /** Sobrescribe el ancho del contenedor flex (fieldWidthClass) de la fila solo para este campo. */
  readonly rowWidthClass?: string;
  readonly columnSpan?: 1 | 2 | 3;
  /** Campo numerico que es un conteo entero (p.ej. Sacos): no se le fuerzan 2 decimales al
   *  formatear, a diferencia del resto de los campos 'count' (pesos/kilos, que si son decimales). */
  readonly integer?: boolean;
}

export interface FormFieldSection {
  readonly heading?: string;
  readonly fields: FormFieldDefinition[];
}
