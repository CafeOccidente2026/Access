import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Role } from '../models/role.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso Role (solo lectura). */
@Injectable({ providedIn: 'root' })
export class RoleService {
  private readonly http = inject(HttpClient);

  list(): Observable<Role[]> {
    return this.http.get<Role[]>(`${API_BASE_URL}/roles`);
  }
}
