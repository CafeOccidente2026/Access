import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PurchaseCorrectionRequest, PurchaseCorrectionResult } from '../models/purchase-correction.model';
import { AnnulmentCandidate, AnnulmentDirection, AnnulmentModule } from '../models/purchase-annulment.model';

/** Unica responsabilidad: "Menu Habilitacion" (/api/purchases/correction, HU-05) en la agencia de trabajo. */
@Injectable({ providedIn: 'root' })
export class PurchaseCorrectionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/purchases/correction`;

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

  /** El calculo con el anuncio original, sin guardar. Si la compra no se puede corregir, responde el motivo. */
  preview(module: AnnulmentModule, id: number, request: PurchaseCorrectionRequest): Observable<PurchaseCorrectionResult> {
    return this.http.post<PurchaseCorrectionResult>(`${this.url}/${module}/${id}/preview`, request);
  }

  correct(module: AnnulmentModule, id: number, request: PurchaseCorrectionRequest): Observable<PurchaseCorrectionResult> {
    return this.http.put<PurchaseCorrectionResult>(`${this.url}/${module}/${id}`, request);
  }
}
