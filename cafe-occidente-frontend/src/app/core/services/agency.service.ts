import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Agency } from '../models/agency.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso Agency (solo lectura). */
@Injectable({ providedIn: 'root' })
export class AgencyService {
  private readonly http = inject(HttpClient);

  list(): Observable<Agency[]> {
    return this.http.get<Agency[]>(`${API_BASE_URL}/agencies`);
  }
}
