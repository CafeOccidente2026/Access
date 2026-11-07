import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Announcement } from '../models/dry-coffee-purchase.model';
import { FuturePurchaseRequest, FuturePurchaseResponse } from '../models/future-purchase.model';

/** Unica responsabilidad: llamadas HTTP para "Ingresar Compras a Futuro". Sin preview ni factura -
 *  el compromiso se registra directo (ver FuturePurchaseServiceImpl, backend). */
@Injectable({ providedIn: 'root' })
export class FuturePurchaseService {
  private readonly http = inject(HttpClient);

  /** Anuncios de hoy del especial (RN-21.4.3): la lista de Anuncio. */
  todayAnnouncements(specialType: string): Observable<Announcement[]> {
    const params = `specialType=${encodeURIComponent(specialType)}`;
    return this.http.get<Announcement[]>(`${API_BASE_URL}/purchases/future/announcements?${params}`);
  }

  /** Compras anunciadas de la cédula con saldo (ventana de Facturar Compras Anunciadas, RN-21.5.2). */
  openFor(idNumber: string): Observable<FuturePurchaseResponse[]> {
    return this.http.get<FuturePurchaseResponse[]>(
      `${API_BASE_URL}/purchases/future/open?idNumber=${encodeURIComponent(idNumber)}`,
    );
  }

  create(request: FuturePurchaseRequest): Observable<FuturePurchaseResponse> {
    return this.http.post<FuturePurchaseResponse>(`${API_BASE_URL}/purchases/future`, request);
  }
}
