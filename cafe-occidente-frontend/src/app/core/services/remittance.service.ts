import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Remittance, RemittanceFiles } from '../models/remittance.model';

/** Los 4 archivos de la remesa que genera cada remision (siempre de la agencia de trabajo de la sesion). */
@Injectable({ providedIn: 'root' })
export class RemittanceService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/remittances`;

  findByNumber(number: string): Observable<Remittance[]> {
    return this.http.get<Remittance[]>(this.url, { params: { number } });
  }

  /** Desmarca una remesa vieja (sin remision): solo el Administrador. */
  unmark(id: number): Observable<Remittance> {
    return this.http.post<Remittance>(`${this.url}/${id}/unmark`, {});
  }

  files(id: number): Observable<RemittanceFiles> {
    return this.http.get<RemittanceFiles>(`${this.url}/${id}/files`);
  }
}
