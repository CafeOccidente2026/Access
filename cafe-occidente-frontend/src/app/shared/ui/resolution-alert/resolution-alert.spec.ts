import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import content from '../../../../../public/assets/data/resolution-alert.json';
import { API_BASE_URL } from '../../../core/config/api.config';
import { Screen, UserRole } from '../../../core/models';
import { ContentService } from '../../../core/services/content.service';
import { ResolutionAlertComponent, resolutionAlertText } from './resolution-alert';

const URL = `${API_BASE_URL}/control-records/resolution-alerts`;

describe('ResolutionAlertComponent (R1, 17-nov)', () => {
  beforeEach(() => localStorage.clear());

  function render(role: UserRole, screens: readonly Screen[] = []) {
    localStorage.setItem('cafeoccidente.token', 'jwt');
    localStorage.setItem('cafeoccidente.role', role);
    localStorage.setItem('cafeoccidente.screens', JSON.stringify(screens));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ContentService, useValue: { loadJson: () => of(content) } }],
    });
    const fixture = TestBed.createComponent(ResolutionAlertComponent);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController) };
  }

  it('the Administrator sees the resolutions about to expire', () => {
    const { fixture, http } = render('ADMINISTRADOR');
    http.expectOne(URL).flush([{ agencyName: 'El Tambo', expirationDate: '2028-07-04', daysLeft: 7 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'La resolución DIAN de El Tambo vence el 04/07/2028 (faltan 7 días). Registre la nueva resolución en el Registro de Control.');
  });

  it('a User with the Registro de Control permission sees them too; without it nothing is asked', () => {
    render('USUARIO', ['REGISTRO_CONTROL']).http.expectOne(URL).flush([]);
    TestBed.resetTestingModule();
    render('USUARIO').http.expectNone(URL);
  });

  it('a failed request shows nothing', () => {
    const { fixture, http } = render('ADMINISTRADOR');
    http.expectOne(URL).flush('x', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('says when it expires today and when it already expired', () => {
    expect(resolutionAlertText({ agencyName: 'Buesaco 1', expirationDate: '2026-11-17', daysLeft: 0 }, content))
      .toBe('La resolución DIAN de Buesaco 1 vence hoy, 17/11/2026. Registre la nueva resolución en el Registro de Control.');
    expect(resolutionAlertText({ agencyName: 'Buesaco 1', expirationDate: '2026-11-16', daysLeft: -1 }, content))
      .toContain('venció el 16/11/2026');
  });
});
