/** Campos de entrada de "Ingresar Compras a Futuro" (Form_COMPRAS A FUTURO.bas). Sin cascada de
 *  precio ni factura: solo registra el compromiso de entrega. */
export interface FuturePurchaseRequest {
  readonly agencyId: number;
  readonly idNumber: string;
  readonly specialType: string;
  readonly announcedKg: number;
  readonly deliveryDate: string;
  readonly finca: string | null;
  readonly municipality: string | null;
  readonly vereda: string | null;
}

export interface FuturePurchaseResponse {
  readonly id: number;
  readonly agencyId: number;
  readonly agencyName: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly address: string;
  readonly specialType: string;
  readonly announcedKg: number;
  readonly remainingKg: number;
  readonly deliveryDate: string;
  readonly finca: string | null;
  readonly municipality: string | null;
  readonly vereda: string | null;
  readonly healthyUnitPrice: number;
  readonly defectiveUnitPrice: number;
  readonly bonus: number;
  readonly costs: number;
  readonly qualityIncrement: number;
  readonly basePriceLoad: number;
}
