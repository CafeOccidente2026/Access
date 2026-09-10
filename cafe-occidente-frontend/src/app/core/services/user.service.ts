import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { CreateUserRequest, User } from '../models/user.model';

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
}
