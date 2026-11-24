export type PurchaseModule = 'DRY_COFFEE' | 'GREEN_COFFEE' | 'HUSK';

/** Una entrada de inventario (una fila por cada compra real de los 5 módulos), creada
 *  automáticamente al guardarse la compra - de solo lectura acá. */
export interface InventoryMovementResponse {
  readonly id: number;
  readonly purchaseModule: PurchaseModule;
  readonly purchaseId: number;
  readonly agencyId: number;
  readonly agencyName: string;
  readonly productCode: string;
  readonly specialType: string;
  readonly invoiceNumber: number;
  readonly purchaseDate: string;
  readonly sacos: number;
  readonly grossKg: number;
  readonly netKg: number;
  readonly remainingKg: number;
  readonly healthyPercentage: number | null;
  readonly inventoryValue: number;
  /** Cedula del vendedor y fondo de la compra (C7: columna Cedula y filtro por Fondo). */
  readonly idNumber: string | null;
  readonly fundCode: string | null;
  /** La compra ya esta en una remesa: no sale en Registrar Salidas. */
  readonly remitted: boolean;
}

/** Una factura que sale completa: el servidor toma su saldo, sacos y kilos brutos. */
export interface RemissionLineRequest {
  readonly inventoryMovementId: number;
}

export interface RemissionLineResponse {
  readonly id: number;
  readonly inventoryMovementId: number;
  readonly quantity: number;
  readonly unitValue: number;
  readonly outputValue: number;
  /** Clase de cafe, factor y fondo (LF/RP) de la entrada origen: para la remision impresa. */
  readonly specialType: string;
  readonly healthyPercentage: number | null;
  readonly fundCode: string;
  /** null en lineas anteriores a V31. */
  readonly sacos: number | null;
  readonly grossKg: number | null;
  /** % salida (frpond de Sld3), el Factor de la remision; null en lineas anteriores a V32. */
  readonly exitPercentage: number | null;
}

export interface RemissionRequest {
  readonly remissionDate: string;
  readonly destination: string | null;
  readonly conductorIdNumber: string | null;
  readonly lines: RemissionLineRequest[];
  /** Empaques que salen con la remision y su tipo (NUEVO o USADO). */
  readonly packagingQuantity: number;
  readonly packagingType: string;
}

export interface RemissionResponse {
  readonly id: number;
  readonly remissionNumber: number;
  readonly agencyId: number;
  readonly agencyName: string;
  readonly remissionDate: string;
  readonly destination: string | null;
  readonly conductorIdNumber: string | null;
  readonly conductorName: string | null;
  readonly transportCompany: string | null;
  readonly vehiclePlate: string | null;
  readonly exported: boolean;
  readonly lines: RemissionLineResponse[];
  /** Numero impreso: {prefijo}-{consecutivo}, el mismo de su remesa; en las viejas, el que tenian. */
  readonly displayNumber: string;
  readonly status: 'VIGENTE' | 'ANULADA';
  /** Su remesa; null en las remisiones anteriores a V31. */
  readonly remittanceId: number | null;
}

/** Fila de los reportes de inventario: entrada (factura) o salida (remision); lo del otro tipo va en null. */
export interface InventoryReportRow {
  readonly date: string;
  readonly invoiceNumber: number | null;
  readonly remissionNumber: string | null;
  readonly productCode: string;
  readonly specialType: string;
  readonly netKg: number | null;
  readonly healthyPercentage: number | null;
  readonly inventoryValue: number | null;
  readonly quantity: number | null;
  readonly exitPercentage: number | null;
  readonly outputValue: number | null;
  /** Solo la fila de saldo inicial (cierre de año): «Saldo inicial — viene de <año>». */
  readonly label?: string | null;
}

export interface ProductCodeOption {
  readonly code: string;
  readonly name: string | null;
}
