import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { ChangeHistoryFilter, ChangeHistoryPage } from '../models/history.model';

/** Unica responsabilidad: consultar el historial de cambios (solo Administrador). */
@Injectable({ providedIn: 'root' })
export class HistoryService {
  private readonly http = inject(HttpClient);

  search(filter: ChangeHistoryFilter): Observable<ChangeHistoryPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<ChangeHistoryPage>(`${API_BASE_URL}/history`, { params });
  }
}
