/**
 * Alta de Caja / Caja Menor / Suministros (LedgerEntryRequest del backend). El Id de transaccion lo genera el servidor;
 * entryDate solo vale en Ingresar Suministros. checkNumber: los ultimos 4 digitos, como texto (0548).
 */
export interface LedgerEntryRequest {
  readonly fundCode: string | null;
  readonly entryDate: string | null;
  readonly idNumber: string | null;
  readonly detail: string | null;
  readonly amount: number;
  readonly paymentMethod: string | null;
  readonly checkNumber: string | null;
}

/** Alta de Empaque (Suministro Empaques = Entradas, Prestamo Empaques = Salidas en quantity). */
export interface PackagingEntryRequest {
  readonly packagingType: string;
  readonly idNumber: string | null;
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
  readonly checkNumber: string | null;
}
