import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  Announcement,
  DryCoffeePurchaseRequest,
  DryCoffeePurchaseResponse,
  Fund,
} from '../models/dry-coffee-purchase.model';

/** Unica responsabilidad: llamadas HTTP para el formulario de Compras Café Seco. */
@Injectable({ providedIn: 'root' })
export class DryCoffeePurchaseService {
  private readonly http = inject(HttpClient);

  funds(): Observable<Fund[]> {
    return this.http.get<Fund[]>(`${API_BASE_URL}/funds`);
  }

  latestAnnouncement(agencyId: number, fundId: number): Observable<Announcement> {
    return this.http.get<Announcement>(
      `${API_BASE_URL}/announcements/latest?agencyId=${agencyId}&fundId=${fundId}`,
    );
  }

  create(request: DryCoffeePurchaseRequest): Observable<DryCoffeePurchaseResponse> {
    return this.http.post<DryCoffeePurchaseResponse>(`${API_BASE_URL}/purchases/dry-coffee`, request);
  }
}
