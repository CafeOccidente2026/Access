import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { API_BASE_URL } from '../config/api.config';
import { Conductor, Grower, GrowerCreateRequest, GrowerProgram } from '../models/grower.model';

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

  /** "Actualizar uno ya existente": solo conductores, max 10, desde 3 digitos (backend). */
  searchConductors(idNumberPrefix: string): Observable<Conductor[]> {
    return this.http.get<Conductor[]>(`${API_BASE_URL}/growers/conductors`, {
      params: new HttpParams().set('idNumber', idNumberPrefix),
    });
  }

  /** Solo Emp. Transp. y Vehiculo (el backend no acepta otros campos). */
  updateConductor(idNumber: string, transportCompany: string | null, vehiclePlate: string | null): Observable<Conductor> {
    return this.http.put<Conductor>(`${API_BASE_URL}/growers/conductors/${idNumber}`, { transportCompany, vehiclePlate });
  }

  /** Programa/Cupo informativos; null si no hay match (204) - nunca bloquea la captura. */
  findProgram(idNumber: string, special?: string): Observable<GrowerProgram | null> {
    const params = special ? new HttpParams().set('special', special) : undefined;
    return this.http
      .get<GrowerProgram>(`${API_BASE_URL}/growers/${idNumber}/program`, { params })
      .pipe(catchError(() => of(null)));
  }
}
