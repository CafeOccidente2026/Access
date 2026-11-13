import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { CreateUserRequest, UpdateUserRequest, User } from '../models/user.model';

/** Unica responsabilidad: llamadas HTTP al backend para el recurso User. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  list(): Observable<User[]> {
    return this.http.get<User[]>(`${API_BASE_URL}/users`);
  }

  create(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(`${API_BASE_URL}/users`, request);
  }

  update(id: number, request: UpdateUserRequest): Observable<User> {
    return this.http.put<User>(`${API_BASE_URL}/users/${id}`, request);
  }

  /** S4: quita la espera por intentos fallidos de login. */
  unlock(id: number): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/users/${id}/unlock`, null);
  }
}
