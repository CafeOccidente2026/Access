import { AnnulmentModule } from './purchase-annulment.model';

/** Una factura pendiente de remesar (GET /api/remittances/pending). */
export interface RemittancePurchase {
  readonly module: AnnulmentModule;
  readonly id: number;
  readonly invoice: string;
  readonly purchaseDate: string;
  readonly specialType: string;
  readonly idNumber: string;
  readonly sellerName: string;
  readonly netKg: number;
  readonly grossValue: number;
}

export interface RemittanceRequest {
  readonly fundCode: string;
  /** null = "Todos". */
  readonly specialType: string | null;
  readonly purchases: { readonly module: AnnulmentModule; readonly id: number }[];
  readonly packagingQuantity: number;
  readonly packagingType: string;
}

export interface Remittance {
  readonly id: number;
  readonly number: string;
  readonly fundCode: string;
  readonly specialType: string | null;
  readonly packagingQuantity: number | null;
  readonly packagingType: string | null;
  readonly status: 'VIGENTE' | 'DESMARCADA';
  readonly createdAt: string;
  readonly purchases: number;
  readonly netKg: number;
}

/** Una factura en los archivos, con los valores de las consultas de Access ya calculados en el servidor. */
export interface RemittanceLine {
  readonly idNumber: string;
  readonly sellerName: string;
  /** Fecha y hora de la compra (Fecha_Compra), sin zona: "2026-11-03T09:12:00". */
  readonly purchaseDateTime: string;
  readonly invoice: string;
  readonly fundCode: string;
  readonly specialType: string;
  readonly announcementDate: string | null;
  readonly netKg: number;
  readonly factor: number;
  readonly kas: number;
  readonly grossValue: number;
  readonly unitPrice: number;
  readonly healthyPrice: number;
  readonly bonus: number;
  readonly healthyPercentage: number;
  readonly huskPercentage: number;
  readonly wastePercentage: number;
  readonly announcementBasePrice: number;
  readonly bonusTotal: number;
  readonly purchaseBasePrice: number;
}

export interface RemittanceFiles {
  readonly number: string;
  readonly agencyName: string;
  /** Fecha y hora en que se genero la remesa (fechaOrigEntr de ParaOpen). */
  readonly generatedAt: string;
  readonly lines: RemittanceLine[];
}
