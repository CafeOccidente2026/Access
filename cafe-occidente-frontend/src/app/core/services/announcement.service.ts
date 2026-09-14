import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Announcement, AnnouncementRequest } from '../models/dry-coffee-purchase.model';

/** Unica responsabilidad: llamadas HTTP para "Actualizar Anuncio con Factor" (solo ADMIN). */
@Injectable({ providedIn: 'root' })
export class AnnouncementService {
  private readonly http = inject(HttpClient);

  create(request: AnnouncementRequest): Observable<Announcement> {
    return this.http.post<Announcement>(`${API_BASE_URL}/announcements`, request);
  }

  history(): Observable<Announcement[]> {
    return this.http.get<Announcement[]>(`${API_BASE_URL}/announcements/history`);
  }
}
