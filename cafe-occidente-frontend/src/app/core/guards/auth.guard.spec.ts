import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { Screen, UserRole } from '../models';
import { authGuard } from './auth.guard';

/** Guard de sesion y de rol (RF-01): toda pantalla salvo el login. */
describe('authGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  });

  function signIn(role: UserRole): void {
    localStorage.setItem('cafeoccidente.token', 'jwt');
    localStorage.setItem('cafeoccidente.role', role);
  }

  function run(roles?: readonly UserRole[], screens?: readonly Screen[]): true | string {
    const route = { data: { ...(roles ? { roles } : {}), ...(screens ? { screens } : {}) } } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => authGuard(route, {} as RouterStateSnapshot));
    return result === true ? true : TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('HU-01 CA-4: a granted screen opens its route even if the role does not (RF-02)', () => {
    signIn('OPERACIONES');
    expect(run(['ADMINISTRADOR'], ['ANULAR_DOCUMENTO'])).toBe('/menu-principal');
    localStorage.setItem('cafeoccidente.screens', JSON.stringify(['ANULAR_DOCUMENTO']));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    expect(run(['ADMINISTRADOR'], ['ANULAR_DOCUMENTO'])).toBe(true);
    expect(run(['ADMINISTRADOR'], ['HISTORIAL'])).toBe('/menu-principal');
  });

  it('sends to the login when there is no session', () => {
    expect(run()).toBe('/login');
  });

  it('lets the Administrator into an administration screen', () => {
    signIn('ADMINISTRADOR');
    expect(run(['ADMINISTRADOR'])).toBe(true);
  });

  it('sends the User back to his menu from an administration screen', () => {
    signIn('USUARIO');
    expect(run(['ADMINISTRADOR'])).toBe('/menu-principal');
  });

  it('keeps Operaciones out of operation screens (default roles) but lets it update announcements', () => {
    signIn('OPERACIONES');
    expect(run()).toBe('/menu-principal');
    expect(run(['ADMINISTRADOR', 'OPERACIONES'])).toBe(true);
  });

  it('lets Administrator and User into operation screens by default', () => {
    signIn('USUARIO');
    expect(run()).toBe(true);
  });
});
