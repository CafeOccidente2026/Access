import { FormFieldDefinition } from '../../core/models';

/** adminOnly: respuesta del area (11-nov), solo el Administrador lo cambia; con el permiso temporal queda de solo lectura. */
export type ControlRecordField = FormFieldDefinition & { readonly adminOnly?: boolean };

export interface ControlRecordContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly agencyLabel: string;
  readonly saveButton: string;
  readonly createNotice: string;
  readonly savedMessage: string;
  readonly errorMessage: string;
  readonly applyGreenButton: string;
  /** {count}: agencias con Registro de Control activo. */
  readonly applyGreenConfirm: string;
  readonly applyGreenAccept: string;
  readonly applyGreenCancel: string;
  readonly applyGreenDone: string;
  readonly leftColumn: ControlRecordField[];
  readonly rightColumn: ControlRecordField[];
  readonly greenHeading: string;
  readonly greenColumn: ControlRecordField[];
}
