/** Cupo asignado a un anuncio (pantalla "Asignar Cupo", Access "ACTUALIZA CUPOS"). Entregados/Saldo
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
