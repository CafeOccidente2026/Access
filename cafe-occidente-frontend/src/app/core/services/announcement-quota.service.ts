import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AnnouncementQuotaRequest, AnnouncementQuotaResponse, QuotaBalance, QuotaCandidate } from '../models/announcement-quota.model';

/** Unica responsabilidad: llamadas HTTP para "Asignar Cupo a Anuncios" (RF-21.1). La agencia es la
 *  de trabajo de la sesion: la pone el servidor (RF-05.1). */
@Injectable({ providedIn: 'root' })
export class AnnouncementQuotaService {
  private readonly http = inject(HttpClient);

  /** Anuncios de la agencia sin cupo, sin pasilla ni verde, con sus Kilos Entregados. */
  available(): Observable<QuotaCandidate[]> {
    return this.http.get<QuotaCandidate[]>(`${API_BASE_URL}/announcement-quotas/available`);
  }

  /** Anuncios del fondo y especial con cupo y saldo (ventana de Facturar Anuncios con Cupos). */
  withBalance(fundId: number, specialType: string): Observable<QuotaBalance[]> {
    const params = `fundId=${fundId}&specialType=${encodeURIComponent(specialType)}`;
    return this.http.get<QuotaBalance[]>(`${API_BASE_URL}/announcement-quotas/with-balance?${params}`);
  }

  assign(announcementNumber: number, request: AnnouncementQuotaRequest): Observable<AnnouncementQuotaResponse> {
    return this.http.put<AnnouncementQuotaResponse>(`${API_BASE_URL}/announcement-quotas/${announcementNumber}`, request);
  }
}
