export interface HistoryContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly entityLabel: string;
  readonly agencyLabel: string;
  readonly userLabel: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly searchButton: string;
  readonly previousButton: string;
  readonly nextButton: string;
  readonly all: string;
  readonly yes: string;
  readonly no: string;
  readonly emptyMessage: string;
  readonly errorMessage: string;
  readonly totalLabel: string;
  readonly columns: Record<'at' | 'user' | 'entity' | 'action' | 'agency' | 'detail', string>;
  readonly entities: Record<string, string>;
  readonly actions: Record<string, string>;
  readonly fields: Record<string, string>;
}
