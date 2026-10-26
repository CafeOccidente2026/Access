import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  Announcement,
  AnnouncementRequest,
  AnnouncementType,
  AnnouncementTypeRequest,
  Fund,
  HuskAnnouncementRequest,
} from '../models/dry-coffee-purchase.model';

/** Unica responsabilidad: llamadas HTTP para "Actualizar Anuncio con Factor" / "Actualizar Anuncio
 *  Pasilla" (Administrador, Operaciones o con el permiso Actualizar Anuncios). */
@Injectable({ providedIn: 'root' })
export class AnnouncementService {
  private readonly http = inject(HttpClient);

  funds(): Observable<Fund[]> {
    return this.http.get<Fund[]>(`${API_BASE_URL}/funds`);
  }

  /** RN-08.3: un anuncio por cada tipo de cafe del fondo, con el mismo precio. */
  publishAll(request: AnnouncementRequest): Observable<Announcement[]> {
    return this.http.post<Announcement[]>(`${API_BASE_URL}/announcements`, request);
  }

  /** RN-08.5: los tipos de cafe del fondo con su anuncio vigente. */
  types(fundId: number): Observable<AnnouncementType[]> {
    return this.http.get<AnnouncementType[]>(`${API_BASE_URL}/announcements/types`, { params: { fundId } });
  }

  /** RN-08.6: un anuncio nuevo solo para ese tipo. */
  publishType(request: AnnouncementTypeRequest): Observable<Announcement> {
    return this.http.post<Announcement>(`${API_BASE_URL}/announcements/types`, request);
  }

  createHusk(request: HuskAnnouncementRequest): Observable<Announcement> {
    return this.http.post<Announcement>(`${API_BASE_URL}/announcements/husk`, request);
  }

  history(): Observable<Announcement[]> {
    return this.http.get<Announcement[]>(`${API_BASE_URL}/announcements/history`);
  }

  /** Anuncio vigente para Agencia+Fondo+Especial - mismo endpoint que ya consultan los 5 formularios
   *  de compra internamente; reusado aca (Compras a Futuro) con Fondo fijo en RP. */
  latest(fundId: number, specialType: string): Observable<Announcement> {
    const params = `fundId=${fundId}&specialType=${encodeURIComponent(specialType)}`;
    return this.http.get<Announcement>(`${API_BASE_URL}/announcements/latest?${params}`);
  }
}
