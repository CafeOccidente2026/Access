import { FormFieldDefinition } from '../../core/models';

export interface ControlRecordContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly leftColumn: FormFieldDefinition[];
  readonly rightColumn: FormFieldDefinition[];
}
