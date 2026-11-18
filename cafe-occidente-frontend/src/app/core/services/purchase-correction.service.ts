import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { InvoiceListService } from './invoice-list.service';
import { PurchaseCorrectionRequest, PurchaseCorrectionResult } from '../models/purchase-correction.model';
import { AnnulmentModule } from '../models/purchase-annulment.model';

/** Unica responsabilidad: "Menu Habilitacion" (/api/purchases/correction, HU-05) en la agencia de trabajo. */
@Injectable({ providedIn: 'root' })
export class PurchaseCorrectionService extends InvoiceListService {
  protected readonly url = `${API_BASE_URL}/purchases/correction`;

  /** El calculo con el anuncio original, sin guardar. Si la compra no se puede corregir, responde el motivo. */
  preview(module: AnnulmentModule, id: number, request: PurchaseCorrectionRequest): Observable<PurchaseCorrectionResult> {
    return this.http.post<PurchaseCorrectionResult>(`${this.url}/${module}/${id}/preview`, request);
  }

  correct(module: AnnulmentModule, id: number, request: PurchaseCorrectionRequest): Observable<PurchaseCorrectionResult> {
    return this.http.put<PurchaseCorrectionResult>(`${this.url}/${module}/${id}`, request);
  }
}
