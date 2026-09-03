/** Configuracion de la barra de titulo tipo ventana de escritorio. */
export interface WindowConfig {
  readonly title: string;
  readonly icon?: 'form' | 'table';
}

export interface StatusBarConfig {
  readonly recordLabel?: string;
  readonly currentRecord?: number;
  readonly totalRecords?: number;
  readonly filterLabel?: string;
  readonly searchPlaceholder?: string;
}
