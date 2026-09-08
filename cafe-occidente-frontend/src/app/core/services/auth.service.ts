import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap, throwError } from 'rxjs';

import { AuthSession, LoginResponseDto, UserRole } from '../models';
import { API_BASE_URL } from './api-base-url';

const REFRESH_TOKEN_KEY = 'cafeoccidente.refreshToken';

/**
 * Unica responsabilidad: manejar la sesion (login, refresh, logout).
 * El access token vive solo en memoria (se pierde al recargar, se recupera
 * via restoreSession()). El refresh token se guarda en localStorage porque
 * el backend todavia no expone una cookie httpOnly para el; queda expuesto
 * a XSS mientras tanto, el trade-off aceptado hasta que se pueda mover a
 * Set-Cookie httpOnly + SameSite en el login/refresh del backend.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private accessToken: string | null = null;
  private readonly sessionSignal = signal<AuthSession | null>(null);

  readonly session = this.sessionSignal.asReadonly();

  login(username: string, password: string): Observable<AuthSession> {
    return this.http
      .post<LoginResponseDto>(`${API_BASE_URL}/auth/login`, { username, password })
      .pipe(
        tap((response) => this.setSession(response)),
        map((response) => ({ username: response.username, role: response.role })),
      );
  }

  // ponytail: no comparte una unica llamada en vuelo entre 401 concurrentes,
  // cada uno dispara su propio /auth/refresh. Agregar un Observable
  // compartido si empiezan a salir varios 401 a la vez en la misma pantalla.
  restoreSession(): Observable<AuthSession> {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return throwError(() => new Error('No hay sesion para restaurar'));
    }
    return this.http
      .post<LoginResponseDto>(`${API_BASE_URL}/auth/refresh`, { refreshToken })
      .pipe(
        tap((response) => this.setSession(response)),
        map((response) => ({ username: response.username, role: response.role })),
      );
  }

  logout(): void {
    this.accessToken = null;
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.sessionSignal.set(null);
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  hasStoredRefreshToken(): boolean {
    return !!localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  hasRole(role: UserRole): boolean {
    return this.sessionSignal()?.role === role;
  }

  private setSession(response: LoginResponseDto): void {
    this.accessToken = response.accessToken;
    localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    this.sessionSignal.set({ username: response.username, role: response.role });
  }
}
