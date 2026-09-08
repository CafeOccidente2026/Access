import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { UserRole } from '../../core/models';
import { API_BASE_URL } from '../../core/services/api-base-url';
import { UserRecord } from './user-management.model';

export interface CreateUserPayload {
  readonly username: string;
  readonly password: string;
  readonly fullName: string;
  readonly role: UserRole;
}

/** Unica responsabilidad: llamadas HTTP a /users (solo accesible por ADMIN). */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  list(): Observable<UserRecord[]> {
    return this.http.get<UserRecord[]>(`${API_BASE_URL}/users`);
  }

  create(payload: CreateUserPayload): Observable<UserRecord> {
    return this.http.post<UserRecord>(`${API_BASE_URL}/users`, payload);
  }

  deactivate(id: number): Observable<void> {
    return this.http.patch<void>(`${API_BASE_URL}/users/${id}/deactivate`, {});
  }
}
