import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AnnulmentCandidate } from '../models/purchase-annulment.model';

/** Unica responsabilidad: "Anular Documento" (/api/purchases/annulment). */
@Injectable({ providedIn: 'root' })
export class PurchaseAnnulmentService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/purchases/annulment`;

  findByInvoice(invoiceNumber: number): Observable<AnnulmentCandidate[]> {
    return this.http.get<AnnulmentCandidate[]>(this.url, { params: { invoiceNumber } });
  }

  annul(candidate: AnnulmentCandidate): Observable<AnnulmentCandidate> {
    return this.http.post<AnnulmentCandidate>(`${this.url}/${candidate.module}/${candidate.id}`, null);
  }
}
