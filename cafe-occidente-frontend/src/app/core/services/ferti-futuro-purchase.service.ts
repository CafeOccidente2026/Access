import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Fund } from '../models/dry-coffee-purchase.model';
import {
  FertiFuturoNextInvoiceNumber,
  FertiFuturoPurchaseCalculation,
  FertiFuturoPurchaseRequest,
  FertiFuturoPurchaseResponse,
} from '../models/ferti-futuro-purchase.model';
import { FuturePurchaseResponse } from '../models/future-purchase.model';

/** Unica responsabilidad: llamadas HTTP para el formulario FERTIFUTURO. */
@Injectable({ providedIn: 'root' })
export class FertiFuturoPurchaseService {
  private readonly http = inject(HttpClient);

  funds(): Observable<Fund[]> {
    return this.http.get<Fund[]>(`${API_BASE_URL}/funds`);
  }

  create(request: FertiFuturoPurchaseRequest): Observable<FertiFuturoPurchaseResponse> {
    return this.http.post<FertiFuturoPurchaseResponse>(`${API_BASE_URL}/purchases/ferti-futuro`, request);
  }

  preview(request: FertiFuturoPurchaseRequest): Observable<FertiFuturoPurchaseCalculation> {
    return this.http.post<FertiFuturoPurchaseCalculation>(
      `${API_BASE_URL}/purchases/ferti-futuro/preview`,
      request,
    );
  }

  nextInvoiceNumber(): Observable<FertiFuturoNextInvoiceNumber> {
    return this.http.get<FertiFuturoNextInvoiceNumber>(`${API_BASE_URL}/purchases/ferti-futuro/next-invoice-number`);
  }

  /** Para validar/mostrar el compromiso opcional (Compras a Futuro) antes de liquidar contra él. */
  findFuturePurchase(id: number): Observable<FuturePurchaseResponse> {
    return this.http.get<FuturePurchaseResponse>(`${API_BASE_URL}/purchases/future/${id}`);
  }
}
