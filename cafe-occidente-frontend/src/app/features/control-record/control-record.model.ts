import { FormFieldDefinition } from '../../core/models';

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
  readonly leftColumn: FormFieldDefinition[];
  readonly rightColumn: FormFieldDefinition[];
}
