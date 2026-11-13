export interface TableAccessContent {
  readonly windowTitle: string;
  readonly closeButton: string;
  readonly backButton: string;
  readonly emptyMessage: string;
  readonly errorMessage: string;
  readonly agencyLabel: string;
  readonly allAgencies: string;
  readonly allAgenciesNote: string;
  readonly agencyNote: string;
  readonly searchLabel: string;
  readonly searchPlaceholder: string;
  readonly filterPlaceholder: string;
  readonly fromPlaceholder: string;
  readonly toPlaceholder: string;
  readonly filterLabel: string;
  /** "Registro {x} de {n}", como el contador de registros de Access. */
  readonly counter: string;
  readonly loading: string;
  readonly sheetLabel: string;
  /** Nombre de cada tabla en la lista; la que no este aca se muestra con su nombre real. */
  readonly tables: Record<string, string>;
}
