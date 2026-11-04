import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AnnulmentCandidate, AnnulmentDirection, AnnulmentModule } from '../models/purchase-annulment.model';
import { PurchaseQueryDetail } from '../models/purchase-query.model';

/** Unica responsabilidad: "Consultar Compras" (/api/purchases/query) de la agencia de trabajo. */
@Injectable({ providedIn: 'root' })
export class PurchaseQueryService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/purchases/query`;

  findByInvoice(invoiceNumber: number): Observable<AnnulmentCandidate[]> {
    return this.http.get<AnnulmentCandidate[]>(this.url, { params: { invoiceNumber } });
  }

  /** La factura vecina de {@code from}; lista vacia si no hay mas. */
  navigate(from: number | null, direction: AnnulmentDirection): Observable<AnnulmentCandidate[]> {
    const params: Record<string, string | number> = { direction };
    if (from != null) {
      params['from'] = from;
    }
    return this.http.get<AnnulmentCandidate[]>(`${this.url}/navigate`, { params });
  }

  detail(module: AnnulmentModule, id: number): Observable<PurchaseQueryDetail> {
    return this.http.get<PurchaseQueryDetail>(`${this.url}/${module}/${id}`);
  }
}
