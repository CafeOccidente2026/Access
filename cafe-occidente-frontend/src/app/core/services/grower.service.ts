import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { API_BASE_URL } from '../config/api.config';
import { Grower, GrowerCreateRequest, GrowerProgram } from '../models/grower.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso Grower (lectura + alta de conductores). */
@Injectable({ providedIn: 'root' })
export class GrowerService {
  private readonly http = inject(HttpClient);

  findByIdNumber(idNumber: string): Observable<Grower> {
    return this.http.get<Grower>(`${API_BASE_URL}/growers/${idNumber}`);
  }

  /** "Ingresar Conductores" (Form_Conductores.bas) - alta rapida desde Registrar Salidas. */
  createConductor(request: GrowerCreateRequest): Observable<Grower> {
    return this.http.post<Grower>(`${API_BASE_URL}/growers`, request);
  }

  /** Programa/Cupo informativos; null si no hay match (204) - nunca bloquea la captura. */
  findProgram(idNumber: string, special?: string): Observable<GrowerProgram | null> {
    const params = special ? new HttpParams().set('special', special) : undefined;
    return this.http
      .get<GrowerProgram>(`${API_BASE_URL}/growers/${idNumber}/program`, { params })
      .pipe(catchError(() => of(null)));
  }
}
