import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Screen } from '../models';
import { Permission } from '../models/permission.model';

/** Unica responsabilidad: conceder, retirar y consultar permisos temporales (RF-02, solo Administrador). */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  private readonly http = inject(HttpClient);

  list(userId: number): Observable<Permission[]> {
    return this.http.get<Permission[]>(`${API_BASE_URL}/permissions`, { params: { userId } });
  }

  grant(userId: number, screens: readonly Screen[]): Observable<Permission[]> {
    return this.http.post<Permission[]>(`${API_BASE_URL}/permissions`, { userId, screens });
  }

  revoke(userId: number, screen: Screen): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/permissions/${userId}/${screen}`);
  }
}
