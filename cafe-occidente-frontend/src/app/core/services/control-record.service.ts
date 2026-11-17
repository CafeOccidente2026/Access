import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { ControlRecordRequest, ControlRecordResponse, GreenCoffeeValues, ResolutionAlert } from '../models/control-record.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso ControlRecord (RegControl), por agencia. */
@Injectable({ providedIn: 'root' })
export class ControlRecordService {
  private readonly http = inject(HttpClient);

  /** Hallazgo #6: huella del Registro de Control de la agencia de trabajo (cambia si cambia algún valor). */
  stamp(): Observable<{ stamp: string }> {
    return this.http.get<{ stamp: string }>(`${API_BASE_URL}/control-records/stamp`);
  }

  /** R1 (17-nov): resoluciones DIAN por vencer o vencidas (todas las agencias para el Administrador). */
  resolutionAlerts(): Observable<ResolutionAlert[]> {
    return this.http.get<ResolutionAlert[]>(`${API_BASE_URL}/control-records/resolution-alerts`);
  }

  getByAgency(agencyId: number): Observable<ControlRecordResponse> {
    return this.http.get<ControlRecordResponse>(`${API_BASE_URL}/control-records/${agencyId}`);
  }

  create(agencyId: number, request: ControlRecordRequest): Observable<ControlRecordResponse> {
    return this.http.post<ControlRecordResponse>(`${API_BASE_URL}/control-records/${agencyId}`, request);
  }

  update(agencyId: number, request: ControlRecordRequest): Observable<ControlRecordResponse> {
    return this.http.put<ControlRecordResponse>(`${API_BASE_URL}/control-records/${agencyId}`, request);
  }

  /** Los 5 valores de cafe verde en todos los Registros de Control activos (solo Administrador). */
  applyGreenCoffeeToAll(values: GreenCoffeeValues): Observable<{ agencies: number }> {
    return this.http.put<{ agencies: number }>(`${API_BASE_URL}/control-records/green-coffee`, values);
  }
}
