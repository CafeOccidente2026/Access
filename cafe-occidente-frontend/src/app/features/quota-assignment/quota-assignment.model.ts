import { FormFieldDefinition } from '../../core/models';

export interface QuotaAssignmentContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly announcementLabel: string;
  readonly saveButton: string;
  readonly createNotice: string;
  readonly savedMessage: string;
  readonly errorMessage: string;
  readonly leftColumn: FormFieldDefinition[];
  readonly rightColumn: FormFieldDefinition[];
}
