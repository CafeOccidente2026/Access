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
  readonly deliveredKg: number;
  readonly balance: number;
}

export interface AnnouncementQuotaRequest {
  readonly assignedQuota: number;
}

/** Fila de la ventana de Facturar Anuncios con Cupos (RN-21.2.4): el anuncio con Cupo, Kl_Entregados y Saldo. */
export interface QuotaBalance {
  readonly announcement: Announcement;
  readonly assignedQuota: number;
  readonly deliveredKg: number;
  readonly balance: number;
}

/** Anuncio de la lista de Asignar Cupo, con la suma de los kilos ya comprados con él (RN-21.1.6). */
export interface QuotaCandidate {
  readonly announcement: Announcement;
  readonly deliveredKg: number;
}
