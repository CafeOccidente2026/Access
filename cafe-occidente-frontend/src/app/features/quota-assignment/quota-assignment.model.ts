import { FormFieldDefinition } from '../../core/models';

export interface QuotaAssignmentContent {
  readonly windowTitle: string;
  readonly fields: FormFieldDefinition[];
}
