import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Announcement } from '../models/dry-coffee-purchase.model';
import {
  FuturePurchaseRequest,
  FuturePurchaseResponse,
  FuturePurchaseRestoreRow,
  PendingAnnouncementRow,
} from '../models/future-purchase.model';

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

  /** Anunciadas Hoy (HU-31, P2): compras a futuro con saldo, de cualquier fecha, por anuncio. */
  pending(): Observable<PendingAnnouncementRow[]> {
    return this.http.get<PendingAnnouncementRow[]>(`${API_BASE_URL}/purchases/future/pending`);
  }

  /** Restaurar Compras Anunciadas: saldo guardado y recalculado, sin escribir (solo el Administrador). */
  restorePreview(idNumber: string): Observable<FuturePurchaseRestoreRow[]> {
    return this.http.get<FuturePurchaseRestoreRow[]>(`${API_BASE_URL}/purchases/future/restore?idNumber=${encodeURIComponent(idNumber)}`);
  }

  /** Recalcula el saldo desde cero y lo guarda (P6). */
  restore(idNumber: string): Observable<FuturePurchaseRestoreRow[]> {
    return this.http.post<FuturePurchaseRestoreRow[]>(
      `${API_BASE_URL}/purchases/future/restore?idNumber=${encodeURIComponent(idNumber)}`, null);
  }

  create(request: FuturePurchaseRequest): Observable<FuturePurchaseResponse> {
    return this.http.post<FuturePurchaseResponse>(`${API_BASE_URL}/purchases/future`, request);
  }
}
