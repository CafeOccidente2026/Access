import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { ControlRecordRequest, ControlRecordResponse } from '../models/control-record.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso ControlRecord (RegControl), por agencia. */
@Injectable({ providedIn: 'root' })
export class ControlRecordService {
  private readonly http = inject(HttpClient);

  getByAgency(agencyId: number): Observable<ControlRecordResponse> {
    return this.http.get<ControlRecordResponse>(`${API_BASE_URL}/control-records/${agencyId}`);
  }

  create(agencyId: number, request: ControlRecordRequest): Observable<ControlRecordResponse> {
    return this.http.post<ControlRecordResponse>(`${API_BASE_URL}/control-records/${agencyId}`, request);
  }

  update(agencyId: number, request: ControlRecordRequest): Observable<ControlRecordResponse> {
    return this.http.put<ControlRecordResponse>(`${API_BASE_URL}/control-records/${agencyId}`, request);
  }
}
