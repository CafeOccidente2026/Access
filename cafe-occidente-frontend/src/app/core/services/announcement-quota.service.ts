import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AnnouncementQuotaRequest, AnnouncementQuotaResponse } from '../models/announcement-quota.model';

/** Unica responsabilidad: llamadas HTTP para "Asignar Cupo" (Access "ACTUALIZA CUPOS"). La agencia es la
 *  de trabajo de la sesion: la pone el servidor (RF-05.1). */
@Injectable({ providedIn: 'root' })
export class AnnouncementQuotaService {
  private readonly http = inject(HttpClient);

  get(announcementNumber: number): Observable<AnnouncementQuotaResponse> {
    return this.http.get<AnnouncementQuotaResponse>(`${API_BASE_URL}/announcement-quotas/${announcementNumber}`);
  }

  assign(announcementNumber: number, request: AnnouncementQuotaRequest): Observable<AnnouncementQuotaResponse> {
    return this.http.put<AnnouncementQuotaResponse>(`${API_BASE_URL}/announcement-quotas/${announcementNumber}`, request);
  }
}
