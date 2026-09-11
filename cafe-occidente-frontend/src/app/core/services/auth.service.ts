import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { LoginRequest, LoginResponse } from '../models/user.model';

const TOKEN_KEY = 'cafeoccidente.token';
const USERNAME_KEY = 'cafeoccidente.username';
const ROLE_KEY = 'cafeoccidente.role';
const AGENCY_ID_KEY = 'cafeoccidente.agencyId';
const AGENCY_NAME_KEY = 'cafeoccidente.agencyName';

/** Unica responsabilidad: autenticar al usuario y mantener su sesion (token + datos basicos). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly username = signal<string | null>(localStorage.getItem(USERNAME_KEY));
  readonly roleName = signal<string | null>(localStorage.getItem(ROLE_KEY));
  readonly agencyId = signal<number | null>(Number(localStorage.getItem(AGENCY_ID_KEY)) || null);
  readonly agencyName = signal<string | null>(localStorage.getItem(AGENCY_NAME_KEY));

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_KEY, response.token);
        localStorage.setItem(USERNAME_KEY, response.username);
        localStorage.setItem(ROLE_KEY, response.roleName);
        localStorage.setItem(AGENCY_ID_KEY, String(response.agencyId));
        localStorage.setItem(AGENCY_NAME_KEY, response.agencyName);
        this.username.set(response.username);
        this.roleName.set(response.roleName);
        this.agencyId.set(response.agencyId);
        this.agencyName.set(response.agencyName);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(AGENCY_ID_KEY);
    localStorage.removeItem(AGENCY_NAME_KEY);
    this.username.set(null);
    this.roleName.set(null);
    this.agencyId.set(null);
    this.agencyName.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  isAdmin(): boolean {
    return this.roleName() === 'ADMIN';
  }
}
