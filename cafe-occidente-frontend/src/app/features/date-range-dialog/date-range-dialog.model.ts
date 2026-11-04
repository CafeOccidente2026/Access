import { PurchasesByDateText } from './purchases-by-date-pdf';

export interface DateRangeDialogContent {
  readonly windowTitle: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly pdfTitle: string;
  readonly messages: Record<'fromAfterTo' | 'empty' | 'error', string>;
  readonly pdf: PurchasesByDateText;
}
