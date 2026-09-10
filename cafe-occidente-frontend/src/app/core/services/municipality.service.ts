import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { CreateMunicipalityRequest, Municipality } from '../models/municipality.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso Municipality. */
@Injectable({ providedIn: 'root' })
export class MunicipalityService {
  private readonly http = inject(HttpClient);

  list(): Observable<Municipality[]> {
    return this.http.get<Municipality[]>(`${API_BASE_URL}/municipalities`);
  }

  create(request: CreateMunicipalityRequest): Observable<Municipality> {
    return this.http.post<Municipality>(`${API_BASE_URL}/municipalities`, request);
  }
}
