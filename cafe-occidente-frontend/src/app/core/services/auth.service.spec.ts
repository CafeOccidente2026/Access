import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../config/api.config';
import { AuthService } from './auth.service';

/** La agencia de trabajo la decide el servidor (RF-05.1); la sesion solo la guarda para mostrarla. */
describe('AuthService', () => {
  let auth: AuthService;
  let backend: HttpTestingController;
  const tambo = { username: 'admin', roleName: 'ADMINISTRADOR', agencyId: 4, agencyName: 'El Tambo' };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
  });

  it('HU-06 CA-1: the chosen agency becomes the working agency of the session', () => {
    auth.chooseWorkingAgency(4).subscribe();
    const request = backend.expectOne(`${API_BASE_URL}/me/working-agency`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ agencyId: 4 });
    request.flush(tambo);

    expect(auth.agencyId()).toBe(4);
    expect(auth.agencyName()).toBe('El Tambo');
    expect(localStorage.getItem('cafeoccidente.agencyName')).toBe('El Tambo');
  });

  it('opening the app refreshes role and agency from the server', () => {
    localStorage.setItem('cafeoccidente.token', 'jwt');
    auth.refreshSession().subscribe();
    backend.expectOne(`${API_BASE_URL}/me`).flush(tambo);

    expect(auth.roleName()).toBe('ADMINISTRADOR');
    expect(auth.agencyId()).toBe(4);
  });

  it('without a session there is nothing to refresh', () => {
    auth.refreshSession().subscribe();
    backend.expectNone(`${API_BASE_URL}/me`);
  });

  it('hasRole: no role list means any session', () => {
    localStorage.setItem('cafeoccidente.role', 'OPERACIONES');
    const fresh = TestBed.inject(AuthService);
    fresh.roleName.set('OPERACIONES');
    expect(fresh.hasRole(undefined)).toBe(true);
    expect(fresh.hasRole(['ADMINISTRADOR', 'OPERACIONES'])).toBe(true);
    expect(fresh.hasRole(['ADMINISTRADOR', 'USUARIO'])).toBe(false);
  });
});
