import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/permissions.json';
import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../core/config/api.config';
import { ContentService } from '../../core/services/content.service';
import { PermissionService } from '../../core/services/permission.service';
import { UserService } from '../../core/services/user.service';
import { PermissionsComponent } from './permissions';

const USERS = [
  { id: 1, username: 'admin', roleName: 'ADMINISTRADOR' },
  { id: 5, username: 'richar', roleName: 'USUARIO' },
  { id: 7, username: 'ops', roleName: 'OPERACIONES' },
];

describe('PermissionsComponent (HU-03)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: UserService, useValue: { list: () => of(USERS) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(PermissionsComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  function choose(component: PermissionsComponent, userId: number, granted: unknown[] = []): void {
    component.onUserChange(String(userId));
    http.expectOne(`${API_BASE_URL}/permissions?userId=${userId}`).flush(granted);
  }

  it('CA-1: lists the screens of the catalog, and only non administrators to choose', () => {
    const { fixture, component, el } = render();
    expect(Array.from(el.querySelectorAll('[data-testid="user"] option')).map((o) => o.textContent?.trim())).toEqual([
      'Seleccione un usuario',
      'richar (USUARIO)',
      'ops (OPERACIONES)',
    ]);
    choose(component, 5);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="catalog"]')?.textContent).toContain('Anular Documento');
    expect(component.catalog()).toEqual(['ANULAR_DOCUMENTO', 'ACCESO_TABLAS', 'ACTUALIZAR_ANUNCIOS', 'REGISTRO_CONTROL', 'HISTORIAL']);
  });

  it('Operaciones is not offered Actualizar Anuncios, which its role already has', () => {
    const { component } = render();
    choose(component, 7);
    expect(component.catalog()).not.toContain('ACTUALIZAR_ANUNCIOS');
  });

  it('CA-2: saves the marked screens for the chosen user', () => {
    const { fixture, component, el } = render();
    choose(component, 5);
    component.toggle('ANULAR_DOCUMENTO', true);
    component.toggle('HISTORIAL', true);
    component.toggle('HISTORIAL', false);
    component.save();

    const request = http.expectOne(`${API_BASE_URL}/permissions`);
    expect(request.request.body).toEqual({ userId: 5, screens: ['ANULAR_DOCUMENTO'] });
    request.flush([{ screen: 'ANULAR_DOCUMENTO', grantedAt: '2026-10-24T09:00:00-05:00' }]);
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="current"]')?.textContent).toContain('Anular Documento');
    expect(component.message()).toBe('Permisos guardados.');
  });

  it('CA-3: revokes a granted screen', () => {
    const { component } = render();
    choose(component, 5, [{ screen: 'HISTORIAL', grantedAt: '2026-10-24T09:00:00-05:00' }]);
    expect(component.isGranted('HISTORIAL')).toBe(true);

    component.revoke('HISTORIAL');
    const request = http.expectOne(`${API_BASE_URL}/permissions/5/HISTORIAL`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(component.current()).toEqual([]);
    expect(component.message()).toBe('Permiso retirado.');
  });

  it('shows an error if the server rejects it and does nothing without a user or screens', () => {
    const { component } = render();
    component.save();
    component.revoke('HISTORIAL');
    http.expectNone(`${API_BASE_URL}/permissions`);

    choose(component, 5);
    component.save();
    component.toggle('HISTORIAL', true);
    component.save();
    http.expectOne(`${API_BASE_URL}/permissions`).flush(null, { status: 400, statusText: 'Bad Request' });
    expect(component.error()).toBe(true);

    component.revoke('HISTORIAL');
    http.expectOne(`${API_BASE_URL}/permissions/5/HISTORIAL`).flush(null, { status: 404, statusText: 'Not Found' });
    expect(component.error()).toBe(true);
  });

  it('double submit: while saving, Guardar and Retirar send only once and are disabled', () => {
    const { fixture, component, el } = render();
    choose(component, 5, [{ screen: 'HISTORIAL', grantedAt: '2026-10-24T09:00:00-05:00' }]);
    component.toggle('ANULAR_DOCUMENTO', true);

    component.save();
    component.save();
    http.expectOne(`${API_BASE_URL}/permissions`);
    fixture.detectChanges();
    const buttons = Array.from(el.querySelectorAll('app-button button')) as HTMLButtonElement[];
    expect(buttons.every((b) => b.disabled)).toBe(true);

    component.revoke('HISTORIAL');
    http.expectNone(`${API_BASE_URL}/permissions/5/HISTORIAL`);
  });

  it('double submit: Retirar twice in a row removes the permission only once', () => {
    const { component } = render();
    choose(component, 5, [{ screen: 'HISTORIAL', grantedAt: '2026-10-24T09:00:00-05:00' }]);
    component.revoke('HISTORIAL');
    component.revoke('HISTORIAL');
    http.expectOne(`${API_BASE_URL}/permissions/5/HISTORIAL`).flush(null);
    expect(component.saving()).toBe(false);
  });

  it('only the Administrator can open the route', () => {
    const shell = routes.find((r) => r.children?.some((c) => c.path === 'permisos'));
    expect(shell?.children?.find((c) => c.path === 'permisos')?.data?.['roles']).toEqual(['ADMINISTRADOR']);
  });
});

describe('PermissionService', () => {
  it('sends the user as a query parameter', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(PermissionService).list(5).subscribe();
    TestBed.inject(HttpTestingController).expectOne(`${API_BASE_URL}/permissions?userId=5`).flush([]);
  });
});
