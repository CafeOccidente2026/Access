import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { InvoiceListService } from './invoice-list.service';
import { AnnulmentCandidate } from '../models/purchase-annulment.model';

/** Unica responsabilidad: "Anular Documento" (/api/purchases/annulment). */
@Injectable({ providedIn: 'root' })
export class PurchaseAnnulmentService extends InvoiceListService {
  protected readonly url = `${API_BASE_URL}/purchases/annulment`;

  /** El motivo es obligatorio y queda en el registro de anulaciones (26-nov). */
  annul(candidate: AnnulmentCandidate, reason: string): Observable<AnnulmentCandidate> {
    return this.http.post<AnnulmentCandidate>(`${this.url}/${candidate.module}/${candidate.id}`, { reason: reason.trim() });
  }
}
