/** Tablas de "Acceso a Tablas" (RF-03), en el orden del formulario Tablas de Access. */
export type QueryableTable =
  | 'CAJA_MENOR'
  | 'COMPRAS_CAFE_SECO'
  | 'COMPRAS_CAFE_VERDE'
  | 'COMPRAS_PASILLA'
  | 'COMPRAS_A_FUTURO'
  | 'INVENTARIO'
  | 'PRODUCTOS'
  | 'EMPAQUE'
  | 'SUMINISTROS'
  | 'CAJA';

/** Una pagina de solo lectura: nombres de columna, filas como texto y el total de filas. */
export interface TablePage {
  readonly columns: string[];
  readonly rows: (string | null)[][];
  readonly total: number;
}
