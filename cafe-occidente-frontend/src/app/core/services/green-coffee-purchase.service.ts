import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  GreenAnnouncementInfo,
  GreenCoffeePurchaseCalculation,
  GreenCoffeePurchaseRequest,
  GreenCoffeePurchaseResponse,
  GreenNextInvoiceNumber,
} from '../models/green-coffee-purchase.model';

/** Unica responsabilidad: llamadas HTTP para el formulario Compras Cafe Verde (VERDES). */
@Injectable({ providedIn: 'root' })
export class GreenCoffeePurchaseService {
  private readonly http = inject(HttpClient);

  create(request: GreenCoffeePurchaseRequest): Observable<GreenCoffeePurchaseResponse> {
    return this.http.post<GreenCoffeePurchaseResponse>(`${API_BASE_URL}/purchases/green-coffee`, request);
  }

  preview(request: GreenCoffeePurchaseRequest): Observable<GreenCoffeePurchaseCalculation> {
    return this.http.post<GreenCoffeePurchaseCalculation>(
      `${API_BASE_URL}/purchases/green-coffee/preview`,
      request,
    );
  }

  /** Factura: siguiente consecutivo propio de VERDES. */
  nextInvoiceNumber(): Observable<GreenNextInvoiceNumber> {
    return this.http.get<GreenNextInvoiceNumber>(`${API_BASE_URL}/purchases/green-coffee/next-invoice-number`);
  }

  /** Cod Prod + anuncio vigente (Especial "CV" / Fondo "RP" fijos). */
  announcementInfo(): Observable<GreenAnnouncementInfo> {
    return this.http.get<GreenAnnouncementInfo>(`${API_BASE_URL}/purchases/green-coffee/announcement-info`);
  }
}
