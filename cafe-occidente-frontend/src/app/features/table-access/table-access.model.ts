import { QueryableTable } from '../../core/models/table-access.model';

export interface TableAccessContent {
  readonly windowTitle: string;
  readonly closeButton: string;
  readonly backButton: string;
  readonly previousButton: string;
  readonly nextButton: string;
  readonly totalLabel: string;
  readonly emptyMessage: string;
  readonly errorMessage: string;
  readonly agencyNote: string;
  readonly allAgenciesNote: string;
  readonly tables: Record<QueryableTable, string>;
}
