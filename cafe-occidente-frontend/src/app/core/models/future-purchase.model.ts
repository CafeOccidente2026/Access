/** Campos de entrada de "Ingresar Compras a Futuro" (Form_COMPRAS A FUTURO.bas). Sin cascada de
 *  precio ni factura: solo registra el compromiso de entrega. */
export interface FuturePurchaseRequest {
  readonly idNumber: string;
  readonly specialType: string;
  /** Número del anuncio del día elegido en la lista (RN-21.4.3). */
  readonly announcementNumber: number;
  readonly announcedKg: number;
  readonly deliveryDate: string;
  readonly finca: string | null;
  readonly municipality: string | null;
  readonly vereda: string | null;
}

/** Anunciadas Hoy (HU-31, P2): un anuncio con la suma de sus compras a futuro que tienen saldo. */
export interface PendingAnnouncementRow {
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly specialType: string;
  readonly healthyUnitPrice: number;
  readonly announcedKg: number;
  readonly remainingKg: number;
  readonly growers: number;
}

/** Restaurar Compras Anunciadas: saldo guardado y el que sale de recalcular desde cero. */
export interface FuturePurchaseRestoreRow {
  readonly id: number;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly specialType: string;
  readonly announcedKg: number;
  readonly deliveredKg: number;
  readonly currentBalance: number;
  readonly recalculatedBalance: number;
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
