import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  Announcement,
  AnnouncementRequest,
  Fund,
  HuskAnnouncementRequest,
} from '../models/dry-coffee-purchase.model';

/** Unica responsabilidad: llamadas HTTP para "Actualizar Anuncio con Factor" / "Actualizar Anuncio
 *  Pasilla" (ambas solo ADMIN). */
@Injectable({ providedIn: 'root' })
export class AnnouncementService {
  private readonly http = inject(HttpClient);

  funds(): Observable<Fund[]> {
    return this.http.get<Fund[]>(`${API_BASE_URL}/funds`);
  }

  create(request: AnnouncementRequest): Observable<Announcement> {
    return this.http.post<Announcement>(`${API_BASE_URL}/announcements`, request);
  }

  createHusk(request: HuskAnnouncementRequest): Observable<Announcement> {
    return this.http.post<Announcement>(`${API_BASE_URL}/announcements/husk`, request);
  }

  history(): Observable<Announcement[]> {
    return this.http.get<Announcement[]>(`${API_BASE_URL}/announcements/history`);
  }

  /** Anuncio vigente para Agencia+Fondo+Especial - mismo endpoint que ya consultan los 5 formularios
   *  de compra internamente; reusado aca (Compras a Futuro) con Fondo fijo en RP. */
  latest(agencyId: number, fundId: number, specialType: string): Observable<Announcement> {
    const params = `agencyId=${agencyId}&fundId=${fundId}&specialType=${encodeURIComponent(specialType)}`;
    return this.http.get<Announcement>(`${API_BASE_URL}/announcements/latest?${params}`);
  }
}
