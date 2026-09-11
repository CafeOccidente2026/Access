import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Grower } from '../models/grower.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso Grower (solo lectura). */
@Injectable({ providedIn: 'root' })
export class GrowerService {
  private readonly http = inject(HttpClient);

  findByIdNumber(idNumber: string): Observable<Grower> {
    return this.http.get<Grower>(`${API_BASE_URL}/growers/${idNumber}`);
  }
}
