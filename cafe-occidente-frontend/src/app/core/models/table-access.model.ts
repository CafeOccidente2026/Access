/** El tipo de la columna decide el filtro: texto "contiene"; numero y fecha tambien "desde / hasta". */
export type ColumnKind = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN';

export interface ColumnInfo {
  readonly name: string;
  readonly kind: ColumnKind;
}

/** Una tabla de "Acceso a Tablas" (RF-03): byAgency = tiene agencia y se filtra por ella (RN-03.2). */
export interface TableInfo {
  readonly name: string;
  readonly byAgency: boolean;
  readonly primaryKey: string;
  readonly columns: ColumnInfo[];
}

/** Un tramo de filas de solo lectura: nombres de columna, filas como texto y el total con los filtros. */
export interface TablePage {
  readonly columns: string[];
  readonly rows: (string | null)[][];
  readonly total: number;
}
