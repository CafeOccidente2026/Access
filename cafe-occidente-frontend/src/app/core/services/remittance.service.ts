import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Remittance, RemittanceFiles, RemittancePurchase, RemittanceRequest } from '../models/remittance.model';

/** Genera Remesa (RF-11, RF-12): siempre sobre la agencia de trabajo de la sesion. */
@Injectable({ providedIn: 'root' })
export class RemittanceService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/remittances`;

  specials(fundCode: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/specials`, { params: { fundCode } });
  }

  /** specialType vacio = "Todos". */
  pending(fundCode: string, specialType: string): Observable<RemittancePurchase[]> {
    const params: Record<string, string> = { fundCode };
    if (specialType) {
      params['specialType'] = specialType;
    }
    return this.http.get<RemittancePurchase[]>(`${this.url}/pending`, { params });
  }

  create(request: RemittanceRequest): Observable<Remittance> {
    return this.http.post<Remittance>(this.url, request);
  }

  findByNumber(number: string): Observable<Remittance[]> {
    return this.http.get<Remittance[]>(this.url, { params: { number } });
  }

  files(id: number): Observable<RemittanceFiles> {
    return this.http.get<RemittanceFiles>(`${this.url}/${id}/files`);
  }

  unmark(id: number): Observable<Remittance> {
    return this.http.post<Remittance>(`${this.url}/${id}/unmark`, {});
  }
}
