import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AnnulmentCandidate, AnnulmentDirection } from '../models/purchase-annulment.model';

/** Unica responsabilidad: "Anular Documento" (/api/purchases/annulment). */
@Injectable({ providedIn: 'root' })
export class PurchaseAnnulmentService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/purchases/annulment`;

  findByInvoice(invoiceNumber: number): Observable<AnnulmentCandidate[]> {
    return this.http.get<AnnulmentCandidate[]>(this.url, { params: { invoiceNumber } });
  }

  /** La factura vecina de {@code from} en la agencia de trabajo; lista vacia si no hay mas. */
  navigate(from: number | null, direction: AnnulmentDirection): Observable<AnnulmentCandidate[]> {
    const params: Record<string, string | number> = { direction };
    if (from != null) {
      params['from'] = from;
    }
    return this.http.get<AnnulmentCandidate[]>(`${this.url}/navigate`, { params });
  }

  annul(candidate: AnnulmentCandidate): Observable<AnnulmentCandidate> {
    return this.http.post<AnnulmentCandidate>(`${this.url}/${candidate.module}/${candidate.id}`, null);
  }
}
