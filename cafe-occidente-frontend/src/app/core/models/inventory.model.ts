export type PurchaseModule = 'DRY_COFFEE' | 'GREEN_COFFEE' | 'HUSK' | 'OTHER_COFFEE' | 'FERTI_FUTURO';

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
}

export interface RemissionLineRequest {
  readonly inventoryMovementId: number;
  readonly quantity: number;
  /** Sacos y Kilos Brutos que salen en este despacho (Form_EXITS). */
  readonly sacos: number;
  readonly grossKg: number;
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
  readonly agencyId: number;
  readonly remissionDate: string;
  readonly destination: string | null;
  readonly conductorIdNumber: string | null;
  readonly lines: RemissionLineRequest[];
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
  /** Numero impreso: {prefijo}-{LF|RP}-{0000}; en las remisiones viejas, su numero tal cual. */
  readonly displayNumber: string;
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
}

export interface ProductCodeOption {
  readonly code: string;
  readonly name: string | null;
}
