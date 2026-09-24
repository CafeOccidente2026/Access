import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { FuturePurchaseRequest, FuturePurchaseResponse } from '../models/future-purchase.model';

/** Unica responsabilidad: llamadas HTTP para "Ingresar Compras a Futuro". Sin preview ni factura -
 *  el compromiso se registra directo (ver FuturePurchaseServiceImpl, backend). */
@Injectable({ providedIn: 'root' })
export class FuturePurchaseService {
  private readonly http = inject(HttpClient);

  create(request: FuturePurchaseRequest): Observable<FuturePurchaseResponse> {
    return this.http.post<FuturePurchaseResponse>(`${API_BASE_URL}/purchases/future`, request);
  }
}
