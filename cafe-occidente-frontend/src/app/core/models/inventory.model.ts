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
}

export interface RemissionLineResponse {
  readonly id: number;
  readonly inventoryMovementId: number;
  readonly quantity: number;
  readonly unitValue: number;
  readonly outputValue: number;
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
}
