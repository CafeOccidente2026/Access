import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import access from '../../../../public/assets/data/main-access.json';
import { UserRole } from '../../core/models';
import { ContentService } from '../../core/services/content.service';
import { MainAccessComponent } from './main-access';

/** RN-01.1 y Q19: Usuarios, Acceso a Tablas, Menu Habilitacion y Permisos son solo del Administrador. */
describe('MainAccessComponent', () => {
  beforeEach(() => localStorage.clear());

  function render(role: UserRole): HTMLElement {
    localStorage.setItem('cafeoccidente.token', 'jwt');
    localStorage.setItem('cafeoccidente.role', role);
    localStorage.setItem('cafeoccidente.username', 'jperez');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(access) } },
      ],
    });
    const fixture = TestBed.createComponent(MainAccessComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const labels = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('app-menu-button-grid app-button')).map((b) => b.textContent?.trim());

  it('the Administrator sees the administration options', () => {
    expect(labels(render('ADMINISTRADOR'))).toEqual(['Menú Principal', 'Usuarios', 'Acceso a Tablas', 'Menú Habilitación', 'Permisos']);
  });

  it('the User only sees the way to the main menu', () => {
    expect(labels(render('USUARIO'))).toEqual(['Menú Principal']);
  });

  it('welcomes the user of the session', () => {
    expect(render('ADMINISTRADOR').textContent).toContain('jperez');
  });
});
