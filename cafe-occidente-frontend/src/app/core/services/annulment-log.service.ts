import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AnnulmentLogFilter, AnnulmentLogPage } from '../models/annulment-log.model';

/** Unica responsabilidad: consultar el registro de anulaciones (solo Administrador). */
@Injectable({ providedIn: 'root' })
export class AnnulmentLogService {
  private readonly http = inject(HttpClient);

  search(filter: AnnulmentLogFilter): Observable<AnnulmentLogPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<AnnulmentLogPage>(`${API_BASE_URL}/annulment-log`, { params });
  }
}
