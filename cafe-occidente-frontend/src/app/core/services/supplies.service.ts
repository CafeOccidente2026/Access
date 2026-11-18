import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { LedgerEntryRequest, PackagingEntryRequest, SuppliesEntryResponse, SuppliesReportRow } from '../models/supplies.model';

/** Unica responsabilidad: llamadas HTTP de "MENUS SUMINISTROS". endpoint = segmento de
 *  /api/supplies (altas) o de /api/supplies/reports (informes), tal como viene del JSON. */
@Injectable({ providedIn: 'root' })
export class SuppliesService {
  private readonly http = inject(HttpClient);

  create(endpoint: string, request: LedgerEntryRequest | PackagingEntryRequest): Observable<SuppliesEntryResponse> {
    return this.http.post<SuppliesEntryResponse>(`${API_BASE_URL}/supplies/${endpoint}`, request);
  }

  /** El Id que tomaria la proxima alta de la pantalla (RF §2.1); value es el fondo o el tipo de empaque. */
  nextTransactionId(screen: string, value: string | null): Observable<{ transactionId: string }> {
    const params: Record<string, string> = value ? { screen, value } : { screen };
    return this.http.get<{ transactionId: string }>(`${API_BASE_URL}/supplies/next-id`, { params });
  }

  /** Relacion Cheques es POST: como en Access, marca los cheques que devuelve. */
  report(endpoint: string, params: Record<string, string | number | null>, post = false): Observable<SuppliesReportRow[]> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== '') {
        httpParams = httpParams.set(key, value);
      }
    }
    const url = `${API_BASE_URL}/supplies/reports/${endpoint}`;
    return post
      ? this.http.post<SuppliesReportRow[]>(url, null, { params: httpParams })
      : this.http.get<SuppliesReportRow[]>(url, { params: httpParams });
  }
}
