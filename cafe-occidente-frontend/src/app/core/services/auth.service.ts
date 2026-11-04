import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Screen, UserRole } from '../models';
import { LoginRequest, LoginResponse, SessionResponse } from '../models/user.model';

const TOKEN_KEY = 'cafeoccidente.token';
const USERNAME_KEY = 'cafeoccidente.username';
const ROLE_KEY = 'cafeoccidente.role';
const AGENCY_ID_KEY = 'cafeoccidente.agencyId';
const AGENCY_NAME_KEY = 'cafeoccidente.agencyName';
const SCREENS_KEY = 'cafeoccidente.screens';

/** Unica responsabilidad: autenticar al usuario y mantener su sesion (token, rol y agencia de trabajo). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly username = signal<string | null>(localStorage.getItem(USERNAME_KEY));
  readonly roleName = signal<string | null>(localStorage.getItem(ROLE_KEY));
  /** Agencia de trabajo (RF-05.1): la decide el servidor; aca solo se muestra. */
  readonly agencyId = signal<number | null>(Number(localStorage.getItem(AGENCY_ID_KEY)) || null);
  readonly agencyName = signal<string | null>(localStorage.getItem(AGENCY_NAME_KEY));
  /** Pantallas concedidas por el Administrador (RF-02); el servidor las vuelve a revisar en cada peticion. */
  readonly screens = signal<readonly Screen[]>(readScreens());

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, request).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_KEY, response.token);
        this.store(response);
      }),
    );
  }

  /** Relee la sesion del servidor (rol, agencia de trabajo y permisos). Sin sesion no hace nada. */
  refreshSession(): Observable<SessionResponse | null> {
    if (!this.isAuthenticated()) {
      return of(null);
    }
    return this.http.get<SessionResponse>(`${API_BASE_URL}/me`).pipe(
      tap((session) => this.store(session)),
      catchError(() => of(null)),
    );
  }

  /** Boton Agencias (RF-05.1, solo el Administrador): el servidor la guarda y la aplica en todas las pantallas. */
  chooseWorkingAgency(agencyId: number): Observable<SessionResponse> {
    return this.http
      .put<SessionResponse>(`${API_BASE_URL}/me/working-agency`, { agencyId })
      .pipe(tap((session) => this.store(session)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(AGENCY_ID_KEY);
    localStorage.removeItem(AGENCY_NAME_KEY);
    localStorage.removeItem(SCREENS_KEY);
    this.username.set(null);
    this.roleName.set(null);
    this.agencyId.set(null);
    this.agencyName.set(null);
    this.screens.set([]);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  isAdmin(): boolean {
    return this.roleName() === 'ADMINISTRADOR';
  }

  /** Sin lista de roles, cualquier sesion pasa. */
  hasRole(roles: readonly UserRole[] | undefined): boolean {
    return !roles || roles.includes(this.roleName() as UserRole);
  }

  /** Su rol lo permite o el Administrador le concedio alguna de esas pantallas (RN-01.4, RF-02). */
  canAccess(roles: readonly UserRole[] | undefined, screens: readonly Screen[] | undefined): boolean {
    return this.hasRole(roles) || (screens ?? []).some((screen) => this.screens().includes(screen));
  }

  private store(session: SessionResponse): void {
    localStorage.setItem(USERNAME_KEY, session.username);
    localStorage.setItem(ROLE_KEY, session.roleName);
    localStorage.setItem(AGENCY_ID_KEY, String(session.agencyId));
    localStorage.setItem(AGENCY_NAME_KEY, session.agencyName);
    localStorage.setItem(SCREENS_KEY, JSON.stringify(session.screens ?? []));
    this.username.set(session.username);
    this.roleName.set(session.roleName);
    this.agencyId.set(session.agencyId);
    this.agencyName.set(session.agencyName);
    this.screens.set(session.screens ?? []);
  }
}

function readScreens(): readonly Screen[] {
  try {
    return JSON.parse(localStorage.getItem(SCREENS_KEY) ?? '[]') as Screen[];
  } catch {
    return [];
  }
}
