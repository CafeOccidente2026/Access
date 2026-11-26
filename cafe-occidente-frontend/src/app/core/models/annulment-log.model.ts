/** Una anulacion del registro de auditoria (26-nov). Las de Access no traen fecha, usuario ni motivo. */
export interface AnnulmentLogEntry {
  readonly id: number;
  readonly origin: 'WEB' | 'ACCESS';
  readonly documentType: string;
  readonly documentNumber: string;
  readonly agencyName: string;
  readonly documentId: number | null;
  readonly annulledAt: string | null;
  /** Fecha de la compra anulada (yyyy-mm-dd); null en una remision. */
  readonly purchaseDate: string | null;
  readonly username: string | null;
  readonly reason: string | null;
  /** Foto del documento al anularlo: kilos, valores, forma de pago, caficultor (o los de la remision). */
  readonly values: Record<string, string>;
  readonly note: string | null;
}

export interface AnnulmentLogPage {
  readonly items: AnnulmentLogEntry[];
  readonly total: number;
}

export interface AnnulmentLogFilter {
  readonly agencyId: number | null;
  readonly documentType: string;
  readonly userId: number | null;
  readonly from: string;
  readonly to: string;
  /** Las de Access no tienen fecha de anulacion: marcada, salen sin mirar Desde/Hasta; desmarcada, no salen. */
  readonly includeAccess: boolean;
  readonly page: number;
  readonly size: number;
}
