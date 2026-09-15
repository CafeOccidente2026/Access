import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  HuskAnnouncementInfo,
  HuskNextInvoiceNumber,
  HuskPurchaseCalculation,
  HuskPurchaseRequest,
  HuskPurchaseResponse,
} from '../models/husk-purchase.model';

/** Unica responsabilidad: llamadas HTTP para el formulario Compra Pasilla (PASILLA). */
@Injectable({ providedIn: 'root' })
export class HuskPurchaseService {
  private readonly http = inject(HttpClient);

  create(request: HuskPurchaseRequest): Observable<HuskPurchaseResponse> {
    return this.http.post<HuskPurchaseResponse>(`${API_BASE_URL}/purchases/husk`, request);
  }

  preview(request: HuskPurchaseRequest): Observable<HuskPurchaseCalculation> {
    return this.http.post<HuskPurchaseCalculation>(`${API_BASE_URL}/purchases/husk/preview`, request);
  }

  /** Factura: siguiente consecutivo propio de PASILLA. */
  nextInvoiceNumber(): Observable<HuskNextInvoiceNumber> {
    return this.http.get<HuskNextInvoiceNumber>(`${API_BASE_URL}/purchases/husk/next-invoice-number`);
  }

  /** Cod Prod + anuncio vigente (Especial "PASILLA" / Fondo "RP" fijos). */
  announcementInfo(): Observable<HuskAnnouncementInfo> {
    return this.http.get<HuskAnnouncementInfo>(`${API_BASE_URL}/purchases/husk/announcement-info`);
  }
}
