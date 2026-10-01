/** Alta de Caja / Caja Menor / Suministros (LedgerEntryRequest del backend). */
export interface LedgerEntryRequest {
  readonly transactionId: string;
  readonly agencyId: number | null;
  readonly fundCode: string | null;
  readonly entryDate: string | null;
  readonly idNumber: string;
  readonly detail: string | null;
  readonly amount: number;
  readonly paymentMethod: string | null;
  readonly checkNumber: number | null;
}

/** Alta de Empaque (Suministro Empaques = Entradas, Prestamo Empaques = Salidas en quantity). */
export interface PackagingEntryRequest {
  readonly transactionId: string;
  readonly agencyId: number | null;
  readonly packagingType: string;
  readonly entryDate: string | null;
  readonly idNumber: string;
  readonly detail: string | null;
  readonly quantity: number;
}

export interface SuppliesEntryResponse {
  readonly id: number;
  readonly transactionId: string;
  readonly agencyName: string;
  readonly entryDate: string;
  readonly idNumber: string;
  readonly firstNames: string | null;
  readonly lastNames: string | null;
}

/** Fila comun de los informes; balance = Saldo acumulado (null en Relacion Cheques / Forma de Pago). */
export interface SuppliesReportRow {
  readonly agencyName: string;
  readonly transactionId: string;
  readonly fundCode: string | null;
  readonly date: string;
  readonly idNumber: string;
  readonly firstNames: string | null;
  readonly lastNames: string | null;
  readonly detail: string | null;
  readonly inflow: number;
  readonly outflow: number;
  readonly balance: number | null;
  readonly paymentMethod: string | null;
  readonly checkNumber: number | null;
}
