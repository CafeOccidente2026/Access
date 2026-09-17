import { FormFieldDefinition } from '../../core/models';

export interface ControlRecordContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly agencyLabel: string;
  readonly saveButton: string;
  readonly createNotice: string;
  readonly savedMessage: string;
  readonly errorMessage: string;
  readonly leftColumn: FormFieldDefinition[];
  readonly rightColumn: FormFieldDefinition[];
}
