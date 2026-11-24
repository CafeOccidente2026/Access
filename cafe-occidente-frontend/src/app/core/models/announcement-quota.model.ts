import { Announcement } from './dry-coffee-purchase.model';

/** Cupo asignado a un anuncio (RF-21.1). Entregados/Saldo
 *  se recalculan en cada consulta contra las compras reales - nunca se guardan. */
export interface AnnouncementQuotaResponse {
  readonly id: number;
  readonly agencyId: number;
  readonly announcementNumber: number;
  readonly announcementDate: string;
  readonly specialType: string;
  readonly assignedQuota: number;
  /** Kilos Entregados: todas las compras válidas con ese anuncio y especial, normales y de cupo (informativo). */
  readonly deliveredKg: number;
  /** Kilos con Cupo: las compras de Facturar Anuncios con Cupos. Saldo = Cupo - Kilos con Cupo. */
  readonly quotaKg: number;
  readonly balance: number;
  /** El anuncio con el prefijo del punto ("SDTA-3738"); se muestra con announcementDisplay (R4). */
  readonly displayNumber: string;
}

export interface AnnouncementQuotaRequest {
  readonly assignedQuota: number;
}

/**
 * Fila de la ventana de Facturar Anuncios con Cupos (RN-21.2.4): el anuncio con Cupo, Kl_Entregados, Kilos con Cupo y
 * Saldo. Facturar Compras Anunciadas usa la misma ventana con sus compras a futuro, sin Kilos con Cupo.
 */
export interface QuotaBalance {
  readonly announcement: Announcement;
  readonly assignedQuota: number;
  readonly deliveredKg: number;
  readonly quotaKg?: number;
  readonly balance: number;
}

/** Anuncio de la lista de Asignar Cupo, con Kilos Entregados (todas las compras, RN-21.1.6) y Kilos con Cupo. */
export interface QuotaCandidate {
  readonly announcement: Announcement;
  readonly deliveredKg: number;
  readonly quotaKg: number;
}
