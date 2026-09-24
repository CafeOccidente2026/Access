import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AnnouncementQuotaRequest, AnnouncementQuotaResponse } from '../models/announcement-quota.model';

/** Unica responsabilidad: llamadas HTTP para "Asignar Cupo" (Access "ACTUALIZA CUPOS"), solo ADMIN. */
@Injectable({ providedIn: 'root' })
export class AnnouncementQuotaService {
  private readonly http = inject(HttpClient);

  get(agencyId: number, announcementNumber: number): Observable<AnnouncementQuotaResponse> {
    return this.http.get<AnnouncementQuotaResponse>(
      `${API_BASE_URL}/announcement-quotas/${agencyId}/${announcementNumber}`,
    );
  }

  assign(
    agencyId: number,
    announcementNumber: number,
    request: AnnouncementQuotaRequest,
  ): Observable<AnnouncementQuotaResponse> {
    return this.http.put<AnnouncementQuotaResponse>(
      `${API_BASE_URL}/announcement-quotas/${agencyId}/${announcementNumber}`,
      request,
    );
  }
}
