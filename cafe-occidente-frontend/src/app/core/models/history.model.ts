/** Una fila del historial de cambios (GET /api/history): solo los campos que cambiaron. */
export interface ChangeHistoryEntry {
  readonly id: number;
  readonly at: string;
  readonly username: string;
  readonly entity: string;
  readonly entityId: number;
  readonly action: string;
  readonly agencyName: string | null;
  readonly before: Record<string, string | null> | null;
  readonly after: Record<string, string | null>;
}

export interface ChangeHistoryPage {
  readonly items: ChangeHistoryEntry[];
  readonly total: number;
}

/** Filtros opcionales de la pantalla Historial; from/to en formato yyyy-MM-dd. */
export interface ChangeHistoryFilter {
  readonly entity?: string;
  readonly agencyId?: number | null;
  readonly userId?: number | null;
  readonly from?: string;
  readonly to?: string;
  readonly page?: number;
}
